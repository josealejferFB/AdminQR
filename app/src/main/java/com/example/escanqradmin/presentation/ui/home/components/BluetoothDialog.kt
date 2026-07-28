package com.example.escanqradmin.presentation.ui.home.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.escanqradmin.domain.model.BluetoothDeviceDomain
import com.example.escanqradmin.domain.repository.BluetoothConnectionState
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.escanqradmin.presentation.common.sharedcomponents.AppCard
import com.example.escanqradmin.presentation.common.sharedcomponents.AppCardDefaults

@Composable
fun BluetoothDialog(
    onDismiss: () -> Unit,
    pairedDevices: List<BluetoothDeviceDomain>,
    scannedDevices: List<BluetoothDeviceDomain>,
    isScanning: Boolean,
    connectionState: BluetoothConnectionState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    // ── Auto-close logic (only on NEW connection) ──────────────────
    var connectionInitiated by remember { mutableStateOf(false) }

    LaunchedEffect(connectionState) {
        if (connectionState is BluetoothConnectionState.Connected && connectionInitiated) {
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape          = AppShapes.Pill,
            color          = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier       = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text       = "Vincular ESP32",
                            style      = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text  = "Busca y conecta tu tarjeta",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick  = onDismiss,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Error feedback
                if (connectionState is BluetoothConnectionState.Error) {
                    AppCard(
                        colors = AppCardDefaults.colors(containerColor = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = AppShapes.Button,
                        border = null
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(text = connectionState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Device List
                LazyColumn(
                    modifier            = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (pairedDevices.isNotEmpty()) {
                        item { DeviceSectionHeader(title = "Dispositivos Vinculados") }
                        items(pairedDevices) { device ->
                            val isThisDeviceConnected = (connectionState as? BluetoothConnectionState.Connected)?.deviceAddress == device.address
                            val isThisDeviceConnecting = (connectionState as? BluetoothConnectionState.Connecting)?.deviceAddress == device.address
                            DeviceItem(
                                device            = device,
                                isDeviceConnected = isThisDeviceConnected,
                                isDeviceConnecting = isThisDeviceConnecting,
                                onClick           = {
                                    connectionInitiated = true
                                    onConnect(device.address)
                                },
                                onDisconnect      = onDisconnect,
                                connectionState   = connectionState
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
                                Text("No se encontraron dispositivos", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    } else {
                        items(scannedDevices) { device ->
                            val isThisDeviceConnected = (connectionState as? BluetoothConnectionState.Connected)?.deviceAddress == device.address
                            val isThisDeviceConnecting = (connectionState as? BluetoothConnectionState.Connecting)?.deviceAddress == device.address
                            DeviceItem(
                                device          = device,
                                isDeviceConnected = isThisDeviceConnected,
                                isDeviceConnecting = isThisDeviceConnecting,
                                onClick         = {
                                    connectionInitiated = true
                                    onConnect(device.address)
                                },
                                onDisconnect    = onDisconnect,
                                connectionState = connectionState
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Button
                Button(
                    onClick  = if (isScanning) onStopScan else onStartScan,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = AppShapes.Input,
                    colors   = ButtonDefaults.buttonColors(
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
                
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun DeviceSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text     = title.uppercase(),
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun DeviceItem(
    device           : BluetoothDeviceDomain,
    isDeviceConnected: Boolean,
    isDeviceConnecting: Boolean,
    onClick          : () -> Unit,
    onDisconnect     : () -> Unit,
    connectionState  : BluetoothConnectionState
) {
    val isConnecting = isDeviceConnecting
    // Note: We don't have the "connecting address" in the state easily here without modifying the state class, 
    // but we can assume it's connecting if the state is Connecting. 
    // To be precise, we'd need the address in the Connecting state.
    
    // For now, let's keep it simple.

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (!isConnecting && !isDeviceConnected) onClick else null,
        colors = AppCardDefaults.colors(
            containerColor = if (isDeviceConnected) (if (isSystemInDarkTheme()) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.secondaryContainer) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isDeviceConnected) AppCardDefaults.border(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)) else AppCardDefaults.border()
    ) {
        Row(
            modifier          = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = if (isDeviceConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                contentDescription = null,
                tint               = if (isDeviceConnected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = device.name ?: "Desconocido",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color      = if (isDeviceConnected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text  = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDeviceConnected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            when {
                isDeviceConnected -> {
                    TextButton(onClick = onDisconnect) {
                        Text(
                            "DESCONECTAR", 
                            color      = MaterialTheme.colorScheme.error, 
                            style      = MaterialTheme.typography.labelMedium, 
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
                        color      = MaterialTheme.colorScheme.primary, 
                        style      = MaterialTheme.typography.labelMedium, 
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
