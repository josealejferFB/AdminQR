package com.example.escanqradmin.presentation.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.escanqradmin.domain.model.QrContent
import com.example.escanqradmin.domain.repository.HistoryRepository
import com.example.escanqradmin.domain.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SyncStatus {
    object Idle : SyncStatus()
    object Loading : SyncStatus()
    object Success : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

data class ResultUiState(
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val showQrCode: Boolean = false
) {
    val syncDone get() = syncStatus is SyncStatus.Success
    val qrUnlocked get() = syncDone
}

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val repository: HistoryRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val _qrData = MutableStateFlow<QrContent?>(null)
    val qrData = _qrData.asStateFlow()

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState = _uiState.asStateFlow()

    fun setQrData(data: QrContent) {
        if (_qrData.value == data) return
        _qrData.value = data
        _uiState.value = ResultUiState()
    }

    fun registerEntry(onSuccess: () -> Unit = {}) {
        val data = _qrData.value ?: return
        if (_uiState.value.syncStatus is SyncStatus.Loading) return
        _uiState.update { it.copy(syncStatus = SyncStatus.Loading) }
        viewModelScope.launch {
            val result = syncRepository.syncEntry(data)
            if (result.isSuccess) {
                repository.addRecord(data)
                _uiState.update { it.copy(syncStatus = SyncStatus.Success) }
                onSuccess()
            } else {
                _uiState.update {
                    it.copy(syncStatus = SyncStatus.Error(result.exceptionOrNull()?.message ?: "Error de red"))
                }
            }
        }
    }

    fun toggleQr() = _uiState.update { it.copy(showQrCode = !it.showQrCode) }
}
