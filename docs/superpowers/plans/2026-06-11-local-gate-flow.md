# Registro Local + Configuración Odoo Diferida — Plan de Implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Separar la configuración WiFi local del ESP32 del registro en Odoo, permitiendo que el portón aparezca en los chips inmediatamente después de configurar WiFi, con estado "No configurada con el servidor".

**Architecture:** Se modifica el `GateRegistrationViewModel` para que después de `VerifyingWifi` emita un evento `GateConfiguredLocally` en vez de ir a `NameGate`. `HomeViewModel` mantiene una lista local de portones (`_localGates`) que se fusiona con los portones de Odoo en la UI. Nuevo `OdooConfigDialog` para configurar Odoo desde chips locales.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, kotlinx.serialization

---

### Task 1: Modificar `GateInfo` — id nullable + flags

**Files:**
- Modify: `app/src/main/java/com/example/escanqradmin/domain/model/GateInfo.kt:1-10`

- [ ] **Reemplazar contenido de GateInfo**

```kotlin
package com.example.escanqradmin.domain.model

data class GateInfo(
    val id: Int? = null,
    val name: String,
    val macAddress: String,
    val ipAddress: String? = null,
    val isOnline: Boolean = false,
    val btName: String = "ESP32_Seguro",
    val hostname: String = "",
    val isOdooRegistered: Boolean = false
)
```

- [ ] **Commit**

```bash
git add app/src/main/java/com/example/escanqradmin/domain/model/GateInfo.kt
git commit -m "model: GateInfo con id nullable, hostname, isOdooRegistered"
```

---

### Task 2: Simplificar `GateRegistrationViewModel` — unificar gateName, eliminar pasos Odoo

**Files:**
- Modify: `app/src/main/java/com/example/escanqradmin/presentation/ui/home/GateRegistrationViewModel.kt:1-391`

- [ ] **Cambiar `GateStep`** — eliminar NameGate, Registering, Done; agregar LocalDone

```kotlin
sealed class GateStep {
    data object SelectBluetooth : GateStep()
    data class WiFiConfig(val macAddress: String? = null) : GateStep()
    data object VerifyingWifi : GateStep()
    data object LocalDone : GateStep()
    data class Error(val message: String) : GateStep()
}
```

- [ ] **Cambiar `GateRegistrationUiState`** — eliminar btName, hostname, gateDescription, registeredGateId

```kotlin
data class GateRegistrationUiState(
    val step: GateStep = GateStep.SelectBluetooth,
    val gateName: String = "",
    val ssid: String = "",
    val password: String = "",
    val macAddress: String = "",
    val isSubmitting: Boolean = false,
    val availableNetworks: List<String> = emptyList(),
    val isLoadingNetworks: Boolean = false
)
```

- [ ] **Cambiar `GateRegistrationEvent`** — agregar `GateConfiguredLocally`

```kotlin
sealed class GateRegistrationEvent {
    data object CloseDialog : GateRegistrationEvent()
    data class GateConfiguredLocally(
        val name: String,
        val macAddress: String,
        val btName: String,
        val hostname: String
    ) : GateRegistrationEvent()
}
```

- [ ] **Actualizar `connectToBluetoothDevice`** — ya no auto-rellena btName/hostname, solo setea step WiFiConfig

```kotlin
fun connectToBluetoothDevice(address: String, deviceName: String? = null) {
    viewModelScope.launch {
        lastDeviceAddress = address
        _uiState.update { it.copy(isSubmitting = true) }
        bluetoothRepository.connectToDevice(address)
        bluetoothRepository.connectionState.first { state ->
            when (state) {
                is BluetoothConnectionState.Connected -> {
                    _uiState.update { it.copy(step = GateStep.WiFiConfig(), isSubmitting = false) }
                    true
                }
                is BluetoothConnectionState.Error -> {
                    _uiState.update { it.copy(step = GateStep.Error(state.message), isSubmitting = false) }
                    true
                }
                else -> false
            }
        }
    }
}
```

- [ ] **Actualizar `sendWiFiConfig`** — gateName se usa para bt_name y hostname

```kotlin
fun sendWiFiConfig() {
    viewModelScope.launch {
        val state = _uiState.value
        _uiState.update { it.copy(isSubmitting = true) }

        val safeHostname = state.gateName.lowercase()
            .replace(Regex("[^a-z0-9-]"), "-")
            .trim('-')
            .take(63)

        val payload = buildJsonObject {
            put("action", "config_network")
            put("ssid", state.ssid)
            put("password", state.password)
            put("bt_name", state.gateName)
            put("hostname", safeHostname)
        }.toString()

        val response = bluetoothRepository.sendMessageAndWaitForReply(payload)

        if (response == null) {
            _uiState.update { it.copy(step = GateStep.Error("No se recibió respuesta del ESP32"), isSubmitting = false) }
            return@launch
        }

        try {
            val jsonElement = json.parseToJsonElement(response)
            val obj = jsonElement.jsonObject
            val status = obj["status"]?.jsonPrimitive?.content ?: "error"
            if (status == "success") {
                val mac = obj["mac_address"]?.jsonPrimitive?.content
                if (mac != null) {
                    _uiState.update { it.copy(step = GateStep.VerifyingWifi, macAddress = mac, isSubmitting = true) }
                    verifyWiFiConnection()
                } else {
                    _uiState.update { it.copy(step = GateStep.Error("Respuesta del ESP32 no contiene mac_address"), isSubmitting = false) }
                }
            } else {
                val msg = obj["message"]?.jsonPrimitive?.content ?: "Error del ESP32"
                _uiState.update { it.copy(step = GateStep.Error(msg), isSubmitting = false) }
            }
        } catch (_: Exception) {
            _uiState.update { it.copy(step = GateStep.Error("Respuesta inválida del ESP32"), isSubmitting = false) }
        }
    }
}
```

- [ ] **Actualizar `verifyWiFiConnection`** — al reconectar emite GateConfiguredLocally + CloseDialog en vez de NameGate

```kotlin
private fun verifyWiFiConnection() {
    verificationJob?.cancel()
    verificationJob = viewModelScope.launch {
        val address = lastDeviceAddress
        if (address == null) {
            _uiState.update { it.copy(step = GateStep.Error("Error interno"), isSubmitting = false) }
            return@launch
        }

        delay(3000)

        val startTime = System.currentTimeMillis()
        val maxDuration = 45_000L

        while (System.currentTimeMillis() - startTime < maxDuration) {
            bluetoothRepository.disconnect()
            delay(300)
            bluetoothRepository.connectToDevice(address)

            val connected = try {
                withTimeout(5000) {
                    bluetoothRepository.connectionState.first { state ->
                        state is BluetoothConnectionState.Connected || state is BluetoothConnectionState.Error
                    }
                }
                bluetoothRepository.connectionState.value is BluetoothConnectionState.Connected
            } catch (_: Exception) {
                false
            }

            if (connected) {
                val state = _uiState.value
                val safeHostname = state.gateName.lowercase()
                    .replace(Regex("[^a-z0-9-]"), "-")
                    .trim('-')
                    .take(63)
                _events.emit(GateRegistrationEvent.GateConfiguredLocally(
                    name = state.gateName,
                    macAddress = state.macAddress,
                    btName = state.gateName,
                    hostname = safeHostname
                ))
                _events.emit(GateRegistrationEvent.CloseDialog)
                _uiState.update { it.copy(step = GateStep.LocalDone, isSubmitting = false) }
                return@launch
            }
        }

        _uiState.update {
            it.copy(
                step = GateStep.Error("No se pudo reconectar con el ESP32 tras enviar la configuración WiFi. Verifica que el nombre de red y contraseña sean correctos."),
                isSubmitting = false
            )
        }
    }
}
```

- [ ] **Eliminar `registerGate()`** — ya no se usa (el registro se hace desde OdooConfigDialog)

```kotlin
// Eliminar completa la función registerGate() y su llamada desde el flujo
```

- [ ] **Actualizar `goBackOneStep`** — quitar referencias a NameGate, ajustar targets

```kotlin
fun goBackOneStep() {
    val currentStep = _uiState.value.step
    if (currentStep is GateStep.Error || currentStep is GateStep.WiFiConfig || currentStep is GateStep.VerifyingWifi) {
        val targetStep = when {
            _uiState.value.ssid.isNotEmpty() -> GateStep.WiFiConfig()
            else -> GateStep.SelectBluetooth
        }
        _uiState.update { it.copy(step = targetStep) }
    }
}
```

- [ ] **Actualizar `closeDialog`** — sin cambios, ya reinicia estado.

- [ ] **Commit**

```bash
git add app/src/main/java/com/example/escanqradmin/presentation/ui/home/GateRegistrationViewModel.kt
git commit -m "vm: GateRegistration simplificado, gateName único, eventos local"
```

---

### Task 3: `HomeViewModel` — lista local de portones + selección por MAC

**Files:**
- Modify: `app/src/main/java/com/example/escanqradmin/presentation/ui/home/HomeViewModel.kt`

- [ ] **Agregar `_localGates` y métodos de gestión**

```kotlin
// Después de private val _uiState...
private val _localGates = MutableStateFlow<List<GateInfo>>(emptyList())

fun addLocalGate(name: String, macAddress: String, btName: String, hostname: String) {
    val gate = GateInfo(
        id = null,
        name = name,
        macAddress = macAddress,
        btName = btName,
        hostname = hostname,
        isOdooRegistered = false
    )
    _localGates.update { it + gate }
    loadGates()
}

fun markGateAsOdooRegistered(macAddress: String, odooId: Int) {
    _localGates.update { gates ->
        gates.map { if (it.macAddress == macAddress) it.copy(id = odooId, isOdooRegistered = true) else it }
    }
    loadGates()
}
```

- [ ] **Actualizar `loadGates()`** — fusionar Odoo gates + local gates

```kotlin
fun loadGates() {
    viewModelScope.launch {
        gateRepository.getGates().onSuccess { odooGates ->
            val local = _localGates.value.filter { !it.isOdooRegistered }
            _uiState.update { it.copy(gates = odooGates + local) }
        }.onFailure {
            val local = _localGates.value.filter { !it.isOdooRegistered }
            _uiState.update { it.copy(gates = local) }
        }
    }
}
```

- [ ] **Cambiar selección de `selectedGateId: Int?` a `selectedMacAddress: String?`**

```kotlin
data class HomeUiState(
    val totalScans: Int = 0,
    val totalUsers: Int = 0,
    val activeUsers: List<ActiveUser> = emptyList(),
    val isRefreshing: Boolean = false,
    val isServerOnline: Boolean = true,
    val gates: List<GateInfo> = emptyList(),
    val selectedMacAddress: String? = null,
    val gateUsers: List<ActiveUser> = emptyList()
)

fun selectGate(macAddress: String?) {
    _uiState.update { it.copy(selectedMacAddress = macAddress) }
    if (macAddress != null) {
        val gate = _uiState.value.gates.find { it.macAddress == macAddress }
        if (gate?.id != null) {
            loadGateUsers(gate.id!!)
        }
    } else {
        _uiState.update { it.copy(gateUsers = emptyList()) }
    }
}
```

- [ ] **Commit**

```bash
git add app/src/main/java/com/example/escanqradmin/presentation/ui/home/HomeViewModel.kt
git commit -m "vm: HomeViewModel con localGates y selección por MAC"
```

---

### Task 4: Simplificar `GateRegistrationDialog` — WiFiConfig con gateName

**Files:**
- Modify: `app/src/main/java/com/example/escanqradmin/presentation/ui/home/components/GateRegistrationDialog.kt`

- [ ] **Actualizar función signature** — eliminar callbacks de btName/hostname/gateDescription/register

```kotlin
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
    onGateNameChange: (String) -> Unit,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRefreshNetworks: () -> Unit,
    onSelectNetwork: (String) -> Unit,
    onSendWiFiConfig: () -> Unit,
    onDismissError: () -> Unit,
    onGoBackFromError: () -> Unit,
    onDismiss: () -> Unit,
    onReset: () -> Unit
)
```

Callbacks eliminados: `onBtNameChange`, `onHostnameChange`, `onGateDescriptionChange`, `onRegisterGate`.

- [ ] **Actualizar `AnimatedContent`** — reemplazar NameGate/Registering/Done con LocalDone (mostrar check y cerrar)

```kotlin
is GateStep.LocalDone -> {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Portón configurado", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(uiState.gateName, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text("MAC: ${uiState.macAddress}", style = MaterialTheme.typography.bodySmall)
    }
}
```

- [ ] **Actualizar `WiFiConfigContent`** — eliminar campos btName y hostname, agregar campo gateName

```kotlin
@Composable
private fun WiFiConfigContent(
    uiState: GateRegistrationUiState,
    onGateNameChange: (String) -> Unit,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRefreshNetworks: () -> Unit,
    onSelectNetwork: (String) -> Unit,
    onSendWiFiConfig: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Configurar WiFi", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.gateName,
            onValueChange = onGateNameChange,
            label = { Text("Nombre del portón") },
            placeholder = { Text("Mi Portón") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // SSID dropdown (existing)
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = uiState.ssid,
                onValueChange = onSsidChange,
                label = { Text("Red WiFi (SSID)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                singleLine = true
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                uiState.availableNetworks.forEach { ssid ->
                    DropdownMenuItem(
                        text = { Text(ssid) },
                        onClick = { onSelectNetwork(ssid); expanded = false }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onRefreshNetworks) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar redes")
                Spacer(Modifier.width(4.dp))
                Text("Redes")
            }
            Button(
                onClick = onSendWiFiConfig,
                enabled = uiState.gateName.isNotBlank() && uiState.ssid.isNotBlank() && uiState.password.isNotBlank()
            ) {
                Text("ENVIAR AL ESP32")
            }
        }
    }
}
```

- [ ] **Eliminar `NameGateContent` y `RegisteringContent`** — ya no existen. El `AnimatedContent` ya no referencia NameGate, Registering ni Done salvo LocalDone (que es simple check).

- [ ] **Commit**

```bash
git add app/src/main/java/com/example/escanqradmin/presentation/ui/home/components/GateRegistrationDialog.kt
git commit -m "ui: GateRegistrationDialog simplificado con gateName único"
```

---

### Task 5: Crear `OdooConfigDialog` — registro en backend + URL al ESP32

**Files:**
- Create: `app/src/main/java/com/example/escanqradmin/presentation/ui/home/components/OdooConfigDialog.kt`

- [ ] **Crear el diálogo**

```kotlin
package com.example.escanqradmin.presentation.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.escanqradmin.domain.model.GateInfo
import com.example.escanqradmin.domain.repository.BluetoothConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OdooConfigDialog(
    gate: GateInfo,
    connectionStateProvider: () -> BluetoothConnectionState,
    onConnect: (String) -> Unit,
    onSendMessageAndWaitForReply: suspend (String, Long) -> String?,
    onRegisterInOdoo: suspend (String, String) -> Result<Int>,
    onDismiss: () -> Unit,
    onSuccess: (odooId: Int) -> Unit
) {
    var gateName by remember { mutableStateOf(gate.name) }
    var protocol by remember { mutableStateOf("http") }
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8059") }
    var ipError by remember { mutableStateOf<String?>(null) }
    var phase by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = { Text("Configurar con Odoo") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (result != null) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(result!!, style = MaterialTheme.typography.bodyMedium)
                } else if (phase != null) {
                    Text(phase!!, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(8.dp))
                    if (isWorking) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(
                        value = gateName,
                        onValueChange = { gateName = it },
                        label = { Text("Nombre del portón") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Servidor Odoo", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))

                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = protocol,
                            onValueChange = { protocol = it },
                            label = { Text("Protocolo") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("http", "https").forEach { p ->
                                DropdownMenuItem(text = { Text(p) }, onClick = { protocol = p; expanded = false })
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it; ipError = null },
                        label = { Text("IP del servidor") },
                        isError = ipError != null,
                        supportingText = ipError?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Puerto") },
                        placeholder = { Text("8059") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (result == null) {
                Button(
                    onClick = {
                        if (!isValidIp(ip)) {
                            ipError = "IP inválida"
                            return@Button
                        }
                        isWorking = true
                        phase = "Registrando en servidor..."
                        scope.launch {
                            val registerResult = onRegisterInOdoo(gateName, gate.macAddress)
                            registerResult.fold(
                                onSuccess = { odooId ->
                                    phase = "Conectando al ESP32..."
                                    onConnect(gate.macAddress)

                                    val maxWait = System.currentTimeMillis() + 10000
                                    while (System.currentTimeMillis() < maxWait) {
                                        val state = connectionStateProvider()
                                        if (state is BluetoothConnectionState.Connected) break
                                        if (state is BluetoothConnectionState.Error) {
                                            phase = "Error de conexión BT"
                                            isWorking = false
                                            result = "Registrado en Odoo, pero no se pudo enviar la URL al ESP32. Reintenta desde el chip."
                                            return@launch
                                        }
                                        delay(500)
                                    }

                                    if (connectionStateProvider() !is BluetoothConnectionState.Connected) {
                                        phase = "No se pudo conectar BT"
                                        isWorking = false
                                        result = "Registrado en Odoo, pero sin conexión BT al ESP32."
                                        return@launch
                                    }

                                    phase = "Enviando URL de Odoo al ESP32..."
                                    val payload = "{\"protocolo\":\"$protocol\",\"ip_odoo\":\"$ip\",\"port\":$port}"
                                    val reply = onSendMessageAndWaitForReply("config\n$payload\n", 10000)

                                    if (reply != null && reply.contains("CONFIG_OK")) {
                                        phase = "Reiniciando ESP32..."
                                        delay(1500)
                                        result = "Portón configurado con Odoo correctamente"
                                        onSuccess(odooId)
                                    } else {
                                        result = "Registrado en Odoo, pero el ESP32 no confirmó la URL. Reintenta desde el chip."
                                    }
                                    isWorking = false
                                },
                                onFailure = { e ->
                                    phase = null
                                    result = "Error al registrar en Odoo: ${e.message}"
                                    isWorking = false
                                }
                            )
                        }
                    },
                    enabled = gateName.isNotBlank() && ip.isNotBlank() && port.isNotBlank() && !isWorking
                ) { Text("CONFIGURAR") }
            }
        },
        dismissButton = {
            if (result == null) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        }
    )
}

private fun isValidIp(value: String): Boolean {
    val octets = value.split(".")
    if (octets.size != 4) return false
    return octets.all { o -> o.toIntOrNull()?.let { it in 0..255 } ?: false }
}
```

- [ ] **Commit**

```bash
git add app/src/main/java/com/example/escanqradmin/presentation/ui/home/components/OdooConfigDialog.kt
git commit -m "ui: OdooConfigDialog para registrar portón + configurar URL ESP32"
```

---

### Task 6: `HomeScreen` — GateChipRow con estado + OdooConfigDialog + evento local

**Files:**
- Modify: `app/src/main/java/com/example/escanqradmin/presentation/ui/home/HomeScreen.kt`

- [ ] **Actualizar import** — agregar OdooConfigDialog

```kotlin
import com.example.escanqradmin.presentation.ui.home.components.OdooConfigDialog
```

- [ ] **Agregar estado showOdooDialog**

```kotlin
var showOdooDialog by remember { mutableStateOf(false) }
// junto con los otros show*Dialog states
```

- [ ] **Actualizar el collector de eventos** — manejar GateConfiguredLocally

```kotlin
LaunchedEffect(Unit) {
    viewModel.refreshData()
    viewModel.snackbarMessages.collectLatest { message ->
        snackbarHostState.showSnackbar(message)
    }
}
LaunchedEffect(Unit) {
    registrationViewModel.events.collect { event ->
        when (event) {
            is GateRegistrationEvent.CloseDialog -> {
                showGateRegistrationDialog = false
                viewModel.loadGates()
            }
            is GateRegistrationEvent.GateConfiguredLocally -> {
                viewModel.addLocalGate(event.name, event.macAddress, event.btName, event.hostname)
                showGateRegistrationDialog = false
            }
        }
    }
}
```

- [ ] **Actualizar `GateChipRow` signature** — selectedMacAddress + onSelect por MAC + gate status

```kotlin
private fun GateChipRow(
    gates: List<GateInfo>,
    selectedMacAddress: String?,
    onSelect: (String?) -> Unit,
    onAddGate: () -> Unit,
    onConfigureIp: (GateInfo) -> Unit,
    onChangeHostname: (GateInfo) -> Unit,
    onRename: (GateInfo) -> Unit,
    onConfigureOdoo: (GateInfo) -> Unit,
    onDetails: (GateInfo) -> Unit
)
```

Nuevo callback: `onConfigureOdoo: (GateInfo) -> Unit`.

- [ ] **Actualizar el chip rendering** — mostrar estado y dropdown condicional

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GateChipRow(
    gates: List<GateInfo>,
    selectedMacAddress: String?,
    onSelect: (String?) -> Unit,
    onAddGate: () -> Unit,
    onConfigureIp: (GateInfo) -> Unit,
    onChangeHostname: (GateInfo) -> Unit,
    onRename: (GateInfo) -> Unit,
    onConfigureOdoo: (GateInfo) -> Unit,
    onDetails: (GateInfo) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // "Todas" chip
        item {
            FilterChip(
                selected = selectedMacAddress == null,
                onClick = { onSelect(null) },
                label = { Text("Todas") }
            )
        }

        items(gates, key = { it.macAddress }) { gate ->
            var showMenu by remember { mutableStateOf(false) }
            FilterChip(
                selected = selectedMacAddress == gate.macAddress,
                onClick = { onSelect(gate.macAddress) },
                label = {
                    Column {
                        Text(gate.name)
                        if (!gate.isOdooRegistered) {
                            Text(
                                "No configurada",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                trailingIcon = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (gate.isOdooRegistered) {
                                DropdownMenuItem(
                                    text = { Text("Ver detalles") },
                                    onClick = { showMenu = false; onDetails(gate) }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Configurar con Odoo") },
                                    onClick = { showMenu = false; onConfigureOdoo(gate) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Configurar IP") },
                                onClick = { showMenu = false; onConfigureIp(gate) }
                            )
                            DropdownMenuItem(
                                text = { Text("Cambiar Hostname") },
                                onClick = { showMenu = false; onChangeHostname(gate) }
                            )
                            if (gate.isOdooRegistered) {
                                DropdownMenuItem(
                                    text = { Text("Renombrar") },
                                    onClick = { showMenu = false; onRename(gate) }
                                )
                            }
                        }
                    }
                }
            )
        }

        // Botón +
        item {
            IconButton(onClick = onAddGate) {
                Icon(Icons.Default.Add, contentDescription = "Agregar portón")
            }
        }
    }
}
```

- [ ] **Actualizar llamada a `GateChipRow`** en HomeScreen — mapear callbacks

```kotlin
GateChipRow(
    gates = uiState.gates,
    selectedMacAddress = uiState.selectedMacAddress,
    onSelect = { viewModel.selectGate(it) },
    onAddGate = { showGateRegistrationDialog = true },
    onConfigureIp = { gate -> selectedGateForDialog = gate; showGateIpDialog = true },
    onChangeHostname = { gate -> selectedGateForDialog = gate; showHostnameDialog = true },
    onRename = { gate -> selectedGateForDialog = gate; showRenameDialog = true },
    onConfigureOdoo = { gate -> selectedGateForDialog = gate; showOdooDialog = true },
    onDetails = { gate -> /* TODO */ }
)
```

- [ ] **Agregar el OdooConfigDialog** en el bloque de diálogos

```kotlin
if (showOdooDialog && selectedGateForDialog != null) {
    OdooConfigDialog(
        gate = selectedGateForDialog!!,
        connectionStateProvider = { bluetoothConnectionState },
        onConnect = { address -> viewModel.connectToDevice(address) },
        onSendMessageAndWaitForReply = { msg, timeout -> viewModel.sendMessageAndWaitForReply(msg, timeout) },
        onRegisterInOdoo = { name, mac ->
            viewModel.syncRepository.registerGate(name, mac, "")
        },
        onDismiss = { showOdooDialog = false; selectedGateForDialog = null },
        onSuccess = { odooId ->
            viewModel.markGateAsOdooRegistered(selectedGateForDialog!!.macAddress, odooId)
            showOdooDialog = false
            selectedGateForDialog = null
        }
    )
}
```

- [ ] **Actualizar los botones de gate del dropdown** para que funcione con la nueva selección por MAC

- [ ] **Commit**

```bash
git add app/src/main/java/com/example/escanqradmin/presentation/ui/home/HomeScreen.kt
git commit -m "ui: HomeScreen con GateChipRow de estado + OdooConfigDialog"
```

---

### Task 7: Actualizar `ChangeHostnameDialog` — pre-llenado desde name

**Files:**
- Modify: `app/src/main/java/com/example/escanqradmin/presentation/ui/home/components/ChangeHostnameDialog.kt:22`

- [ ] **Cambiar valor inicial** — usar `gate.name` en vez de `gate.btName`

```kotlin
var hostname by remember { mutableStateOf(gate.name.ifBlank { "ESP32-Gate" }) }
```

- [ ] **Commit**

```bash
git add app/src/main/java/com/example/escanqradmin/presentation/ui/home/components/ChangeHostnameDialog.kt
git commit -m "fix: ChangeHostnameDialog pre-llena hostname desde gate.name"
```

---

### Verificación final

- [ ] **Build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Commit final si hay ajustes**

```bash
git add -A
git commit -m "fix: ajustes post-build local gate flow"
```

---

## Resumen de archivos

| Archivo | Acción |
|---|---|
| `domain/model/GateInfo.kt` | Modificar: id nullable, hostname, isOdooRegistered |
| `presentation/ui/home/GateRegistrationViewModel.kt` | Modificar: gateName único, eliminar steps Odoo, evento local |
| `presentation/ui/home/HomeViewModel.kt` | Modificar: localGates, merge list, selección MAC |
| `presentation/ui/home/components/GateRegistrationDialog.kt` | Modificar: WiFiConfig simplificado, LocalDone |
| `presentation/ui/home/components/OdooConfigDialog.kt` | Crear |
| `presentation/ui/home/HomeScreen.kt` | Modificar: chips con estado, OdooConfigDialog |
| `presentation/ui/home/components/ChangeHostnameDialog.kt` | Modificar: pre-llenado desde name |

## Especificación de referencia

Ver `docs/superpowers/specs/2026-06-11-local-gate-flow-design.md` para diseño detallado.
