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
        topBar = {
            TopAppBar(
                title = { Text("Sincronizar con Usuario", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF7B1FA2))
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(userName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Cédula: $cedula", color = Color.Gray)
                    }
                }
            }
            
            item {
                Text("Dispositivos vinculados", fontWeight = FontWeight.Bold)
            }

            items(uiState.pairedDevices) { device ->
                ListItem(
                    headlineContent = { Text(device.name ?: "Desconocido") },
                    supportingContent = { Text(device.address) },
                    leadingContent = { Icon(Icons.Default.Bluetooth, null) },
                    modifier = Modifier.clickable { viewModel.selectDevice(device.address) }
                        .border(1.dp, if(uiState.selectedDeviceAddress == device.address) Color(0xFF7B1FA2) else Color.Transparent, RoundedCornerShape(8.dp))
                )
            }

            item {
                Button(
                    onClick = { uiState.selectedDeviceAddress?.let { viewModel.syncToUser(it) } },
                    enabled = uiState.selectedDeviceAddress != null && uiState.status !is UserSyncStatus.Sending,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uiState.status is UserSyncStatus.Sending) "Enviando..." else "SINCRONIZAR")
                }
            }
        }
    }
}
