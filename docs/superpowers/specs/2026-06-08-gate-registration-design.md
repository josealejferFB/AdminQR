# Gate Registration — ESP32 Portón Registration Flow

**Date:** 2026-06-08
**Status:** Draft
**Based on:** `docs/Investigacion-Tarjetas-ESP32.md`, `docs/Master-Prompt-App-Admin-Kotlin.md`

---

## 1. Objective

Add a new feature to the EscanQR Admin app allowing administrators to register a physical ESP32 gate controller into the Odoo system. The flow uses Bluetooth to send WiFi credentials and receive the ESP32's MAC address, then prompts the admin to name the gate and registers it via a new Odoo endpoint.

## 2. Scope

### In Scope
- New "Registrar Portón" button on HomeScreen
- Multi-step `GateRegistrationDialog` (Bluetooth connect → WiFi config → Name gate → Register in Odoo)
- New domain models: `GateRegistration`, `EspConfigResponse`, `GateRegisterRequest`, `GateRegisterResponse`
- New BluetoothRepository method: `sendMessageAndWaitForReply()`
- New SyncRepository method: `registerGate()`
- New `GateRegistrationViewModel` with step-based UI state
- New ApiConstants endpoint `REGISTER_GATE`
- Error handling: timeouts, JSON parse errors, Odoo errors, cancellation

### Out of Scope
- ESP32 firmware changes (will be done after app implementation)
- Existing ESPConfig screen changes (kept for V6 legacy support)
- User-to-gate permission management (Many2many — future backend feature)
- MQTT architecture (Architecture B / HTTP is what is being implemented)
- Unit tests (no mock infrastructure available)

## 3. Data Models

### 3.1 Domain Model — `GateRegistration`

```kotlin
data class GateRegistration(
    val name: String,
    val macAddress: String,
    val description: String = ""
)
```

### 3.2 Bluetooth Network DTOs

```kotlin
@Serializable
data class EspConfigResponse(
    val status: String,
    val message: String? = null,
    @SerialName("mac_address") val macAddress: String? = null
)
```

### 3.3 Odoo Network DTOs

```kotlin
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

### 3.4 ApiConstants Addition

```kotlin
private var endpointRegisterGate: String = "/api/v1/gates/register"
// In Endpoints object:
val REGISTER_GATE: String get() = "$BASE_URL${endpointRegisterGate}"
```

## 4. Architecture & Component Changes

### 4.1 BluetoothRepository — New Method

```kotlin
interface BluetoothRepository {
    // existing methods...
    suspend fun sendMessageAndWaitForReply(
        message: String,
        timeoutMs: Long = 10000
    ): String?
}
```

Implementation notes:
- Encodes `message` with `\n` suffix for ESP32 println protocol
- Clears any stale buffered data before sending
- Collects from `messages` SharedFlow until receiving a non-empty line or timeout
- Returns null on timeout or IO error

### 4.2 SyncRepository — New Method

```kotlin
interface SyncRepository {
    suspend fun registerGate(
        name: String, 
        macAddress: String,
        description: String = ""
    ): Result<GateRegisterResponse>
}
```

Implementation:
- Uses OkHttp directly (no Retrofit — follows project convention)
- POST to `ApiConstants.Endpoints.REGISTER_GATE`
- JSON-RPC 2.0 body (match existing project patterns):
  ```json
  {
      "jsonrpc": "2.0",
      "method": "call",
      "params": {
          "name": "...",
          "mac_address": "...",
          "description": "..."
      }
  }
  ```
- Reads response, parses `GateRegisterResponse`, returns success/failure

### 4.3 GateRegistrationViewModel — State Machine

```kotlin
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
    val macAddress: String = "",
    val registeredGateId: Int? = null,
    val isSubmitting: Boolean = false
)
```

ViewModel is a `@HiltViewModel` injecting `BluetoothRepository` and `SyncRepository`.

## 5. UI Structure

### 5.1 HomeScreen — New Button

Add a third card in the action row (next to "Aprovisionar" and "Usuarios Registrados") labeled **"Registrar Portón"** with a `Router` or `AddLocation` icon.

### 5.2 GateRegistrationDialog

A single `Dialog` with `RoundedCornerShape(24.dp)` (consistent with spec) that swaps content via `AnimatedContent` based on `GateStep`.

#### Step 1: SelectBluetooth
- Reuse Bluetooth discovery logic (ESP32 name filter, paired/scanned lists)
- Connect button per device
- Shows loading spinner while connecting
- On connect → advance to Step 2

#### Step 2: WiFiConfig
- Title: "Configurar Red WiFi"
- Two `OutlinedTextField` fields: SSID, Password
- Button: "Enviar al ESP32"
- Sends JSON: `{"action":"config_network","ssid":"...","password":"..."}`
- Waits for ESP32 response via `sendMessageAndWaitForReply()`
- On valid response with `mac_address` → advance to Step 3
- On error → show error with retry option

#### Step 3: NameGate
- Title: "Portón Detectado"
- Shows read-only MAC address: `A1:B2:C3:D4:E5:F6`
- Editable field: "Nombre del Portón" (e.g. "Portón Principal")
- Optional field: "Descripción"
- Button: "Registrar en Odoo"
- Loading state while registering

#### Step 4: Done / Error
- Success: green check, "Portón 'X' registrado exitosamente (ID: Y)", button "Cerrar"
- Error: red text with message, button "Reintentar" or "Cerrar"

### 5.3 Theme Compliance
- All colors from `MaterialTheme.colorScheme`
- `secondary` for success states, `error` for failures, `primary` for emphasis
- `OutlinedTextField` shape `RoundedCornerShape(12.dp)` per design guidelines
- `Button` shape `RoundedCornerShape(12.dp)` per design guidelines

## 6. Error Handling

| Scenario | Handling |
|---|---|
| Bluetooth timeout | `GateStep.Error("No se recibió respuesta del ESP32")` |
| JSON parse error | `GateStep.Error("Respuesta inválida del ESP32")` |
| ESP32 status=error | Show ESP32's `message` field |
| HTTP error from Odoo | Show status code + message |
| Odoo success=false | Show Odoo's `message` field |
| User cancels at any step | Close dialog, no side effects |
| Double tap "Guardar" | Button disabled while `isSubmitting = true` |

## 7. Files Changed

| File | Action |
|---|---|
| `domain/model/GateRegistration.kt` | **NEW** |
| `data/network/model/GateDtos.kt` | **NEW** (`EspConfigResponse`, `GateRegisterRequest`, `GateRegisterResponse`) |
| `data/network/ApiConstants.kt` | Add `endpointRegisterGate` + `REGISTER_GATE` |
| `domain/repository/BluetoothRepository.kt` | Add `sendMessageAndWaitForReply()` |
| `data/repository/BluetoothRepositoryImpl.kt` | Implement `sendMessageAndWaitForReply()` |
| `domain/repository/SyncRepository.kt` | Add `registerGate()` |
| `data/repository/SyncRepositoryImpl.kt` | Implement `registerGate()` |
| `presentation/ui/home/GateRegistrationViewModel.kt` | **NEW** |
| `presentation/ui/home/components/GateRegistrationDialog.kt` | **NEW** |
| `presentation/ui/home/HomeScreen.kt` | Add "Registrar Portón" button + dialog state |

## 8. Open Questions (for firmware later)

- Exact JSON response format from ESP32 (may add fields)
- Whether ESP32 supports both old V6 and new JSON protocol
- How to handle WiFi connection failure on the ESP32 side
