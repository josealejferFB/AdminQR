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
- Body: `{"jsonrpc":"2.0","method":"call","params":{"ip":"192.168.x.x"}}`
- La URL de Odoo se configura vía Bluetooth (comando `config`) y se guarda en NVS del ESP32

## Estado Actual

- No hay autenticación entre la app Admin y el servidor Odoo
- El tráfico es HTTP plano (sin TLS)
- Las IPs y puertos son configurables por el usuario
- No hay CI/CD configurado
- Las pruebas son unitarias stub (`ExampleUnitTest`) e instrumentadas stub (`ExampleInstrumentedTest`)
