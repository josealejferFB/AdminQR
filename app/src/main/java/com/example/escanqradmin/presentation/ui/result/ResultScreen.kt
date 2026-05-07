package com.example.escanqradmin.presentation.ui.result

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.escanqradmin.domain.model.QrContent
import com.example.escanqradmin.presentation.navigation.UserSync
import com.example.escanqradmin.presentation.theme.color.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    navController: NavHostController,
    onScanAgain: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel()
) {
    val qrData by viewModel.qrData.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    LaunchedEffect(savedStateHandle) {
        savedStateHandle?.getStateFlow("sync_success", false)?.collect { success ->
            if (success) {
                viewModel.markUserSyncCompleted()
                savedStateHandle.set("sync_success", false)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activación de Usuario", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onScanAgain) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryBlue)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            qrData?.let { data ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(data.userName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Cédula: ${data.cedula}", color = Color.Gray)
                    }
                }

                StepItem(1, "ESP32", "Registro físico", uiState.step1Done, uiState.espUploadStatus is EspUploadStatus.Loading) {
                    Button(onClick = { viewModel.uploadToEsp32() }, enabled = uiState.espUploadStatus !is EspUploadStatus.Loading && !uiState.step1Done) {
                        Text(if (uiState.step1Done) "COMPLETADO" else "SUBIR AL ESP32")
                    }
                }

                StepItem(2, "Usuario", "Sincronizar App", uiState.userSyncCompleted, false, !uiState.step1Done) {
                    Button(onClick = { navController.navigate(UserSync(data.userName, data.cedula)) }, enabled = uiState.step1Done && !uiState.userSyncCompleted) {
                        Text(if (uiState.userSyncCompleted) "COMPLETADO" else "SINCRONIZAR")
                    }
                }

                StepItem(3, "Odoo", "Registro servidor", uiState.syncStatus is SyncStatus.Success, uiState.syncStatus is SyncStatus.Loading, !uiState.step1Done) {
                    Button(onClick = { viewModel.registerEntry { onScanAgain() } }, enabled = uiState.step1Done && uiState.syncStatus !is SyncStatus.Loading) {
                        Text(if (uiState.syncStatus is SyncStatus.Success) "COMPLETADO" else "FINALIZAR")
                    }
                }
            }
        }
    }
}

@Composable
fun StepItem(num: Int, title: String, sub: String, done: Boolean, loading: Boolean, locked: Boolean = false, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Box(modifier = Modifier.size(32.dp).background(if (done) Color(0xFF2E7D32) else if (locked) Color.Gray else PrimaryBlue, CircleShape), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            else if (done) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            else Text("$num", color = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(sub, fontSize = 12.sp, color = Color.Gray)
        }
        content()
    }
}
