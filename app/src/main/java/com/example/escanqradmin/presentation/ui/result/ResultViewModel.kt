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

// ── Upload state ──────────────────────────────────────────────────
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
    /** Paso 1 terminado con éxito */
    val step1Done get() = espUploadStatus is EspUploadStatus.Success
    /** Pasos 2 y 3 se desbloquean sólo tras éxito del paso 1 */
    val stepsUnlocked get() = step1Done
}

// ── ViewModel ─────────────────────────────────────────────────────
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
        // Solo reseteamos si es un QR genuinamente nuevo.
        // Si el usuario vuelve de UserSyncScreen (back), el composable se re-ejecuta
        // pero los datos son los mismos → NO reseteamos el progreso del flujo.
        if (_qrData.value == data) return
        _qrData.value = data
        _uiState.value = ResultUiState()
    }

    // ── Paso 1: Subir al ESP32 ────────────────────────────────────
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
                    fail("No se pudo enviar el comando. Verifica la conexión BT con el ESP32.")
                    return@launch
                }

                setLoading("Esperando respuesta del ESP32...")
                val ready = waitFor(inbox, 8_000) { it == "LISTO_PARA_AGREGAR" }
                if (ready == null) {
                    fail("Tiempo agotado. El ESP32 no respondió.")
                    return@launch
                }

                setLoading("Enviando datos del usuario...")
                val json = buildJson(data)
                if (!bluetoothRepository.sendMessage("$json\n")) {
                    fail("No se pudieron enviar los datos del usuario.")
                    return@launch
                }

                setLoading("Guardando en ESP32...")
                val result = waitFor(inbox, 12_000) { it == "USUARIO_GUARDADO" || it.startsWith("ERROR") }
                when {
                    result == null             -> fail("Tiempo agotado al guardar. Revisa la tarjeta.")
                    result == "USUARIO_GUARDADO" -> _uiState.update { it.copy(espUploadStatus = EspUploadStatus.Success) }
                    else                       -> fail("ESP32: ${friendlyError(result)}")
                }
            } catch (e: Exception) {
                fail("Error inesperado: ${e.message ?: "desconocido"}")
            } finally {
                collectJob.cancel()
                inbox.close()
            }
        }
    }

    // ── Paso 2: Marcar sincronización con App Usuario completada ──
    fun markUserSyncCompleted() {
        _uiState.update { it.copy(userSyncCompleted = true) }
    }

    // ── Paso 3: Registrar en servidor + reconectar ESP32 ─────────
    fun registerEntry(onSuccess: () -> Unit) {
        val data = _qrData.value ?: return
        _uiState.update { it.copy(syncStatus = SyncStatus.Loading) }

        viewModelScope.launch {
            val result = syncRepository.syncEntry(data)
            if (result.isSuccess) {
                repository.addRecord(data)
                _uiState.update { it.copy(syncStatus = SyncStatus.Success) }
                onSuccess()
                // Reconectar al ESP32 si la sincronización con el usuario lo desconectó
                reconnectToEsp32()
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Error al sincronizar con el servidor"
                _uiState.update { it.copy(syncStatus = SyncStatus.Error(msg)) }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────
    private fun reconnectToEsp32() {
        viewModelScope.launch {
            delay(400)
            val state = bluetoothRepository.connectionState.value
            if (state !is BluetoothConnectionState.Connected) {
                bluetoothRepository.connectToDevice(HomeViewModel.ESP32_TARGET_MAC)
            }
        }
    }

    private fun buildJson(data: QrContent) =
        """{"cedula":"${data.cedula}","mac":"${data.androidId}","placa":"${data.plate}"}"""

    private suspend fun waitFor(
        inbox: Channel<String>,
        timeoutMs: Long,
        predicate: (String) -> Boolean
    ): String? = withTimeoutOrNull(timeoutMs) {
        var found: String? = null
        while (found == null) {
            val msg = inbox.receive()
            if (predicate(msg)) found = msg
        }
        found
    }

    private fun setLoading(step: String) =
        _uiState.update { it.copy(espUploadStatus = EspUploadStatus.Loading(step)) }

    private fun fail(msg: String) =
        _uiState.update { it.copy(espUploadStatus = EspUploadStatus.Error(msg)) }

    private fun friendlyError(token: String) = when (token) {
        "ERROR_JSON"      -> "JSON inválido recibido por la tarjeta."
        "ERROR_AGREGAR"   -> "Error interno al guardar en la tarjeta."
        "TIMEOUT_AGREGAR" -> "La tarjeta tardó demasiado y canceló la operación."
        else              -> "Error desconocido ($token)."
    }
}
