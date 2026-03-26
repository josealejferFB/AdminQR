package com.example.escanqradmin.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.escanqradmin.domain.repository.BluetoothConnectionState
import com.example.escanqradmin.domain.repository.BluetoothRepository
import com.example.escanqradmin.domain.repository.HistoryRepository
import com.example.escanqradmin.domain.repository.SyncRepository
import javax.inject.Inject

data class ActiveUser(
    val id: String,
    val name: String,
    val document: String,
    val status: String,
    val plate: String
)

data class HomeUiState(
    val totalScans: Int = 0,
    val totalUsers: Int = 0,
    val activeUsers: List<ActiveUser> = emptyList(),
    val isRefreshing: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HistoryRepository,
    private val bluetoothRepository: BluetoothRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    val scannedDevices = bluetoothRepository.scannedDevices
    val pairedDevices = bluetoothRepository.pairedDevices
    val isScanning = bluetoothRepository.isScanning
    val bluetoothConnectionState = bluetoothRepository.connectionState
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    // SharedFlow para eventos únicos de UI (como el Snackbar)
    private val _snackbarMessages = MutableSharedFlow<String>()
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    private var autoDisconnectJob: Job? = null
    private var isHomeActive = false

    init {
        observeHistory()
        observeBluetoothConnection()
    }

    private fun observeHistory() {
        viewModelScope.launch {
            repository.getHistory().collect { history ->
                val activeUsers = history.map { qr ->
                    ActiveUser(
                        id = qr.androidId,
                        name = qr.userName,
                        document = qr.cedula,
                        status = "VALIDADO",
                        plate = qr.plate
                    )
                }
                
                _uiState.update { 
                    it.copy(
                        activeUsers = activeUsers,
                        totalUsers = activeUsers.size,
                        totalScans = activeUsers.size
                    )
                }
            }
        }
    }

    private fun observeBluetoothConnection() {
        viewModelScope.launch {
            bluetoothConnectionState.collect { state ->
                // Reiniciar o detener el temporizador según el estado de la conexión y la pantalla
                if (state is BluetoothConnectionState.Connected) {
                    if (isHomeActive) resetAutoDisconnectTimer()
                } else {
                    stopAutoDisconnectTimer()
                }
            }
        }
    }

    fun onHomeEntered() {
        isHomeActive = true
        // Al entrar al Home, si ya está conectado, iniciamos el conteo
        if (bluetoothConnectionState.value is BluetoothConnectionState.Connected) {
            resetAutoDisconnectTimer()
        }
    }

    fun onHomeExited() {
        isHomeActive = false
        stopAutoDisconnectTimer()
    }

    fun resetAutoDisconnectTimer() {
        autoDisconnectJob?.cancel()
        autoDisconnectJob = viewModelScope.launch {
            delay(20000) // 20 segundos exactos
            if (isHomeActive && bluetoothConnectionState.value is BluetoothConnectionState.Connected) {
                // 1. Notificar a la UI PRIMERO
                _snackbarMessages.emit("Bluetooth desconectado automáticamente por inactividad")
                // 2. Ejecutar la desconexión
                bluetoothRepository.disconnect()
            }
        }
    }

    fun stopAutoDisconnectTimer() {
        autoDisconnectJob?.cancel()
    }

    // Métodos delegados del Repositorio
    fun startDiscovery() { bluetoothRepository.startDiscovery() }
    fun stopDiscovery() { bluetoothRepository.stopDiscovery() }
    fun connectToDevice(address: String) { bluetoothRepository.connectToDevice(address) }
    fun disconnect() { bluetoothRepository.disconnect() }

    // Métodos de gestión de usuarios
    fun deleteUser(id: String, document: String) {
        viewModelScope.launch {
            syncRepository.deleteEntry(document).onSuccess {
                repository.deleteRecord(id)
            }
        }
    }

    fun updateUser(user: ActiveUser) {
        viewModelScope.launch {
            val qrContent = com.example.escanqradmin.domain.model.QrContent(
                androidId = user.id, userName = user.name, cedula = user.document, plate = user.plate
            )
            syncRepository.updateEntry(qrContent).onSuccess {
                repository.updateRecord(qrContent)
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            syncRepository.refreshConductores().onSuccess { records ->
                repository.syncWithServer(records)
            }
            delay(500)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}
