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

    companion object {
        // MAC del ESP32_Seguro activo. Actualizar si se cambia el hardware.
        const val ESP32_TARGET_MAC = "E0:5A:1B:31:29:6E"
    }
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    // SharedFlow para eventos únicos de UI (como el Snackbar)
    private val _snackbarMessages = MutableSharedFlow<String>()
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    // true = la desconexión fue iniciada por el usuario o por una reconexión
    // false = la desconexión fue inesperada (timeout del ESP32, pérdida de señal)
    @Volatile private var isManualDisconnect = false
    // true = estamos en medio de un intento de conexión (evita falso snackbar)
    @Volatile private var isConnecting = false
    private var previousConnectionState: BluetoothConnectionState = BluetoothConnectionState.Idle
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
                // Detectamos si se desconectó automáticamente.
                // Solo emitimos si veníamos de Connected, y no fue manual ni parte de una reconexión.
                if (previousConnectionState is BluetoothConnectionState.Connected &&
                    state !is BluetoothConnectionState.Connected &&
                    !isManualDisconnect &&
                    !isConnecting
                ) {
                    _snackbarMessages.emit("Bluetooth desconectado automáticamente")
                }

                when (state) {
                    is BluetoothConnectionState.Connected -> {
                        isManualDisconnect = false
                        isConnecting = false

                        if (pendingDeleteId != null && pendingDeleteCedula != null && !isDeleteInProgress) {
                            isDeleteInProgress = true
                            delay(500)
                            bluetoothRepository.sendMessage("eliminar\n")
                        }
                    }
                    is BluetoothConnectionState.Connecting -> {
                        // No hacemos nada especial
                    }
                    else -> {
                        isDeleteInProgress = false
                    }
                }

                previousConnectionState = state
            }
        }
    }



    // Métodos delegados del Repositorio
    fun startDiscovery() { bluetoothRepository.startDiscovery() }
    fun stopDiscovery() { bluetoothRepository.stopDiscovery() }
    fun connectToDevice(address: String) { 
        // Marcamos que estamos en reconexión para no disparar snackbar falso
        isConnecting = true
        isManualDisconnect = false
        bluetoothRepository.connectToDevice(address) 
    }
    fun disconnect() { 
        isManualDisconnect = true
        isConnecting = false
        bluetoothRepository.disconnect() 
    }

    /**
     * Busca la dirección del ESP32 objetivo.
     * Prioriza la MAC exacta configurada; si no está vinculada,
     * cae en el primer dispositivo cuyo nombre empiece con "ESP32".
     */
    private fun findEsp32Address(): String? {
        val devices = pairedDevices.value
        return devices.firstOrNull { it.address.equals(ESP32_TARGET_MAC, ignoreCase = true) }?.address
            ?: devices.firstOrNull { it.name?.startsWith("ESP32", ignoreCase = true) == true }?.address
    }

    fun connectToEsp32() {
        viewModelScope.launch {
            val isConnected = bluetoothConnectionState.value is BluetoothConnectionState.Connected
            
            if (isConnected) {
                _snackbarMessages.emit("Ya estás conectado al ESP32")
            } else {
                val esp32Address = findEsp32Address()
                
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
                val esp32Address = findEsp32Address()
                
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
                val esp32Address = findEsp32Address()
                
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
