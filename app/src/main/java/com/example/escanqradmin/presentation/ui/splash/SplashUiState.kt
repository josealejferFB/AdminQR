package com.example.escanqradmin.presentation.ui.splash

sealed interface SplashUiState {
    object Idle : SplashUiState
    object Animating : SplashUiState
    object Completed : SplashUiState
}
