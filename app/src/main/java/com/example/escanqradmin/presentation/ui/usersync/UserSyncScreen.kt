package com.example.escanqradmin.presentation.ui.usersync

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.escanqradmin.domain.model.BluetoothDeviceDomain
import com.example.escanqradmin.presentation.theme.color.PrimaryBlue
import kotlinx.coroutines.flow.collectLatest

private val SyncPurple = Color(0xFF7B1FA2)
private val SyncBg = Color(0xFFF8F4FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSyncScreen(
    navController: NavHostController,
    userName: String,
    cedula: String,
    viewModel: UserSyncViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) viewModel.startDiscovery()
    }

    LaunchedEffect(uiState.status) {
        if (uiState.status is UserSyncStatus.Success) {
            navController.previousBackStackEntry?.savedStateHandle?.set("sync_success", true)
        }
    }

    Scaffold(
        containerColor = SyncBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sincronizar Usuario", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                        Text("Bluetooth Classic", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SyncPurple)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).background(SyncPurple.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, tint = SyncPurple)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(userName, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("Cédula: $cedula", color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                SyncStatusBanner(uiState.status)
            }

            item {
                Text("DISPOSITIVOS VINCULADOS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray, letterSpacing = 1.sp)
            }

            if (uiState.pairedDevices.isEmpty()) {
                item {
                    Text("No hay dispositivos vinculados.", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                items(uiState.pairedDevices) { device ->
                    DeviceCard(
                        device = device,
                        selected = uiState.selectedDeviceAddress == device.address,
                        onClick = { viewModel.selectDevice(device.address) }
                    )
                }
            }

            item {
                Button(
                    onClick = { uiState.selectedDeviceAddress?.let { viewModel.syncToUser(it) } },
                    enabled = uiState.selectedDeviceAddress != null && uiState.status !is UserSyncStatus.Sending && uiState.status !is UserSyncStatus.Connecting,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SyncPurple)
                ) {
                    if (uiState.status is UserSyncStatus.Connecting || uiState.status is UserSyncStatus.Sending) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Icon(Icons.Default.Bluetooth, null)
                        Spacer(Modifier.width(12.dp))
                        Text("SINCRONIZAR AHORA", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatusBanner(status: UserSyncStatus) {
    val (color, text, icon) = when(status) {
        is UserSyncStatus.Success -> Triple(Color(0xFF2E7D32), "¡Sincronización exitosa!", Icons.Default.CheckCircle)
        is UserSyncStatus.Error -> Triple(Color(0xFFC62828), status.message, Icons.Default.Error)
        is UserSyncStatus.Connecting -> Triple(SyncPurple, "Conectando...", Icons.Default.BluetoothConnected)
        is UserSyncStatus.Sending -> Triple(SyncPurple, "Enviando datos...", Icons.Default.Upload)
        else -> return
    }
    Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun DeviceCard(device: BluetoothDeviceDomain, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().border(2.dp, if(selected) SyncPurple else Color.Transparent, RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PhoneAndroid, null, tint = if(selected) SyncPurple else Color.Gray)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name ?: "Desconocido", fontWeight = FontWeight.Bold)
                Text(device.address, fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
            }
            if (selected) Icon(Icons.Default.CheckCircle, null, tint = SyncPurple)
        }
    }
}
