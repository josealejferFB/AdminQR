package com.example.escanqradmin.presentation.ui.home

import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.escanqradmin.domain.model.SecurityConstants
import com.example.escanqradmin.domain.repository.BluetoothConnectionState
import com.example.escanqradmin.domain.repository.BluetoothRepository
import com.example.escanqradmin.domain.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

sealed class GateStep {
    data object SelectBluetooth : GateStep()
    data class WiFiConfig(val macAddress: String? = null) : GateStep()
    data object VerifyingWifi : GateStep()
    data object RegisteringInOdoo : GateStep()
    data class LocalDone(val odooId: Int? = null, val message: String = "") : GateStep()
    data class Error(val message: String) : GateStep()
}

data class GateRegistrationUiState(
    val step: GateStep = GateStep.SelectBluetooth,
    val gateName: String = "",
    val ssid: String = "",
    val password: String = "",
    val macAddress: String = "",
    val odooRegistered: Boolean = false,
    val odooMessage: String = "",
    val isSubmitting: Boolean = false,
    val availableNetworks: List<String> = emptyList(),
    val isLoadingNetworks: Boolean = false
)

sealed class GateRegistrationEvent {
    data object CloseDialog : GateRegistrationEvent()
    data class GateRegisteredInOdoo(
        val name: String,
        val macAddress: String,
        val btName: String,
        val hostname: String,
        val odooId: Int?
    ) : GateRegistrationEvent()
}

@HiltViewModel
class GateRegistrationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothRepository: BluetoothRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GateRegistrationUiState())
    val uiState: StateFlow<GateRegistrationUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GateRegistrationEvent>()
    val events: SharedFlow<GateRegistrationEvent> = _events.asSharedFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /** Envía comando report_ip al ESP32 para re-enviar MAC+IP a Odoo */
    suspend fun sendReportIp(): Boolean {
        val payload = """{"action":"report_ip"}"""
        val response = bluetoothRepository.sendMessageAndWaitForReply(payload, timeoutMs = 15000)
        if (response == null) return false
        return try {
            val obj = json.parseToJsonElement(response).jsonObject
            obj["status"]?.jsonPrimitive?.content == "success"
        } catch (_: Exception) {
            false
        }
    }

    private var lastDeviceAddress: String? = null
    private var verificationJob: Job? = null

    fun setSsid(value: String) {
        _uiState.update { it.copy(ssid = value) }
    }

    fun setPassword(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun refreshAvailableNetworks() {
        _uiState.update { it.copy(isLoadingNetworks = true) }
        try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                val networkList = mutableSetOf<String>()

                val connInfo = wifiManager.connectionInfo
                val connectedSsid = connInfo.ssid?.trim('"', ' ') ?: ""
                if (connectedSsid.isNotBlank() && connectedSsid != "<unknown ssid>") {
                    networkList.add(connectedSsid)
                }

                @Suppress("DEPRECATION")
                val configured = wifiManager.configuredNetworks
                if (configured != null) {
                    for (cfg in configured) {
                        val ssid = cfg.SSID.trim('"', ' ')
                        if (ssid.isNotBlank() && ssid.length > 1) {
                            networkList.add(ssid)
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        availableNetworks = networkList.toList().sorted(),
                        isLoadingNetworks = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoadingNetworks = false) }
            }
        } catch (_: Exception) {
            _uiState.update { it.copy(isLoadingNetworks = false) }
        }
    }

    fun selectNetwork(ssid: String) {
        _uiState.update { it.copy(ssid = ssid) }
    }

    fun setGateName(value: String) {
        _uiState.update { it.copy(gateName = value) }
    }

    fun connectToBluetoothDevice(address: String, deviceName: String? = null) {
        viewModelScope.launch {
            lastDeviceAddress = address
            _uiState.update { it.copy(isSubmitting = true) }
            bluetoothRepository.connectToDevice(address)
            bluetoothRepository.connectionState.first { state ->
                when (state) {
                    is BluetoothConnectionState.Connected -> {
                        _uiState.update { it.copy(step = GateStep.WiFiConfig(), isSubmitting = false) }
                        true
                    }
                    is BluetoothConnectionState.Error -> {
                        _uiState.update { it.copy(step = GateStep.Error(state.message), isSubmitting = false) }
                        true
                    }
                    else -> false
                }
            }
        }
    }

    fun sendWiFiConfig() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(isSubmitting = true) }

            val safeHostname = state.gateName.lowercase()
                .replace(Regex("[^a-z0-9-]"), "-")
                .trim('-')
                .take(63)
                .ifEmpty { "gate" }

            val payload = buildJsonObject {
                put("action", "config_network")
                put("ssid", state.ssid)
                put("password", state.password)
                put("bt_name", state.gateName)
                put("hostname", safeHostname)
                put("iot_token", SecurityConstants.IOT_TOKEN)
            }.toString()

            val response = bluetoothRepository.sendMessageAndWaitForReply(payload, 40000L)

            if (response == null) {
                _uiState.update { it.copy(step = GateStep.Error("No se recibió respuesta del ESP32"), isSubmitting = false) }
                return@launch
            }

            try {
                val jsonElement = json.parseToJsonElement(response)
                val obj = jsonElement.jsonObject
                val status = obj["status"]?.jsonPrimitive?.content ?: "error"
                if (status == "success") {
                    val mac = obj["mac_address"]?.jsonPrimitive?.content
                    if (mac != null) {
                        _uiState.update { it.copy(step = GateStep.RegisteringInOdoo, macAddress = mac, isSubmitting = true) }
                        
                        syncRepository.registerGate(state.gateName, mac)
                            .onSuccess { response ->
                                val gateId = response.gateId
                                val msg = response.message ?: "Portón registrado exitosamente"
                                _uiState.update {
                                    it.copy(
                                        step = GateStep.LocalDone(odooId = gateId, message = msg),
                                        odooRegistered = true,
                                        odooMessage = msg,
                                        isSubmitting = false
                                    )
                                }
                                _events.emit(GateRegistrationEvent.GateRegisteredInOdoo(
                                    name = state.gateName,
                                    macAddress = mac,
                                    btName = state.gateName,
                                    hostname = safeHostname,
                                    odooId = gateId
                                ))
                            }
                            .onFailure { e ->
                                _uiState.update {
                                    it.copy(
                                        step = GateStep.Error("Error al registrar en Odoo: ${e.message}"),
                                        isSubmitting = false
                                    )
                                }
                            }
                    } else {
                        _uiState.update { it.copy(step = GateStep.Error("Respuesta del ESP32 no contiene mac_address"), isSubmitting = false) }
                    }
                } else {
                    val msg = obj["message"]?.jsonPrimitive?.content ?: "Error del ESP32"
                    _uiState.update { it.copy(step = GateStep.Error(msg), isSubmitting = false) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(step = GateStep.Error("Respuesta inválida del ESP32"), isSubmitting = false) }
            }
        }
    }



    fun resetToSelectBluetooth() {
        verificationJob?.cancel()
        lastDeviceAddress = null
        _uiState.update { GateRegistrationUiState() }
    }

    private fun resetState() {
        verificationJob?.cancel()
        lastDeviceAddress = null
        _uiState.update { GateRegistrationUiState() }
    }

    fun dismissError() {
        goBackOneStep()
    }

    fun goBackOneStep() {
        val currentStep = _uiState.value.step
        if (currentStep is GateStep.Error || currentStep is GateStep.WiFiConfig) {
            val targetStep = when {
                _uiState.value.ssid.isNotEmpty() -> GateStep.WiFiConfig()
                else -> GateStep.SelectBluetooth
            }
            _uiState.update { it.copy(step = targetStep) }
        }
    }

    fun goBackTwoSteps() {
        val currentStep = _uiState.value.step
        if (currentStep is GateStep.Error) {
            val targetStep = when {
                _uiState.value.macAddress.isNotEmpty() && _uiState.value.ssid.isNotEmpty() -> GateStep.WiFiConfig()
                else -> GateStep.SelectBluetooth
            }
            _uiState.update { it.copy(step = targetStep) }
        }
    }

    fun closeDialog() {
        resetState()
        viewModelScope.launch {
            bluetoothRepository.disconnect()
            _events.emit(GateRegistrationEvent.CloseDialog)
        }
    }

    /** Cierra el diálogo tras registración exitosa (desconecta BT) */
    fun closeDone() {
        viewModelScope.launch {
            bluetoothRepository.disconnect()
            _events.emit(GateRegistrationEvent.CloseDialog)
            resetState()
        }
    }
}
