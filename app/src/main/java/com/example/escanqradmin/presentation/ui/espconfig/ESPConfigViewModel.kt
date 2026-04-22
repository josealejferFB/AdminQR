package com.example.escanqradmin.presentation.ui.espconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.escanqradmin.domain.repository.BluetoothRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ── Models ───────────────────────────────────────────────────────

data class ConsoleMessage(
    val text: String,
    val isSent: Boolean,
    val timestamp: String
)

/**
 * Tracks which step of the ESP32 protocol we are currently in.
 * Each state maps exactly to what the board is waiting to receive.
 */
enum class EspFlowState {
    IDLE,
    /** agregar → board awaits JSON {"cedula":...,"mac":...,"placa":...} */
    WAIT_JSON_AGREGAR,
    /** eliminar → board awaits plain cedula string */
    WAIT_CEDULA_ELIMINAR,
    /** consultar → board awaits plain cedula string */
    WAIT_CEDULA_CONSULTAR,
    /** modificar step-1 → board awaits plain cedula string */
    WAIT_CEDULA_MODIFICAR,
    /** modificar step-2 → board awaits JSON {"mac":...,"placa":...} */
    WAIT_JSON_MODIFICAR,
    /** listar → acumulando respuestas del ESP32 */
    WAIT_LISTING,
}

/**
 * Holds the individual field values for the form currently shown.
 * Only the fields relevant to the current [EspFlowState] are used.
 */
data class FormFields(
    val cedula: String = "",
    val mac: String    = "",
    val placa: String  = ""
)

data class ESPConfigUiState(
    val messages: List<ConsoleMessage> = emptyList(),
    val freeCommand: String = "",          // free-form input bar
    val flowState: EspFlowState = EspFlowState.IDLE,
    val form: FormFields = FormFields(),
    val activeMode: String? = null,
    // Resultados acumulados del comando listar
    val userList: List<String> = emptyList(),
    val showUserList: Boolean = false
)

// ── ViewModel ─────────────────────────────────────────────────────

@HiltViewModel
class ESPConfigViewModel @Inject constructor(
    private val bluetoothRepository: BluetoothRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ESPConfigUiState())
    val uiState = _uiState.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var listingTimeoutJob: Job? = null

    init { observeMessages() }

    // ── Observe incoming BT messages ─────────────────────────────

    private fun observeMessages() {
        viewModelScope.launch {
            bluetoothRepository.messages.collect { raw ->
                val msg = raw.trim()
                addRx(msg)
                advanceFlow(msg)
            }
        }
    }

    /**
     * Maps known ESP32 response tokens to the next [EspFlowState].
     * Mirrors the state-machine in the C++ firmware exactly.
     */
    private fun advanceFlow(msg: String) {
        when (msg) {
            "LISTO_PARA_AGREGAR"   -> enter(EspFlowState.WAIT_JSON_AGREGAR,    "Modo Agregar")
            "LISTO_PARA_ELIMINAR"  -> enter(EspFlowState.WAIT_CEDULA_ELIMINAR, "Modo Eliminar")
            "LISTO_PARA_CONSULTAR" -> enter(EspFlowState.WAIT_CEDULA_CONSULTAR,"Modo Consultar")
            "LISTO_PARA_MODIFICAR" -> enter(EspFlowState.WAIT_CEDULA_MODIFICAR,"Modo Modificar — Paso 1")
            // Board sends this after receiving the cedula; we now wait for the new JSON
            "ENVIE_NUEVOS_DATOS"   -> enter(EspFlowState.WAIT_JSON_MODIFICAR,  "Modo Modificar — Paso 2")
            // Any terminal response → reset to IDLE
            else -> {
                if (msg.startsWith("DATOS_ACTUALES:")) return  // ENVIE_NUEVOS_DATOS follows, keep waiting

                // En modo listing acumulamos cada línea de respuesta del ESP32
                if (_uiState.value.flowState == EspFlowState.WAIT_LISTING) {
                    // Reiniciamos el timer de inactividad cada vez que llega una línea.
                    // Si pasan 1.5s sin recibir nada, asumimos que terminó (el ESP32 no manda fin de lista).
                    listingTimeoutJob?.cancel()
                    listingTimeoutJob = viewModelScope.launch {
                        delay(1500)
                        backToIdle()
                    }

                    if (isTerminal(msg)) {
                        listingTimeoutJob?.cancel()
                        backToIdle()
                    } else {
                        // Solo agregamos a la lista de usuarios las líneas que contienen datos reales
                        if (msg.startsWith("Cedula", ignoreCase = true)) {
                            _uiState.update { it.copy(userList = it.userList + msg) }
                        }
                    }
                    return
                }

                if (isTerminal(msg)) backToIdle()
            }
        }
    }

    private fun isTerminal(msg: String) = msg in setOf(
        "USUARIO_AGREGADO", "USUARIO_ELIMINADO", "USUARIO_MODIFICADO",
        "ERROR_CEDULA_NO_EXISTE", "ERROR_JSON", "ERROR_MODIFICAR",
        "ERROR_ELIMINAR", "USUARIO_NO_EXISTE", "TIMEOUT_MODIFICAR",
        "TIMEOUT_CONSULTAR", "MAC_NO_REGISTRADA", "TIMEOUT",
        "ERROR_AGREGAR"
    ) || msg.startsWith("RESULTADO_CONSULTA:")

    private fun enter(state: EspFlowState, mode: String) {
        _uiState.update { it.copy(flowState = state, activeMode = mode, form = FormFields()) }
    }

    private fun backToIdle() {
        _uiState.update { it.copy(flowState = EspFlowState.IDLE, activeMode = null, form = FormFields()) }
    }

    // ── Listar usuarios ───────────────────────────────────────────

    fun sendListCommand() {
        _uiState.update { it.copy(
            userList    = emptyList(),
            showUserList = true,
            flowState   = EspFlowState.WAIT_LISTING,
            activeMode  = "Listando usuarios..."
        )}
        sendRaw("listar")
        
        // Iniciamos el timer de seguridad inicial
        listingTimeoutJob?.cancel()
        listingTimeoutJob = viewModelScope.launch {
            delay(3000) // 3s iniciales para recibir la primera línea
            if (_uiState.value.userList.isEmpty() && _uiState.value.flowState == EspFlowState.WAIT_LISTING) {
                backToIdle()
            }
        }
    }

    fun dismissUserList() {
        _uiState.update { it.copy(showUserList = false) }
    }

    // ── Form field updates ────────────────────────────────────────

    fun onCedulaChange(v: String) = _uiState.update { it.copy(form = it.form.copy(cedula = v)) }
    fun onMacChange(v: String)    = _uiState.update { it.copy(form = it.form.copy(mac = v)) }
    fun onPlacaChange(v: String)  = _uiState.update { it.copy(form = it.form.copy(placa = v)) }
    fun onFreeCommandChange(v: String) = _uiState.update { it.copy(freeCommand = v) }

    // ── Submit form ───────────────────────────────────────────────

    fun submitForm() {
        val st = _uiState.value
        val payload: String = when (st.flowState) {
            EspFlowState.WAIT_JSON_AGREGAR -> {
                val f = st.form
                """{"cedula":"${f.cedula}","mac":"${f.mac}","placa":"${f.placa}"}"""
            }
            EspFlowState.WAIT_CEDULA_ELIMINAR,
            EspFlowState.WAIT_CEDULA_CONSULTAR,
            EspFlowState.WAIT_CEDULA_MODIFICAR -> st.form.cedula.trim()
            EspFlowState.WAIT_JSON_MODIFICAR -> {
                val f = st.form
                """{"mac":"${f.mac}","placa":"${f.placa}"}"""
            }
            EspFlowState.IDLE,
            EspFlowState.WAIT_LISTING -> return
        }
        sendRaw(payload)
    }

    /** Send a quick command button ("agregar", "eliminar", etc.) */
    fun sendQuickCommand(command: String) = sendRaw(command)

    /** Free-form input bar submit */
    fun sendFreeCommand() {
        val cmd = _uiState.value.freeCommand.trim()
        if (cmd.isBlank()) return
        sendRaw(cmd)
        _uiState.update { it.copy(freeCommand = "") }
    }

    fun dismissForm() = backToIdle()

    // ── Internal helpers ──────────────────────────────────────────

    private fun sendRaw(text: String) {
        viewModelScope.launch {
            val ok = bluetoothRepository.sendMessage(text + "\n")
            addTx(if (ok) text else "⚠ Error enviando: $text")
        }
    }

    private fun addTx(text: String) = addMsg(text, isSent = true)
    private fun addRx(text: String) = addMsg(text, isSent = false)
    private fun addMsg(text: String, isSent: Boolean) {
        _uiState.update {
            it.copy(messages = it.messages + ConsoleMessage(text, isSent, timeFormat.format(Date())))
        }
    }
}
