package com.example.escanqradmin.presentation.ui.home.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.example.escanqradmin.domain.model.GateInfo
import com.example.escanqradmin.domain.repository.BluetoothConnectionState
import com.example.escanqradmin.presentation.common.sharedcomponents.AppCard
import com.example.escanqradmin.presentation.common.sharedcomponents.AppCardDefaults

@Composable
fun BluetoothConnectionPanel(
    gates: List<GateInfo>,
    connectionState: BluetoothConnectionState,
    pairedDeviceAddresses: List<String>,
    connectedGateName: String?,
    onConnectToGate: (GateInfo) -> Unit,
    onDisconnect: () -> Unit,
    onPairGate: (GateInfo) -> Unit,
    onRegisterNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        border = AppCardDefaults.border(
            if (connectionState is BluetoothConnectionState.Connected)
                Color.Green.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // ── Header ─────────────────────────────────────────
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
                                if (connectionState is BluetoothConnectionState.Connected)
                                    Color.Green.copy(alpha = 0.15f)
                                else
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (connectionState is BluetoothConnectionState.Connected)
                                Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = if (connectionState is BluetoothConnectionState.Connected)
                                Color.Green else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "Conexiones Bluetooth",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (connectedGateName != null) {
                            Text(
                                "Conectado a: $connectedGateName",
                                fontSize = 12.sp,
                                color = Color.Green.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                // ── Pulse indicator ────────────────────────────
                if (connectionState is BluetoothConnectionState.Connected) {
                    val pulseTransition = rememberInfiniteTransition(label = "btPulse")
                    val pulseAlpha by pulseTransition.animateFloat(
                        initialValue = 0.5f, targetValue = 0f,
                        animationSpec = infiniteRepeatable(tween(1000, easing = EaseOutCubic), RepeatMode.Restart),
                        label = "pulseAlpha"
                    )
                    Box(contentAlignment = Alignment.Center) {
                        Box(Modifier.size(24.dp).background(Color.Green.copy(alpha = pulseAlpha), CircleShape))
                        Box(Modifier.size(10.dp).background(Color.Green, CircleShape))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                when (connectionState) {
                                    is BluetoothConnectionState.Connecting -> MaterialTheme.colorScheme.secondary
                                    is BluetoothConnectionState.Error -> MaterialTheme.colorScheme.error
                                    else -> Color.Gray
                                },
                                CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Gate list ──────────────────────────────────────
            if (gates.isEmpty()) {
                EmptyConnectionState(onRegisterNew = onRegisterNew)
            } else {
                gates.forEachIndexed { index, gate ->
                    val isConnected = connectionState is BluetoothConnectionState.Connected &&
                            (connectionState as? BluetoothConnectionState.Connected)?.deviceAddress == gate.macAddress
                    val isPaired = gate.macAddress in pairedDeviceAddresses
                    val isConnecting = connectionState is BluetoothConnectionState.Connecting

                    GateConnectionRow(
                        gate = gate,
                        isConnected = isConnected,
                        isPaired = isPaired,
                        isConnecting = isConnecting,
                        onConnect = { onConnectToGate(gate) },
                        onDisconnect = onDisconnect,
                        onPair = { onPairGate(gate) }
                    )
                    if (index < gates.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Footer ─────────────────────────────────────────
            FilledTonalButton(
                onClick = onRegisterNew,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Registrar nueva tarjeta", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyConnectionState(onRegisterNew: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.BluetoothSearching,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "No hay tarjetas registradas",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onRegisterNew,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Registrar primera tarjeta", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GateConnectionRow(
    gate: GateInfo,
    isConnected: Boolean,
    isPaired: Boolean,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onPair: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    when {
                        isConnected -> Color.Green
                        isConnecting -> MaterialTheme.colorScheme.secondary
                        else -> Color.Gray
                    },
                    CircleShape
                )
        )
        Spacer(modifier = Modifier.width(12.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(gate.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(
                if (gate.macAddress.isNotEmpty()) "MAC: ${gate.macAddress}" else "BT: ${gate.btName}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Action
        when {
            isConnected -> {
                TextButton(onClick = onDisconnect) {
                    Text("Desconectar", color = Color(0xFFD32F2F), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            isConnecting -> {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            isPaired -> {
                TextButton(onClick = onConnect) {
                    Text("Conectar", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            else -> {
                TextButton(onClick = onPair) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Vincular", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
