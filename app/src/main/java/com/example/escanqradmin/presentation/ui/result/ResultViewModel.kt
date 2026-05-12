package com.example.escanqradmin.presentation.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.escanqradmin.data.network.ApiConstants
import com.example.escanqradmin.domain.model.QrContent
import com.example.escanqradmin.domain.model.SecurityConstants
import com.example.escanqradmin.domain.repository.BluetoothConnectionState
import com.example.escanqradmin.domain.repository.BluetoothRepository
import com.example.escanqradmin.domain.repository.HistoryRepository
import com.example.escanqradmin.domain.repository.SyncRepository
import com.example.escanqradmin.presentation.ui.home.HomeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed class EspUploadStatus {
    object Idle : EspUploadStatus()
    data class Loading(val step: String = "Conectando...") : EspUploadStatus()
    object Success : EspUploadStatus()
    data class Error(val message: String) : EspUploadStatus()
}

sealed class SyncStatus {
    object Idle : SyncStatus()
    object Loading : SyncStatus()
    object Success : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

data class ResultUiState(
    val espUploadStatus: EspUploadStatus = EspUploadStatus.Idle,
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val showQrCode: Boolean = false
) {
    val step1Done get() = espUploadStatus is EspUploadStatus.Success
    val step2Done get() = syncStatus is SyncStatus.Success
    val qrUnlocked get() = step1Done && step2Done
    val step2Unlocked get() = step1Done
}

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val repository: HistoryRepository,
    private val bluetoothRepository: BluetoothRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val _qrData = MutableStateFlow<QrContent?>(null)
    val qrData = _qrData.asStateFlow()

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState = _uiState.asStateFlow()

    fun setQrData(data: QrContent) {
        if (_qrData.value == data) return
        _qrData.value = data
        _uiState.value = ResultUiState()
    }

    fun uploadToEsp32() {
        val data = _qrData.value ?: return
        if (_uiState.value.espUploadStatus is EspUploadStatus.Loading) return
        viewModelScope.launch {
            val inbox = Channel<String>(Channel.BUFFERED)
            val collectJob: Job = launch {
                bluetoothRepository.messages.collect { inbox.trySend(it.trim()) }
            }
            try {
                setLoading("Iniciando modo agregar...")
                if (!bluetoothRepository.sendMessage("agregar\n")) { fail("No se pudo conectar. Verifica BT."); return@launch }
                setLoading("Esperando ESP32...")
                val ready = waitFor(inbox, 8_000) { it == "AGREGAR" }
                if (ready == null) { fail("ESP32 no respondió."); return@launch }
                setLoading("Enviando datos...")
                
                // Construcción segura del JSON para evitar que caracteres ocultos o saltos de línea rompan la cadena
                val jsonPayload = org.json.JSONObject().apply {
                    put("cedula", data.cedula.trim().replace("\n", "").replace("\r", ""))
                    put("mac", data.androidId.trim().replace("\n", "").replace("\r", ""))
                    put("placa", data.plate.trim().replace("\n", "").replace("\r", ""))
                }.toString()
                
                if (!bluetoothRepository.sendMessage("$jsonPayload\n")) { fail("Error al enviar datos."); return@launch }
                setLoading("Guardando en ESP32...")
                val res = waitFor(inbox, 15_000) { token ->
                    setLoading("ESP32: $token")
                    token.contains("GUARDADO_OK") || token.contains("CEDULA_EXISTE") || token.contains("JSON_ERROR") || token.contains("ERROR")
                }
                if (res != null && res.contains("GUARDADO_OK")) {
                    _uiState.update { it.copy(espUploadStatus = EspUploadStatus.Success) }
                } else {
                    fail("Fallo: ${res ?: "Tiempo agotado"}")
                }
            } catch (e: Exception) {
                fail("Error: ${e.message}")
            } finally {
                collectJob.cancel()
                inbox.close()
            }
        }
    }

    fun registerEntry(onSuccess: () -> Unit = {}) {
        val data = _qrData.value ?: return
        if (_uiState.value.syncStatus is SyncStatus.Loading) return
        _uiState.update { it.copy(syncStatus = SyncStatus.Loading) }
        viewModelScope.launch {
            val result = syncRepository.syncEntry(data)
            if (result.isSuccess) {
                repository.addRecord(data)
                _uiState.update { it.copy(syncStatus = SyncStatus.Success) }
                onSuccess()
                reconnectToEsp32()
            } else {
                _uiState.update { it.copy(syncStatus = SyncStatus.Error(result.exceptionOrNull()?.message ?: "Error de red")) }
            }
        }
    }

    fun toggleQr() = _uiState.update { it.copy(showQrCode = !it.showQrCode) }

    fun buildProvisioningJson(): String =
        """{"endpoint":"${ApiConstants.BASE_URL}","target_mac":"${HomeViewModel.ESP32_TARGET_MAC}","token":"${SecurityConstants.PROVISIONING_TOKEN}"}"""

    private fun reconnectToEsp32() {
        viewModelScope.launch {
            delay(500)
            if (bluetoothRepository.connectionState.value !is BluetoothConnectionState.Connected)
                bluetoothRepository.connectToDevice(HomeViewModel.ESP32_TARGET_MAC)
        }
    }

    private suspend fun waitFor(inbox: Channel<String>, timeout: Long, predicate: (String) -> Boolean): String? =
        withTimeoutOrNull(timeout) {
            var found: String? = null
            while (found == null) { val msg = inbox.receive(); if (predicate(msg)) found = msg }
            found
        }

    private fun setLoading(step: String) = _uiState.update { it.copy(espUploadStatus = EspUploadStatus.Loading(step)) }
    private fun fail(msg: String) = _uiState.update { it.copy(espUploadStatus = EspUploadStatus.Error(msg)) }
}
