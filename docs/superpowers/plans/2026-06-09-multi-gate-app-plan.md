# Admin App Multi-Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform the Admin app from single-gate to multi-gate with chip selector, user filtering, and static IP configuration via BT.

**Architecture:** New `GateRepository` + `GateRepositoryImpl` for Odoo sync. Chips with `FilterChip` M3 in HomeScreen. Reuse `BluetoothRepository.sendMessageAndWaitForReply()` for `config_ip`/`set_bt_name`. New dialogs for IP config and rename.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Hilt, kotlinx.serialization, OkHttp

---

### Task B1: Domain model + DTOs

**Files:**
- Create: `domain/model/GateInfo.kt`
- Modify: `data/network/model/GateDtos.kt`

- [ ] **1.1: Crear `GateInfo.kt`**

```kotlin
package com.example.escanqradmin.domain.model

data class GateInfo(
    val id: Int,
    val name: String,
    val macAddress: String,
    val ipAddress: String? = null,
    val isOnline: Boolean = false,
    val btName: String = "ESP32_Seguro"
)
```

- [ ] **1.2: Añadir DTOs a `GateDtos.kt`**

```kotlin
@Serializable
data class GateListResponse(
    val success: Boolean,
    val gates: List<GateDto>? = null,
    val message: String? = null
)

@Serializable
data class GateDto(
    val id: Int,
    val name: String,
    @SerialName("mac_address") val macAddress: String,
    @SerialName("ip_address") val ipAddress: String? = null,
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("bt_name") val btName: String = "ESP32_Seguro"
)

@Serializable
data class GateNameUpdateRequest(
    @SerialName("gate_id") val gateId: Int,
    val name: String
)
```

- [ ] **1.3: Commit**

```bash
git add domain/model/GateInfo.kt data/network/model/GateDtos.kt
git commit -m "feat: add GateInfo domain model and GateDto network DTOs"
```

---

### Task B2: GateRepository + ApiConstants

**Files:**
- Create: `domain/repository/GateRepository.kt`
- Create: `data/repository/GateRepositoryImpl.kt`
- Modify: `data/network/ApiConstants.kt`
- Modify: `di/modules/NetworkModule.kt`

- [ ] **2.1: Crear interface `GateRepository.kt`**

```kotlin
package com.example.escanqradmin.domain.repository

import com.example.escanqradmin.domain.model.GateInfo

interface GateRepository {
    suspend fun getGates(): Result<List<GateInfo>>
    suspend fun updateGateName(gateId: Int, newName: String): Result<Unit>
}
```

- [ ] **2.2: Crear implementación `GateRepositoryImpl.kt`**

Sigue el patrón de `SyncRepositoryImpl`:
- Inyecta `Json` (kotlinx.serialization), OkHttpClient
- Usa `withContext(Dispatchers.IO)`
- Construye JSON-RPC 2.0 payload
- Parsea respuesta con `Json.decodeFromString<GateListResponse>()`
- Para `updateGateName`: JSON-RPC con `GateNameUpdateRequest`

- [ ] **2.3: Añadir endpoints en `ApiConstants.kt`**

```kotlin
// En objeto Endpoints:
val GATES_LIST: String get() = "$BASE_URL/api/v1/gates/list"
val GATE_UPDATE: String get() = "$BASE_URL/api/v1/gates/update"
```

- [ ] **2.4: Registrar en Hilt**

En `di/modules/NetworkModule.kt`:
```kotlin
@Provides @Singleton
fun provideGateRepository(impl: GateRepositoryImpl): GateRepository = impl
```

- [ ] **2.5: Commit**

```bash
git add domain/repository/GateRepository.kt data/repository/GateRepositoryImpl.kt data/network/ApiConstants.kt di/modules/NetworkModule.kt
git commit -m "feat: add GateRepository with getGates and updateGateName"
```

---

### Task B3: SyncRepository — getGateUsers

**Files:**
- Modify: `domain/repository/SyncRepository.kt`
- Modify: `data/repository/SyncRepositoryImpl.kt`
- Modify: `data/network/ApiConstants.kt`

- [ ] **3.1: Añadir método a interface**

```kotlin
suspend fun getGateUsers(gateId: Int): Result<List<QrContent>>
```

- [ ] **3.2: Implementar en `SyncRepositoryImpl`**

```kotlin
override suspend fun getGateUsers(gateId: Int): Result<List<QrContent>> = withContext(Dispatchers.IO) {
    try {
        val url = apiConstants.getEndpoint(apiConstants.Endpoints.GATE_USERS(gateId))
        val payload = buildJsonRpc("call", buildJsonObject { put("gate_id", gateId) })
        val response = client.newCall(Request.Builder().url(url).post(payload.toRequestBody()).build()).execute()
        val body = response.body?.string() ?: return@withContext Result.failure(Exception("Respuesta vacía"))
        val jsonRpc = Json.decodeFromString<JsonRpcResponse>(body)
        val users = Json.decodeFromJsonElement<List<QrContent>>(jsonRpc.result ?: return@withContext Result.failure(Exception("Sin datos")))
        Result.success(users)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

Añadir endpoint en `ApiConstants.kt`:
```kotlin
object Endpoints {
    // ... existing ...
    fun GATE_USERS(gateId: Int): String = "api/v1/gates/$gateId/users"
}
```

- [ ] **3.3: Commit**

```bash
git add domain/repository/SyncRepository.kt data/repository/SyncRepositoryImpl.kt data/network/ApiConstants.kt
git commit -m "feat: add getGateUsers to SyncRepository"
```

---

### Task B4: HomeViewModel — gates state + selection

**Files:**
- Modify: `presentation/ui/home/HomeViewModel.kt`

- [ ] **4.1: Añadir campos a HomeUiState**

```kotlin
data class HomeUiState(
    val totalScans: Int = 0,
    val totalUsers: Int = 0,
    val activeUsers: List<ActiveUser> = emptyList(),
    val isRefreshing: Boolean = false,
    val isServerOnline: Boolean = true,
    val gates: List<GateInfo> = emptyList(),
    val selectedGateId: Int? = null,
    val gateUsers: List<ActiveUser> = emptyList()
)
```

- [ ] **4.2: Inyectar GateRepository**

```kotlin
class HomeViewModel @Inject constructor(
    private val repository: HistoryRepository,
    private val bluetoothRepository: BluetoothRepository,
    private val syncRepository: SyncRepository,
    private val themeRepository: ThemeRepository,
    private val gateRepository: GateRepository
) : ViewModel()
```

- [ ] **4.3: Métodos**

```kotlin
fun loadGates() {
    viewModelScope.launch {
        gateRepository.getGates().onSuccess { gates ->
            _uiState.update { it.copy(gates = gates) }
        }
    }
}

fun selectGate(gateId: Int?) {
    _uiState.update { it.copy(selectedGateId = gateId) }
    if (gateId != null) loadGateUsers(gateId)
    else _uiState.update { it.copy(gateUsers = emptyList()) }
}

private fun loadGateUsers(gateId: Int) {
    viewModelScope.launch {
        syncRepository.getGateUsers(gateId).onSuccess { users ->
            _uiState.update { it.copy(gateUsers = users.map { it.toActiveUser() }) }
        }
    }
}

fun renameGate(gateId: Int, newName: String) {
    viewModelScope.launch {
        gateRepository.updateGateName(gateId, newName).onSuccess {
            loadGates()
        }
    }
}
```

- [ ] **4.4: Modificar `refreshData()` para también cargar gates**

```kotlin
fun refreshData() {
    _uiState.update { it.copy(isRefreshing = true) }
    viewModelScope.launch {
        // existing logic...
        loadGates()
        _uiState.update { it.copy(isRefreshing = false) }
    }
}
```

- [ ] **4.5: Commit**

```bash
git add presentation/ui/home/HomeViewModel.kt
git commit -m "feat: add multi-gate state management to HomeViewModel"
```

---

### Task B5: HomeScreen — Chip Selector

**Files:**
- Modify: `presentation/ui/home/HomeScreen.kt`

- [ ] **5.1: Añadir LazyRow de chips después de "Panel de Control"**

```kotlin
@Composable
private fun GateChipRow(
    gates: List<GateInfo>,
    selectedGateId: Int?,
    onSelect: (Int?) -> Unit,
    onAddGate: () -> Unit,
    onConfigureIp: (GateInfo) -> Unit,
    onRename: (GateInfo) -> Unit,
    onDetails: (GateInfo) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selectedGateId == null,
                onClick = { onSelect(null) },
                label = { Text("Todas") }
            )
        }
        items(gates) { gate ->
            var showMenu by remember { mutableStateOf(false) }
            FilterChip(
                selected = selectedGateId == gate.id,
                onClick = { onSelect(gate.id) },
                label = { Text(gate.name) },
                trailingIcon = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Opciones")
                    }
                }
            )
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Configurar IP") }, onClick = { showMenu = false; onConfigureIp(gate) })
                DropdownMenuItem(text = { Text("Renombrar") }, onClick = { showMenu = false; onRename(gate) })
                DropdownMenuItem(text = { Text("Ver detalles") }, onClick = { showMenu = false; onDetails(gate) })
            }
        }
        item {
            IconButton(onClick = onAddGate) {
                Icon(Icons.Default.Add, "Registrar tarjeta")
            }
        }
    }
}
```

- [ ] **5.2: Filtrar usuarios por gate**

Cuando `selectedGateId != null`, mostrar `uiState.gateUsers` en lugar de `uiState.activeUsers.filter`:

```kotlin
private val displayUsers: List<ActiveUser>
    get() = if (uiState.selectedGateId != null) uiState.gateUsers
            else uiState.activeUsers.filter { /* existing search filter */ }
```

- [ ] **5.3: Commit**

```bash
git add presentation/ui/home/HomeScreen.kt
git commit -m "feat: add gate chip selector with filter and context menu"
```

---

### Task B6: GateIpConfigDialog

**Files:**
- Create: `presentation/ui/home/components/GateIpConfigDialog.kt`

- [ ] **6.1: Crear diálogo de configuración IP**

```kotlin
@Composable
fun GateIpConfigDialog(
    gate: GateInfo,
    bluetoothRepository: BluetoothRepository,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var ip by remember { mutableStateOf("") }
    var gateway by remember { mutableStateOf("") }
    var netmask by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = { Text("Configurar IP - ${gate.name}") },
        text = {
            Column {
                if (result != null) {
                    Text(result!!, color = MaterialTheme.colorScheme.primary)
                } else {
                    OutlinedTextField(value = ip, onValueChange = { ip = it }, label = { Text("IP") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = gateway, onValueChange = { gateway = it }, label = { Text("Gateway") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = netmask, onValueChange = { netmask = it }, label = { Text("Máscara") })
                    if (isSending) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            if (result == null) {
                Button(
                    onClick = {
                        isSending = true
                        CoroutineScope(Dispatchers.IO).launch {
                            val payload = """{"jsonrpc":"2.0","action":"config_ip","ip":"$ip","gateway":"$gateway","netmask":"$netmask"}"""
                            // buscar ESP32 por MAC y enviar
                            val reply = bluetoothRepository.sendMessageAndWaitForReply(
                                macAddress = gate.macAddress,
                                message = payload,
                                timeoutMs = 10000
                            )
                            isSending = false
                            result = reply.getOrNull() ?: reply.exceptionOrNull()?.message ?: "Error"
                            if (reply.isSuccess) onSuccess()
                        }
                    },
                    enabled = !isSending
                ) { Text("Enviar") }
            }
        },
        dismissButton = {
            if (result == null) TextButton(onClick = onDismiss) { Text("Cancelar") }
            else TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
```

- [ ] **6.2: Commit**

```bash
git add presentation/ui/home/components/GateIpConfigDialog.kt
git commit -m "feat: add GateIpConfigDialog for static IP configuration via BT"
```

---

### Task B7: RenameGateDialog

**Files:**
- Create: `presentation/ui/home/components/RenameGateDialog.kt`

- [ ] **7.1: Crear diálogo de renombrar**

```kotlin
package com.example.escanqradmin.presentation.ui.home.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.escanqradmin.domain.model.GateInfo

@Composable
fun RenameGateDialog(
    gate: GateInfo,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(gate.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar ${gate.name}") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nuevo nombre") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
```

- [ ] **7.2: Commit**

```bash
git add presentation/ui/home/components/RenameGateDialog.kt
git commit -m "feat: add RenameGateDialog for updating gate name in Odoo"
```
