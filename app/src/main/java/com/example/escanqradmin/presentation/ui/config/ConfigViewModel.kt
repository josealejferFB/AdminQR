package com.example.escanqradmin.presentation.ui.config

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.escanqradmin.data.network.ApiConstants
import com.example.escanqradmin.domain.model.GateInfo
import com.example.escanqradmin.domain.repository.GateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
data class ServerHistory(
    val protocol: String = "http",
    val host: String,
    val port: String,
    val endpointSync: String = "/api/control_acceso",
    val endpointConductores: String = "/api/get_conductores",
    val endpointRegisterGate: String = "/api/v1/gates/register",
    val endpointGatesList: String = "/api/v1/gates/list",
    val endpointGateUpdate: String = "/api/v1/gates/update",
    val endpointGateUsers: String = "/api/v1/gates/{id}/users",
    val timestamp: Long
)

data class ConfigUiState(
    val protocol: String = "http",
    val host: String = "",
    val port: String = "",
    val endpointSync: String = "/api/control_acceso",
    val endpointConductores: String = "/api/get_conductores",
    val endpointRegisterGate: String = "/api/v1/gates/register",
    val endpointGatesList: String = "/api/v1/gates/list",
    val endpointGateUpdate: String = "/api/v1/gates/update",
    val endpointGateUsers: String = "/api/v1/gates/{id}/users",
    val serverHistory: List<ServerHistory> = emptyList(),
    val isLoading: Boolean = false,
    val gates: List<GateInfo> = emptyList(),
    val isLoadingGates: Boolean = false
)

@HiltViewModel
class ConfigViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gateRepository: GateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState = _uiState.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>()
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    private val prefs: android.content.SharedPreferences = context.getSharedPreferences("api_config_prefs", Context.MODE_PRIVATE)
    private val historyKey = "server_history_v2"

    init {
        loadCurrentConfig()
        loadHistory()
    }

    private fun loadCurrentConfig() {
        _uiState.update {
            it.copy(
                protocol = ApiConstants.getProtocol(),
                host = ApiConstants.getHost(),
                port = ApiConstants.getPort(),
                endpointSync = ApiConstants.getEndpointSync(),
                endpointConductores = ApiConstants.getEndpointConductores(),
                endpointRegisterGate = ApiConstants.getEndpointRegisterGate(),
                endpointGatesList = ApiConstants.getEndpointGatesList(),
                endpointGateUpdate = ApiConstants.getEndpointGateUpdate(),
                endpointGateUsers = ApiConstants.getEndpointGateUsers()
            )
        }
    }

    private fun loadHistory() {
        val historyJson = prefs.getString(historyKey, "[]") ?: "[]"
        try {
            val historyList: List<ServerHistory> = Json.decodeFromString(historyJson)
            _uiState.update { it.copy(serverHistory = historyList.sortedByDescending { h -> h.timestamp }) }
        } catch (e: Exception) {
            // Fallback for old history if necessary or just start fresh
            _uiState.update { it.copy(serverHistory = emptyList()) }
        }
    }

    fun onProtocolChange(value: String) {
        _uiState.update { it.copy(protocol = value) }
    }

    fun onHostChange(value: String) {
        _uiState.update { it.copy(host = value) }
    }

    fun onPortChange(value: String) {
        _uiState.update { it.copy(port = value) }
    }

    fun onEndpointSyncChange(value: String) {
        _uiState.update { it.copy(endpointSync = value) }
    }

    fun onEndpointConductoresChange(value: String) {
        _uiState.update { it.copy(endpointConductores = value) }
    }

    fun onEndpointRegisterGateChange(value: String) {
        _uiState.update { it.copy(endpointRegisterGate = value) }
    }

    fun onEndpointGatesListChange(value: String) {
        _uiState.update { it.copy(endpointGatesList = value) }
    }

    fun onEndpointGateUpdateChange(value: String) {
        _uiState.update { it.copy(endpointGateUpdate = value) }
    }

    fun onEndpointGateUsersChange(value: String) {
        _uiState.update { it.copy(endpointGateUsers = value) }
    }

    fun saveConfig() {
        viewModelScope.launch {
            val protocol = _uiState.value.protocol
            val host = _uiState.value.host.trim()
            val port = _uiState.value.port.trim()
            val endpointSync = _uiState.value.endpointSync.trim()
            val endpointConductores = _uiState.value.endpointConductores.trim()
            val endpointRegisterGate = _uiState.value.endpointRegisterGate.trim()
            val endpointGatesList = _uiState.value.endpointGatesList.trim()
            val endpointGateUpdate = _uiState.value.endpointGateUpdate.trim()
            val endpointGateUsers = _uiState.value.endpointGateUsers.trim()

            if (host.isEmpty()) {
                _snackbarMessages.emit("Por favor ingrese la dirección del host")
                return@launch
            }

            if (port.isNotEmpty() && (port.toIntOrNull() == null || port.toIntOrNull()!! <= 0)) {
                _snackbarMessages.emit("El puerto debe ser un número válido")
                return@launch
            }

            _uiState.update { it.copy(isLoading = true) }

            try {
                ApiConstants.saveConfig(
                    context, protocol, host, port,
                    endpointSync, endpointConductores,
                    endpointRegisterGate, endpointGatesList,
                    endpointGateUpdate, endpointGateUsers
                )
                saveToHistory(
                    protocol, host, port,
                    endpointSync, endpointConductores,
                    endpointRegisterGate, endpointGatesList,
                    endpointGateUpdate, endpointGateUsers
                )
                _snackbarMessages.emit("Configuración guardada correctamente")
            } catch (e: Exception) {
                _snackbarMessages.emit("Error al guardar: ${e.message}")
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun saveToHistory(
        protocol: String, host: String, port: String,
        endpointSync: String, endpointConductores: String,
        endpointRegisterGate: String, endpointGatesList: String,
        endpointGateUpdate: String, endpointGateUsers: String
    ) {
        val currentHistory = _uiState.value.serverHistory.toMutableList()
        
        currentHistory.removeAll { 
            it.host == host && it.port == port && it.protocol == protocol && 
            it.endpointSync == endpointSync && it.endpointConductores == endpointConductores &&
            it.endpointRegisterGate == endpointRegisterGate && it.endpointGatesList == endpointGatesList &&
            it.endpointGateUpdate == endpointGateUpdate && it.endpointGateUsers == endpointGateUsers
        }
        
        currentHistory.add(0, ServerHistory(
            protocol, host, port,
            endpointSync, endpointConductores,
            endpointRegisterGate, endpointGatesList,
            endpointGateUpdate, endpointGateUsers,
            System.currentTimeMillis()
        ))
        
        val limitedHistory = currentHistory.take(15)
        
        _uiState.update { it.copy(serverHistory = limitedHistory) }
        
        val json = Json.encodeToString(limitedHistory)
        prefs.edit().putString(historyKey, json).apply()
    }

    fun selectFromHistory(history: ServerHistory) {
        _uiState.update {
            it.copy(
                protocol = history.protocol,
                host = history.host,
                port = history.port,
                endpointSync = history.endpointSync,
                endpointConductores = history.endpointConductores,
                endpointRegisterGate = history.endpointRegisterGate,
                endpointGatesList = history.endpointGatesList,
                endpointGateUpdate = history.endpointGateUpdate,
                endpointGateUsers = history.endpointGateUsers
            )
        }
    }

    fun removeFromHistory(history: ServerHistory) {
        val newList = _uiState.value.serverHistory.filter { it != history }
        _uiState.update { it.copy(serverHistory = newList) }
        val json = Json.encodeToString(newList)
        prefs.edit().putString(historyKey, json).apply()
    }

    fun fetchGates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingGates = true) }
            gateRepository.getGates()
                .onSuccess { gates ->
                    _uiState.update { it.copy(gates = gates) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(gates = emptyList()) }
                    _snackbarMessages.emit("Error al listar portones: ${e.message}")
                }
            _uiState.update { it.copy(isLoadingGates = false) }
        }
    }
}
