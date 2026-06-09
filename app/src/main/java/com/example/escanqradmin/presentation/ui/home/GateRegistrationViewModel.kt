package com.example.escanqradmin.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.escanqradmin.domain.repository.BluetoothRepository
import com.example.escanqradmin.domain.repository.BluetoothConnectionState
import com.example.escanqradmin.domain.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

sealed class GateStep {
    data object SelectBluetooth : GateStep()
    data class WiFiConfig(val macAddress: String? = null) : GateStep()
    data class NameGate(val macAddress: String) : GateStep()
    data object Registering : GateStep()
    data object Done : GateStep()
    data class Error(val message: String) : GateStep()
}

data class GateRegistrationUiState(
    val step: GateStep = GateStep.SelectBluetooth,
    val ssid: String = "",
    val password: String = "",
    val gateName: String = "",
    val gateDescription: String = "",
    val macAddress: String = "",
    val registeredGateId: Int? = null,
    val isSubmitting: Boolean = false
)

sealed class GateRegistrationEvent {
    data object CloseDialog : GateRegistrationEvent()
}

@HiltViewModel
class GateRegistrationViewModel @Inject constructor(
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

    fun setSsid(value: String) {
        _uiState.update { it.copy(ssid = value) }
    }

    fun setPassword(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun setGateName(value: String) {
        _uiState.update { it.copy(gateName = value) }
    }

    fun setGateDescription(value: String) {
        _uiState.update { it.copy(gateDescription = value) }
    }

    fun connectToBluetoothDevice(address: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            bluetoothRepository.connectToDevice(address)
            bluetoothRepository.connectionState.first { state ->
                when (state) {
                    is BluetoothConnectionState.Connected -> {
                        _uiState.update {
                            it.copy(
                                step = GateStep.WiFiConfig(),
                                isSubmitting = false
                            )
                        }
                        true
                    }
                    is BluetoothConnectionState.Error -> {
                        _uiState.update {
                            it.copy(
                                step = GateStep.Error(state.message),
                                isSubmitting = false
                            )
                        }
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

            val payload = buildJsonObject {
                put("action", "config_network")
                put("ssid", state.ssid)
                put("password", state.password)
            }.toString()

            val response = bluetoothRepository.sendMessageAndWaitForReply(payload)

            if (response == null) {
                _uiState.update {
                    it.copy(
                        step = GateStep.Error("No se recibió respuesta del ESP32"),
                        isSubmitting = false
                    )
                }
                return@launch
            }

            try {
                val jsonElement = json.parseToJsonElement(response)
                val obj = jsonElement.jsonObject
                val status = obj["status"]?.jsonPrimitive?.content ?: "error"
                if (status == "success") {
                    val mac = obj["mac_address"]?.jsonPrimitive?.content
                    if (mac != null) {
                        _uiState.update {
                            it.copy(
                                step = GateStep.NameGate(macAddress = mac),
                                macAddress = mac,
                                isSubmitting = false
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                step = GateStep.Error("Respuesta del ESP32 no contiene mac_address"),
                                isSubmitting = false
                            )
                        }
                    }
                } else {
                    val msg = obj["message"]?.jsonPrimitive?.content ?: "Error del ESP32"
                    _uiState.update {
                        it.copy(
                            step = GateStep.Error(msg),
                            isSubmitting = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        step = GateStep.Error("Respuesta inválida del ESP32"),
                        isSubmitting = false
                    )
                }
            }
        }
    }

    fun registerGate() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(step = GateStep.Registering, isSubmitting = true) }

            val result = syncRepository.registerGate(
                name = state.gateName,
                macAddress = state.macAddress,
                description = state.gateDescription
            )

            result.fold(
                onSuccess = { response ->
                    _uiState.update {
                        it.copy(
                            step = GateStep.Done,
                            registeredGateId = response.gateId,
                            isSubmitting = false
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            step = GateStep.Error(error.message ?: "Error al registrar en Odoo"),
                            isSubmitting = false
                        )
                    }
                }
            )
        }
    }

    fun resetToSelectBluetooth() {
        _uiState.update { GateRegistrationUiState() }
    }

    fun dismissError() {
        goBackOneStep()
    }

    fun goBackOneStep() {
        val currentStep = _uiState.value.step
        if (currentStep is GateStep.Error || currentStep is GateStep.NameGate || currentStep is GateStep.WiFiConfig) {
            val targetStep = when {
                _uiState.value.macAddress.isNotEmpty() -> GateStep.NameGate(macAddress = _uiState.value.macAddress)
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
        viewModelScope.launch {
            bluetoothRepository.disconnect()
            _events.emit(GateRegistrationEvent.CloseDialog)
        }
    }
}
