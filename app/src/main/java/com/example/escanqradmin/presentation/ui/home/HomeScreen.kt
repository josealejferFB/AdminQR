package com.example.escanqradmin.presentation.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.escanqradmin.domain.repository.BluetoothConnectionState
import com.example.escanqradmin.presentation.common.sharedcomponents.CustomBottomBar
import com.example.escanqradmin.presentation.common.sharedcomponents.CustomSnackbar
import com.example.escanqradmin.presentation.navigation.Config
import com.example.escanqradmin.presentation.navigation.ESPConfig
import com.example.escanqradmin.presentation.theme.color.*
import com.example.escanqradmin.presentation.ui.home.components.ActiveUserCard
import com.example.escanqradmin.presentation.ui.home.components.BluetoothDialog
import com.example.escanqradmin.presentation.ui.home.components.SearchBar
import com.example.escanqradmin.presentation.ui.home.components.StatCard
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val scannedDevices by viewModel.scannedDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val bluetoothConnectionState by viewModel.bluetoothConnectionState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showBluetoothDialog by remember { mutableStateOf(false) }
    var userToDelete by remember { mutableStateOf<ActiveUser?>(null) }

    // Observe snackbar messages
    LaunchedEffect(Unit) {
        viewModel.refreshData()
        viewModel.snackbarMessages.collectLatest { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            showBluetoothDialog = true
            viewModel.startDiscovery()
        }
    }

    val requestBluetoothAction = {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        val allGranted = permissions.all { perm ->
            androidx.core.content.ContextCompat.checkSelfPermission(navController.context, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            showBluetoothDialog = true
            viewModel.startDiscovery()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    Scaffold(
        containerColor = BackgroundLight,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "EscanQR",
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = requestBluetoothAction,
                        modifier = Modifier.size(36.dp).background(PrimaryBlue.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier.size(36.dp).background(SecondaryOrange.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = SecondaryOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            val refreshState = rememberPullToRefreshState()
            
            PullToRefreshBox(
                state = refreshState,
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refreshData() },
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = refreshState,
                        isRefreshing = uiState.isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)
                    )
                }
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 80.dp, bottom = 120.dp, start = 24.dp, end = 24.dp)
                ) {
                    item {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(text = "ESTÁS EN LÍNEA", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Registros Activos", color = PrimaryBlue, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                        }

                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(modifier = Modifier.weight(1f), value = uiState.totalScans.toString(), label = "ESCANEOS TOTALES", valueColor = PrimaryBlue)
                            StatCard(modifier = Modifier.weight(1f), value = uiState.totalUsers.toString(), label = "USUARIOS TOTALES", valueColor = SecondaryOrange)
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        SearchBar(query = searchQuery, onQueryChange = { searchQuery = it })
                        Spacer(modifier = Modifier.height(24.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Usuarios Activos", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Box(modifier = Modifier.background(SurfaceGrey.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                                Text(text = "${uiState.activeUsers.size} En línea", color = PrimaryBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f).clickable {
                                        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
                                        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                                        
                                        val allGranted = permissions.all { perm ->
                                            androidx.core.content.ContextCompat.checkSelfPermission(navController.context, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        }
                                        if (allGranted) viewModel.connectToEsp32() else permissionLauncher.launch(permissions)
                                    }
                                ) {
                                    Text(text = "Estado ESP32", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (bluetoothConnectionState is BluetoothConnectionState.Connected) Color.Green else Color.Red))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = when (val s = bluetoothConnectionState) {
                                                is BluetoothConnectionState.Connected -> "CONECTADO (${s.deviceAddress})"
                                                is BluetoothConnectionState.Connecting -> "CONECTANDO..."
                                                is BluetoothConnectionState.Error -> "ERROR"
                                                else -> "DESCONECTADO"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (bluetoothConnectionState is BluetoothConnectionState.Connected) Color.Black else Color.Gray,
                                            fontSize = if (bluetoothConnectionState is BluetoothConnectionState.Connected) 11.sp else 12.sp
                                        )
                                    }
                                }
                                
                                Button(
                                    onClick = { navController.navigate(ESPConfig) },
                                    enabled = bluetoothConnectionState is BluetoothConnectionState.Connected,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.SettingsInputComponent, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("CONFIG")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    val filteredUsers = uiState.activeUsers.filter {
                        it.name.contains(searchQuery, ignoreCase = true) || it.document.contains(searchQuery, ignoreCase = true)
                    }

                    items(filteredUsers) { user ->
                        ActiveUserCard(
                            user = user, 
                            onDelete = { userToDelete = user; showDeleteDialog = true }, 
                            onUpdate = { viewModel.updateUser(it) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                }
            }

            if (showDeleteDialog && userToDelete != null) {
                val connected = bluetoothConnectionState is BluetoothConnectionState.Connected
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Confirmar eliminación", fontWeight = FontWeight.Bold) },
                    text = { Text("¿Eliminar registro de ${userToDelete?.name}? ${if(connected) "Se eliminará del ESP32 también." else ""}") },
                    confirmButton = {
                        TextButton(onClick = {
                            userToDelete?.let { viewModel.deleteUser(it.id, it.document) }
                            showDeleteDialog = false; userToDelete = null
                        }) { Text("ELIMINAR", color = Color.Red, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) { Text("CANCELAR", color = Color.Gray) }
                    },
                    containerColor = Color.White,
                    shape = RoundedCornerShape(24.dp)
                )
            }

            if (showBluetoothDialog) {
                BluetoothDialog(
                    onDismiss = { showBluetoothDialog = false; viewModel.stopDiscovery() },
                    pairedDevices = pairedDevices.filter { it.name?.startsWith("ESP32", ignoreCase = true) == true },
                    scannedDevices = scannedDevices.filter { it.name?.startsWith("ESP32", ignoreCase = true) == true },
                    isScanning = isScanning,
                    connectionState = bluetoothConnectionState,
                    onStartScan = { viewModel.startDiscovery() },
                    onStopScan = { viewModel.stopDiscovery() },
                    onConnect = { address -> viewModel.connectToDevice(address) },
                    onDisconnect = { viewModel.disconnect() }
                )
            }

            CustomBottomBar(
                navController = navController,
                isFloating = true,
                isBluetoothConnected = bluetoothConnectionState is BluetoothConnectionState.Connected,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).zIndex(1f)
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp).zIndex(100f)
            ) { data ->
                val isBluetooth = data.visuals.message.contains("Bluetooth", ignoreCase = true)
                CustomSnackbar(
                    message = data.visuals.message,
                    icon = if (isBluetooth) Icons.Default.BluetoothDisabled else Icons.Default.Lock,
                    containerColor = if (isBluetooth) PrimaryBlue else SecondaryOrange
                )
            }
        }
    }
}
