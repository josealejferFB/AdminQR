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

sealed class UserSyncStatus {
    object Idle : UserSyncStatus()
    object Connecting : UserSyncStatus()
    object Sending : UserSyncStatus()
    object Success : UserSyncStatus()
    data class Error(val message: String) : UserSyncStatus()
}

data class UserSyncUiState(
    val status: UserSyncStatus = UserSyncStatus.Idle,
    val pairedDevices: List<BluetoothDeviceDomain> = emptyList(),
    val scannedDevices: List<BluetoothDeviceDomain> = emptyList(),
    val isScanning: Boolean = false,
    val selectedDeviceAddress: String? = null,
    val endpointPreview: String = "",
    val targetMacPreview: String = ""
)

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
        updatePayloadPreview()
    }

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

    fun selectDevice(address: String) {
        _uiState.update { it.copy(selectedDeviceAddress = address) }
    }

    fun startDiscovery() {
        bluetoothRepository.startDiscovery()
    }

    fun stopDiscovery() {
        bluetoothRepository.stopDiscovery()
    }

    fun syncToUser(address: String) {
        if (_uiState.value.status is UserSyncStatus.Connecting ||
            _uiState.value.status is UserSyncStatus.Sending) return

        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _uiState.update { it.copy(status = UserSyncStatus.Connecting) }
            bluetoothRepository.disconnect()
            delay(300)

            bluetoothRepository.connectToDevice(address)

            val connected = withTimeoutOrNull(15_000L) {
                var result = false
                bluetoothRepository.connectionState.collect { state ->
                    if (state is BluetoothConnectionState.Connected) {
                        result = true
                        return@collect
                    }
                }
                result
            }

            if (connected != true) {
                _uiState.update { it.copy(status = UserSyncStatus.Error("No se pudo conectar al teléfono.")) }
                return@launch
            }

            _uiState.update { it.copy(status = UserSyncStatus.Sending) }
            val payload = ProvisioningPayload(
                endpoint = ApiConstants.BASE_URL,
                target_mac = HomeViewModel.ESP32_TARGET_MAC,
                token = SecurityConstants.PROVISIONING_TOKEN
            )

            if (bluetoothRepository.sendMessage(payload.toJson())) {
                delay(600)
                bluetoothRepository.disconnect()
                _uiState.update { it.copy(status = UserSyncStatus.Success) }
            } else {
                _uiState.update { it.copy(status = UserSyncStatus.Error("Fallo al enviar los datos.")) }
            }
        }
    }

    fun resetStatus() {
        syncJob?.cancel()
        _uiState.update { it.copy(status = UserSyncStatus.Idle) }
    }
}
