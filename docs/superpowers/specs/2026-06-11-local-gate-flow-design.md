# Diseño: Flujo de registro local + configuración Odoo diferida

## Resumen

Actualmente el registro de un portón ESP32 requiere completar 4 pasos secuenciales (BT → WiFi → verificar → nombrar → registrar en Odoo). Este diseño separa la configuración WiFi local del registro en el servidor Odoo, permitiendo que el portón quede operativo en la red local inmediatamente y que la vinculación con Odoo se haga después, desde el chip del portón.

## Cambios en el modelo de dominio

### `GateInfo` — `domain/model/GateInfo.kt`

```kotlin
data class GateInfo(
    val id: Int? = null,             // null = no registrado en Odoo
    val name: String,
    val macAddress: String,
    val ipAddress: String? = null,
    val isOnline: Boolean = false,
    val btName: String = "ESP32_Seguro",
    val hostname: String = "",
    val isOdooRegistered: Boolean = false
)
```

- `id: Int?` — nullable. `null` cuando el portón solo existe localmente.
- `isOdooRegistered` — indica si ya se registró en el backend y se le envió la URL de Odoo.

### `GateRegistrationUiState` — `presentation/ui/home/GateRegistrationViewModel.kt`

Se eliminan los campos `gateName` y `gateDescription`. En su lugar, el campo `btName` del paso WiFiConfig cumple la función de nombre único.

```kotlin
data class GateRegistrationUiState(
    val step: GateStep = GateStep.SelectBluetooth,
    val gateName: String = "",       // nombre único (BT + hostname + chip)
    val ssid: String = "",
    val password: String = "",
    val macAddress: String = "",
    val registeredGateId: Int? = null,
    val isSubmitting: Boolean = false,
    val availableNetworks: List<String> = emptyList(),
    val isLoadingNetworks: Boolean = false
)
```

Campos eliminados: `btName`, `hostname`, `gateDescription`.

### `GateStep` — se eliminan pasos de Odoo

```kotlin
sealed class GateStep {
    data object SelectBluetooth : GateStep()
    data class WiFiConfig(val macAddress: String? = null) : GateStep()
    data object VerifyingWifi : GateStep()
    data object LocalDone : GateStep()           // reemplaza NameGate + Registering + Done
    data class Error(val message: String) : GateStep()
}
```

### Nuevo evento: `GateConfiguredLocally`

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

## Flujo de registro local (nuevo)

### Paso 1: SelectBluetooth (sin cambios)

Usuario selecciona dispositivo BT → se conecta → pasa a `WiFiConfig`.

### Paso 2: WiFiConfig — formulario simplificado

```
┌──────────────────────────────────┐
│  Nombre del portón               │
│  [_____________________________] │ ← se usa para BT name, hostname y chip
│                                  │
│  Red WiFi                        │
│  [┌────────────────────────────▼] │ ← ExposedDropdownMenuBox
│  │  MiRed                    ▲ │ │
│  │  RedOficina               │ │ │
│  │  RedInvitados             │ │ │
│  └─────────────────────────────┘ │
│                                  │
│  Contraseña                      │
│  [_____________________________] │
│                                  │
│  [🔄] (refresh networks)         │
│                                  │
│  [ENVIAR AL ESP32]               │
└──────────────────────────────────┘
```

- **Nombre del portón** unifica: BT name, hostname DHCP y nombre del chip.
- Al enviar, se genera `bt_name = gateName` y `hostname = gateName.toLowerCase().replace(' ', '-')` (para cumplir RFC 952).
- Se envía JSON: `{ "action": "config_network", "ssid": ..., "password": ..., "bt_name": ..., "hostname": ... }`

### Paso 3: VerifyingWifi (sin cambios)

Loop de reconexión BT de 45s máximo. Al reconectar exitosamente:

### Paso 4: LocalDone (NUEVO)

- Emite evento `GateConfiguredLocally` con el nombre, MAC, BT name y hostname.
- Resetea estado y cierra el diálogo.

### `GateRegistrationViewModel.sendWiFiConfig()` — modificado

Después de recibir `"status":"success"` con `mac_address` del ESP32:

```kotlin
// verifyWiFiConnection() exitoso → en vez de NameGate:
_events.emit(GateRegistrationEvent.GateConfiguredLocally(
    name = state.gateName,
    macAddress = mac,
    btName = state.gateName,   // BT name = nombre único
    hostname = state.gateName.lowercase().replace(' ', '-')  // hostname normalizado
))
_events.emit(GateRegistrationEvent.CloseDialog)
```

## Flujo de configuración Odoo (nuevo, desde chip local)

### `OdooConfigDialog` — nuevo diálogo

```
┌──────────────────────────────────┐
│  Configurar con Odoo             │
│                                  │
│  Nombre del portón               │
│  [_____________________________] │ ← pre-llenado del chip, editable
│                                  │
│  Servidor Odoo                   │
│                                  │
│  Protocolo: [http ▼]             │
│                                  │
│  IP:                             │
│  [_____________________________] │
│                                  │
│  Puerto:                         │
│  [_____________________________] │ ← placeholder 8059
│                                  │
│  [CONFIGURAR]                    │
└──────────────────────────────────┘
```

**Al confirmar:**
1. Valida IP y puerto
2. `syncRepository.registerGate(name, macAddress, description = "")` → obtiene `gateId`
3. Conecta BT al ESP32 → envía JSON: `{ "protocolo": "http", "ip_odoo": "...", "port": ... }`
4. Espera respuesta `"CONFIG_OK"`
5. Actualiza `localGates`: marca como `isOdooRegistered = true`, asigna `id = gateId`

**Manejo de errores:**
- Si el ESP32 no responde o BT falla, se registra igual en Odoo (se puede reintentar URL después).
- Si Odoo falla, se cancela todo y se muestra error.

## `HomeViewModel` — lista local de portones

### Nuevo `localGates: MutableStateFlow<List<GateInfo>>`

In-memory, mismo patrón que `HistoryRepositoryImpl`:

```kotlin
private val _localGates = MutableStateFlow<List<GateInfo>>(emptyList())
val localGates: StateFlow<List<GateInfo>> = _localGates.asStateFlow()

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
}

fun markGateAsOdooRegistered(macAddress: String, odooId: Int) {
    _localGates.update { gates ->
        gates.map { if (it.macAddress == macAddress) it.copy(id = odooId, isOdooRegistered = true) else it }
    }
}
```

### `HomeUiState.gates` — lista combinada

`gates` ahora es la unión de Odoo gates + local gates:

```kotlin
// En loadGates(), después de obtener gates de Odoo + localGates:
val combined = odooGates + localGates.value.filter { !it.isOdooRegistered }
_uiState.update { it.copy(gates = combined) }
```

Los portones locales con `isOdooRegistered = true` se excluyen de la lista combinada porque ya son devueltos por Odoo.

## `GateChipRow` — indicador de estado

### Visual

Los chips de portones locales muestran:
- Nombre del portón
- Texto "No configurada" en gris (estilo `onSurfaceVariant`)

Los chips Odoo-registrados se muestran como hasta ahora (solo nombre).

### Dropdown menu

**Portón local** (isOdooRegistered = false):
- Configurar con Odoo
- Configurar IP
- Cambiar Hostname
- Eliminar

**Portón registrado** (isOdooRegistered = true):
- Ver detalles
- Configurar IP
- Cambiar Hostname
- Renombrar

### `onSelect` — selección por MAC

El `selectedGateId: Int?` cambia a `selectedMacAddress: String?` para soportar portones sin ID de Odoo.

## Firmware — sin cambios

El ESP32 ya soporta `bt_name` y `hostname` en `config_network`. Solo cambia cómo la app genera estos valores (ambos desde el nombre único del portón).

## Archivos modificados/nuevos

| Archivo | Tipo | Cambio |
|---|---|---|
| `domain/model/GateInfo.kt` | Modificado | `id: Int?`, `isOdooRegistered`, `hostname` |
| `domain/repository/GateRepository.kt` | Sin cambios | — |
| `data/repository/GateRepositoryImpl.kt` | Sin cambios | — |
| `presentation/ui/home/HomeViewModel.kt` | Modificado | `localGates`, merge list, `addLocalGate()`, `markGateAsOdooRegistered()` |
| `presentation/ui/home/HomeScreen.kt` | Modificado | `GateChipRow` con estado, nuevo diálogo OdooConfig |
| `presentation/ui/home/GateRegistrationViewModel.kt` | Modificado | `gateName` unificado, eliminar btName/hostname separados, nuevo evento |
| `presentation/ui/home/components/GateRegistrationDialog.kt` | Modificado | WiFiConfigContent sin btName/hostname, con gateName |
| `presentation/ui/home/components/OdooConfigDialog.kt` | **Nuevo** | Diálogo de configuración Odoo |
| `presentation/ui/home/components/ChangeHostnameDialog.kt` | Modificado | Opcional: campo pre-llenado desde nombre |
| `domain/repository/LocalGateRepository.kt` | Opcional | Si se quiere persistencia |

## Consideraciones

- Los portones locales solo existen en memoria. Al reiniciar la app, se pierden. Si se requiere persistencia, se puede añadir SharedPreferences (como `server_history_v2`).
- El nombre único del portón se normaliza para hostname: `"Puerta Principal"` → `"puerta-principal"` (lowercase, spaces → hyphens, max 63 chars).
- BT name puede tener espacios y mayúsculas sin problema.
- Durante la verificación WiFi, si el ESP32 no reconecta en 45s, se muestra error y no se crea el portón local.
