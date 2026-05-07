package com.example.escanqradmin.presentation.ui.usersync

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.escanqradmin.data.network.ApiConstants
import com.example.escanqradmin.domain.model.BluetoothDeviceDomain
import com.example.escanqradmin.domain.model.ProvisioningPayload
import com.example.escanqradmin.domain.model.SecurityConstants
import com.example.escanqradmin.domain.model.toJson
import com.example.escanqradmin.domain.repository.BluetoothConnectionState
import com.example.escanqradmin.domain.repository.BluetoothRepository
import com.example.escanqradmin.presentation.ui.home.HomeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

// ── Status del flujo de sincronización ────────────────────────────

sealed class UserSyncStatus {
    object Idle : UserSyncStatus()
    object Connecting : UserSyncStatus()
    object Sending : UserSyncStatus()
    object Success : UserSyncStatus()
    data class Error(val message: String) : UserSyncStatus()
}

// ── UI State ──────────────────────────────────────────────────────

data class UserSyncUiState(
    val status: UserSyncStatus = UserSyncStatus.Idle,
    val pairedDevices: List<BluetoothDeviceDomain> = emptyList(),
    val scannedDevices: List<BluetoothDeviceDomain> = emptyList(),
    val isScanning: Boolean = false,
    val selectedDeviceAddress: String? = null,
    /** Preview del payload que se va a enviar (para mostrar en la UI) */
    val endpointPreview: String = "",
    val targetMacPreview: String = ""
)

// ── ViewModel ─────────────────────────────────────────────────────

@HiltViewModel
class UserSyncViewModel @Inject constructor(
    private val bluetoothRepository: BluetoothRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserSyncUiState())
    val uiState = _uiState.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>()
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    private var syncJob: Job? = null

    init {
        observeBluetoothState()
        refreshPairedDevices()
        updatePayloadPreview()
    }

    // ── Inicialización ────────────────────────────────────────────

    private fun updatePayloadPreview() {
        _uiState.update {
            it.copy(
                endpointPreview = ApiConstants.BASE_URL,
                targetMacPreview = HomeViewModel.ESP32_TARGET_MAC
            )
        }
    }

    private fun observeBluetoothState() {
        viewModelScope.launch {
            bluetoothRepository.pairedDevices.collect { devices ->
                _uiState.update { it.copy(pairedDevices = devices) }
            }
        }
        viewModelScope.launch {
            bluetoothRepository.scannedDevices.collect { devices ->
                _uiState.update { it.copy(scannedDevices = devices) }
            }
        }
        viewModelScope.launch {
            bluetoothRepository.isScanning.collect { scanning ->
                _uiState.update { it.copy(isScanning = scanning) }
            }
        }
    }

    private fun refreshPairedDevices() {
        // Forzamos el refresco al inicializar la pantalla.
        // Los dispositivos vinculados se cargan automáticamente via StateFlow del repo.
        // El repo los actualiza al llamar startDiscovery o internamente en init.
    }

    // ── Selección de dispositivo ──────────────────────────────────

    fun selectDevice(address: String) {
        _uiState.update { it.copy(selectedDeviceAddress = address) }
    }

    // ── Discovery ─────────────────────────────────────────────────

    fun startDiscovery() {
        bluetoothRepository.startDiscovery()
    }

    fun stopDiscovery() {
        bluetoothRepository.stopDiscovery()
    }

    // ── Flujo principal de sincronización ─────────────────────────

    /**
     * Conecta al dispositivo indicado por [address] vía RFCOMM (SPP UUID),
     * envía el payload de provisionamiento y cierra la conexión.
     *
     * UUID usado: 00001101-0000-1000-8000-00805F9B34FB (Serial Port Profile)
     * — el mismo que la App de Usuario escucha.
     */
    fun syncToUser(address: String) {
        if (_uiState.value.status is UserSyncStatus.Connecting ||
            _uiState.value.status is UserSyncStatus.Sending) return

        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _uiState.update { it.copy(status = UserSyncStatus.Connecting) }

            // Desconectamos cualquier conexión previa (con el ESP32, etc.) para no interferir
            bluetoothRepository.disconnect()
            delay(300)

            // Iniciamos la conexión RFCOMM
            bluetoothRepository.connectToDevice(address)

            // Esperamos hasta 15s a que se establezca la conexión
            val connected = withTimeoutOrNull(15_000L) {
                var result = false
                bluetoothRepository.connectionState.collect { state ->
                    when (state) {
                        is BluetoothConnectionState.Connected -> {
                            result = true
                            return@collect
                        }
                        is BluetoothConnectionState.Error -> {
                            return@collect
                        }
                        else -> { /* Connecting: seguimos esperando */ }
                    }
                }
                result
            }

            if (connected != true) {
                // Verificamos el estado real por si el withTimeoutOrNull terminó sin error explícito
                val currentState = bluetoothRepository.connectionState.value
                if (currentState !is BluetoothConnectionState.Connected) {
                    val errMsg = if (currentState is BluetoothConnectionState.Error)
                        currentState.message
                    else
                        "No se pudo conectar al dispositivo. ¿Está la App de Usuario en la pantalla de sincronización?"
                    _uiState.update { it.copy(status = UserSyncStatus.Error(errMsg)) }
                    return@launch
                }
            }

            // Conexión establecida → construimos y enviamos el payload
            _uiState.update { it.copy(status = UserSyncStatus.Sending) }

            val payload = ProvisioningPayload(
                endpoint = ApiConstants.BASE_URL,
                target_mac = HomeViewModel.ESP32_TARGET_MAC,
                token = SecurityConstants.PROVISIONING_TOKEN
            )

            // Enviamos el JSON SIN \n al final (la App de Usuario cuenta las llaves {})
            val sent = bluetoothRepository.sendMessage(payload.toJson())

            if (!sent) {
                _uiState.update {
                    it.copy(status = UserSyncStatus.Error("Fallo al enviar los datos. La conexión se interrumpió."))
                }
                return@launch
            }

            // Damos tiempo al socket para que fluyan los bytes antes de cerrar
            delay(600)

            // Cerramos la conexión (el protocolo no requiere ACK de la App de Usuario)
            bluetoothRepository.disconnect()

            _uiState.update { it.copy(status = UserSyncStatus.Success) }
        }
    }

    /** Reinicia el estado para permitir un nuevo intento. */
    fun resetStatus() {
        syncJob?.cancel()
        _uiState.update {
            it.copy(
                status = UserSyncStatus.Idle,
                selectedDeviceAddress = it.selectedDeviceAddress
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothRepository.stopDiscovery()
        // NO desconectamos aquí: si la pantalla se destruye tras éxito,
        // no queremos perturbar la conexión principal con el ESP32.
    }
}
