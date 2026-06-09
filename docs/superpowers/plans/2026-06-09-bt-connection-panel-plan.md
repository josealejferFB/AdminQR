# BT Connection Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single-device "Lector Físico (ESP32)" card with a multi-gate Bluetooth Connection Panel.

**Architecture:** New `BluetoothConnectionPanel` composable in `components/` renders all gates from `uiState.gates` with per-gate connection status. HomeViewModel gets `connectToGate()` and `getConnectedGateName()` helpers. HomeScreen replaces old card + wires new panel.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Hilt

---

### Task 1: HomeViewModel — connectToGate + helpers

**Files:**
- Modify: `app/src/main/java/com/example/escanqradmin/presentation/ui/home/HomeViewModel.kt`

- [ ] **1.1: Añadir `connectToGate()`**

Añadir después de `connectToEsp32()`:
```kotlin
    fun connectToGate(gate: GateInfo) {
        viewModelScope.launch {
            val isConnected = bluetoothConnectionState.value is BluetoothConnectionState.Connected
            if (isConnected) {
                val currentAddress = (bluetoothConnectionState.value as BluetoothConnectionState.Connected).deviceAddress
                if (currentAddress == gate.macAddress) {
                    _snackbarMessages.emit("Ya conectado a ${gate.name}")
                    return@launch
                }
                disconnect()
                delay(500)
            }
            connectToDevice(gate.macAddress)
        }
    }
```

- [ ] **1.2: Añadir `getConnectedGateName()`**

Añadir después de `connectToGate()`:
```kotlin
    fun getConnectedGate(gates: List<GateInfo>): GateInfo? {
        val state = bluetoothConnectionState.value
        if (state is BluetoothConnectionState.Connected) {
            return gates.firstOrNull { it.macAddress == state.deviceAddress }
        }
        return null
    }
```

- [ ] **1.3: Añadir `isGatePaired()`**

```kotlin
    fun isGatePaired(gate: GateInfo): Boolean {
        return pairedDevices.value.any { it.address == gate.macAddress }
    }
```

- [ ] **1.4: Commit**

```bash
git add app/src/main/java/com/example/escanqradmin/presentation/ui/home/HomeViewModel.kt
git commit -m "feat: add connectToGate and helper methods for multi-gate BT"
```

---

### Task 2: BluetoothConnectionPanel composable

**Files:**
- Create: `app/src/main/java/com/example/escanqradmin/presentation/ui/home/components/BluetoothConnectionPanel.kt`

- [ ] **2.1: Crear composable**

```kotlin
package com.example.escanqradmin.presentation.ui.home.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
                gates.forEach { gate ->
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
                    if (gate != gates.last()) {
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
```

- [ ] **2.2: Commit**

```bash
git add app/src/main/java/com/example/escanqradmin/presentation/ui/home/components/BluetoothConnectionPanel.kt
git commit -m "feat: add BluetoothConnectionPanel composable for multi-gate BT management"
```

---

### Task 3: HomeScreen — wire panel, remove old card

**Files:**
- Modify: `app/src/main/java/com/example/escanqradmin/presentation/ui/home/HomeScreen.kt`

- [ ] **3.1: Reemplazar imports**

Añadir import:
```kotlin
import com.example.escanqradmin.presentation.ui.home.components.BluetoothConnectionPanel
```

- [ ] **3.2: Eliminar la card ESP32 antigua**

Eliminar el bloque completo de la card "Lector Físico (ESP32)" (líneas ~308-452), desde `item { AppCard(` hasta el `}` de cierre de ese item.

- [ ] **3.3: Insertar BluetoothConnectionPanel**

En el mismo lugar (dentro de un `item { ... }`), insertar:

```kotlin
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
                            onPairGate = { gate ->
                                selectedGateForDialog = gate
                                requestBluetoothAction()
                            },
                            onRegisterNew = { showGateRegistrationDialog = true }
                        )
                    }
```

- [ ] **3.4: Commit**

```bash
git add app/src/main/java/com/example/escanqradmin/presentation/ui/home/HomeScreen.kt
git commit -m "refactor: replace single ESP32 card with multi-gate BluetoothConnectionPanel"
```

---

### Self-review

**Spec coverage:**
1. Header with icon + connection badge ✅ (Task 2 — header row with pulse indicator + connectedGateName)
2. Gate list with per-gate status ✅ (Task 2 — GateConnectionRow with states)
3. Conectar / Desconectar / Vincular actions ✅ (Tasks 1 + 2)
4. Empty state ✅ (Task 2 — EmptyConnectionState composable)
5. Footer with "Registrar" button ✅ (Task 2 — FilledTonalButton)
6. HomeViewModel helpers ✅ (Task 1)
7. Remove old card ✅ (Task 3)
8. Wire panel into HomeScreen ✅ (Task 3)

**No placeholders, no type inconsistencies.**
