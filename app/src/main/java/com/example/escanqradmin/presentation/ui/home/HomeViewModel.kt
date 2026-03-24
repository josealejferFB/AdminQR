package com.example.escanqradmin.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val repository: com.example.escanqradmin.domain.repository.HistoryRepository
) : ViewModel() {
    
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
                        contact = "", // Not in QR
                        plate = qr.plate
                    )
                }
                
                _uiState.update { 
                    it.copy(
                        activeUsers = activeUsers,
                        totalUsers = activeUsers.size,
                        totalScans = activeUsers.size // Simplifying for now
                    )
                }
            }
        }
    }

    fun deleteUser(id: String) {
        repository.deleteRecord(id)
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            // Simulación de carga (1.5 segundos)
            delay(1500)
            _uiState.update { 
                it.copy(
                    totalScans = it.totalScans + 1,
                    isRefreshing = false
                )
            }
        }
    }
}
