package com.example.escanqradmin.presentation.ui.espconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.escanqradmin.domain.repository.BluetoothConnectionState
import com.example.escanqradmin.domain.repository.BluetoothRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
 * Tracks which step of the ESP32 V6 protocol we are currently in.
 * V6 only accepts "config" and "wifi" over Bluetooth.
 * Each state maps exactly to what the board is waiting to receive.
 */
enum class EspFlowState {
    IDLE,
    /** config → board awaits JSON {protocolo, ip_odoo, port} */
    WAIT_JSON_CONFIG,
    /** wifi → board awaits SSID string */
    WAIT_WIFI_SSID,
    /** wifi → board awaits Password string */
    WAIT_WIFI_PASS,
}

/**
 * Holds the individual field values for the form currently shown.
 * Only the fields relevant to the current [EspFlowState] are used.
 */
data class FormFields(
    val protocolo: String = "http",
    val ip_odoo: String = "",
    val port: String = "80",
    val ssid: String = "",
    val password: String = ""
)

data class ESPConfigUiState(
    val messages: List<ConsoleMessage> = emptyList(),
    val freeCommand: String = "",
    val flowState: EspFlowState = EspFlowState.IDLE,
    val form: FormFields = FormFields(),
    val activeMode: String? = null
)

// ── ViewModel ─────────────────────────────────────────────────────

@HiltViewModel
class ESPConfigViewModel @Inject constructor(
    private val bluetoothRepository: BluetoothRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ESPConfigUiState())
    val uiState = _uiState.asStateFlow()

    val connectionState: StateFlow<BluetoothConnectionState> =
        bluetoothRepository.connectionState.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BluetoothConnectionState.Idle
        )

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

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
     * Maps known ESP32 V6 response tokens to the next [EspFlowState].
     * Mirrors the V6 state-machine exactly (only config and wifi flows remain).
     */
    private fun advanceFlow(msg: String) {
        when (msg) {
            "OK_CONFIG" -> enter(EspFlowState.WAIT_JSON_CONFIG, "Configurar red Odoo")
            "SSID:"     -> enter(EspFlowState.WAIT_WIFI_SSID,  "WiFi: Ingrese SSID")
            "PASS:"     -> enter(EspFlowState.WAIT_WIFI_PASS,  "WiFi: Ingrese Password")
            else -> {
                if (isTerminal(msg)) backToIdle()
            }
        }
    }

    private fun isTerminal(msg: String) = msg in setOf(
        "CONFIG_OK", "ERROR_IP", "JSON_ERROR", "TIMEOUT", "CMD_DESCONOCIDO", "REINICIANDO"
    ) || msg.startsWith("SISTEMA LISTO")

    private fun enter(state: EspFlowState, mode: String) {
        _uiState.update { it.copy(flowState = state, activeMode = mode, form = FormFields()) }
    }

    private fun backToIdle() {
        _uiState.update { it.copy(flowState = EspFlowState.IDLE, activeMode = null, form = FormFields()) }
    }

    // ── Form field updates ────────────────────────────────────────

    fun onProtocoloChange(v: String) = _uiState.update { it.copy(form = it.form.copy(protocolo = v)) }
    fun onIpOdooChange(v: String)    = _uiState.update { it.copy(form = it.form.copy(ip_odoo = v)) }
    fun onPortChange(v: String)      = _uiState.update { it.copy(form = it.form.copy(port = v)) }
    fun onSsidChange(v: String)      = _uiState.update { it.copy(form = it.form.copy(ssid = v)) }
    fun onPasswordChange(v: String)  = _uiState.update { it.copy(form = it.form.copy(password = v)) }
    fun onFreeCommandChange(v: String) = _uiState.update { it.copy(freeCommand = v) }

    // ── Submit form ───────────────────────────────────────────────

    fun submitForm() {
        val st = _uiState.value
        val payload: String = when (st.flowState) {
            EspFlowState.WAIT_JSON_CONFIG -> {
                val f = st.form
                val portInt = f.port.toIntOrNull() ?: 0
                buildJsonObject {
                    put("protocolo", f.protocolo)
                    put("ip_odoo", f.ip_odoo)
                    put("port", portInt)
                }.toString()
            }
            EspFlowState.WAIT_WIFI_SSID -> st.form.ssid.trim()
            EspFlowState.WAIT_WIFI_PASS -> st.form.password.trim()
            EspFlowState.IDLE -> return
        }
        sendRaw(payload)
    }

    /** Send a quick command button ("config" | "wifi") */
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
