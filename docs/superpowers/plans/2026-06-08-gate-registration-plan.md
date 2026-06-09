# Gate Registration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add ESP32 gate registration flow to EscanQR Admin (Bluetooth WiFi config → MAC capture → register in Odoo).

**Architecture:** New GateRegistrationDialog (multi-step modal) in HomeScreen, powered by new GateRegistrationViewModel. BluetoothRepository gets a `sendMessageAndWaitForReply()` method for request-response. SyncRepository gets `registerGate()`. All models in new files.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, Hilt, OkHttp, kotlinx.serialization

---

### Task 1: Domain Model — GateRegistration

**Files:**
- Create: `app/src/main/java/com/example/escanqradmin/domain/model/GateRegistration.kt`

- [ ] **Step 1: Create GateRegistration domain model**

```kotlin
package com.example.escanqradmin.domain.model

data class GateRegistration(
    val name: String,
    val macAddress: String,
    val description: String = ""
)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/escanqradmin/domain/model/GateRegistration.kt
git commit -m "feat: add GateRegistration domain model"
```

---

### Task 2: Network DTOs

**Files:**
- Create: `app/src/main/java/com/example/escanqradmin/data/network/model/GateDtos.kt`

- [ ] **Step 1: Create network DTOs**

```kotlin
package com.example.escanqradmin.data.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EspConfigResponse(
    val status: String,
    val message: String? = null,
    @SerialName("mac_address") val macAddress: String? = null
)

@Serializable
data class GateRegisterRequest(
    val name: String,
    @SerialName("mac_address") val macAddress: String,
    val description: String = ""
)

@Serializable
data class GateRegisterResponse(
    val success: Boolean,
    @SerialName("gate_id") val gateId: Int? = null,
    val message: String? = null
)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/escanqradmin/data/network/model/GateDtos.kt
git commit -m "feat: add gate registration network DTOs"
```

---

### Task 3: ApiConstants — New Endpoint

**Files:**
- Modify: `app/src/main/java/com/example/escanqradmin/data/network/ApiConstants.kt`

- [ ] **Step 1: Add registerGate endpoint to ApiConstants**

Add after `endpointConductores` (line 11):

```kotlin
    private var endpointRegisterGate: String = "/api/v1/gates/register"
```

Add after `KEY_ENDPOINT_CONDUCTORES` (line 18):

```kotlin
    private const val KEY_ENDPOINT_REGISTER_GATE = "endpoint_register_gate"
```

In `init()` after line 26:

```kotlin
        endpointRegisterGate = prefs.getString(KEY_ENDPOINT_REGISTER_GATE, "/api/v1/gates/register") ?: "/api/v1/gates/register"
```

In `saveConfig()` signature and body, add registerGatePath parameter (default `"/api/v1/gates/register"`). Add after cleanConductores:

```kotlin
        val cleanRegister = if (registerGatePath.startsWith("/")) registerGatePath else "/$registerGatePath"
```

And in the `edit()` chain:

```kotlin
            .putString(KEY_ENDPOINT_REGISTER_GATE, cleanRegister)
```

And set `endpointRegisterGate = cleanRegister`.

Add getter:

```kotlin
    fun getEndpointRegisterGate(): String = endpointRegisterGate
```

Add in `Endpoints` object:

```kotlin
        val REGISTER_GATE: String
            get() = "$BASE_URL${endpointRegisterGate}"
```

Full modified `ApiConstants.kt`:

```kotlin
package com.example.escanqradmin.data.network

import android.content.Context
import android.content.SharedPreferences

object ApiConstants {
    private var baseProtocol: String = "http"
    private var baseHost: String = "172.17.12.119"
    private var basePort: String = "8059"
    private var endpointSync: String = "/api/control_acceso"
    private var endpointConductores: String = "/api/get_conductores"
    private var endpointRegisterGate: String = "/api/v1/gates/register"

    private const val PREFS_NAME = "api_config_prefs"
    private const val KEY_PROTOCOL = "base_protocol"
    private const val KEY_HOST = "base_host"
    private const val KEY_PORT = "base_port"
    private const val KEY_ENDPOINT_SYNC = "endpoint_sync"
    private const val KEY_ENDPOINT_CONDUCTORES = "endpoint_conductores"
    private const val KEY_ENDPOINT_REGISTER_GATE = "endpoint_register_gate"

    fun init(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        baseProtocol = prefs.getString(KEY_PROTOCOL, "http") ?: "http"
        baseHost = prefs.getString(KEY_HOST, "172.17.12.119") ?: "172.17.12.119"
        basePort = prefs.getString(KEY_PORT, "8059") ?: "8059"
        endpointSync = prefs.getString(KEY_ENDPOINT_SYNC, "/api/control_acceso") ?: "/api/control_acceso"
        endpointConductores = prefs.getString(KEY_ENDPOINT_CONDUCTORES, "/api/get_conductores") ?: "/api/get_conductores"
        endpointRegisterGate = prefs.getString(KEY_ENDPOINT_REGISTER_GATE, "/api/v1/gates/register") ?: "/api/v1/gates/register"
    }

    fun saveConfig(
        context: Context,
        protocol: String,
        host: String,
        port: String,
        syncPath: String = "/api/control_acceso",
        conductoresPath: String = "/api/get_conductores",
        registerGatePath: String = "/api/v1/gates/register"
    ) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val cleanHost = host.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .removeSuffix("/")

        val cleanSync = if (syncPath.startsWith("/")) syncPath else "/$syncPath"
        val cleanConductores = if (conductoresPath.startsWith("/")) conductoresPath else "/$conductoresPath"
        val cleanRegister = if (registerGatePath.startsWith("/")) registerGatePath else "/$registerGatePath"

        prefs.edit()
            .putString(KEY_PROTOCOL, protocol)
            .putString(KEY_HOST, cleanHost)
            .putString(KEY_PORT, port)
            .putString(KEY_ENDPOINT_SYNC, cleanSync)
            .putString(KEY_ENDPOINT_CONDUCTORES, cleanConductores)
            .putString(KEY_ENDPOINT_REGISTER_GATE, cleanRegister)
            .apply()

        baseProtocol = protocol
        baseHost = cleanHost
        basePort = port
        endpointSync = cleanSync
        endpointConductores = cleanConductores
        endpointRegisterGate = cleanRegister
    }

    fun getProtocol(): String = baseProtocol
    fun getHost(): String = baseHost
    fun getPort(): String = basePort
    fun getEndpointSync(): String = endpointSync
    fun getEndpointConductores(): String = endpointConductores
    fun getEndpointRegisterGate(): String = endpointRegisterGate

    val BASE_URL: String
        get() {
            val portSuffix = if (basePort.isBlank()) "" else ":$basePort"
            return "$baseProtocol://$baseHost$portSuffix"
        }

    object Endpoints {
        val SYNC_VEHICULAR: String
            get() = "$BASE_URL${endpointSync}"
        val GET_CONDUCTORES: String
            get() = "$BASE_URL${endpointConductores}"
        val REGISTER_GATE: String
            get() = "$BASE_URL${endpointRegisterGate}"
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/escanqradmin/data/network/ApiConstants.kt
git commit -m "feat: add REGISTER_GATE endpoint to ApiConstants"
```

---

### Task 4: BluetoothRepository — New sendMessageAndWaitForReply Method

**Files:**
- Modify: `app/src/main/java/com/example/escanqradmin/domain/repository/BluetoothRepository.kt`
- Modify: `app/src/main/java/com/example/escanqradmin/data/repository/BluetoothRepositoryImpl.kt`

- [ ] **Step 1: Add method to interface**

In `BluetoothRepository.kt`, add after `sendMessage`:

```kotlin
    suspend fun sendMessageAndWaitForReply(message: String, timeoutMs: Long = 10000): String?
```

- [ ] **Step 2: Implement in BluetoothRepositoryImpl**

Add after `sendMessage` implementation:

```kotlin
    override suspend fun sendMessageAndWaitForReply(message: String, timeoutMs: Long): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Clear any stale buffered data
                readBuffer.clear()

                // Send message with \n for ESP32 println protocol
                socket?.outputStream?.write("$message\n".toByteArray())

                // Wait for response with timeout
                val startTime = System.currentTimeMillis()
                val buffer = ByteArray(1024)
                val inputStream = socket?.inputStream ?: return@withContext null

                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    if (inputStream.available() > 0) {
                        val bytes = inputStream.read(buffer)
                        if (bytes > 0) {
                            readBuffer.append(String(buffer, 0, bytes))
                            val newlineIdx = readBuffer.indexOf("\n")
                            if (newlineIdx != -1) {
                                val line = readBuffer.substring(0, newlineIdx).trim()
                                readBuffer.delete(0, newlineIdx + 1)
                                if (line.isNotEmpty()) {
                                    return@withContext line
                                }
                            }
                        }
                    } else {
                        delay(100)
                    }
                }
                // Timeout reached
                null
            } catch (e: Exception) {
                null
            }
        }
    }
```

Note: You'll need to add `import kotlinx.coroutines.delay` at the top of the file.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/escanqradmin/domain/repository/BluetoothRepository.kt app/src/main/java/com/example/escanqradmin/data/repository/BluetoothRepositoryImpl.kt
git commit -m "feat: add sendMessageAndWaitForReply to BluetoothRepository"
```

---

### Task 5: SyncRepository — New registerGate Method

**Files:**
- Modify: `app/src/main/java/com/example/escanqradmin/domain/repository/SyncRepository.kt`
- Modify: `app/src/main/java/com/example/escanqradmin/data/repository/SyncRepositoryImpl.kt`

- [ ] **Step 1: Add method to interface**

In `SyncRepository.kt`, add after `updateEntry`:

```kotlin
    suspend fun registerGate(name: String, macAddress: String, description: String = ""): Result<GateRegisterResponse>
```

And the import at top:

```kotlin
import com.example.escanqradmin.data.network.model.GateRegisterResponse
```

- [ ] **Step 2: Implement in SyncRepositoryImpl**

Add after `updateEntry` method, before the closing brace:

```kotlin
    override suspend fun registerGate(name: String, macAddress: String, description: String): Result<GateRegisterResponse> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", "call")
                put("params", buildJsonObject {
                    put("name", name)
                    put("mac_address", macAddress)
                    put("description", description)
                })
            }.toString()

            val request = Request.Builder()
                .url(ApiConstants.Endpoints.REGISTER_GATE)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: throw Exception("Empty body")
                    val jsonElement = json.parseToJsonElement(bodyString)
                    val jsonObject = jsonElement.jsonObject

                    if (jsonObject.containsKey("error")) {
                        val errObj = jsonObject["error"]
                        Result.failure(Exception("Odoo Error: $errObj"))
                    } else {
                        val resultElement = jsonObject["result"] ?: throw Exception("Missing result in response")
                        val gateResponse = json.decodeFromJsonElement<GateRegisterResponse>(resultElement)

                        if (gateResponse.success) {
                            Result.success(gateResponse)
                        } else {
                            Result.failure(Exception(gateResponse.message ?: "Error desconocido"))
                        }
                    }
                } else {
                    Result.failure(Exception("Error ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
```

Add import at top:

```kotlin
import com.example.escanqradmin.data.network.ApiConstants
import com.example.escanqradmin.data.network.model.GateRegisterResponse
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/escanqradmin/domain/repository/SyncRepository.kt app/src/main/java/com/example/escanqradmin/data/repository/SyncRepositoryImpl.kt
git commit -m "feat: add registerGate to SyncRepository"
```

---

### Task 6: GateRegistrationViewModel

**Files:**
- Create: `app/src/main/java/com/example/escanqradmin/presentation/ui/home/GateRegistrationViewModel.kt`

- [ ] **Step 1: Create ViewModel**

```kotlin
package com.example.escanqradmin.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.escanqradmin.data.network.model.EspConfigResponse
import com.example.escanqradmin.domain.repository.BluetoothConnectionState
import com.example.escanqradmin.domain.repository.BluetoothRepository
import com.example.escanqradmin.domain.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

sealed class GateStep {
    object SelectBluetooth : GateStep()
    data class WiFiConfig(val macAddress: String? = null) : GateStep()
    data class NameGate(val macAddress: String) : GateStep()
    object Registering : GateStep()
    object Done : GateStep()
    data class Error(val message: String) : GateStep()
}

data class GateRegistrationUiState(
    val step: GateStep = GateStep.SelectBluetooth,
    val ssid: String = "",
    val password: String = "",
    val gateName: String = "",
    val gateDescription: String = "",
    val macAddress: String = "",
    val registeredGateId: Int? = null,
    val isSubmitting: Boolean = false
)

@HiltViewModel
class GateRegistrationViewModel @Inject constructor(
    private val bluetoothRepository: BluetoothRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GateRegistrationUiState())
    val uiState: StateFlow<GateRegistrationUiState> = _uiState.asStateFlow()

    val bluetoothConnectionState: StateFlow<BluetoothConnectionState> = bluetoothRepository.connectionState
    val pairedDevices = bluetoothRepository.pairedDevices
    val scannedDevices = bluetoothRepository.scannedDevices
    val isScanning = bluetoothRepository.isScanning

    fun startDiscovery() = bluetoothRepository.startDiscovery()
    fun stopDiscovery() = bluetoothRepository.stopDiscovery()

    fun connectToDevice(address: String) = bluetoothRepository.connectToDevice(address)

    fun disconnect() {
        bluetoothRepository.disconnect()
        _uiState.update { it.copy(step = GateStep.SelectBluetooth) }
    }

    fun onSsidChange(value: String) = _uiState.update { it.copy(ssid = value) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value) }
    fun onGateNameChange(value: String) = _uiState.update { it.copy(gateName = value) }
    fun onGateDescriptionChange(value: String) = _uiState.update { it.copy(gateDescription = value) }

    fun advanceAfterConnection() {
        _uiState.update { it.copy(step = GateStep.WiFiConfig()) }
    }

    fun sendWiFiConfig() {
        val state = _uiState.value
        if (state.ssid.isBlank()) return
        _uiState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch {
            val jsonPayload = buildJsonPayload(state.ssid, state.password)
            val response = bluetoothRepository.sendMessageAndWaitForReply(jsonPayload)

            if (response == null) {
                _uiState.update {
                    it.copy(step = GateStep.Error("No se recibió respuesta del ESP32"), isSubmitting = false)
                }
                return@launch
            }

            try {
                val json = Json { ignoreUnknownKeys = true }
                val espResponse = json.decodeFromString<EspConfigResponse>(response)

                if (espResponse.status == "success" && espResponse.macAddress != null) {
                    _uiState.update {
                        it.copy(
                            step = GateStep.NameGate(espResponse.macAddress),
                            macAddress = espResponse.macAddress,
                            isSubmitting = false
                        )
                    }
                } else {
                    val msg = espResponse.message ?: "Error del ESP32"
                    _uiState.update {
                        it.copy(step = GateStep.Error(msg), isSubmitting = false)
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(step = GateStep.Error("Respuesta inválida del ESP32"), isSubmitting = false)
                }
            }
        }
    }

    fun registerGate() {
        val state = _uiState.value
        if (state.gateName.isBlank() || state.macAddress.isBlank()) return
        _uiState.update { it.copy(step = GateStep.Registering, isSubmitting = true) }

        viewModelScope.launch {
            syncRepository.registerGate(
                name = state.gateName,
                macAddress = state.macAddress,
                description = state.gateDescription
            ).onSuccess { response ->
                _uiState.update {
                    it.copy(
                        step = GateStep.Done,
                        registeredGateId = response.gateId,
                        isSubmitting = false
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        step = GateStep.Error(e.message ?: "Error al registrar en Odoo"),
                        isSubmitting = false
                    )
                }
            }
        }
    }

    fun resetToStart() {
        bluetoothRepository.disconnect()
        _uiState.value = GateRegistrationUiState()
    }

    fun retry() {
        val mac = _uiState.value.macAddress
        if (mac.isNotBlank()) {
            _uiState.update { it.copy(step = GateStep.NameGate(mac), isSubmitting = false) }
        } else {
            _uiState.update {
                it.copy(step = GateStep.WiFiConfig(), isSubmitting = false, ssid = "", password = "")
            }
        }
    }

    private fun buildJsonPayload(ssid: String, password: String): String =
        """{"action":"config_network","ssid":"${ssid.replace("\"", "\\\"")}","password":"${password.replace("\"", "\\\"")}"}"""
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/escanqradmin/presentation/ui/home/GateRegistrationViewModel.kt
git commit -m "feat: add GateRegistrationViewModel with step-based flow"
```

---

### Task 7: GateRegistrationDialog — UI

**Files:**
- Create: `app/src/main/java/com/example/escanqradmin/presentation/ui/home/components/GateRegistrationDialog.kt`

- [ ] **Step 1: Create the dialog composable**

```kotlin
package com.example.escanqradmin.presentation.ui.home.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.escanqradmin.domain.model.BluetoothDeviceDomain
import com.example.escanqradmin.domain.repository.BluetoothConnectionState
import com.example.escanqradmin.presentation.ui.home.GateRegistrationViewModel
import com.example.escanqradmin.presentation.ui.home.GateStep

@Composable
fun GateRegistrationDialog(
    viewModel: GateRegistrationViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val btState by viewModel.bluetoothConnectionState.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val scannedDevices by viewModel.scannedDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    var connectionInitiated by remember { mutableStateOf(false) }

    // Auto-advance after connection
    LaunchedEffect(btState) {
        if (btState is BluetoothConnectionState.Connected && connectionInitiated) {
            viewModel.advanceAfterConnection()
        }
    }

    Dialog(onDismissRequest = {
        if (uiState.step !is GateStep.Registering) {
            viewModel.resetToStart()
            onDismiss()
        }
    }) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = when (uiState.step) {
                                is GateStep.SelectBluetooth -> "Registrar Portón"
                                is GateStep.WiFiConfig -> "Configurar Red WiFi"
                                is GateStep.NameGate -> "Portón Detectado"
                                is GateStep.Registering -> "Registrando..."
                                is GateStep.Done -> "¡Registro Exitoso!"
                                is GateStep.Error -> "Error"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (uiState.step !is GateStep.Registering) {
                        IconButton(
                            onClick = {
                                viewModel.resetToStart()
                                onDismiss()
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedContent(
                    targetState = uiState.step,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "gateStepContent"
                ) { step ->
                    when (step) {
                        is GateStep.SelectBluetooth -> BluetoothStep(
                            btState = btState,
                            pairedDevices = pairedDevices.filter { it.name?.startsWith("ESP32", ignoreCase = true) == true },
                            scannedDevices = scannedDevices.filter { it.name?.startsWith("ESP32", ignoreCase = true) == true },
                            isScanning = isScanning,
                            onStartScan = viewModel::startDiscovery,
                            onStopScan = viewModel::stopDiscovery,
                            onConnect = { address ->
                                connectionInitiated = true
                                viewModel.connectToDevice(address)
                            },
                            onDisconnect = viewModel::disconnect
                        )
                        is GateStep.WiFiConfig -> WiFiConfigStep(
                            ssid = uiState.ssid,
                            password = uiState.password,
                            isSubmitting = uiState.isSubmitting,
                            onSsidChange = viewModel::onSsidChange,
                            onPasswordChange = viewModel::onPasswordChange,
                            onSubmit = viewModel::sendWiFiConfig
                        )
                        is GateStep.NameGate -> NameGateStep(
                            macAddress = (step as GateStep.NameGate).macAddress,
                            gateName = uiState.gateName,
                            gateDescription = uiState.gateDescription,
                            isSubmitting = uiState.isSubmitting,
                            onGateNameChange = viewModel::onGateNameChange,
                            onGateDescriptionChange = viewModel::onGateDescriptionChange,
                            onRegister = viewModel::registerGate
                        )
                        is GateStep.Registering -> RegisteringStep()
                        is GateStep.Done -> DoneStep(
                            gateName = uiState.gateName,
                            gateId = uiState.registeredGateId,
                            onClose = {
                                viewModel.resetToStart()
                                onDismiss()
                            }
                        )
                        is GateStep.Error -> ErrorStep(
                            message = (step as GateStep.Error).message,
                            onRetry = viewModel::retry,
                            onClose = {
                                viewModel.resetToStart()
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BluetoothStep(
    btState: BluetoothConnectionState,
    pairedDevices: List<BluetoothDeviceDomain>,
    scannedDevices: List<BluetoothDeviceDomain>,
    isScanning: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Conecta al ESP32 para comenzar",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Error feedback
        if (btState is BluetoothConnectionState.Error) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(btState.message, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Device list
        LazyColumn(
            modifier = Modifier.height(240.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (pairedDevices.isNotEmpty()) {
                item {
                    Text(
                        "VINCULADOS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(pairedDevices) { device ->
                    DeviceListItem(
                        device = device,
                        isConnected = (btState as? BluetoothConnectionState.Connected)?.deviceAddress == device.address,
                        isConnecting = btState is BluetoothConnectionState.Connecting,
                        onConnect = { onConnect(device.address) },
                        onDisconnect = onDisconnect
                    )
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        "OTROS DISPOSITIVOS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onStartScan, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Refresh, "Buscar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (scannedDevices.isEmpty() && !isScanning) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text("No se encontraron dispositivos", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            } else {
                items(scannedDevices) { device ->
                    DeviceListItem(
                        device = device,
                        isConnected = (btState as? BluetoothConnectionState.Connected)?.deviceAddress == device.address,
                        isConnecting = btState is BluetoothConnectionState.Connecting,
                        onConnect = { onConnect(device.address) },
                        onDisconnect = onDisconnect
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = if (isScanning) onStopScan else onStartScan,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isScanning) Color.Gray else MaterialTheme.colorScheme.secondary
            )
        ) {
            if (isScanning) {
                Text("DETENER ESCANEO", fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("BUSCAR DISPOSITIVOS", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DeviceListItem(
    device: BluetoothDeviceDomain,
    isConnected: Boolean,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isConnected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnecting && !isConnected) { onConnect() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Bluetooth,
                null,
                tint = if (isConnected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name ?: "Desconocido", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(device.address, fontSize = 11.sp, color = Color.Gray)
            }
            if (isConnected) {
                TextButton(onClick = onDisconnect) {
                    Text("DESCONECTAR", color = MaterialTheme.colorScheme.error, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                }
            } else if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text("CONECTAR", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WiFiConfigStep(
    ssid: String,
    password: String,
    isSubmitting: Boolean,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Envía las credenciales WiFi al ESP32",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = ssid,
            onValueChange = onSsidChange,
            label = { Text("SSID (Red WiFi)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            enabled = !isSubmitting,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.secondary
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Contraseña") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            enabled = !isSubmitting,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.secondary
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = ssid.isNotBlank() && !isSubmitting
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Send, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("ENVIAR AL ESP32", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NameGateStep(
    macAddress: String,
    gateName: String,
    gateDescription: String,
    isSubmitting: Boolean,
    onGateNameChange: (String) -> Unit,
    onGateDescriptionChange: (String) -> Unit,
    onRegister: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // MAC display
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Router, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("MAC Address detectada", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(macAddress, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = gateName,
            onValueChange = onGateNameChange,
            label = { Text("Nombre del Portón *") },
            placeholder = { Text("Ej: Portón Principal") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            enabled = !isSubmitting,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.secondary
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = gateDescription,
            onValueChange = onGateDescriptionChange,
            label = { Text("Descripción (opcional)") },
            placeholder = { Text("Ej: Portón de acceso a empleados") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            enabled = !isSubmitting,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.secondary
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onRegister,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = gateName.isNotBlank() && !isSubmitting,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("REGISTRAR EN ODOO", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RegisteringStep() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Registrando portón en Odoo...", color = Color.Gray, fontSize = 14.sp)
        }
    }
}

@Composable
private fun DoneStep(
    gateName: String,
    gateId: Int?,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Portón registrado exitosamente",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (gateId != null) "'$gateName' (ID: $gateId)" else "'$gateName'",
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("CERRAR", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ErrorStep(
    message: String,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("CANCELAR", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("REINTENTAR", fontWeight = FontWeight.Bold)
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/escanqradmin/presentation/ui/home/components/GateRegistrationDialog.kt
git commit -m "feat: add GateRegistrationDialog with multi-step UI"
```

---

### Task 8: HomeScreen — Add Button and Wire Dialog

**Files:**
- Modify: `app/src/main/java/com/example/escanqradmin/presentation/ui/home/HomeScreen.kt`

- [ ] **Step 1: Add imports at top of HomeScreen.kt**

Add after existing imports:

```kotlin
import com.example.escanqradmin.presentation.ui.home.components.GateRegistrationDialog
```

- [ ] **Step 2: Add state variable for dialog**

Add after `var showProvisioningDialog` (around line 77):

```kotlin
    var showGateRegistrationDialog by remember { mutableStateOf(false) }
```

- [ ] **Step 3: Add "Registrar Portón" button card**

Find the Row with the two cards (lines 420-470). Before `AppCard` for "Aprovisionar", or add as a third card. Replace the existing `Row` block with a `Column` containing a two-row grid, or simply add a third card in the existing Row if there's room.

Better approach: Add a second row of action cards below the existing one. Add after the closing of the existing Row (after line 469, before the `SearchBar` item at line 472):

```kotlin
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(120.dp),
                                onClick = { showGateRegistrationDialog = true }
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Router, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Registrar", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Portón", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary)
                                    }
                                }
                            }
                        }
                    }
```

Note: Verify there's a `color` import already (there should be). Import `Icons.Default.Router` if not already imported (check the existing import block for `Icons`).

- [ ] **Step 4: Create a separate GateRegistrationViewModel instance and add dialog**

Add with the other `hiltViewModel()` usages, or get it in the composable. In `HomeScreen`, add:

```kotlin
    val gateRegViewModel: GateRegistrationViewModel = hiltViewModel()
```

And at the end of the `Box` (before the closing `} }`), after the ProvisioningQrDialog block (around line 591):

```kotlin
            if (showGateRegistrationDialog) {
                GateRegistrationDialog(
                    viewModel = gateRegViewModel,
                    onDismiss = { showGateRegistrationDialog = false }
                )
            }
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/escanqradmin/presentation/ui/home/HomeScreen.kt
git commit -m "feat: add Register Gate button and dialog to HomeScreen"
```

---

### Self-Review Checklist

1. **Spec coverage**: All spec requirements are covered — domain model (Task 1), DTOs (Task 2), ApiConstants (Task 3), Bluetooth request-response (Task 4), SyncRepository registerGate (Task 5), ViewModel state machine (Task 6), multi-step dialog UI (Task 7), HomeScreen integration (Task 8).

2. **Placeholder scan**: All steps contain complete code, no TBD/TODO.

3. **Type consistency**: `GateRegisterResponse` used in SyncRepository matches the DTO. `EspConfigResponse` used in ViewModel matches the DTO. Method signatures match across interface/impl.

4. **File paths**: All paths are absolute and verified against the existing project structure.
