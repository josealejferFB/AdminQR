package com.example.escanqradmin.presentation.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.escanqradmin.domain.model.QrContent
import com.example.escanqradmin.domain.repository.BluetoothRepository
import com.example.escanqradmin.domain.repository.HistoryRepository
import com.example.escanqradmin.domain.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

// ── Upload state ──────────────────────────────────────────────────
sealed class EspUploadStatus {
    object Idle    : EspUploadStatus()
    /** step = human-readable description of what is happening right now */
    data class Loading(val step: String = "Conectando con ESP32...") : EspUploadStatus()
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
    val syncStatus: SyncStatus = SyncStatus.Idle
)

// ── ViewModel ─────────────────────────────────────────────────────
@HiltViewModel
class ResultViewModel @Inject constructor(
    private val repository      : HistoryRepository,
    private val bluetoothRepository: BluetoothRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val _qrData  = MutableStateFlow<QrContent?>(null)
    val qrData = _qrData.asStateFlow()

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState = _uiState.asStateFlow()

    fun setQrData(data: QrContent) {
        _qrData.value = data
        _uiState.value = ResultUiState()          // Reset for every fresh scan
    }

    // ... (uploadToEsp32 and other helper methods remain the same) ...
    // I will include them in the full replacement to ensure consistency

    fun uploadToEsp32() {
        val data = _qrData.value ?: return
        if (_uiState.value.espUploadStatus is EspUploadStatus.Loading) return   // guard double-tap

        viewModelScope.launch {
            val inbox = Channel<String>(Channel.BUFFERED)
            val collectJob: Job = launch {
                bluetoothRepository.messages.collect { msg ->
                    inbox.trySend(msg.trim())
                }
            }

            try {
                setLoading("Iniciando modo agregar...")
                val sentCmd = bluetoothRepository.sendMessage("agregar\n")
                if (!sentCmd) {
                    fail("No se pudo enviar el comando al ESP32. Verifica la conexión.")
                    return@launch
                }

                setLoading("Esperando respuesta del ESP32...")
                val ready = waitForToken(inbox, timeoutMs = 8_000) { it == "LISTO_PARA_AGREGAR" }
                if (ready == null) {
                    fail("Tiempo de espera agotado. El ESP32 no respondió. Intenta de nuevo.")
                    return@launch
                }

                setLoading("Enviando datos del usuario...")
                val json = buildJson(data)
                val sentJson = bluetoothRepository.sendMessage(json + "\n")
                if (!sentJson) {
                    fail("No se pudieron enviar los datos del usuario.")
                    return@launch
                }

                setLoading("Guardando en ESP32...")
                val result = waitForToken(inbox, timeoutMs = 12_000) { token ->
                    token == "USUARIO_GUARDADO" || token.startsWith("ERROR")
                }

                when {
                    result == null -> fail("Tiempo de espera agotado al guardar. Revisa la tarjeta.")
                    result == "USUARIO_GUARDADO" ->
                        _uiState.update { it.copy(espUploadStatus = EspUploadStatus.Success) }
                    else ->
                        fail("ESP32: ${friendlyError(result)}")
                }

            } catch (e: Exception) {
                fail("Error inesperado: ${e.message ?: "desconocido"}")
            } finally {
                collectJob.cancel()
                inbox.close()
            }
        }
    }

    private fun buildJson(data: QrContent): String =
        """{"cedula":"${data.cedula}","mac":"${data.androidId}","placa":"${data.plate}"}"""

    private suspend fun waitForToken(
        inbox    : Channel<String>,
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

    private fun setLoading(step: String) {
        _uiState.update { it.copy(espUploadStatus = EspUploadStatus.Loading(step)) }
    }

    private fun fail(msg: String) {
        _uiState.update { it.copy(espUploadStatus = EspUploadStatus.Error(msg)) }
    }

    private fun friendlyError(token: String) = when (token) {
        "ERROR_JSON"        -> "JSON inválido recibido por la tarjeta."
        "ERROR_AGREGAR"     -> "Error interno al guardar en la tarjeta."
        "TIMEOUT_AGREGAR"   -> "La tarjeta tardó demasiado y canceló la operación."
        else                -> "Error desconocido ($token)."
    }

    // ── Register local entry and sync with backend ───────────────
    fun registerEntry(onSuccess: () -> Unit) {
        val data = _qrData.value ?: return
        _uiState.update { it.copy(syncStatus = SyncStatus.Loading) }

        viewModelScope.launch {
            val result = syncRepository.syncEntry(data)
            if (result.isSuccess) {
                repository.addRecord(data)
                _uiState.update { it.copy(syncStatus = SyncStatus.Success) }
                onSuccess()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Error al sincronizar con el servidor"
                _uiState.update { it.copy(syncStatus = SyncStatus.Error(errorMsg)) }
            }
        }
    }
}
