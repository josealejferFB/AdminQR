package com.example.escanqradmin.presentation.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.escanqradmin.domain.model.QrContent
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
    object Idle    : EspUploadStatus()
    data class Loading(val step: String = "Conectando con ESP32...") : EspUploadStatus()
    object Success : EspUploadStatus()
    data class Error(val message: String) : EspUploadStatus()
}

sealed class SyncStatus {
    object Idle    : SyncStatus()
    object Loading : SyncStatus()
    object Success : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

data class ResultUiState(
    val espUploadStatus: EspUploadStatus = EspUploadStatus.Idle,
    val userSyncCompleted: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.Idle
) {
    val step1Done get() = espUploadStatus is EspUploadStatus.Success
    val stepsUnlocked get() = step1Done
}

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val repository       : HistoryRepository,
    private val bluetoothRepository: BluetoothRepository,
    private val syncRepository   : SyncRepository
) : ViewModel() {

    private val _qrData  = MutableStateFlow<QrContent?>(null)
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
                if (!bluetoothRepository.sendMessage("agregar\n")) {
                    fail("Error al enviar comando. Verifica BT.")
                    return@launch
                }

                setLoading("Esperando al ESP32...")
                val ready = waitFor(inbox, 8_000) { it == "LISTO_PARA_AGREGAR" }
                if (ready == null) {
                    fail("El ESP32 no respondió.")
                    return@launch
                }

                setLoading("Sincronizando datos...")
                val json = """{"cedula":"${data.cedula}","mac":"${data.androidId}","placa":"${data.plate}"}"""
                if (!bluetoothRepository.sendMessage("$json\n")) {
                    fail("Error al enviar datos.")
                    return@launch
                }

                setLoading("Guardando en ESP32...")
                val res = waitFor(inbox, 15_000) { token ->
                    setLoading("ESP32 dice: $token")
                    token.contains("USUARIO_GUARDADO") || token.contains("ERROR")
                }

                if (res != null && res.contains("USUARIO_GUARDADO")) {
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

    fun markUserSyncCompleted() {
        _uiState.update { it.copy(userSyncCompleted = true) }
    }

    fun registerEntry(onSuccess: () -> Unit) {
        val data = _qrData.value ?: return
        _uiState.update { it.copy(syncStatus = SyncStatus.Loading) }
        viewModelScope.launch {
            val result = syncRepository.syncEntry(data)
            if (result.isSuccess) {
                repository.addRecord(data)
                _uiState.update { it.copy(syncStatus = SyncStatus.Success) }
                onSuccess()
                reconnectToEsp32()
            } else {
                _uiState.update { it.copy(syncStatus = SyncStatus.Error("Error de red")) }
            }
        }
    }

    private fun reconnectToEsp32() {
        viewModelScope.launch {
            delay(500)
            if (bluetoothRepository.connectionState.value !is BluetoothConnectionState.Connected) {
                bluetoothRepository.connectToDevice(HomeViewModel.ESP32_TARGET_MAC)
            }
        }
    }

    private suspend fun waitFor(inbox: Channel<String>, timeout: Long, predicate: (String) -> Boolean): String? =
        withTimeoutOrNull(timeout) {
            var found: String? = null
            while (found == null) {
                val msg = inbox.receive()
                if (predicate(msg)) found = msg
            }
            found
        }

    private fun setLoading(step: String) = _uiState.update { it.copy(espUploadStatus = EspUploadStatus.Loading(step)) }
    private fun fail(msg: String) = _uiState.update { it.copy(espUploadStatus = EspUploadStatus.Error(msg)) }
}
