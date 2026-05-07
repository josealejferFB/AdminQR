package com.example.escanqradmin.presentation.ui.config

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.escanqradmin.data.network.ApiConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerHistory(
    val host: String,
    val port: String,
    val timestamp: Long
)

data class ConfigUiState(
    val host: String = "",
    val port: String = "",
    val serverHistory: List<ServerHistory> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class ConfigViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState = _uiState.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>()
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    private val prefs: android.content.SharedPreferences = context.getSharedPreferences("api_config_prefs", Context.MODE_PRIVATE)
    private val historyKey = "server_history"

    init {
        loadCurrentConfig()
        loadHistory()
    }

    private fun loadCurrentConfig() {
        _uiState.update {
            it.copy(
                host = ApiConstants.getHost(),
                port = ApiConstants.getPort()
            )
        }
    }

    private fun loadHistory() {
        val historyJson = prefs.getString(historyKey, "[]") ?: "[]"
        try {
            val historyList = parseHistory(historyJson)
            _uiState.update { it.copy(serverHistory = historyList) }
        } catch (e: Exception) {
            _uiState.update { it.copy(serverHistory = emptyList()) }
        }
    }

    private fun parseHistory(json: String): List<ServerHistory> {
        val list = mutableListOf<ServerHistory>()
        if (json == "[]" || json.isEmpty()) return list
        
        json.removeSurrounding("[").removeSuffix("]").split("},{").forEach { item ->
            val cleanItem = item.removePrefix("{").removeSuffix("}")
            val parts = cleanItem.split(",")
            var host = ""
            var port = ""
            var timestamp = 0L
            parts.forEach { part ->
                val keyValue = part.split(":")
                if (keyValue.size == 2) {
                    when (keyValue[0].trim().replace("\"", "")) {
                        "host" -> host = keyValue[1].trim().replace("\"", "")
                        "port" -> port = keyValue[1].trim().replace("\"", "")
                        "timestamp" -> timestamp = keyValue[1].trim().toLongOrNull() ?: 0L
                    }
                }
            }
            if (host.isNotEmpty() && port.isNotEmpty()) {
                list.add(ServerHistory(host, port, timestamp))
            }
        }
        return list.sortedByDescending { it.timestamp }
    }

    fun onHostChange(value: String) {
        _uiState.update { it.copy(host = value) }
    }

    fun onPortChange(value: String) {
        _uiState.update { it.copy(port = value) }
    }

    fun saveConfig() {
        viewModelScope.launch {
            val host = _uiState.value.host.trim()
            val port = _uiState.value.port.trim()

            if (host.isEmpty() || port.isEmpty()) {
                _snackbarMessages.emit("Por favor complete todos los campos")
                return@launch
            }

            if (port.toIntOrNull() == null || port.toIntOrNull()!! <= 0) {
                _snackbarMessages.emit("El puerto debe ser un número válido")
                return@launch
            }

            _uiState.update { it.copy(isLoading = true) }

            try {
                ApiConstants.saveConfig(context, host, port)
                saveToHistory(host, port)
                _snackbarMessages.emit("Configuración guardada correctamente")
            } catch (e: Exception) {
                _snackbarMessages.emit("Error al guardar: ${e.message}")
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun saveToHistory(host: String, port: String) {
        val currentHistory = _uiState.value.serverHistory.toMutableList()
        
        currentHistory.removeAll { it.host == host && it.port == port }
        
        currentHistory.add(0, ServerHistory(host, port, System.currentTimeMillis()))
        
        val limitedHistory = currentHistory.take(10)
        
        _uiState.update { it.copy(serverHistory = limitedHistory) }
        
        val json = limitedHistory.joinToString(",", "[", "]") { 
            "{\"host\":\"${it.host}\",\"port\":\"${it.port}\",\"timestamp\":${it.timestamp}}" 
        }
        prefs.edit().putString(historyKey, json).apply()
    }

    fun selectFromHistory(history: ServerHistory) {
        _uiState.update {
            it.copy(
                host = history.host,
                port = history.port
            )
        }
    }
}