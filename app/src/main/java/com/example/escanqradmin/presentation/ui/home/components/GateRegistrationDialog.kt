package com.example.escanqradmin.presentation.ui.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.escanqradmin.presentation.theme.shape.AppShapes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import com.example.escanqradmin.domain.model.BluetoothDeviceDomain
import com.example.escanqradmin.domain.model.GateInfo
import com.example.escanqradmin.domain.repository.BluetoothConnectionState
import com.example.escanqradmin.presentation.common.sharedcomponents.AppCard
import com.example.escanqradmin.presentation.common.sharedcomponents.AppCardDefaults
import com.example.escanqradmin.presentation.ui.home.GateRegistrationUiState
import com.example.escanqradmin.presentation.ui.home.GateStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GateRegistrationDialog(
    uiState: GateRegistrationUiState,
    registeredGates: List<GateInfo>,
    scannedDevices: List<BluetoothDeviceDomain>,
    pairedDevices: List<BluetoothDeviceDomain>,
    isScanning: Boolean,
    connectionState: BluetoothConnectionState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectToDevice: (String, String?) -> Unit,
    onCancelConnection: () -> Unit,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRefreshNetworks: () -> Unit,
    onSelectNetwork: (String) -> Unit,
    onSendWiFiConfig: () -> Unit,
    onGateNameChange: (String) -> Unit,
    onDismissError: () -> Unit,
    onGoBackFromError: () -> Unit,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onSendReportIp: () -> Unit = {},
    onCloseDone: () -> Unit = { onDismiss() }
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding()
                .animateContentSize()
        ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Registrar Portón",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = when (uiState.step) {
                                is GateStep.SelectBluetooth -> "Conectar al ESP32"
                                is GateStep.GettingDeviceInfo -> "Obteniendo información"
                                is GateStep.WiFiConfig -> "Configurar WiFi del ESP32"
                                is GateStep.VerifyingWifi -> "Verificando conexión WiFi"
                                is GateStep.RegisteringInOdoo -> "Registrando en Odoo"
                                is GateStep.LocalDone -> "Portón registrado"
                                is GateStep.Error -> "Error"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                AnimatedContent(
                    targetState = uiState.step,
                    transitionSpec = { fadeIn() togetherWith fadeOut() }
                ) { currentStep ->
                    when (currentStep) {
                        is GateStep.SelectBluetooth -> SelectBluetoothContent(
                            registeredGates = registeredGates,
                            scannedDevices = scannedDevices,
                            pairedDevices = pairedDevices,
                            isScanning = isScanning,
                            connectionState = connectionState,
                            onStartScan = onStartScan,
                            onStopScan = onStopScan,
                            onConnectToDevice = onConnectToDevice,
                            onCancelConnection = onCancelConnection
                        )
                        is GateStep.GettingDeviceInfo -> GettingDeviceInfoContent()
                        is GateStep.WiFiConfig -> WiFiConfigContent(
                            uiState = uiState,
                            onSsidChange = onSsidChange,
                            onPasswordChange = onPasswordChange,
                            onRefreshNetworks = onRefreshNetworks,
                            onSelectNetwork = onSelectNetwork,
                            onSendWiFiConfig = onSendWiFiConfig,
                            onGateNameChange = onGateNameChange
                        )
                        is GateStep.VerifyingWifi -> VerifyingWifiContent()
                        is GateStep.RegisteringInOdoo -> RegisteringInOdooContent()
                        is GateStep.LocalDone -> LocalDoneContent(
                            uiState = uiState,
                            onDismiss = onDismiss,
                            onSendReportIp = onSendReportIp,
                            onCloseDone = onCloseDone
                        )
                        is GateStep.Error -> ErrorContent(
                            uiState = uiState,
                            onDismiss = onDismiss,
                            onRetry = onDismissError,
                            onGoBack = onGoBackFromError
                        )
                    }
                }
            }
        }
    }

@Composable
private fun SelectBluetoothContent(
    registeredGates: List<GateInfo>,
    scannedDevices: List<BluetoothDeviceDomain>,
    pairedDevices: List<BluetoothDeviceDomain>,
    isScanning: Boolean,
    connectionState: BluetoothConnectionState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectToDevice: (String, String?) -> Unit,
    onCancelConnection: () -> Unit
) {
    Column {
        if (connectionState is BluetoothConnectionState.Error) {
        AppCard(
            colors = AppCardDefaults.colors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            shape = AppShapes.Button,
            border = null
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(text = connectionState.message, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

        LazyColumn(
            modifier = Modifier.heightIn(max = 350.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        if (pairedDevices.isNotEmpty()) {
            item { DeviceSectionHeader(title = "Dispositivos Vinculados") }
            items(pairedDevices) { device ->
                val isThisDeviceConnected = (connectionState as? BluetoothConnectionState.Connected)?.deviceAddress == device.address
                val isThisDeviceConnecting = (connectionState as? BluetoothConnectionState.Connecting)?.deviceAddress == device.address
                val isAlreadyRegistered = registeredGates.any { it.macAddress == device.address }
                DeviceItem(
                    device = device,
                    isDeviceConnected = isThisDeviceConnected,
                    isDeviceConnecting = isThisDeviceConnecting,
                    isAlreadyRegistered = isAlreadyRegistered,
                    onClick = { onConnectToDevice(device.address, device.name) },
                    onDisconnect = onCancelConnection,
                    connectionState = connectionState
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeviceSectionHeader(title = "Otros Dispositivos", modifier = Modifier.weight(1f))
                if (isScanning) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onStartScan, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Escanear", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        if (scannedDevices.isEmpty() && !isScanning) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                    Text("No se encontraron dispositivos", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        } else {
            items(scannedDevices) { device ->
                val isThisDeviceConnected = (connectionState as? BluetoothConnectionState.Connected)?.deviceAddress == device.address
                val isThisDeviceConnecting = (connectionState as? BluetoothConnectionState.Connecting)?.deviceAddress == device.address
                val isAlreadyRegistered = registeredGates.any { it.macAddress == device.address }
                DeviceItem(
                    device = device,
                    isDeviceConnected = isThisDeviceConnected,
                    isDeviceConnecting = isThisDeviceConnecting,
                    isAlreadyRegistered = isAlreadyRegistered,
                    onClick = { onConnectToDevice(device.address, device.name) },
                    onDisconnect = onCancelConnection,
                    connectionState = connectionState
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    Button(
        onClick = if (isScanning) onStopScan else onStartScan,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = AppShapes.Input,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isScanning) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary
        )
    ) {
        if (isScanning) {
            Text("DETENER ESCANEO", fontWeight = FontWeight.Bold)
        } else {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("BUSCAR DISPOSITIVOS", fontWeight = FontWeight.Bold)
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WiFiConfigContent(
    uiState: GateRegistrationUiState,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRefreshNetworks: () -> Unit,
    onSelectNetwork: (String) -> Unit,
    onSendWiFiConfig: () -> Unit,
    onGateNameChange: (String) -> Unit,
) {
    var ssidExpanded by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onRefreshNetworks()
    }

    Column {
        Text(
            "Configurar Red WiFi",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Ingresa las credenciales WiFi y el nombre del portón para el ESP32",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            ExposedDropdownMenuBox(
                expanded = ssidExpanded,
                onExpandedChange = { ssidExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = uiState.ssid,
                    onValueChange = onSsidChange,
                    label = { Text("SSID") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = AppShapes.Button,
                    enabled = !uiState.isSubmitting,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    trailingIcon = {
                        if (uiState.availableNetworks.isNotEmpty()) {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = ssidExpanded)
                        }
                    }
                )
                if (uiState.availableNetworks.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = ssidExpanded,
                        onDismissRequest = { ssidExpanded = false }
                    ) {
                        uiState.availableNetworks.forEach { network ->
                            DropdownMenuItem(
                                text = { Text(network) },
                                onClick = {
                                    onSelectNetwork(network)
                                    ssidExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onRefreshNetworks,
                enabled = !uiState.isSubmitting
            ) {
                if (uiState.isLoadingNetworks) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Wifi, contentDescription = "Redes disponibles", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = { Text("Contraseña") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.Button,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            enabled = !uiState.isSubmitting
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.gateName,
            onValueChange = onGateNameChange,
            label = { Text("Nombre del Portón") },
            placeholder = { Text("Ej: Portón Principal") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.Button,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            enabled = !uiState.isSubmitting
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSendWiFiConfig,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = AppShapes.Input,
            enabled = uiState.ssid.isNotBlank() && uiState.gateName.isNotBlank() && !uiState.isSubmitting
        ) {
            if (uiState.isSubmitting) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("ENVIAR AL ESP32", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun VerifyingWifiContent() {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                "Verificando conexión WiFi...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "El ESP32 se está conectando a la red WiFi. Esto puede tomar hasta 30 segundos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun GettingDeviceInfoContent() {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                "Obteniendo información...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Leyendo la dirección MAC y versión del ESP32.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun RegisteringInOdooContent() {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                "Registrando en Odoo...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "El portón se está registrando en el servidor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun LocalDoneContent(
    uiState: GateRegistrationUiState,
    onDismiss: () -> Unit,
    onSendReportIp: () -> Unit = {},
    onCloseDone: () -> Unit = onDismiss
) {
    var sendingReport by remember { mutableStateOf(false) }

    if (sendingReport) {
        LaunchedEffect(Unit) {
            delay(3000)
            sendingReport = false
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Portón '${uiState.gateName}' registrado",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (uiState.odooMessage.isNotBlank()) uiState.odooMessage else "Portón registrado exitosamente.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Si el ESP32 no reportó su IP automáticamente, usa el botón \"Reenviar IP\" para notificar a Odoo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    sendingReport = true
                    onSendReportIp()
                },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = AppShapes.Input,
                enabled = !sendingReport
            ) {
                if (sendingReport) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    if (sendingReport) "ENVIANDO..." else "REENVIAR IP",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
            Button(
                onClick = onCloseDone,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = AppShapes.Input
            ) {
                Text("CERRAR", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ErrorContent(
    uiState: GateRegistrationUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onGoBack: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Error",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
        val errorMessage = (uiState.step as? GateStep.Error)?.message ?: "Error desconocido"
        Text(errorMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onGoBack,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = AppShapes.Input
            ) {
                Icon(Icons.Default.NavigateBefore, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("VOLVER", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = AppShapes.Input
            ) {
                Text("REINTENTAR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("CERRAR", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DeviceSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun DeviceItem(
    device: BluetoothDeviceDomain,
    isDeviceConnected: Boolean,
    isDeviceConnecting: Boolean,
    isAlreadyRegistered: Boolean,
    onClick: () -> Unit,
    onDisconnect: () -> Unit,
    connectionState: BluetoothConnectionState
) {
    val isConnecting = isDeviceConnecting
    val isRegistered = isAlreadyRegistered

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (!isConnecting && !isDeviceConnected && !isRegistered) onClick else null,
        colors = AppCardDefaults.colors(
            containerColor = if (isDeviceConnected) (if (androidx.compose.foundation.isSystemInDarkTheme()) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.secondaryContainer) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isDeviceConnected) AppCardDefaults.border(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)) else AppCardDefaults.border()
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isDeviceConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                contentDescription = null,
                tint = if (isDeviceConnected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Desconocido",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDeviceConnected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDeviceConnected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when {
                isRegistered -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "REGISTRADO",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                isDeviceConnected -> {
                    TextButton(onClick = onDisconnect) {
                        Text(
                            "DESCONECTAR",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                isConnecting -> {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                else -> {
                    Text(
                        "CONECTAR",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
