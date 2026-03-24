package com.example.escanqradmin.presentation.ui.result

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.example.escanqradmin.domain.model.QrContent

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val repository: com.example.escanqradmin.domain.repository.HistoryRepository
) : ViewModel() {
    private val _qrData = MutableStateFlow<QrContent?>(null)
    val qrData = _qrData.asStateFlow()

    fun setQrData(data: QrContent) {
        _qrData.value = data
    }

    fun registerScan() {
        qrData.value?.let { 
            repository.addRecord(it)
        }
    }
}
