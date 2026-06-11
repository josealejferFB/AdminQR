package com.example.escanqradmin.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.escanqradmin.domain.repository.ThemeRepository
import com.example.escanqradmin.domain.repository.BluetoothRepository
import com.example.escanqradmin.domain.repository.HistoryRepository
import com.example.escanqradmin.domain.repository.SyncRepository
import com.example.escanqradmin.domain.repository.BluetoothConnectionState
import com.example.escanqradmin.domain.model.GateInfo
import com.example.escanqradmin.domain.repository.GateRepository
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
    val isRefreshing: Boolean = false,
    val isServerOnline: Boolean = true,
    val gates: List<GateInfo> = emptyList(),
    val selectedMacAddress: String? = null,
    val gateUsers: List<ActiveUser> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HistoryRepository,
    private val bluetoothRepository: BluetoothRepository,
    private val syncRepository: SyncRepository,
    private val themeRepository: ThemeRepository,
    private val gateRepository: GateRepository
) : ViewModel() {

    val isDarkMode = themeRepository.isDarkMode().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    fun toggleTheme() {
        viewModelScope.launch {
            themeRepository.setDarkMode(!isDarkMode.value)
        }
    }

    val scannedDevices = bluetoothRepository.scannedDevices
    val pairedDevices  = bluetoothRepository.pairedDevices
    val isScanning     = bluetoothRepository.isScanning
    val bluetoothConnectionState = bluetoothRepository.connectionState

    companion object {
        // El portón se descubre por nombre en lugar de MAC fija.
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    // SharedFlow para eventos únicos de UI (como el Snackbar)
    private val _snackbarMessages = MutableSharedFlow<String>()
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    // true = la desconexión fue iniciada por el usuario
    @Volatile private var isManualDisconnect = false
    // true = estamos en medio de un intento de conexión
    @Volatile private var isConnecting = false
    private var previousConnectionState: BluetoothConnectionState = BluetoothConnectionState.Idle

    private val _localGates = MutableStateFlow<List<GateInfo>>(emptyList())

    fun addLocalGate(name: String, macAddress: String, btName: String, hostname: String) {
        val gate = GateInfo(
            id = null,
            name = name,
            macAddress = macAddress,
            btName = btName,
            hostname = hostname,
            isOdooRegistered = false
        )
        _localGates.update { it + gate }
        loadGates()
    }

    fun markGateAsOdooRegistered(macAddress: String, odooId: Int) {
        _localGates.update { gates ->
            gates.map { if (it.macAddress == macAddress) it.copy(id = odooId, isOdooRegistered = true) else it }
        }
        loadGates()
    }

    init {
        observeHistory()
        observeBluetoothConnection()
        refreshData()
    }

    private fun observeHistory() {
        viewModelScope.launch {
            repository.getHistory().collect { history ->
                val activeUsers = history.map { qr ->
                    ActiveUser(
                        id       = qr.androidId,
                        name     = qr.userName,
                        document = qr.cedula,
                        status   = "VALIDADO",
                        plate    = qr.plate
                    )
                }
                _uiState.update {
                    it.copy(
                        activeUsers = activeUsers,
                        totalUsers  = activeUsers.size,
                        totalScans  = activeUsers.size
                    )
                }
            }
        }
    }

    private fun observeBluetoothConnection() {
        viewModelScope.launch {
            bluetoothConnectionState.collect { state ->
                // Notificamos desconexión inesperada (solo si veníamos de Connected)
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
                    }
                    else -> { /* nada especial */ }
                }

                previousConnectionState = state
            }
        }
    }

    // ── Métodos delegados del Repositorio BT ─────────────────────
    fun startDiscovery() { bluetoothRepository.startDiscovery() }
    fun stopDiscovery()  { bluetoothRepository.stopDiscovery() }

    fun connectToDevice(address: String) {
        isConnecting = true
        isManualDisconnect = false
        bluetoothRepository.connectToDevice(address)
    }

    fun disconnect() {
        isManualDisconnect = true
        isConnecting = false
        bluetoothRepository.disconnect()
    }

    fun connectToGate(gate: GateInfo) {
        viewModelScope.launch {
            val isConnected = bluetoothConnectionState.value is BluetoothConnectionState.Connected
            if (isConnected) {
                val currentAddress = (bluetoothConnectionState.value as BluetoothConnectionState.Connected).deviceAddress
                if (currentAddress == gate.macAddress) {
                    _snackbarMessages.emit("Ya conectado a ${gate.name}")
                    return@launch
                }
                disconnect()
                delay(500)
            }
            connectToDevice(gate.macAddress)
        }
    }

    fun getConnectedGate(gates: List<GateInfo>): GateInfo? {
        val state = bluetoothConnectionState.value
        if (state is BluetoothConnectionState.Connected) {
            return gates.firstOrNull { it.macAddress == state.deviceAddress }
        }
        return null
    }

    // ── Multi-Gate support (V8) ────────────────────────────────────
    fun loadGates() {
        viewModelScope.launch {
            gateRepository.getGates().onSuccess { odooGates ->
                val local = _localGates.value.filter { !it.isOdooRegistered }
                _uiState.update { it.copy(gates = odooGates + local, isServerOnline = true) }
            }.onFailure {
                val local = _localGates.value.filter { !it.isOdooRegistered }
                _uiState.update { it.copy(gates = local, isServerOnline = false) }
            }
        }
    }

    fun selectGate(macAddress: String?) {
        if (macAddress != null) {
            val gate = _uiState.value.gates.find { it.macAddress == macAddress }
            _uiState.update { it.copy(selectedMacAddress = macAddress) }
            if (gate?.id != null) {
                loadGateUsers(gate.id!!)
            } else {
                _uiState.update { it.copy(gateUsers = emptyList()) }
            }
        } else {
            _uiState.update { it.copy(selectedMacAddress = null, gateUsers = emptyList()) }
        }
    }

    private fun loadGateUsers(gateId: Int) {
        viewModelScope.launch {
            syncRepository.getGateUsers(gateId).onSuccess { users ->
                val activeUsers = users.map { qr ->
                    ActiveUser(
                        id = qr.androidId,
                        name = qr.userName,
                        document = qr.cedula,
                        status = "VALIDADO",
                        plate = qr.plate
                    )
                }
                _uiState.update { it.copy(gateUsers = activeUsers) }
            }.onFailure {
                _uiState.update { it.copy(gateUsers = emptyList(), isServerOnline = false) }
            }
        }
    }

    fun renameGate(gateId: Int, newName: String) {
        viewModelScope.launch {
            gateRepository.updateGateName(gateId, newName).onSuccess {
                _snackbarMessages.emit("Nombre actualizado correctamente")
                loadGates()
            }.onFailure { e ->
                _snackbarMessages.emit("Error al renombrar: ${e.message}")
            }
        }
    }

    // ── Gestión de usuarios (solo vía servidor) ───────────────────

    fun deleteUser(id: String, document: String) {
        viewModelScope.launch {
            syncRepository.deleteEntry(document).onSuccess {
                repository.deleteRecord(id)
                _snackbarMessages.emit("Usuario eliminado correctamente")
                _uiState.update { it.copy(isServerOnline = true) }
            }.onFailure { e ->
                _snackbarMessages.emit("Error al eliminar del servidor: ${e.message}")
                _uiState.update { it.copy(isServerOnline = false) }
            }
        }
    }

    fun updateUser(user: ActiveUser) {
        viewModelScope.launch {
            val qrContent = com.example.escanqradmin.domain.model.QrContent(
                androidId = user.id,
                userName  = user.name,
                cedula    = user.document,
                plate     = user.plate
            )
            syncRepository.updateEntry(qrContent).onSuccess {
                repository.updateRecord(qrContent)
                _snackbarMessages.emit("Usuario modificado correctamente")
                _uiState.update { it.copy(isServerOnline = true) }
            }.onFailure { e ->
                _snackbarMessages.emit("Error al modificar en servidor: ${e.message}")
                _uiState.update { it.copy(isServerOnline = false) }
            }
        }
    }

    suspend fun sendMessageAndWaitForReply(message: String, timeoutMs: Long = 10000): String? {
        return bluetoothRepository.sendMessageAndWaitForReply(message, timeoutMs)
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            syncRepository.refreshConductores()
                .onSuccess { records ->
                    repository.syncWithServer(records)
                    _uiState.update { it.copy(isServerOnline = true) }
                }
                .onFailure {
                    _uiState.update { it.copy(isServerOnline = false) }
                }
            loadGates()
            delay(500)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}
