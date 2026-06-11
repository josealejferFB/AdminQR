package com.example.escanqradmin.presentation.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    var isExpanded by remember { mutableStateOf(gates.isNotEmpty() && connectedGateName != null) }
    val isFullyConnected = connectionState is BluetoothConnectionState.Connected
    val connectedGates = gates.filter { gate ->
        connectionState is BluetoothConnectionState.Connected &&
            connectionState.deviceAddress == gate.macAddress
    }
    val connectedCount = if (isFullyConnected) connectedGates.size else 0

    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            // ── Compact header (always visible) ─────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = gates.isNotEmpty()) { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFullyConnected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFullyConnected) Icons.Default.BluetoothConnected
                                      else Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = if (isFullyConnected) MaterialTheme.colorScheme.secondary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Bluetooth",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isFullyConnected) {
                            Spacer(Modifier.width(6.dp))
                            PulseDot()
                        }
                    }
                    Text(
                        if (connectedGateName != null) "Conectado: $connectedGateName"
                        else if (gates.isEmpty()) "Sin tarjetas registradas"
                        else "${gates.size} tarjeta${if (gates.size != 1) "s" else ""} disponible${if (gates.size != 1) "s" else ""}",
                        fontSize = 11.sp,
                        color = if (isFullyConnected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (gates.isNotEmpty()) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp
                                      else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // ── Expanded content ────────────────────────────
            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(10.dp))

                    if (gates.isEmpty()) {
                        EmptyConnectionState(onRegisterNew = onRegisterNew)
                    } else {
                        gates.forEachIndexed { index, gate ->
                            val isConnected = connectionState is BluetoothConnectionState.Connected &&
                                    connectionState.deviceAddress == gate.macAddress
                            val isPaired = gate.macAddress in pairedDeviceAddresses
                            val isConnecting = connectionState is BluetoothConnectionState.Connecting &&
                                    connectionState.deviceAddress == gate.macAddress

                            GateConnectionCard(
                                gate = gate,
                                isConnected = isConnected,
                                isPaired = isPaired,
                                isConnecting = isConnecting,
                                onConnect = { onConnectToGate(gate) },
                                onDisconnect = onDisconnect,
                                onPair = { onPairGate(gate) }
                            )
                            if (index < gates.lastIndex) {
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    FilledTonalButton(
                        onClick = onRegisterNew,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Registrar tarjeta", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PulseDot() {
    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseOutCubic), RepeatMode.Restart),
        label = "pulseAlpha"
    )
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(18.dp).background(MaterialTheme.colorScheme.secondary.copy(alpha = pulseAlpha), CircleShape))
        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
    }
}

@Composable
private fun GateConnectionCard(
    gate: GateInfo,
    isConnected: Boolean,
    isPaired: Boolean,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onPair: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isConnected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = if (isConnected) androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
        ) else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status + icon
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isConnected -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                            isConnecting -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                            isPaired -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isConnected -> Icons.Default.BluetoothConnected
                        isConnecting -> Icons.Default.BluetoothSearching
                        else -> Icons.Default.Bluetooth
                    },
                    contentDescription = null,
                    tint = when {
                        isConnected -> MaterialTheme.colorScheme.secondary
                        isConnecting -> MaterialTheme.colorScheme.tertiary
                        isPaired -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(12.dp))

            // Info
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        gate.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isConnected) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                "Conectado",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    if (gate.macAddress.isNotEmpty()) "MAC: ${gate.macAddress}"
                    else "BT: ${gate.btName}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action
            when {
                isConnected -> {
                    TextButton(
                        onClick = onDisconnect,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Desconectar",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                isConnecting -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                isPaired -> {
                    Button(
                        onClick = onConnect,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            "Conectar",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                else -> {
                    TextButton(
                        onClick = onPair,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Bluetooth,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Vincular", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyConnectionState(onRegisterNew: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.BluetoothSearching,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "No hay tarjetas registradas",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onRegisterNew,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Registrar primera", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}
