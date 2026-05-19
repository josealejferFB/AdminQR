package com.example.escanqradmin.presentation.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<SplashUiState>(SplashUiState.Idle)
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        startSplashAnimation()
    }

    private fun startSplashAnimation() {
        viewModelScope.launch {
            _uiState.value = SplashUiState.Animating
            // Wait for 2000ms for logo animation to complete
            delay(2000)
            _uiState.value = SplashUiState.Completed
        }
    }
}
