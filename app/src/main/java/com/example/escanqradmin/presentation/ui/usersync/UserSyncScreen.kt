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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.escanqradmin.domain.model.BluetoothDeviceDomain
import com.example.escanqradmin.presentation.theme.color.PrimaryBlue
import kotlinx.coroutines.flow.collectLatest

// ── Design tokens ─────────────────────────────────────────────────
private val SyncPurple   = Color(0xFF7B1FA2)
private val SyncPurpleL  = Color(0xFFCE93D8)
private val SyncGreen    = Color(0xFF2E7D32)
private val SyncRed      = Color(0xFFC62828)
private val SyncBg       = Color(0xFFF8F4FF)
private val CardWhite    = Color.White
private val TextGray     = Color(0xFF757575)
private val ChipBg       = Color(0xFFF3E5F5)

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

    // Permisos Bluetooth
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            viewModel.startDiscovery()
        }
    }

    val requestScanPermission = {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissionLauncher.launch(permissions)
    }

    // Snackbar messages
    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        }
    }

    // Cuando la sincronización tiene éxito, notificamos a ResultScreen via savedStateHandle
    LaunchedEffect(uiState.status) {
        if (uiState.status is UserSyncStatus.Success) {
            navController.previousBackStackEntry
                ?.savedStateHandle
                ?.set("sync_success", true)
        }
    }

    Scaffold(
        containerColor = SyncBg,
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .statusBarsPadding()
                    .displayCutoutPadding(),
                title = {
                    Column {
                        Text(
                            "Sincronizar Usuario",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 17.sp
                        )
                        Text(
                            "Bluetooth Classic (RFCOMM/SPP)",
                            color = SyncPurpleL,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SyncPurple)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Spacer top
            item { Spacer(Modifier.height(8.dp)) }

            // ── Tarjeta del usuario objetivo
            item {
                UserTargetCard(userName = userName, cedula = cedula)
            }

            // ── Tarjeta de estado de la sincronización
            item {
                SyncStatusCard(status = uiState.status)
            }

            // ── Payload preview
            item {
                PayloadPreviewCard(
                    endpoint = uiState.endpointPreview,
                    targetMac = uiState.targetMacPreview
                )
            }

            // ── Dispositivos vinculados (candidatos a ser la App de Usuario)
            item {
                SectionHeader(
                    title = "Dispositivos vinculados",
                    subtitle = "Selecciona el teléfono del usuario",
                    icon = Icons.Default.PhoneAndroid
                )
            }

            if (uiState.pairedDevices.isEmpty()) {
                item {
                    EmptyDevicesHint(
                        message = "No hay dispositivos vinculados.\nVe a Ajustes → Bluetooth y vincula el teléfono del usuario."
                    )
                }
            } else {
                items(uiState.pairedDevices) { device ->
                    DeviceItem(
                        device = device,
                        isSelected = device.address == uiState.selectedDeviceAddress,
                        onClick = { viewModel.selectDevice(device.address) }
                    )
                }
            }

            // ── Dispositivos escaneados
            if (uiState.scannedDevices.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Dispositivos cercanos",
                        subtitle = "Detectados en el escaneo",
                        icon = Icons.Default.BluetoothSearching
                    )
                }
                items(uiState.scannedDevices) { device ->
                    DeviceItem(
                        device = device,
                        isSelected = device.address == uiState.selectedDeviceAddress,
                        onClick = { viewModel.selectDevice(device.address) }
                    )
                }
            }

            // ── Botones de acción
            item {
                Spacer(Modifier.height(4.dp))
                ActionButtons(
                    uiState = uiState,
                    onSync = {
                        val addr = uiState.selectedDeviceAddress
                        if (addr != null) {
                            viewModel.syncToUser(addr)
                        }
                    },
                    onRetry = { viewModel.resetStatus() },
                    onScan = requestScanPermission,
                    onStopScan = { viewModel.stopDiscovery() }
                )
            }
        }
    }
}

// ── Tarjeta del usuario objetivo ─────────────────────────────────

@Composable
private fun UserTargetCard(userName: String, cedula: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(ChipBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = SyncPurple,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = "USUARIO OBJETIVO",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextGray,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = userName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "Cédula: $cedula",
                    fontSize = 13.sp,
                    color = TextGray
                )
            }
        }
    }
}

// ── Tarjeta de estado ─────────────────────────────────────────────

@Composable
private fun SyncStatusCard(status: UserSyncStatus) {
    val (bgColor, contentColor, icon, text) = when (status) {
        is UserSyncStatus.Idle ->
            StatusVisuals(Color(0xFFF5F5F5), TextGray, Icons.Default.BluetoothSearching, "Listo para sincronizar")
        is UserSyncStatus.Connecting ->
            StatusVisuals(Color(0xFFE3F2FD), PrimaryBlue, Icons.Default.BluetoothConnected, "Conectando con el dispositivo...")
        is UserSyncStatus.Sending ->
            StatusVisuals(Color(0xFFE8F5E9), SyncGreen, Icons.Default.Upload, "Enviando configuración...")
        is UserSyncStatus.Success ->
            StatusVisuals(Color(0xFF1B5E20), Color.White, Icons.Default.CheckCircle, "¡Sincronización exitosa!")
        is UserSyncStatus.Error ->
            StatusVisuals(Color(0xFFFFEBEE), SyncRed, Icons.Default.ErrorOutline, status.message)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Spinner animado para estados de carga
            if (status is UserSyncStatus.Connecting || status is UserSyncStatus.Sending) {
                val rotation by rememberInfiniteTransition(label = "spin").animateFloat(
                    initialValue = 0f, targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
                    label = "rotation"
                )
                val scale by rememberInfiniteTransition(label = "pulse").animateFloat(
                    initialValue = 0.9f, targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        tween(700, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse
                    ),
                    label = "scale"
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .scale(scale)
                        .background(contentColor.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = contentColor,
                        strokeWidth = 2.5.dp
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(contentColor.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.width(14.dp))
            Text(
                text = text,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private data class StatusVisuals(
    val bgColor: Color,
    val contentColor: Color,
    val icon: ImageVector,
    val text: String
)

// ── Payload preview ───────────────────────────────────────────────

@Composable
private fun PayloadPreviewCard(endpoint: String, targetMac: String) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DataObject, contentDescription = null,
                        tint = SyncPurple, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Payload a enviar", fontWeight = FontWeight.Bold,
                        fontSize = 13.sp, color = Color.Black)
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = Color(0xFFEEEEEE))
                    Spacer(Modifier.height(10.dp))
                    PayloadRow("endpoint", endpoint)
                    Spacer(Modifier.height(6.dp))
                    PayloadRow("target_mac", targetMac)
                    Spacer(Modifier.height(6.dp))
                    PayloadRow("token", "ALCARAVAN_2025")
                }
            }
        }
    }
}

@Composable
private fun PayloadRow(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "\"$key\":",
            color = SyncPurple,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = "\"$value\"",
            color = Color(0xFF2E7D32),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Section header ────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, subtitle: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = SyncPurple, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
            Text(subtitle, fontSize = 11.sp, color = TextGray)
        }
    }
}

// ── Device item ───────────────────────────────────────────────────

@Composable
private fun DeviceItem(
    device: BluetoothDeviceDomain,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) SyncPurple else Color.Transparent
    val bgColor = if (isSelected) ChipBg else CardWhite

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isSelected) SyncPurple.copy(alpha = 0.1f) else Color(0xFFF5F5F5),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isSelected) Icons.Default.BluetoothConnected else Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = if (isSelected) SyncPurple else TextGray,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Dispositivo desconocido",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.Black
                )
                Text(
                    text = device.address,
                    fontSize = 11.sp,
                    color = TextGray,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(SyncPurple, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Seleccionado",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// ── Empty hint ────────────────────────────────────────────────────

@Composable
private fun EmptyDevicesHint(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5), RoundedCornerShape(16.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.BluetoothDisabled,
                contentDescription = null,
                tint = TextGray,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = TextGray,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ── Action buttons ────────────────────────────────────────────────

@Composable
private fun ActionButtons(
    uiState: UserSyncUiState,
    onSync: () -> Unit,
    onRetry: () -> Unit,
    onScan: () -> Unit,
    onStopScan: () -> Unit
) {
    val status = uiState.status
    val hasSelection = uiState.selectedDeviceAddress != null
    val isBusy = status is UserSyncStatus.Connecting || status is UserSyncStatus.Sending
    val isSuccess = status is UserSyncStatus.Success
    val isError = status is UserSyncStatus.Error

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // Botón principal: Sincronizar / Reintentar / Éxito
        when {
            isSuccess -> {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = Color(0xFF1B5E20).copy(alpha = 0.8f),
                        disabledContentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("SINCRONIZACIÓN EXITOSA ✓", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            isError -> {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SyncRed)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("REINTENTAR", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            else -> {
                Button(
                    onClick = onSync,
                    enabled = hasSelection && !isBusy,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SyncPurple,
                        disabledContainerColor = SyncPurple.copy(alpha = 0.4f)
                    )
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = when {
                            isBusy && status is UserSyncStatus.Connecting -> "Conectando..."
                            isBusy && status is UserSyncStatus.Sending -> "Enviando..."
                            !hasSelection -> "Selecciona un dispositivo"
                            else -> "SINCRONIZAR"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Botón secundario: Escanear / Detener escaneo
        if (!isSuccess) {
            OutlinedButton(
                onClick = if (uiState.isScanning) onStopScan else onScan,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SyncPurple),
                enabled = !isBusy
            ) {
                Icon(
                    if (uiState.isScanning) Icons.Default.Stop else Icons.Default.Search,
                    contentDescription = null,
                    tint = SyncPurple,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (uiState.isScanning) "DETENER BÚSQUEDA" else "BUSCAR DISPOSITIVOS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = SyncPurple
                )
                if (uiState.isScanning) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = SyncPurple,
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}
