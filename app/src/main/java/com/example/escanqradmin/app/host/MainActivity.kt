package com.example.escanqradmin.app.host

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import com.example.escanqradmin.presentation.navigation.AppNavigation
import com.example.escanqradmin.presentation.theme.theme.EscanQRAdminTheme
import dagger.hilt.android.AndroidEntryPoint

import com.example.escanqradmin.presentation.common.util.SetSystemBarsVisibility

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.escanqradmin.domain.repository.ThemeRepository
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeRepository: ThemeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkMode by themeRepository.isDarkMode().collectAsState(initial = false)
            EscanQRAdminTheme(darkTheme = isDarkMode) {
                // Global Immersive Mode: Hide system HUD across all screens
                SetSystemBarsVisibility(visible = false)
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavigation(navController = navController)
                }
            }
        }
    }
}