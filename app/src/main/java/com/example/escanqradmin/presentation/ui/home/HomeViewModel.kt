package com.example.escanqradmin.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
    val contact: String,
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
    private val syncRepository: com.example.escanqradmin.domain.repository.SyncRepository
) : ViewModel() {

    val scannedDevices = bluetoothRepository.scannedDevices
    val pairedDevices = bluetoothRepository.pairedDevices
    val isScanning = bluetoothRepository.isScanning
    val bluetoothConnectionState = bluetoothRepository.connectionState
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeHistory()
    }

    private fun observeHistory() {
        viewModelScope.launch {
            repository.getHistory().collect { history ->
                val activeUsers = history.map { qr ->
                    ActiveUser(
                        id = qr.androidId,
                        name = qr.userName,
                        document = qr.cedula,
                        status = "VALIDADO", // Default for scanned
                        contact = qr.phone,
                        plate = qr.plate
                    )
                }
                
                _uiState.update { 
                    it.copy(
                        activeUsers = activeUsers,
                        totalUsers = activeUsers.size,
                        totalScans = activeUsers.size // Deriving from history
                    )
                }
            }
        }
    }

    fun deleteUser(id: String) {
        repository.deleteRecord(id)
    }

    fun updateUser(user: ActiveUser) {
        val qrContent = com.example.escanqradmin.domain.model.QrContent(
            androidId = user.id,
            userName = user.name,
            cedula = user.document,
            phone = user.contact,
            plate = user.plate
        )
        repository.updateRecord(qrContent)
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            
            // Sync with server
            syncRepository.fetchEntries().onSuccess { records ->
                repository.syncWithServer(records)
            }
            
            delay(500) // Aesthetic delay
            
            _uiState.update { 
                it.copy(isRefreshing = false)
            }
        }
    }

    // Bluetooth Methods
    fun startDiscovery() {
        bluetoothRepository.startDiscovery()
    }

    fun stopDiscovery() {
        bluetoothRepository.stopDiscovery()
    }

    fun connectToDevice(address: String) {
        bluetoothRepository.connectToDevice(address)
    }

    fun disconnect() {
        bluetoothRepository.disconnect()
    }
}
