package com.example.escanqradmin.presentation.ui.home.components

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
import com.example.escanqradmin.presentation.theme.color.*

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
            shape          = RoundedCornerShape(28.dp),
            color          = Color.White,
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
                            color      = PrimaryBlue
                        )
                        Text(
                            text  = "Busca y conecta tu tarjeta",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    IconButton(
                        onClick  = onDismiss,
                        modifier = Modifier.background(SurfaceGrey, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Error feedback
                if (connectionState is BluetoothConnectionState.Error) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDECEA)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(text = connectionState.message, color = Color(0xFFD32F2F), fontSize = 11.sp, fontWeight = FontWeight.Medium)
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
                            DeviceItem(
                                device            = device,
                                isDeviceConnected = isThisDeviceConnected,
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
                                CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(onClick = onStartScan, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Escanear", tint = PrimaryBlue, modifier = Modifier.size(18.dp))
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
                                device          = device,
                                isDeviceConnected = isThisDeviceConnected,
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
                    shape    = RoundedCornerShape(16.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) Color.Gray else PrimaryBlue
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
                // Technical Info Footer
                Text(
                    text = "Service UUID: 00001101-0000-1000-8000-00805F9B34FB",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray.copy(alpha = 0.6f),
                    fontSize = 8.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun DeviceSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text     = title.uppercase(),
        style    = MaterialTheme.typography.labelSmall,
        color    = Color.Gray,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun DeviceItem(
    device           : BluetoothDeviceDomain,
    isDeviceConnected: Boolean,
    onClick          : () -> Unit,
    onDisconnect     : () -> Unit,
    connectionState  : BluetoothConnectionState
) {
    val isConnecting = connectionState is BluetoothConnectionState.Connecting
    // Note: We don't have the "connecting address" in the state easily here without modifying the state class, 
    // but we can assume it's connecting if the state is Connecting. 
    // To be precise, we'd need the address in the Connecting state.
    
    // For now, let's keep it simple.

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnecting && !isDeviceConnected) { onClick() },
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDeviceConnected) Color(0xFFE8F5E9) else SurfaceGrey.copy(alpha = 0.5f)
        ),
        border = if (isDeviceConnected) BorderStroke(1.dp, Color(0xFF4CAF50)) else null
    ) {
        Row(
            modifier          = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = if (isDeviceConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                contentDescription = null,
                tint               = if (isDeviceConnected) Color(0xFF2E7D32) else PrimaryBlue,
                modifier           = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = device.name ?: "Desconocido",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color      = if (isDeviceConnected) Color(0xFF1B5E20) else Color.Black
                )
                Text(
                    text  = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDeviceConnected) Color(0xFF2E7D32).copy(alpha = 0.7f) else Color.Gray
                )
            }
            
            when {
                isDeviceConnected -> {
                    TextButton(onClick = onDisconnect) {
                        Text(
                            "DESCONECTAR", 
                            color      = Color(0xFFD32F2F), 
                            fontSize   = 10.sp, 
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                isConnecting -> {
                    CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                else -> {
                    Text(
                        "CONECTAR", 
                        color      = PrimaryBlue, 
                        fontSize   = 11.sp, 
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
