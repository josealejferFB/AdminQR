package com.example.escanqradmin.presentation.ui.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.escanqradmin.presentation.theme.color.*
import com.example.escanqradmin.presentation.ui.result.components.ResultSnackbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    onScanAgain: () -> Unit,
    viewModel  : ResultViewModel = hiltViewModel()
) {
    val qrData  by viewModel.qrData.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val isIdle     = uiState.espUploadStatus is EspUploadStatus.Idle
    val isLoading  = uiState.espUploadStatus is EspUploadStatus.Loading
    val isSuccess  = uiState.espUploadStatus is EspUploadStatus.Success
    val isError    = uiState.espUploadStatus is EspUploadStatus.Error

    Scaffold(
        containerColor = BackgroundLight,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .statusBarsPadding()
                    .displayCutoutPadding(),
                title = { Text("Resultados del Escaneo", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = PrimaryBlue,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            qrData?.let { data ->

                // ── Data cards ─────────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DataCard("ESTADO DE VERIFICACIÓN", "VALIDADO ✓", isStatus = true)
                    DataCard("NOMBRE DE USUARIO",       data.userName)
                    DataCard("CÉDULA",                  data.cedula)
                    DataCard("PLACA REGISTRADA",        data.plate)
                    DataCard("ANDROID ID (Descifrado)", data.androidId)
                }

                // ── Feedback banners ──────────────────────────────
                ResultSnackbar(status = uiState.espUploadStatus)
                
                // Add a second snackbar for synchronization info if loading or error
                if (uiState.syncStatus is SyncStatus.Loading || uiState.syncStatus is SyncStatus.Error) {
                    val syncMsg = when (val s = uiState.syncStatus) {
                        is SyncStatus.Loading -> "Sincronizando con el servidor..."
                        is SyncStatus.Error -> "Error de servidor: ${s.message}"
                        else -> ""
                    }
                    val isError = uiState.syncStatus is SyncStatus.Error
                    
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isError) Color(0xFFB71C1C) else Color(0xFF0D47A1)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (uiState.syncStatus is SyncStatus.Loading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(syncMsg, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // ── Action buttons ─────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    // 1. Upload to ESP32 / Retry
                    when {
                        isSuccess -> {
                            Button(
                                onClick  = {},
                                enabled  = false,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors   = ButtonDefaults.buttonColors(
                                    disabledContainerColor = Color(0xFF1B5E20).copy(alpha = 0.7f),
                                    disabledContentColor   = Color.White
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("ENVIADO AL ESP32 ✓", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        isError -> {
                            Button(
                                onClick  = { viewModel.uploadToEsp32() },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                                shape    = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("REINTENTAR ENVÍO A TARJETA", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        else -> {
                            Button(
                                onClick  = { viewModel.uploadToEsp32() },
                                enabled  = !isLoading,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor        = Color(0xFF1565C0),
                                    disabledContainerColor = Color(0xFF1565C0).copy(alpha = 0.55f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        color       = Color.White,
                                        modifier    = Modifier.size(20.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text       = if (isLoading) "Enviando..." else "SUBIR AL ESP32",
                                    fontSize   = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = Color.White
                                )
                            }
                        }
                    }

                    // 2. Register entry (unlocked only after success)
                    val isSyncing = uiState.syncStatus is SyncStatus.Loading
                    val isSyncError = uiState.syncStatus is SyncStatus.Error
                    
                    Button(
                        onClick  = {
                            viewModel.registerEntry {
                                onScanAgain() // Navigate back/Reset on success
                            }
                        },
                        enabled  = isSuccess && !isSyncing,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor        = if (isSyncError) Color(0xFFB71C1C) else PrimaryBlue,
                            disabledContainerColor = Color(0xFFBDBDBD)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("SINCRONIZANDO...", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(if (isSyncError) Icons.Default.Refresh else Icons.Default.HowToReg, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text       = when {
                                    isSyncError -> "REINTENTAR SINCRONIZACIÓN"
                                    isSuccess -> "AGREGAR ENTRADA"
                                    else -> "Sube al ESP32 primero"
                                },
                                fontSize   = if (isSuccess) 15.sp else 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 3. Scan another QR
                    OutlinedButton(
                        onClick  = onScanAgain,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        border   = BorderStroke(1.dp, PrimaryBlue),
                        shape    = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("ESCANEAR OTRO QR", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                }

            } ?: run {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay datos disponibles", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun DataCard(label: String, value: String, isStatus: Boolean = false) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape     = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text       = label,
                fontWeight = FontWeight.Bold,
                fontSize   = 10.sp,
                color      = Color.Gray
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text       = value,
                fontSize   = if (isStatus) 18.sp else 16.sp,
                fontWeight = if (isStatus) FontWeight.ExtraBold else FontWeight.SemiBold,
                color      = if (isStatus) Color(0xFF1B5E20) else Color.Black
            )
        }
    }
}
