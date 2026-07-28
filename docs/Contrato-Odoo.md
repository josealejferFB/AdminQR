# Contrato Odoo — EscanQR Admin

## Protocolo

- **Transporte:** HTTP 1.1 (cleartext, sin HTTPS)
- **Autenticación:** No hay autenticación por headers ni tokens en las requests al Odoo
- **Formato:** JSON-RPC 2.0
- **Content-Type:** `application/json; charset=utf-8`

## Construcción de URLs

Toda la configuración de red se maneja desde el singleton `ApiConstants` (`data/network/ApiConstants.kt`).

```
BASE_URL = $protocolo://$host:$port
```

- **protocolo:** `http` (default) o `https`
- **host:** `172.17.12.119` (default)
- **port:** `8059` (default)

La URL final se compone así:

```kotlin
val fullUrl = "$BASE_URL$endpoint"
```

### Configuración

- Los valores se almacenan en `SharedPreferences` (`api_config_prefs`)
- Se pueden modificar desde la pantalla **Config** y persisten entre sesiones
- El historial de servidores usados se guarda como JSON en `server_history_v2` (máx. 15 entradas)
- Al guardar, el host se sanitiza (se eliminan protocolos y slashes finales)

## Endpoints

### 1. `POST /api/control_acceso`

Endpoint principal para todas las operaciones CRUD sobre registros de acceso vehicular.

#### Acción: `create` — Registrar entrada

**Request:**
```json
{
    "jsonrpc": "2.0",
    "method": "call",
    "params": {
        "action": "create",
        "cedula": "12345678",
        "nombre": "Juan Pérez",
        "placas": "ABC-1234"
    }
}
```

**Response exitosa:**
```json
{
    "jsonrpc": "2.0",
    "result": {
        "status": "success"
    }
}
```

También acepta `"status": "pending"` como respuesta válida.

**Response con error:**
```json
{
    "jsonrpc": "2.0",
    "result": {
        "status": "error",
        "message": "Error desconocido"
    }
}
```

O si Odoo devuelve un campo `error`:
```json
{
    "jsonrpc": "2.0",
    "error": {
        "code": ...,
        "message": "..."
    }
}
```

#### Acción: `delete` — Eliminar registro

**Request:**
```json
{
    "jsonrpc": "2.0",
    "method": "call",
    "params": {
        "action": "delete",
        "cedula": "12345678"
    }
}
```

**Response exitosa:**
```json
{
    "jsonrpc": "2.0",
    "result": {
        "status": "success"
    }
}
```

#### Acción: `update` — Modificar registro

**Request:**
```json
{
    "jsonrpc": "2.0",
    "method": "call",
    "params": {
        "action": "update",
        "cedula": "12345678",
        "nombre": "Juan Pérez Modificado",
        "placas": "XYZ-9876"
    }
}
```

**Campos opcionales para asignación de portones:**

A partir de la versión híbrida, el payload acepta `add_gate_ids` y/o `remove_gate_ids` como listas de enteros. Estos reemplazan el antiguo campo `puertas_autorizadas` (array de objetos con `mac_address`), que ya no debe enviarse.

```json
{
    "jsonrpc": "2.0",
    "method": "call",
    "params": {
        "action": "update",
        "cedula": "12345678",
        "nombre": "Juan Pérez",
        "placas": "ABC-1234",
        "add_gate_ids": [5, 6],
        "remove_gate_ids": [2]
    }
}
```

- `add_gate_ids`: Lista de IDs de portón que se agregan al usuario
- `remove_gate_ids`: Lista de IDs de portón que se remueven del usuario
- Ambos campos son opcionales y pueden enviarse juntos en un mismo llamado
- Odoo aplica ambas listas en una sola transacción, sin afectar portones no mencionados

**Response exitosa:** misma estructura que create/delete con `"status": "success"`.

---

### 2. `POST /api/get_conductores`

Obtiene la lista completa de conductores desde Odoo.

**Request:**
```json
{
    "jsonrpc": "2.0",
    "method": "call",
    "params": {}
}
```

**Response exitosa:**
```json
{
    "jsonrpc": "2.0",
    "result": {
        "success": true,
        "message": "ok",
        "data": [
            {
                "id": 1,
                "nombre": "Juan Pérez",
                "cedula": "12345678",
                "placas": "ABC-1234",
                "estado": "activo"
            }
        ]
    }
}
```

**DTOs correspondientes:**

```kotlin
@Serializable
data class ConductoresResponse(
    val success: Boolean,
    val message: String,
    val data: List<ConductorDto>
)

@Serializable
data class ConductorDto(
    val id: Int? = null,
    val nombre: String? = null,
    val cedula: String? = null,
    val placas: String? = null,
    val estado: String? = null
)
```

**Gate DTOs:**

```kotlin
@Serializable
data class GateRegisterResponse(
    val success: JsonElement,       // true (boolean) o "success" (string legacy)
    @SerialName("gate_id") val gateId: Int? = null,
    val message: String? = null
) {
    val isSuccess: Boolean get() =
        success.jsonPrimitive.let { it.content == "success" || it.booleanOrNull == true }
}

@Serializable
data class GateListResponse(
    val success: JsonElement,       // booleano
    val data: List<GateDto>? = null,
    val message: String? = null
)

@Serializable
data class GateDto(
    val id: Int,
    val name: String,
    @SerialName("mac_address") val macAddress: String,
    @SerialName("ip_address") val ipAddress: String? = null,
    @SerialName("is_online") val isOnline: Boolean = false,
    val hostname: String = ""
)
```

---

### 3. `POST /api/v1/gates/register`

Registra un nuevo portón ESP32 en Odoo. Se llama inmediatamente después de que la App Admin se conecta vía Bluetooth al ESP32, le configura el WiFi y extrae su MAC.

**Request:**
```json
{
    "jsonrpc": "2.0",
    "params": {
        "name": "Portón Visitantes Norte",
        "mac_address": "A1:B2:C3:D4:E5:F6"
    }
}
```

**Response — Registro exitoso:**
```json
{
    "jsonrpc": "2.0",
    "result": {
        "success": true,
        "gate_id": 15,
        "message": "Portón registrado exitosamente."
    }
}
```

**Response — Reactivación (soft delete previo):**
```json
{
    "jsonrpc": "2.0",
    "result": {
        "success": true,
        "gate_id": 15,
        "message": "Portón reactivado exitosamente."
    }
}
```

**Response — MAC duplicada activa:**
```json
{
    "jsonrpc": "2.0",
    "result": {
        "success": false,
        "message": "Ya existe un portón activo con esta MAC."
    }
}
```

**Response — Parámetros faltantes:**
```json
{
    "jsonrpc": "2.0",
    "result": {
        "success": false,
        "message": "Nombre y MAC Address son obligatorios."
    }
}
```

**Notas:**
- El campo `result.success` es booleano (`true`/`false`), no string
- La app debe leer `success` estrictamente: si es `true` → éxito; si es `false` → error con `message`
- Para el escenario de reactivación, la app debe procesarlo como éxito y actualizar la lista local de portones

---

### 4. `POST /api/v1/gates/delete`

Archiva (soft delete) un portón en Odoo. Lo oculta de la app y detiene sus operaciones, pero preserva el historial.

**Request:**
```json
{
    "jsonrpc": "2.0",
    "params": {
        "gate_id": 1
    }
}
```

**Response — Eliminación exitosa:**
```json
{
    "jsonrpc": "2.0",
    "result": {
        "success": true,
        "message": "Portón archivado/eliminado correctamente."
    }
}
```

**Response — Portón no encontrado:**
```json
{
    "jsonrpc": "2.0",
    "result": {
        "success": false,
        "message": "Portón no encontrado."
    }
}
```

**Notas:**
- El campo `result.success` es booleano (`true`/`false`)
- La app debe validar `success` como booleano, nunca como string
- Tras eliminar exitosamente, la app debe filtrar el portón de la lista local

---

### 5. `POST /api/v1/gates/list`

Obtiene la lista completa de portones registrados en Odoo.

**Request:**
```json
{
    "jsonrpc": "2.0",
    "params": {}
}
```

**Response exitosa:**
```json
{
    "jsonrpc": "2.0",
    "result": {
        "success": true,
        "data": [
            {
                "id": 1,
                "name": "Portón Principal",
                "mac_address": "A1:B2:C3:D4:E5:F6",
                "ip_address": "192.168.1.100",
                "is_online": true,
                "hostname": "esp32-a1b2c3"
            }
        ]
    }
}
```

---

### 6. `POST /api/v1/gates/update`

Actualiza el nombre de un portón existente en Odoo.

**Request:**
```json
{
    "jsonrpc": "2.0",
    "method": "call",
    "params": {
        "gate_id": 1,
        "name": "Portón Nuevo Nombre"
    }
}
```

**Response exitosa:**
```json
{
    "jsonrpc": "2.0",
    "result": {
        "status": "success"
    }
}
```

---

### 7. `POST /api/v1/gates/{id}/users`

Obtiene los usuarios que tienen acceso a un portón específico.

**Request:**
```json
{
    "jsonrpc": "2.0",
    "method": "call",
    "params": {}
}
```

**Response exitosa:**
```json
{
    "jsonrpc": "2.0",
    "result": {
        "success": true,
        "data": [
            {
                "id": 1,
                "nombre": "Juan Pérez",
                "cedula": "12345678",
                "placas": "ABC-1234",
                "estado": "activo"
            }
        ]
    }
}
```

---

## Implementación del Cliente HTTP

Todas las llamadas se realizan desde `SyncRepositoryImpl` con OkHttp directamente:

```kotlin
class SyncRepositoryImpl @Inject constructor(
    private val client: OkHttpClient
) : SyncRepository
```

### Configuración del cliente

```kotlin
OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .addInterceptor(HttpLoggingInterceptor().apply {
        level = if (isDebuggable) HttpLoggingInterceptor.Level.BODY
                else HttpLoggingInterceptor.Level.NONE
    })
    .build()
```

### Manejo de errores

- Códigos HTTP no exitosos → `Result.failure("Error ${code}: ${message}")`
- Respuesta con campo `error` → `Result.failure("Odoo Error: $errObj")`
- Respuesta sin `status` "success"/"pending" → `Result.failure(messageDelCampo)`
- Body vacío → `Result.failure("Empty body")`
- Excepciones de red → `Result.failure(exception)`

### Formato de respuestas

La app debe manejar **dos formatos de respuesta** según el endpoint:

| Endpoint | Campo éxito | Tipo |
|---|---|---|
| `/api/control_acceso` (create/update/delete) | `result.status` | String (`"success"`) |
| `/api/get_conductores` | `result.success` | Boolean (`true`) |
| `/api/v1/gates/*` (register/delete/list/update/users) | `result.success` | Boolean (`true`) o String legacy (`"success"`) |

Para los endpoints de la familia `/api/v1/gates/*`, la app debe validar `success` como booleano usando `booleanOrNull` de kotlinx.serialization, con fallback a comparación de string `"success"` para compatibilidad legacy.

## Endpoints del ESP32 (relacionados con Odoo)

El ESP32 implementa un WebServer interno que Odoo contacta directamente:

### `GET /abrir?token=secreto123`

- Odoo llama a este endpoint cuando la verificación de huella es exitosa
- El ESP32 valida el token (`"secreto123"` hardcodeado en el firmware)
- Responde `200 {"ok":true,"msg":"Acceso concedido"}` y activa el relé por 1 segundo
- Token inválido responde `401 {"error":"No autorizado"}`

### `POST /api/update_esp_ip`

- El ESP32 reporta su IP al Odoo al iniciar o al reconfigurar la red
- Se envía a la URL `{odooUrl}/api/update_esp_ip`
- Body: `{"jsonrpc":"2.0","method":"call","params":{"iot_token":"...","mac_address":"A1:B2:C3:D4:E5:F6","ip":"192.168.x.x","hostname":"esp32-a1b2c3"}}`
- La URL de Odoo se configura vía Bluetooth (comando `config_network` incluyendo `iot_token`) y se guarda en NVS del ESP32
- El ESP32 hace hasta 3 intentos con backoff (5s/15s/30s) para asegurar el reporte

## Estado Actual

- No hay autenticación entre la app Admin y el servidor Odoo
- El tráfico es HTTP plano (sin TLS)
- Las IPs y puertos son configurables por el usuario
- No hay CI/CD configurado
- Las pruebas son unitarias stub (`ExampleUnitTest`) e instrumentadas stub (`ExampleInstrumentedTest`)
