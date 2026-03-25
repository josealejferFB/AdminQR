package com.example.escanqradmin.presentation.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.escanqradmin.domain.model.BluetoothDeviceDomain
import com.example.escanqradmin.domain.repository.BluetoothConnectionState

@Composable
fun BluetoothDialog(
    onDismiss: () -> Unit,
    pairedDevices: List<BluetoothDeviceDomain>,
    scannedDevices: List<BluetoothDeviceDomain>,
    isScanning: Boolean,
    connectionState: BluetoothConnectionState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vincular Dispositivo",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (connectionState is BluetoothConnectionState.Error) {
                    Text(
                        text = connectionState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        DeviceSectionHeader(title = "Dispositivos Vinculados")
                    }
                    items(pairedDevices) { device ->
                        DeviceItem(
                            device = device,
                            onClick = { onConnect(device.address) },
                            connectionState = connectionState
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DeviceSectionHeader(
                                title = "Otros Dispositivos",
                                modifier = Modifier.weight(1f)
                            )
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(onClick = onStartScan) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Escanear")
                                }
                            }
                        }
                    }
                    items(scannedDevices) { device ->
                        DeviceItem(
                            device = device,
                            onClick = { onConnect(device.address) },
                            connectionState = connectionState
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isScanning) {
                    Button(
                        onClick = onStopScan,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("Detener Escaneo")
                    }
                } else {
                    Button(
                        onClick = onStartScan,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Escanear")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun DeviceItem(
    device: BluetoothDeviceDomain,
    onClick: () -> Unit,
    connectionState: BluetoothConnectionState
) {
    val isConnecting = connectionState is BluetoothConnectionState.Connecting
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnecting) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (device.isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                contentDescription = null,
                tint = if (device.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Dispositivo sin nombre",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                TextButton(onClick = onClick) {
                    Text("Conectar")
                }
            }
        }
    }
}
