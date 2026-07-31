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

> [!IMPORTANT]
> **Formato de Petición Obligatorio (JSON-RPC)**
> Todas las peticiones a Odoo deben usar el estándar JSON-RPC 2.0. El payload de negocio NO puede ir en la raíz del JSON, debe ir siempre envuelto dentro del objeto "params".

### 3. `POST /api/v1/gates/register`

Registrar Nuevo Portón (ESP32)
Este endpoint debe ser llamado inmediatamente después de que la App Admin se conecta por Bluetooth a un ESP32 nuevo, le configura el WiFi y extrae su MAC Address. Sirve para crear el registro físico en la base de datos de Odoo.

> [!TIP]
> **Idempotencia:** Este endpoint es 100% idempotente. Si la App envía la misma MAC Address varias veces (ej. por reintentos de red), la API no arrojará error; reactivará el portón si estaba archivado y devolverá su `api_token` sin duplicar el registro.

- **Content-Type:** `application/json`
- **Autenticación:** Ninguna requerida por ahora (el endpoint está desprotegido en beta, se integrará JWT próximamente).

**📤 Body (Request)**
| Parámetro | Tipo | Requerido | Descripción |
|---|---|---|---|
| `name` | String | Sí | Nombre descriptivo asignado por el instalador en la App (Ej. "Portón Principal"). |
| `mac_address` | String | Sí | Dirección MAC física obtenida por Bluetooth desde el ESP32. Debe mantener el formato estándar (Ej. A1:B2:C3:D4:E5:F6). |

**Ejemplo de Petición:**
```json
{
  "jsonrpc": "2.0",
  "params": {
    "name": "Portón Visitantes Norte",
    "mac_address": "A1:B2:C3:D4:E5:F6"
  }
}
```

**📥 Respuestas (Response)**
Odoo siempre devolverá HTTP 200 OK porque usa JSON-RPC. El éxito o fracaso de la operación se define dentro del nodo `result`.

**✅ Escenario 1: Registro Exitoso (o Reintento Idempotente)**
Se creó el portón en Odoo o se procesó un reintento. La App debe extraer el `api_token` y enviarlo al ESP32 por Bluetooth.
```json
{
  "jsonrpc": "2.0",
  "id": null,
  "result": {
    "success": "success",
    "gate_id": 15,
    "api_token": "ff56e3bbafb14b2fb53808617de22b60",
    "message": "Portón registrado exitosamente."
  }
}
```

**❌ Escenario 2: Bad Request (Faltan Parámetros)**
Faltó enviar el `name` o la `mac_address`.
```json
{
  "jsonrpc": "2.0",
  "id": null,
  "result": {
    "success": "error",
    "message": "Nombre y MAC Address son obligatorios."
  }
}
```

---

### 4. `POST /api/v1/gates/delete`

Eliminar (Archivar) un Portón
Este endpoint realiza un archivado lógico del portón (Soft Delete), ocultándolo de la app y apagando sus operaciones en Odoo, pero preservando el historial en base de datos.

- **Content-Type:** `application/json`

**📤 Body (Request)**
```json
{
  "jsonrpc": "2.0",
  "params": {
    "gate_id": 1
  }
}
```

**📥 Respuestas (Response)**

**✅ Escenario 1: Eliminación Exitosa**
El portón ha sido archivado. La app debe leer estrictamente `success: true` y mostrar un popup de éxito verde con el mensaje.
```json
{
  "jsonrpc": "2.0",
  "id": null,
  "result": {
    "success": true,
    "message": "Portón archivado/eliminado correctamente."
  }
}
```

**❌ Escenario 2: Error al Eliminar (No Encontrado)**
Si se envía un ID inválido, la API devolverá `success: false`. La app debe capturar este `false` para mostrar una alerta roja/amarilla usando el `message` provisto.

> [!WARNING]
> Es crucial que la app valide correctamente el boolean `success`. Si la validación falla por un error de tipado en Kotlin, la app caerá en su bloque catch/else y podría terminar mostrando en pantalla "Error: Portón archivado correctamente" (concatenando un mensaje de éxito como si fuera error).

```json
{
  "jsonrpc": "2.0",
  "id": null,
  "result": {
    "success": false,
    "message": "Portón no encontrado."
  }
}
```

---

### 5. `POST /api/v1/gates/list` (O GET)

Listar Todos los Portones
Este endpoint permite a la App Admin obtener la lista completa de hardware registrado, útil para refrescar la interfaz después de un registro.

- **Content-Type:** `application/json`

**📤 Body (Request)**
Como es una simple consulta, pueden enviar un payload JSON-RPC vacío o sin parámetros.
```json
{
  "jsonrpc": "2.0",
  "params": {}
}
```

**📥 Respuesta (Response)**
Devuelve un arreglo `data` con todos los portones del sistema.
```json
{
  "jsonrpc": "2.0",
  "id": null,
  "result": {
    "success": true,
    "data": [
      {
        "id": 1,
        "name": "Portón Principal",
        "mac_address": "A1:B2:C3:D4:E5:F6",
        "ip_address": "192.168.1.55",
        "hostname": "esp-principal",
        "is_online": true,
        "is_active": true
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

### 8. Aclaratoria sobre el Flujo ESP32 (Ping)

> [!WARNING]
> El endpoint `/api/v1/gates/ping` NO DEBE SER LLAMADO POR LA APP MÓVIL.

Es responsabilidad exclusiva del hardware (ESP32) llamar a `/api/v1/gates/ping` una vez que se conecte exitosamente a la red WiFi.

**Nuevo Flujo de Integración (Opción 2):**
1. La App llama a `/api/v1/gates/register` para Odoo y recibe un `api_token`.
2. La App le inyecta este `api_token` (junto a los datos WiFi) al ESP32 a través de Bluetooth.
3. El ESP32 se conecta al WiFi, realiza su ping al backend usando su token propio, y Odoo lo marca como Online.
4. La App Administrativa simplemente refresca su vista llamando a `/api/v1/gates/list` para verificar que el portón ahora aparece `is_online: true`.

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

### `POST /api/v1/gates/ping`

- El ESP32 reporta su IP a Odoo al conectar al WiFi.
- Se envía a la URL configurada (usualmente `{odooUrl}/api/v1/gates/ping`).
- Body: `{"jsonrpc":"2.0","params":{"api_token":"...","ip_address":"192.168.x.x"}}`
- La URL de Odoo se configura vía Bluetooth (comando `config_network`) y se guarda en NVS del ESP32. El Token lo obtiene la App de Odoo al registrar el portón, y se lo pasa al ESP32.
- El ESP32 hace hasta 3 intentos con backoff (5s/15s/30s) para asegurar el reporte.

## Estado Actual

- No hay autenticación entre la app Admin y el servidor Odoo
- El tráfico es HTTP plano (sin TLS)
- Las IPs y puertos son configurables por el usuario
- No hay CI/CD configurado
- Las pruebas son unitarias stub (`ExampleUnitTest`) e instrumentadas stub (`ExampleInstrumentedTest`)
