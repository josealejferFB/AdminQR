package com.example.escanqradmin.presentation.ui.home.components

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.escanqradmin.domain.model.BluetoothDeviceDomain
import com.example.escanqradmin.domain.repository.BluetoothConnectionState
import com.example.escanqradmin.presentation.common.sharedcomponents.AppCard
import com.example.escanqradmin.presentation.common.sharedcomponents.AppCardDefaults
import com.example.escanqradmin.presentation.ui.home.GateRegistrationUiState
import com.example.escanqradmin.presentation.ui.home.GateStep

@Composable
fun GateRegistrationDialog(
    uiState: GateRegistrationUiState,
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
    onBtNameChange: (String) -> Unit,
    onHostnameChange: (String) -> Unit,
    onRefreshNetworks: () -> Unit,
    onSelectNetwork: (String) -> Unit,
    onSendWiFiConfig: () -> Unit,
    onGateNameChange: (String) -> Unit,
    onGateDescriptionChange: (String) -> Unit,
    onRegisterGate: () -> Unit,
    onDismissError: () -> Unit,
    onGoBackFromError: () -> Unit,
    onDismiss: () -> Unit,
    onReset: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
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
                                is GateStep.WiFiConfig -> "Configurar WiFi del ESP32"
                                is GateStep.VerifyingWifi -> "Verificando conexión WiFi"
                                is GateStep.NameGate -> "Asignar nombre al portón"
                                is GateStep.Registering -> "Registrando en Odoo"
                                is GateStep.Done -> "Portón registrado"
                                is GateStep.Error -> "Error"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                AnimatedContent(
                    targetState = uiState.step,
                    transitionSpec = { fadeIn() togetherWith fadeOut() }
                ) { currentStep ->
                    when (currentStep) {
                        is GateStep.SelectBluetooth -> SelectBluetoothContent(
                            scannedDevices = scannedDevices,
                            pairedDevices = pairedDevices,
                            isScanning = isScanning,
                            connectionState = connectionState,
                            onStartScan = onStartScan,
                            onStopScan = onStopScan,
                            onConnectToDevice = onConnectToDevice,
                            onCancelConnection = onCancelConnection
                        )
                        is GateStep.WiFiConfig -> WiFiConfigContent(
                            uiState = uiState,
                            onSsidChange = onSsidChange,
                            onPasswordChange = onPasswordChange,
                            onBtNameChange = onBtNameChange,
                            onHostnameChange = onHostnameChange,
                            onRefreshNetworks = onRefreshNetworks,
                            onSelectNetwork = onSelectNetwork,
                            onSendWiFiConfig = onSendWiFiConfig
                        )
                        is GateStep.VerifyingWifi -> VerifyingWifiContent()
                        is GateStep.NameGate -> NameGateContent(
                            uiState = uiState,
                            onGateNameChange = onGateNameChange,
                            onGateDescriptionChange = onGateDescriptionChange,
                            onRegisterGate = onRegisterGate,
                            onGoBack = { onDismissError() }
                        )
                        is GateStep.Registering -> RegisteringContent()
                        is GateStep.Done -> DoneContent(
                            uiState = uiState,
                            onDismiss = onDismiss
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
}

@Composable
private fun ColumnScope.SelectBluetoothContent(
    scannedDevices: List<BluetoothDeviceDomain>,
    pairedDevices: List<BluetoothDeviceDomain>,
    isScanning: Boolean,
    connectionState: BluetoothConnectionState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectToDevice: (String, String?) -> Unit,
    onCancelConnection: () -> Unit
) {
    if (connectionState is BluetoothConnectionState.Error) {
        AppCard(
            colors = AppCardDefaults.colors(containerColor = Color(0xFFFDECEA)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp),
            border = null
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(text = connectionState.message, color = Color(0xFFD32F2F), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (pairedDevices.isNotEmpty()) {
            item { DeviceSectionHeader(title = "Dispositivos Vinculados") }
            items(pairedDevices) { device ->
                val isThisDeviceConnected = (connectionState as? BluetoothConnectionState.Connected)?.deviceAddress == device.address
                DeviceItem(
                    device = device,
                    isDeviceConnected = isThisDeviceConnected,
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
                    Text("No se encontraron dispositivos", color = Color.Gray, fontSize = 12.sp)
                }
            }
        } else {
            items(scannedDevices) { device ->
                val isThisDeviceConnected = (connectionState as? BluetoothConnectionState.Connected)?.deviceAddress == device.address
                DeviceItem(
                    device = device,
                    isDeviceConnected = isThisDeviceConnected,
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
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isScanning) Color.Gray else MaterialTheme.colorScheme.secondary
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WiFiConfigContent(
    uiState: GateRegistrationUiState,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onBtNameChange: (String) -> Unit,
    onHostnameChange: (String) -> Unit,
    onRefreshNetworks: () -> Unit,
    onSelectNetwork: (String) -> Unit,
    onSendWiFiConfig: () -> Unit,
) {
    var ssidExpanded by remember { mutableStateOf(false) }

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
            "Ingresa las credenciales WiFi para el ESP32",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
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
                    shape = RoundedCornerShape(12.dp),
                    enabled = !uiState.isSubmitting,
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
            shape = RoundedCornerShape(12.dp),
            visualTransformation = PasswordVisualTransformation(),
            enabled = !uiState.isSubmitting
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.btName,
            onValueChange = onBtNameChange,
            label = { Text("Nombre Bluetooth del ESP32") },
            placeholder = { Text("ESP32_Seguro") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = !uiState.isSubmitting
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.hostname,
            onValueChange = onHostnameChange,
            label = { Text("Nombre en el Router (Hostname)") },
            placeholder = { Text("ESP32-Gate") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = !uiState.isSubmitting
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSendWiFiConfig,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = uiState.ssid.isNotBlank() && !uiState.isSubmitting
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
                color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "El ESP32 se está conectando a la red WiFi. Esto puede tomar hasta 30 segundos.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun NameGateContent(
    uiState: GateRegistrationUiState,
    onGateNameChange: (String) -> Unit,
    onGateDescriptionChange: (String) -> Unit,
    onRegisterGate: () -> Unit,
    onGoBack: () -> Unit
) {
    Column {
        Text(
            "Portón Detectado",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            colors = AppCardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("MAC Address", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(uiState.macAddress, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = uiState.gateName,
            onValueChange = onGateNameChange,
            label = { Text("Nombre del Portón") },
            placeholder = { Text("Ej: Portón Principal") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = !uiState.isSubmitting
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.gateDescription,
            onValueChange = onGateDescriptionChange,
            label = { Text("Descripción (opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = !uiState.isSubmitting
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRegisterGate,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = uiState.gateName.isNotBlank() && !uiState.isSubmitting
        ) {
            Text("REGISTRAR EN ODOO", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onGoBack,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(14.dp),
            enabled = !uiState.isSubmitting
        ) {
            Icon(Icons.Default.NavigateBefore, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("CAMBIAR CONFIGURACIÓN WIFI", fontWeight = FontWeight.Medium, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RegisteringContent() {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Registrando portón...", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }
}

@Composable
private fun DoneContent(
    uiState: GateRegistrationUiState,
    onDismiss: () -> Unit
) {
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
            "Portón '${uiState.gateName}' registrado exitosamente",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (uiState.registeredGateId != null) {
            Spacer(Modifier.height(4.dp))
            Text("ID: ${uiState.registeredGateId}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("CERRAR", fontWeight = FontWeight.Bold)
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
        Text(errorMessage, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onGoBack,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.NavigateBefore, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("VOLVER", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp)
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
        color = Color.Gray,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun DeviceItem(
    device: BluetoothDeviceDomain,
    isDeviceConnected: Boolean,
    onClick: () -> Unit,
    onDisconnect: () -> Unit,
    connectionState: BluetoothConnectionState
) {
    val isConnecting = connectionState is BluetoothConnectionState.Connecting

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (!isConnecting && !isDeviceConnected) onClick else null,
        colors = AppCardDefaults.colors(
            containerColor = if (isDeviceConnected) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isDeviceConnected) AppCardDefaults.border(color = Color(0xFF4CAF50).copy(alpha = 0.1f)) else AppCardDefaults.border()
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isDeviceConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                contentDescription = null,
                tint = if (isDeviceConnected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Desconocido",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDeviceConnected) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDeviceConnected) Color(0xFF2E7D32).copy(alpha = 0.7f) else Color.Gray
                )
            }

            when {
                isDeviceConnected -> {
                    TextButton(onClick = onDisconnect) {
                        Text(
                            "DESCONECTAR",
                            color = Color(0xFFD32F2F),
                            fontSize = 10.sp,
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
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
