package com.example.escanqradmin.domain.repository

import kotlinx.coroutines.flow.Flow

interface ThemeRepository {
    fun isDarkMode(): Flow<Boolean>
    suspend fun setDarkMode(isDark: Boolean)
}
