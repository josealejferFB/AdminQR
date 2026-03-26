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
    private var isDeleteInProgress = false

    init {
        observeHistory()
        observeBluetoothConnection()
        observeBluetoothMessages()
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

    private fun observeBluetoothMessages() {
        viewModelScope.launch {
            bluetoothRepository.messages.collect { raw ->
                val msg = raw.trim()
                handleBluetoothDeleteResponse(msg)
            }
        }
    }

    private var pendingDeleteCedula: String? = null
    private var pendingDeleteId: String? = null
    private var pendingUpdateUser: ActiveUser? = null

    private fun handleBluetoothDeleteResponse(msg: String) {
        when (msg) {
            "LISTO_PARA_ELIMINAR" -> {
                pendingDeleteCedula?.let { cedula ->
                    viewModelScope.launch {
                        bluetoothRepository.sendMessage("$cedula\n")
                    }
                }
            }
            "USUARIO_ELIMINADO" -> {
                val id = pendingDeleteId
                val cedula = pendingDeleteCedula
                pendingDeleteId = null
                pendingDeleteCedula = null
                isDeleteInProgress = false
                
                if (id != null && cedula != null) {
                    viewModelScope.launch {
                        deleteFromServer(id, cedula)
                    }
                }
            }
            "ERROR_ELIMINAR", "USUARIO_NO_EXISTE" -> {
                pendingDeleteId = null
                pendingDeleteCedula = null
                isDeleteInProgress = false
                viewModelScope.launch {
                    _snackbarMessages.emit("Error al eliminar en ESP32: $msg")
                }
            }
            "LISTO_PARA_MODIFICAR" -> {
                pendingUpdateUser?.let { user ->
                    viewModelScope.launch {
                        bluetoothRepository.sendMessage("${user.document}\n")
                    }
                }
            }
            "ENVIE_NUEVOS_DATOS" -> {
                pendingUpdateUser?.let { user ->
                    viewModelScope.launch {
                        val json = """{"mac":"${user.plate}","placa":"${user.name}"}"""
                        bluetoothRepository.sendMessage("$json\n")
                    }
                }
            }
            "USUARIO_MODIFICADO" -> {
                val user = pendingUpdateUser
                pendingUpdateUser = null
                isDeleteInProgress = false
                
                if (user != null) {
                    viewModelScope.launch {
                        updateOnServer(user)
                    }
                }
            }
            "ERROR_MODIFICAR", "ERROR_JSON" -> {
                pendingUpdateUser = null
                isDeleteInProgress = false
                viewModelScope.launch {
                    _snackbarMessages.emit("Error al modificar en ESP32: $msg")
                }
            }
        }
    }

    private fun observeBluetoothConnection() {
        viewModelScope.launch {
            bluetoothConnectionState.collect { state ->
                if (state is BluetoothConnectionState.Connected) {
                    if (isHomeActive) resetAutoDisconnectTimer()
                    
                    if (pendingDeleteId != null && pendingDeleteCedula != null && !isDeleteInProgress) {
                        isDeleteInProgress = true
                        delay(500)
                        bluetoothRepository.sendMessage("eliminar\n")
                    }
                } else {
                    stopAutoDisconnectTimer()
                    isDeleteInProgress = false
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

    fun connectToEsp32() {
        viewModelScope.launch {
            val isConnected = bluetoothConnectionState.value is BluetoothConnectionState.Connected
            
            if (isConnected) {
                _snackbarMessages.emit("Ya estás conectado al ESP32")
            } else {
                val esp32Address = pairedDevices.value
                    .firstOrNull { it.name?.startsWith("ESP32", ignoreCase = true) == true }
                    ?.address
                
                if (esp32Address != null) {
                    _snackbarMessages.emit("Conectando al ESP32...")
                    connectToDevice(esp32Address)
                } else {
                    _snackbarMessages.emit("No hay ESP32 vinculado. Conecta uno primero.")
                }
            }
        }
    }

    // Métodos de gestión de usuarios
    fun deleteUser(id: String, document: String) {
        viewModelScope.launch {
            val isConnected = bluetoothConnectionState.value is BluetoothConnectionState.Connected
            
            if (isConnected) {
                startDeleteProcess(id, document)
            } else {
                val esp32Address = pairedDevices.value
                    .firstOrNull { it.name?.startsWith("ESP32", ignoreCase = true) == true }
                    ?.address
                
                if (esp32Address != null) {
                    _snackbarMessages.emit("Conectando al ESP32...")
                    connectToDevice(esp32Address)
                    delay(2000)
                    val connected = bluetoothConnectionState.value is BluetoothConnectionState.Connected
                    if (connected) {
                        startDeleteProcess(id, document)
                    } else {
                        _snackbarMessages.emit("No se pudo conectar al ESP32. No se puede eliminar.")
                    }
                } else {
                    _snackbarMessages.emit("No hay ESP32 vinculado. Conecta uno primero.")
                }
            }
        }
    }

    private fun startDeleteProcess(id: String, document: String) {
        pendingDeleteId = id
        pendingDeleteCedula = document
        isDeleteInProgress = true
        viewModelScope.launch {
            delay(500)
            bluetoothRepository.sendMessage("eliminar\n")
        }
    }

    private suspend fun deleteFromServer(id: String, cedula: String) {
        syncRepository.deleteEntry(cedula).onSuccess {
            repository.deleteRecord(id)
            _snackbarMessages.emit("Usuario eliminado correctamente")
        }.onFailure { e ->
            _snackbarMessages.emit("Error al eliminar del servidor: ${e.message}")
        }
    }

    fun updateUser(user: ActiveUser) {
        viewModelScope.launch {
            val isConnected = bluetoothConnectionState.value is BluetoothConnectionState.Connected
            
            if (isConnected) {
                startUpdateProcess(user)
            } else {
                val esp32Address = pairedDevices.value
                    .firstOrNull { it.name?.startsWith("ESP32", ignoreCase = true) == true }
                    ?.address
                
                if (esp32Address != null) {
                    _snackbarMessages.emit("Conectando al ESP32...")
                    connectToDevice(esp32Address)
                    delay(2000)
                    val connected = bluetoothConnectionState.value is BluetoothConnectionState.Connected
                    if (connected) {
                        startUpdateProcess(user)
                    } else {
                        _snackbarMessages.emit("No se pudo conectar al ESP32. No se puede modificar.")
                    }
                } else {
                    _snackbarMessages.emit("No hay ESP32 vinculado. Conecta uno primero.")
                }
            }
        }
    }

    private fun startUpdateProcess(user: ActiveUser) {
        pendingUpdateUser = user
        isDeleteInProgress = true
        viewModelScope.launch {
            delay(500)
            bluetoothRepository.sendMessage("modificar\n")
        }
    }

    private suspend fun updateOnServer(user: ActiveUser) {
        val qrContent = com.example.escanqradmin.domain.model.QrContent(
            androidId = user.id, userName = user.name, cedula = user.document, plate = user.plate
        )
        syncRepository.updateEntry(qrContent).onSuccess {
            repository.updateRecord(qrContent)
            _snackbarMessages.emit("Usuario modificado correctamente")
        }.onFailure { e ->
            _snackbarMessages.emit("Error al modificar en servidor: ${e.message}")
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
