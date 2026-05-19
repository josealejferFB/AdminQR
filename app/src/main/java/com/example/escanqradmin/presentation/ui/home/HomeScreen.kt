package com.example.escanqradmin.presentation.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextAlign
import com.example.escanqradmin.presentation.common.sharedcomponents.CustomBottomBar
import com.example.escanqradmin.presentation.common.sharedcomponents.AppCard
import com.example.escanqradmin.presentation.common.sharedcomponents.AppCardDefaults
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
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val scannedDevices by viewModel.scannedDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val bluetoothConnectionState by viewModel.bluetoothConnectionState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showBluetoothDialog by remember { mutableStateOf(false) }
    var showProvisioningDialog by remember { mutableStateOf(false) }
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
        containerColor = MaterialTheme.colorScheme.background,
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
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.escanqradmin.R.drawable.ic_app_logo),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "EscanQR",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                }
                IconButton(
                    onClick = { viewModel.toggleTheme() },
                    modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Alternar Modo Oscuro",
                        tint = if (isDarkMode) SecondaryOrange else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
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
                    contentPadding = PaddingValues(top = 80.dp, bottom = 120.dp, start = 24.dp, end = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            if (uiState.isServerOnline) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color.Green, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "ESTÁS EN LÍNEA",
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Panel de Control",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                                )
                            } else {
                                AppCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = AppCardDefaults.colors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                                    border = AppCardDefaults.border(color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(MaterialTheme.colorScheme.error, CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "SIN CONEXIÓN AL SERVIDOR",
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "No se pudo establecer comunicación con el servidor Odoo. Por favor, comprueba tu conexión de red o la configuración de tus endpoints.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = { navController.navigate(Config) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "CONFIGURAR ENDPOINT",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Panel de Control",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                                )
                            }
                        }
                    }

                    // 1. ESP32 Connection Panel
                    item {
                        AppCard(
                            modifier = Modifier.fillMaxWidth(),
                            border = AppCardDefaults.border(
                                if (bluetoothConnectionState is BluetoothConnectionState.Connected) 
                                    Color.Green.copy(alpha = 0.3f) 
                                else 
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(
                                                    if (bluetoothConnectionState is BluetoothConnectionState.Connected) 
                                                        Color.Green.copy(alpha = 0.15f) 
                                                    else 
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), 
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (bluetoothConnectionState is BluetoothConnectionState.Connected) 
                                                    Icons.Default.BluetoothConnected 
                                                else 
                                                    Icons.Default.Bluetooth,
                                                contentDescription = null,
                                                tint = if (bluetoothConnectionState is BluetoothConnectionState.Connected) 
                                                    Color.Green 
                                                else 
                                                    MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(
                                                "Lector Físico (ESP32)", 
                                                fontWeight = FontWeight.Bold, 
                                                fontSize = 15.sp, 
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = when (val s = bluetoothConnectionState) {
                                                    is BluetoothConnectionState.Connected -> "CONECTADO: ${s.deviceAddress}"
                                                    is BluetoothConnectionState.Connecting -> "CONECTANDO..."
                                                    is BluetoothConnectionState.Error -> "ERROR EN CONEXIÓN"
                                                    else -> "DESCONECTADO"
                                                },
                                                fontSize = 12.sp,
                                                color = if (bluetoothConnectionState is BluetoothConnectionState.Connected) 
                                                    Color.Green.copy(alpha = 0.8f) 
                                                else 
                                                    Color.Gray,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(
                                                color = when (bluetoothConnectionState) {
                                                    is BluetoothConnectionState.Connected -> Color.Green
                                                    is BluetoothConnectionState.Connecting -> SecondaryOrange
                                                    else -> Color.Gray
                                                },
                                                shape = CircleShape
                                            )
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (bluetoothConnectionState is BluetoothConnectionState.Connected) {
                                        OutlinedButton(
                                            onClick = { viewModel.disconnect() },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                            border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
                                        ) {
                                            Icon(Icons.Default.BluetoothDisabled, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Desconectar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Button(
                                            onClick = requestBluetoothAction,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Vincular", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    
                                    FilledTonalButton(
                                        onClick = { navController.navigate(ESPConfig) },
                                        enabled = bluetoothConnectionState is BluetoothConnectionState.Connected,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Default.SettingsInputComponent, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Ajustar Red", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // 2. Metrics Card
                    item {
                        AppCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = uiState.totalScans.toString(), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                    Text(text = "ESCANEOS TOTALES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                }
                                
                                Box(modifier = Modifier.width(1.dp).height(50.dp).background(Color.LightGray.copy(alpha = 0.5f)))
                                
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.People, contentDescription = null, tint = SecondaryOrange, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = uiState.totalUsers.toString(), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = SecondaryOrange)
                                    Text(text = "USUARIOS REGISTRADOS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                }
                            }
                        }
                    }

                    // 3. Aprovisionamiento Card
                    item {
                        AppCard(
                            modifier = Modifier.fillMaxWidth(),
                            border = AppCardDefaults.border(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            onClick = { showProvisioningDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(SecondaryOrange.copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.QrCode, contentDescription = null, tint = SecondaryOrange, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text("Aprovisionar Conductor", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Generar código de configuración", fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // 4. Search and List Header
                    item {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            SearchBar(query = searchQuery, onQueryChange = { searchQuery = it })
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "Usuarios Activos", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Box(modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                                    Text(text = "${uiState.activeUsers.size} En línea", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // 5. User Feed
                    val filteredUsers = uiState.activeUsers.filter {
                        it.name.contains(searchQuery, ignoreCase = true) || it.document.contains(searchQuery, ignoreCase = true)
                    }

                    if (filteredUsers.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                                Text("No hay usuarios activos que coincidan", color = Color.Gray, fontSize = 14.sp)
                            }
                        }
                    } else {
                        items(filteredUsers, key = { it.id }) { user ->
                            ActiveUserCard(
                                user = user, 
                                onDelete = { userToDelete = user; showDeleteDialog = true }, 
                                onUpdate = { viewModel.updateUser(it) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }

            if (showDeleteDialog && userToDelete != null) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Confirmar eliminación", fontWeight = FontWeight.Bold) },
                    text = { Text("¿Eliminar el registro de ${userToDelete?.name} del servidor?") },
                    confirmButton = {
                        TextButton(onClick = {
                            userToDelete?.let { viewModel.deleteUser(it.id, it.document) }
                            showDeleteDialog = false; userToDelete = null
                        }) { Text("ELIMINAR", color = Color.Red, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) { Text("CANCELAR", color = Color.Gray) }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
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

            if (showProvisioningDialog) {
                ProvisioningQrDialog(
                    onDismiss = { showProvisioningDialog = false }
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
                    containerColor = if (isBluetooth) MaterialTheme.colorScheme.primary else SecondaryOrange
                )
            }
        }
    }
}

@Composable
fun ProvisioningQrDialog(
    onDismiss: () -> Unit
) {
    val payload = remember {
        """{"endpoint":"${com.example.escanqradmin.data.network.ApiConstants.BASE_URL}","token":"${com.example.escanqradmin.domain.model.SecurityConstants.PROVISIONING_TOKEN}"}"""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(SecondaryOrange.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = null,
                        tint = SecondaryOrange,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Aprovisionar Conductor",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Escanea este código QR desde la App de Conductor para sincronizar el servidor y la configuración de red automáticamente.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    HomeScreenQrImage(content = payload, size = 180)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Servidor configurado:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Text(
                            text = com.example.escanqradmin.data.network.ApiConstants.BASE_URL,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("CERRAR", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun HomeScreenQrImage(content: String, size: Int) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(content) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val writer = com.google.zxing.qrcode.QRCodeWriter()
                val bitMatrix = writer.encode(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
                val width = bitMatrix.width
                val height = bitMatrix.height
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bmp.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                    }
                }
                bitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "QR Code",
            modifier = Modifier.size(size.dp)
        )
    } ?: Box(modifier = Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = SecondaryOrange)
    }
}
