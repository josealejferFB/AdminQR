package com.example.escanqradmin.data.repository

import android.content.Context
import com.example.escanqradmin.domain.repository.ThemeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ThemeRepository {

    private val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    private val keyDarkMode = "dark_mode"

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean(keyDarkMode, false))

    override fun isDarkMode(): Flow<Boolean> = _isDarkMode.asStateFlow()

    override suspend fun setDarkMode(isDark: Boolean) {
        prefs.edit().putBoolean(keyDarkMode, isDark).apply()
        _isDarkMode.value = isDark
    }
}
