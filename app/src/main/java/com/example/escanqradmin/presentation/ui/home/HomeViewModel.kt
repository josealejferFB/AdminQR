package com.example.escanqradmin.presentation.ui.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class ActiveUser(
    val id: String,
    val name: String,
    val document: String,
    val status: String,
    val contact: String,
    val plate: String
)

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {
    
    private val _totalScans = MutableStateFlow(20)
    val totalScans = _totalScans.asStateFlow()
    
    private val _totalUsers = MutableStateFlow(32)
    val totalUsers = _totalUsers.asStateFlow()

    private val _activeUsers = MutableStateFlow(
        listOf(
            ActiveUser("1", "José Fernandez", "C.I: V-27.865.738", "VALIDADO", "+58 (412) 442-0728", "B-XQ 4920"),
            ActiveUser("2", "Juan Velasquez", "ID: 31.053.989", "ESPERANDO", "+58 (412) 535-9589", "K-LP 1123")
        )
    )
    val activeUsers = _activeUsers.asStateFlow()
}
