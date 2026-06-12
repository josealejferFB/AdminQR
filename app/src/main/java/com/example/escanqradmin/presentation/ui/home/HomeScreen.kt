package com.example.escanqradmin.presentation.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.escanqradmin.domain.model.GateInfo
import com.example.escanqradmin.domain.repository.BluetoothConnectionState
import com.example.escanqradmin.presentation.common.sharedcomponents.CustomSnackbar
import com.example.escanqradmin.presentation.common.sharedcomponents.AppCard
import com.example.escanqradmin.presentation.common.sharedcomponents.AppCardDefaults
import com.example.escanqradmin.presentation.navigation.Config
import com.example.escanqradmin.presentation.navigation.ESPConfig
import com.example.escanqradmin.presentation.theme.color.*
import com.example.escanqradmin.presentation.ui.home.components.ActiveUserCard
import com.example.escanqradmin.presentation.ui.home.components.BluetoothConnectionPanel
import com.example.escanqradmin.presentation.ui.home.components.BluetoothDialog
import com.example.escanqradmin.presentation.ui.home.components.ChangeHostnameDialog
import com.example.escanqradmin.presentation.ui.home.components.GateRegistrationDialog
import com.example.escanqradmin.presentation.ui.home.components.OdooConfigDialog
import com.example.escanqradmin.presentation.ui.home.components.RenameGateDialog
import com.example.escanqradmin.presentation.ui.home.components.SearchBar
import com.example.escanqradmin.presentation.ui.home.components.StatCard
import com.example.escanqradmin.presentation.common.sharedcomponents.QrCodeBox
import com.example.escanqradmin.presentation.common.util.buildProvisioningJson
import com.example.escanqradmin.data.network.ApiConstants
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image

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

    val gateRegistrationViewModel: GateRegistrationViewModel = hiltViewModel()

    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by remember { mutableStateOf("") }
    var showActiveUsers by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showBluetoothDialog by remember { mutableStateOf(false) }
    var showProvisioningDialog by remember { mutableStateOf(false) }
    var showGateRegistrationDialog by remember { mutableStateOf(false) }
    var userToDelete by remember { mutableStateOf<ActiveUser?>(null) }
    var showHostnameDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showOdooDialog by remember { mutableStateOf(false) }
    var selectedGateForDialog by remember { mutableStateOf<GateInfo?>(null) }

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
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = com.example.escanqradmin.R.drawable.ic_app_logo),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "EscanQR",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleTheme() },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Alternar modo oscuro",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                val isBluetooth = data.visuals.message.contains("Bluetooth", ignoreCase = true)
                CustomSnackbar(
                    message = data.visuals.message,
                    icon = if (isBluetooth) Icons.Default.BluetoothDisabled else Icons.Default.Lock,
                    containerColor = if (isBluetooth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            val refreshState = rememberPullToRefreshState()

            PullToRefreshBox(
                state = refreshState,
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refreshData() },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        if (uiState.isServerOnline) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(MaterialTheme.colorScheme.secondary, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Sistema en línea",
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Panel de Control",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            } else {
                                AppCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = AppCardDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    border = AppCardDefaults.border(
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(20.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(MaterialTheme.colorScheme.error, CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "SIN CONEXIÓN AL SERVIDOR",
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "No se pudo conectar con el servidor Odoo. Verifica tu conexión de red o la configuración de endpoints.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                        OutlinedButton(
                                            onClick = { navController.navigate(Config) },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
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
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Panel de Control",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                    }

                    // ── Gate Chip Selector ─────────────────────────────
                    item {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            GateChipRow(
                                gates = uiState.gates,
                                selectedMacAddress = uiState.selectedMacAddress,
                                onSelect = { viewModel.selectGate(it) },
                                onAddGate = { showGateRegistrationDialog = true },
                                onChangeHostname = { gate ->
                                    selectedGateForDialog = gate
                                    showHostnameDialog = true
                                },
                                onRename = { gate ->
                                    selectedGateForDialog = gate
                                    showRenameDialog = true
                                },
                                onConfigureOdoo = { gate ->
                                    selectedGateForDialog = gate
                                    showOdooDialog = true
                                },
                                onDeleteLocalGate = { gate ->
                                    viewModel.deleteLocalGate(gate.macAddress)
                                },
                                onDetails = { gate ->
                                    // TODO: details dialog
                                }
                            )
                        }
                    }

                    item {
                        val connectedGate = viewModel.getConnectedGate(uiState.gates)
                        val pairedAddresses = pairedDevices.map { it.address }

                        BluetoothConnectionPanel(
                            gates = uiState.gates,
                            connectionState = bluetoothConnectionState,
                            pairedDeviceAddresses = pairedAddresses,
                            connectedGateName = connectedGate?.name,
                            onConnectToGate = { viewModel.connectToGate(it) },
                            onDisconnect = { viewModel.disconnect() },
                            onPairGate = {
                                selectedGateForDialog = it
                                requestBluetoothAction()
                            },
                            onUnpairGate = { viewModel.unpairGate(it) },
                            onRegisterNew = { showGateRegistrationDialog = true }
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(120.dp)
                                    .border(
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                                        shape = AppCardDefaults.Shape
                                    ),
                                onClick = { showProvisioningDialog = true },
                                border = null
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Aprovisionar", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Text("QR", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }

                            AppCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(120.dp)
                                    .border(
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                                        shape = AppCardDefaults.Shape
                                    ),
                                onClick = { showGateRegistrationDialog = true },
                                border = null
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Router, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Registrar", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Portón", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary)
                                    }
                                }
                            }

                            AppCard(
                                modifier = Modifier.weight(1f).height(120.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = uiState.totalUsers.toString(), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.secondary)
                                    Text(text = "USUARIOS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
                                    Text(text = "REGISTRADOS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                    
                    val displayUsers = if (uiState.selectedMacAddress != null) uiState.gateUsers else uiState.activeUsers
                    val filteredUsers = displayUsers.filter {
                        it.name.contains(searchQuery, ignoreCase = true) || it.document.contains(searchQuery, ignoreCase = true)
                    }

                    item {
                        Column {
                            SearchBar(query = searchQuery, onQueryChange = { searchQuery = it })
                            Spacer(modifier = Modifier.height(16.dp))
                            val noRippleSection = remember { MutableInteractionSource() }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = noRippleSection,
                                        indication = null
                                    ) { showActiveUsers = !showActiveUsers },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Usuarios",
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${filteredUsers.size} en línea",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (showActiveUsers) Icons.Default.KeyboardArrowUp
                                                  else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (showActiveUsers) "Contraer sección" else "Expandir sección",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    item {
                        if (filteredUsers.isEmpty()) {
                            AnimatedVisibility(
                                visible = showActiveUsers,
                                enter = fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) +
                                        expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)),
                                exit = fadeOut(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) +
                                       shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
                            ) {
                                AppCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.PeopleOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            "No hay usuarios activos",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(filteredUsers, key = { it.id }) { user ->
                        AnimatedVisibility(
                            visible = showActiveUsers,
                            enter = fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) +
                                    expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)),
                            exit = fadeOut(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) +
                                   shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
                        ) {
                            Column {
                                ActiveUserCard(
                                    user = user,
                                    gates = uiState.gates,
                                    onDelete = { userToDelete = user; showDeleteDialog = true },
                                    onUpdate = { viewModel.updateUser(it) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
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
                    pairedDevices = pairedDevices,
                    scannedDevices = scannedDevices,
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

            if (showGateRegistrationDialog) {
                val gateUiState by gateRegistrationViewModel.uiState.collectAsState()

                LaunchedEffect(Unit) {
                    gateRegistrationViewModel.events.collectLatest { event ->
                        when (event) {
                            is GateRegistrationEvent.CloseDialog -> {
                                showGateRegistrationDialog = false
                            }
                            is GateRegistrationEvent.GateRegisteredInOdoo -> {
                                viewModel.addLocalGate(
                                    name = event.name,
                                    macAddress = event.macAddress,
                                    btName = event.btName,
                                    hostname = event.hostname,
                                    odooId = event.odooId
                                )
                                showGateRegistrationDialog = false
                            }
                        }
                    }
                }

                GateRegistrationDialog(
                    uiState = gateUiState,
                    scannedDevices = scannedDevices,
                    pairedDevices = pairedDevices,
                    isScanning = isScanning,
                    connectionState = bluetoothConnectionState,
                    onStartScan = { viewModel.startDiscovery() },
                    onStopScan = { viewModel.stopDiscovery() },
                    onConnectToDevice = { address, name -> gateRegistrationViewModel.connectToBluetoothDevice(address, name) },
                    onCancelConnection = { viewModel.disconnect() },
                    onSsidChange = { gateRegistrationViewModel.setSsid(it) },
                    onPasswordChange = { gateRegistrationViewModel.setPassword(it) },
                    onRefreshNetworks = { gateRegistrationViewModel.refreshAvailableNetworks() },
                    onSelectNetwork = { gateRegistrationViewModel.selectNetwork(it) },
                    onSendWiFiConfig = { gateRegistrationViewModel.sendWiFiConfig() },
                    onGateNameChange = { gateRegistrationViewModel.setGateName(it) },
                    onDismissError = { gateRegistrationViewModel.dismissError() },
                    onGoBackFromError = { gateRegistrationViewModel.goBackTwoSteps() },
                    onDismiss = {
                        gateRegistrationViewModel.closeDialog()
                        showGateRegistrationDialog = false
                    },
                    onReset = { gateRegistrationViewModel.resetToSelectBluetooth() }
                )
            }

            if (showRenameDialog && selectedGateForDialog != null) {
                RenameGateDialog(
                    gate = selectedGateForDialog!!,
                    onConfirm = { newName ->
                        selectedGateForDialog!!.id?.let { gateId ->
                            viewModel.renameGate(gateId, newName)
                        }
                        showRenameDialog = false
                        selectedGateForDialog = null
                    },
                    onDismiss = { showRenameDialog = false; selectedGateForDialog = null }
                )
            }

            if (showHostnameDialog && selectedGateForDialog != null) {
                ChangeHostnameDialog(
                    gate = selectedGateForDialog!!,
                    connectionStateProvider = { bluetoothConnectionState },
                    onConnect = { address -> viewModel.connectToDevice(address) },
                    onSendMessageAndWaitForReply = { msg, timeout -> viewModel.sendMessageAndWaitForReply(msg, timeout) },
                    onDismiss = { showHostnameDialog = false; selectedGateForDialog = null },
                    onSuccess = { showHostnameDialog = false; selectedGateForDialog = null }
                )
            }

            if (showOdooDialog && selectedGateForDialog != null) {
                OdooConfigDialog(
                    gate = selectedGateForDialog!!,
                    onRegisterInOdoo = { name, mac -> viewModel.registerGateInOdoo(name, mac) },
                    onDismiss = { showOdooDialog = false; selectedGateForDialog = null },
                    onSuccess = { odooId ->
                        if (odooId != null) {
                            viewModel.markGateAsOdooRegistered(selectedGateForDialog!!.macAddress, odooId)
                        }
                        showOdooDialog = false
                        selectedGateForDialog = null
                    }
                )
            }
        }
    }
}

@Composable
fun ProvisioningQrDialog(
    onDismiss: () -> Unit
) {
    val payload = remember { buildProvisioningJson() }

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
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Aprovisionar Conductor",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                QrCodeBox(content = payload, size = 180.dp)

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
                            text = ApiConstants.BASE_URL,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GateChipRow(
    gates: List<GateInfo>,
    selectedMacAddress: String?,
    onSelect: (String?) -> Unit,
    onAddGate: () -> Unit,
    onChangeHostname: (GateInfo) -> Unit,
    onRename: (GateInfo) -> Unit,
    onConfigureOdoo: (GateInfo) -> Unit,
    onDeleteLocalGate: (GateInfo) -> Unit,
    onDetails: (GateInfo) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selectedMacAddress == null,
                onClick = { onSelect(null) },
                label = { Text("Todas") }
            )
        }

        items(gates, key = { it.macAddress }) { gate ->
            var showMenu by remember { mutableStateOf(false) }
            ChipWithMenu(
                gate = gate,
                isSelected = selectedMacAddress == gate.macAddress,
                onSelect = { onSelect(gate.macAddress) },
                showMenu = showMenu,
                onToggleMenu = { showMenu = it },
                onRename = { showMenu = false; onRename(gate) },
                onChangeHostname = { showMenu = false; onChangeHostname(gate) },
                onConfigureOdoo = { showMenu = false; onConfigureOdoo(gate) },
                onDeleteLocalGate = { showMenu = false; onDeleteLocalGate(gate) }
            )
        }

        item {
            IconButton(onClick = onAddGate) {
                Icon(Icons.Default.Add, contentDescription = "Agregar portón")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChipWithMenu(
    gate: GateInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    showMenu: Boolean,
    onToggleMenu: (Boolean) -> Unit,
    onRename: () -> Unit,
    onChangeHostname: () -> Unit,
    onConfigureOdoo: () -> Unit,
    onDeleteLocalGate: () -> Unit
) {
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.secondaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val contentColor = if (isSelected)
        MaterialTheme.colorScheme.onSecondaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    val borderColor = if (isSelected)
        MaterialTheme.colorScheme.secondary
    else
        MaterialTheme.colorScheme.outline

    Box {
        Surface(
            modifier = Modifier.combinedClickable(
                onClick = onSelect,
                onLongClick = { onToggleMenu(true) }
            ),
            shape = RoundedCornerShape(8.dp),
            color = containerColor,
            border = BorderStroke(1.dp, borderColor.copy(alpha = 0.3f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            if (gate.isOdooRegistered) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.outline,
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = gate.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor
                    )
                    if (!gate.isOdooRegistered) {
                        Text(
                            "No configurado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { onToggleMenu(false) },
            modifier = Modifier.width(220.dp)
        ) {
            // Status header
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (gate.isOdooRegistered) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.outline,
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (gate.isOdooRegistered)
                                "Registrado en Odoo${if (gate.id != null) " (ID ${gate.id})" else ""}"
                            else "Local — No registrado",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                onClick = {},
                enabled = false
            )

            if (!gate.isOdooRegistered) {
                DropdownMenuItem(
                    text = { Text("Registrar en Odoo") },
                    onClick = onConfigureOdoo,
                    leadingIcon = {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Enviar URL Odoo al ESP32") },
                    onClick = onConfigureOdoo,
                    leadingIcon = {
                        Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
                DropdownMenuItem(
                    text = { Text("Renombrar") },
                    onClick = onRename,
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
            }

            DropdownMenuItem(
                text = { Text("Cambiar Hostname") },
                onClick = onChangeHostname,
                leadingIcon = {
                    Icon(Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )

            if (!gate.isOdooRegistered) {
                DropdownMenuItem(
                    text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                    onClick = onDeleteLocalGate,
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    }
                )
            }
        }
    }
}
