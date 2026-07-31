package com.example.escanqradmin.presentation.ui.home

import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.escanqradmin.data.network.ApiConstants
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

sealed class GateStep {
    data object SelectBluetooth : GateStep()
    data object GettingDeviceInfo : GateStep()
    data class WiFiConfig(val macAddress: String? = null) : GateStep()
    data object RegisteringInOdoo : GateStep()
    data object VerifyingWifi : GateStep()
    data class LocalDone(val odooId: Int? = null, val message: String = "") : GateStep()
    data class Error(val message: String) : GateStep()
}

data class GateRegistrationUiState(
    val step: GateStep = GateStep.SelectBluetooth,
    val gateName: String = "",
    val ssid: String = "",
    val password: String = "",
    val macAddress: String = "",
    val apiToken: String = "",
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingNetworks = true) }
            try {
                // Ya estamos conectados por Bluetooth en este paso
                val payload = """{"action":"scan_wifi"}"""
                val response = bluetoothRepository.sendMessageAndWaitForReply(payload, 15000L)
                
                if (response != null) {
                    val obj = json.parseToJsonElement(response).jsonObject
                    val status = obj["status"]?.jsonPrimitive?.content
                    if (status == "success") {
                        val networksArray = obj["networks"]?.jsonArray
                        if (networksArray != null) {
                            val networks = networksArray.mapNotNull { it.jsonPrimitive.content.takeIf { s -> s.isNotBlank() } }.toSet().sorted()
                            _uiState.update { it.copy(availableNetworks = networks, isLoadingNetworks = false) }
                            return@launch
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently fallback if error
            }
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

            // Forzar estado limpio antes de conectar (BUG-11 fix)
            val currentState = bluetoothRepository.connectionState.value
            if (currentState is BluetoothConnectionState.Connected ||
                currentState is BluetoothConnectionState.Connecting) {
                bluetoothRepository.disconnect()
                delay(500)
            }

            bluetoothRepository.connectToDevice(address)
            
            val connectedState = bluetoothRepository.connectionState.first { state ->
                state is BluetoothConnectionState.Connected || state is BluetoothConnectionState.Error
            }
            
            if (connectedState is BluetoothConnectionState.Error) {
                _uiState.update { it.copy(step = GateStep.Error(connectedState.message), isSubmitting = false) }
                return@launch
            }

            _uiState.update { it.copy(step = GateStep.GettingDeviceInfo, isSubmitting = true) }
            
            val infoPayload = """{"action":"get_info"}"""
            val infoResponse = bluetoothRepository.sendMessageAndWaitForReply(infoPayload, timeoutMs = 10000)
            
            if (infoResponse == null) {
                _uiState.update { it.copy(step = GateStep.Error("No se pudo obtener información del ESP32 (Timeout)"), isSubmitting = false) }
                return@launch
            }

            try {
                val obj = json.parseToJsonElement(infoResponse).jsonObject
                val status = obj["status"]?.jsonPrimitive?.content
                if (status != "success") {
                    val msg = obj["message"]?.jsonPrimitive?.content ?: "Error desconocido"
                    _uiState.update { it.copy(step = GateStep.Error("Error obteniendo info: $msg"), isSubmitting = false) }
                    return@launch
                }
                val mac = obj["mac_address"]?.jsonPrimitive?.content ?: throw Exception("Sin MAC")
                _uiState.update { it.copy(macAddress = mac, step = GateStep.WiFiConfig(mac), isSubmitting = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(step = GateStep.Error("Respuesta inválida al get_info: ${e.message}"), isSubmitting = false) }
            }
        }
    }

    fun sendWiFiConfig() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.macAddress.isBlank()) {
                _uiState.update { it.copy(step = GateStep.Error("No hay MAC address disponible"), isSubmitting = false) }
                return@launch
            }
            
            _uiState.update { it.copy(step = GateStep.RegisteringInOdoo, isSubmitting = true) }

            val safeHostname = state.gateName.lowercase()
                .replace(Regex("[^a-z0-9-]"), "-")
                .trim('-')
                .take(63)
                .ifEmpty { "gate" }

            val odooResult = syncRepository.registerGate(state.gateName, state.macAddress)
            
            if (odooResult.isFailure) {
                _uiState.update { it.copy(step = GateStep.Error("Error en Odoo: ${odooResult.exceptionOrNull()?.message}"), isSubmitting = false) }
                return@launch
            }
            
            val response = odooResult.getOrNull()
            val apiToken = response?.apiToken
            if (apiToken.isNullOrBlank()) {
                _uiState.update { it.copy(step = GateStep.Error("Odoo no retornó un api_token válido"), isSubmitting = false) }
                return@launch
            }
            
            _uiState.update { it.copy(apiToken = apiToken) }

            val payload = buildJsonObject {
                put("action", "config_network")
                put("ssid", state.ssid.trim())
                put("password", state.password.trim())
                put("bt_name", state.gateName)
                put("hostname", safeHostname)
                put("api_token", apiToken)
                put("odoo_url", "${ApiConstants.BASE_URL}/api/v1/gates/ping")
            }.toString()

            _uiState.update { it.copy(step = GateStep.VerifyingWifi, isSubmitting = true) }

            val btResponse = bluetoothRepository.sendMessageAndWaitForReply(payload, 40000L)

            if (btResponse == null) {
                val btState = bluetoothRepository.connectionState.value
                if (btState is BluetoothConnectionState.Idle) {
                    _uiState.update {
                        it.copy(
                            step = GateStep.Error("El ESP32 se desconectó sin confirmar (posible éxito, revisa Odoo)."),
                            isSubmitting = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(step = GateStep.Error("No se recibió respuesta del ESP32"), isSubmitting = false) }
                }
                return@launch
            }

            try {
                val obj = json.parseToJsonElement(btResponse).jsonObject
                val status = obj["status"]?.jsonPrimitive?.content ?: "error"
                if (status == "success") {
                    val msg = response.message ?: "Portón registrado exitosamente"
                    _uiState.update {
                        it.copy(
                            step = GateStep.LocalDone(odooId = response.gateId, message = msg),
                            odooRegistered = true,
                            odooMessage = msg,
                            isSubmitting = false
                        )
                    }
                    _events.emit(GateRegistrationEvent.GateRegisteredInOdoo(
                        name = state.gateName,
                        macAddress = state.macAddress,
                        btName = state.gateName,
                        hostname = safeHostname,
                        odooId = response.gateId
                    ))
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

    /** Cierra el diálogo tras registración exitosa */
    fun closeDone() {
        viewModelScope.launch {
            if (bluetoothRepository.connectionState.value is BluetoothConnectionState.Connected) {
                bluetoothRepository.disconnect()
            }
            _events.emit(GateRegistrationEvent.CloseDialog)
            resetState()
        }
    }
}
