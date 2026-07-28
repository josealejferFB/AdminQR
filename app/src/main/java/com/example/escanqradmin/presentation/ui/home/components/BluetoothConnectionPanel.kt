package com.example.escanqradmin.presentation.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.escanqradmin.presentation.theme.shape.AppShapes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
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
    onUnpairGate: (GateInfo) -> Unit,
    onRegisterNew: () -> Unit,
    onSendReportIp: (GateInfo) -> Unit = {},
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
            val noRipple = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = noRipple,
                        indication = null,
                        enabled = gates.isNotEmpty()
                    ) { isExpanded = !isExpanded },
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
                            style = MaterialTheme.typography.titleSmall,
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
                        style = MaterialTheme.typography.labelSmall,
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
                                onPair = { onPairGate(gate) },
                                onUnpair = { onUnpairGate(gate) },
                                onSendReportIp = { onSendReportIp(gate) }
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
                        shape = AppShapes.Button
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Registrar tarjeta", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
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
    onPair: () -> Unit,
    onUnpair: () -> Unit,
    onSendReportIp: () -> Unit = {}
) {
    var sendingReport by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    if (sendingReport) {
        LaunchedEffect(Unit) {
            delay(3000)
            sendingReport = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.Button,
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
                        style = MaterialTheme.typography.labelLarge,
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
                                style = MaterialTheme.typography.labelSmall,
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
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action
            when {
                isConnected -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (sendingReport) "Enviando..." else "Reenviar IP", color = MaterialTheme.colorScheme.secondary) },
                                    onClick = {
                                        if (!sendingReport) {
                                            sendingReport = true
                                            onSendReportIp()
                                            showMenu = false
                                        }
                                    },
                                    leadingIcon = {
                                        if (sendingReport) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.secondary)
                                        } else {
                                            Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Desconectar", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        onDisconnect()
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.BluetoothDisabled, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    }
                                )
                            }
                        }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = onConnect,
                            shape = AppShapes.Chip,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Conectar", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Desvincular", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        onUnpair()
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.BluetoothDisabled, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    }
                                )
                            }
                        }
                    }
                }
                else -> {
                    TextButton(
                        onClick = onPair,
                        ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Bluetooth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Vincular", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onRegisterNew,
            shape = AppShapes.Surface,
            ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Registrar primera", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
    }
}
