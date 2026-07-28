# Contrato ESP32 — EscanQR Admin

## Hardware Objetivo

| Componente | Especificación |
|---|---|
| Microcontrolador | ESP32 |
| Conectividad | Bluetooth Classic SPP + WiFi 2.4GHz |
| Pantalla | OLED SSD1306 (128×64, I2C dirección 0x3C) |
| Relé | Pin GPIO 0 (activo alto, 1 segundo) |
| LED OK | Pin GPIO 19 |
| LED Error | Pin GPIO 4 |
| LED Wait | Pin GPIO 23 |

## Bluetooth

### Perfil

- **Protocolo:** Bluetooth Classic SPP (Serial Port Profile)
- **UUID SPP:** `00001101-0000-1000-8000-00805F9B34FB`
- **Nombre del dispositivo:** `ESP32_Seguro` (por defecto, configurable vía `set_bt_name` o `bt_name` en `config_network`)
- **Baudrate:** 115200 (solo para debug serial, no influye en BT)

### Descubrimiento desde la app Admin

- La app busca dispositivos cuyo nombre comience con **"ESP32"** (case-insensitive)
- Se filtran tanto dispositivos vinculados como descubiertos
- Se muestra un `BluetoothDialog` en HomeScreen con los dispositivos encontrados

### Conexión

```kotlin
// Método estándar
socket = device.createRfcommSocketToServiceRecord(
    UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
)

// Fallback por reflexión (canal 1)
socket = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
    .invoke(device, 1) as BluetoothSocket
```

La conexión es asíncrona. Estados expuestos como `StateFlow<BluetoothConnectionState>`:

- `Idle` — desconectado
- `Connecting` — intentando conectar
- `Connected(deviceAddress)` — conectado exitosamente
- `Error(message)` — error de conexión

### Envío de mensajes

Los mensajes se envían como bytes crudos terminados en `\n`:

```kotlin
socket?.outputStream?.write("$message\n".toByteArray())
```

### Recepción de mensajes

Se lee el stream de entrada con un buffer de 1024 bytes. Los mensajes se dividen por `\n` (el ESP32 usa `SerialBT.println()` que termina con `\n`). Cada línea completa se emite al `SharedFlow<String>` del repositorio.

## Máquina de Estados del ESP32 (V9)

El ESP32 corre una máquina de estados que determina qué comandos acepta vía Bluetooth.

### Estados

```
ESPERA_CONEXION (0)  →  MODO_CONFIG_BT (1)
                             ├─ "config" → MODO_CONFIG_ODOO (2)   [ELIMINADO en V9]
                             └─ JSON directo (config_network / set_bt_name / set_hostname)
                                                                      └─ MODO_CONECTANDO_WIFI (5) — asíncrono
```

| Estado | Valor | Descripción |
|---|---|---|
| `ESPERA_CONEXION` | 0 | Idle. Parpadea LED. WebServer HTTP activo. Reconexión WiFi automática (max 3 intentos). |
| `MODO_CONFIG_BT` | 1 | Cliente BT conectado. Acepta comandos JSON: `config_network`, `set_bt_name`, `set_hostname`. |
| `MODO_WIFI_SSID` | 3 | (Legacy) Espera SSID de red WiFi por texto. |
| `MODO_WIFI_PASS` | 4 | (Legacy) Espera contraseña WiFi por texto. Guarda y reinicia. |
| `MODO_CONECTANDO_WIFI` | 5 | (V7+) Conexión WiFi asíncrona no bloqueante. Procesa BT solo para limpiar buffer. |

### Diagrama de flujo (V9)

```
            ┌──────────────────────┐
            │   ESPERA_CONEXION     │ ◄──── WebServer HTTP siempre activo
            │ Reconexión WiFi auto  │        Reconexión WiFi automática (max 3)
            │ Parpadeo LED          │
            └────────┬─────────────┘
                     │ SerialBT.hasClient() = true
                     ▼
            ┌──────────────────────┐
            │    MODO_CONFIG_BT     │
            │ Timeout: 30s          │
            └───┬──────────┬───────┘
       JSON    │          │  "wifi" (legacy)
               ▼          ▼
   ┌────────────────────┐  ┌──────────────────┐
   │ Procesa JSON       │  │ MODO_WIFI_SSID   │
   │  • config_network  │  │ (legacy V6)       │
   │  • set_bt_name     │  └────────┬─────────┘
   │  • set_hostname    │           ▼
   └────────┬───────────┘  ┌──────────────────┐
            │              │ MODO_WIFI_PASS   │
            │              │ (legacy V6)       │
            │              └────────┬─────────┘
            │                       │ Guarda y reinicia
            ▼                       ▼
   ┌────────────────────┐      REINICIANDO
   │ MODO_CONECTANDO_WIFI│
   │ WiFi asíncrono      │
   │ Timeout: 30s        │
   │ Max 3 reintentos    │
   └────────┬───────────┘
            │ WiFi OK
            ▼
   ESPERA_CONEXION
   (BT reiniciado con nuevo nombre si cambió)
```

### Timeouts

| Estado | Timeout |
|---|---|
| `MODO_CONFIG_BT` | 30 segundos |
| `MODO_WIFI_SSID` | 60 segundos (legacy) |
| `MODO_WIFI_PASS` | 60 segundos (legacy) |
| `MODO_CONECTANDO_WIFI` | 30 segundos (max 3 intentos totales) |

Al expirar el timeout o superar los reintentos, el ESP32 vuelve a `ESPERA_CONEXION` y muestra error en pantalla. Si se superan los 3 intentos de WiFi, se enciende LED_ERROR fijo.

## Comandos y Respuestas (V9)

### Comandos JSON que acepta el ESP32

| Comando | Descripción |
|---|---|
| `config_network` | Configura WiFi, nombre BT, hostname e IoT token en un solo JSON |
| `set_bt_name` | Cambia el nombre Bluetooth en caliente |
| `set_hostname` | Cambia el hostname DHCP y reinicia |

Cualquier comando no reconocido recibe `{"status":"error","message":"Acción desconocida"}`.

### Comando legacy texto

| Comando | Descripción |
|---|---|
| `wifi` | (Legacy V6) Configuración secuencial de WiFi: SSID → PASS → reinicia |

### Formato de respuesta del ESP32

Todas las respuestas exitosas a comandos JSON son en formato JSON:

```json
{"status":"success","mac_address":"A1:B2:C3:D4:E5:F6","message":"Red configurada"}
```

Respuestas de error:
```json
{"status":"error","message":"SSID requerido"}
```

## Configuración Completa vía Bluetooth (Comando `config_network`)

Este es el flujo principal en V9. La app Admin envía un solo JSON con todos los parámetros:

1. Admin se conecta al ESP32 por Bluetooth
2. Admin envía JSON:
   ```json
   {
       "action": "config_network",
       "ssid": "MiRed",
       "password": "password123",
       "bt_name": "ESP32_Puerta1",
       "hostname": "porton-principal",
       "iot_token": "iot_secret_2024"
   }
   ```
3. ESP32 guarda todo en NVS y responde: `{"status":"success","mac_address":"...","message":"Red configurada"}`
4. ESP32 inmediatamente entra en `MODO_CONECTANDO_WIFI` (no reinicia, no corta BT)
5. Mientras WiFi conecta, el teléfono permanece conectado por BT (el BT restart se hace DESPUÉS de WiFi exitoso)
6. Al conectar WiFi: BT se reinicia con el nuevo nombre, se reporta IP a Odoo, se inicia WebServer
7. Si WiFi falla tras 3 intentos: LED_ERROR fijo, muestra "WIFI ERROR / Sin conexion / Reintente via BT"

**Campos opcionales:** `bt_name`, `hostname`, `iot_token` — si no se envían, se mantienen los valores actuales.

## Comando `set_bt_name`

```json
{"action":"set_bt_name","name":"ESP32_Puerta1"}
```

- Cambia el nombre Bluetooth en caliente
- Responde: `{"status":"success","message":"Nombre BT actualizado"}`
- Persiste en NVS (clave `bt_name`)

## Comando `set_hostname`

```json
{"action":"set_hostname","hostname":"porton-principal"}
```

- Cambia el hostname DHCP
- Responde: `{"status":"success","message":"Hostname configurado"}`
- Persiste en NVS (clave `hostname`)
- **Reinicia el ESP32** para aplicar el cambio

## Configuración de WiFi vía Bluetooth (Comando legacy `wifi`)

1. Admin envía `"wifi"` → ESP32 responde `"SSID:"`
2. Admin envía el SSID de la red → ESP32 responde `"PASS:"`
3. Admin envía la contraseña
4. ESP32 guarda SSID y PASS en NVS (Preferences, claves `ssid` y `pass`)
5. Responde `"REINICIANDO"` y ejecuta `ESP.restart()`

## WebServer Interno (HTTP)

El ESP32 corre un servidor HTTP en el puerto 80 cuando tiene WiFi configurado.

### Endpoints

#### `GET /abrir?token=secreto123`

**Propósito:** Odoo llama aquí cuando la verificación de huella es exitosa.

- **Requiere:** Parámetro `token` igual a `API_TOKEN` (hardcodeado `"secreto123"`)
- **Respuesta exitosa (200):** `{"ok":true,"msg":"Acceso concedido"}`
  - Activa el relé (GPIO 0) por 1000ms
  - Enciende LED OK
  - Muestra "Abriendo porton..." en OLED
- **Token inválido (401):** `{"error":"No autorizado"}`

#### `POST /api/update_esp_ip` (llamada saliente)

**Propósito:** El ESP32 reporta su IP al Odoo.

- Se llama al iniciar (si WiFi configurado) y al conectar WiFi post-configuración
- Body: `{"jsonrpc":"2.0","params":{"iot_token":"...","mac_address":"...","ip":"192.168.x.x","hostname":"..."}}`
- La URL de destino es la configurada vía Bluetooth (valor por defecto: `http://192.168.1.100:8059/api/update_esp_ip`)
- Hasta 3 intentos con backoff (5s / 15s / 30s)

### `GET /status`

Expone el estado interno del ESP32:

```json
{
    "mac": "A1:B2:C3:D4:E5:F6",
    "wifi": "connected" | "connecting" | "disconnected",
    "ip": "192.168.1.100",
    "uptime": 3600,
    "bt_name": "ESP32_Puerta1",
    "hostname": "porton-principal"
}
```

## Persistencia en el ESP32

El ESP32 usa la librería `Preferences` (NVS) para almacenar:

| Clave | Valor | Default |
|---|---|---|
| `ssid` | Nombre de red WiFi | — |
| `pass` | Contraseña WiFi | — |
| `bt_name` | Nombre Bluetooth | `"ESP32_Seguro"` |
| `hostname` | Nombre host DHCP | `"esp32-" + últimos 6 dígitos MAC |
| `iot_token` | Token IoT para auto-reporte | `"iot_secret_2024"` |
| `odoo_url` | URL endpoint Odoo | `http://192.168.1.100:8059/api/update_esp_ip` |
| `static_ip` | IP estática (V8) | — |
| `static_gateway` | Gateway estático (V8) | — |
| `static_netmask` | Máscara estática (V8) | — |

## Consola Bluetooth en la App Admin

La pantalla `ESPConfigScreen` implementa una consola tipo terminal para interactuar con el ESP32:

- **Modo libre (IDLE):** barra de comandos donde se puede escribir cualquier texto
- **Modo guiado:** formularios que se muestran automáticamente según la respuesta del ESP32:
  - `WAIT_WIFI_SSID`: campo SSID
  - `WAIT_WIFI_PASS`: campo Password
- **Botones rápidos:** "Config" y "WiFi" para enviar los comandos raíz
- **Consola:** historial de mensajes TX/RX con marcas de tiempo

### Máquina de estados del ViewModel

```kotlin
enum class EspFlowState {
    IDLE,
    WAIT_WIFI_SSID,     // Espera SSID
    WAIT_WIFI_PASS,     // Espera Password
}
```

### Terminales que finalizan el flujo

- `TIMEOUT`
- `CMD_DESCONOCIDO`
- `REINICIANDO`
- Cualquier respuesta JSON completa

## V8/V9 — Comandos JSON y Configuraciones Avanzadas

### Resumen de cambios V9 respecto a V8

| Cambio | V8 | V9 |
|---|---|---|
| Protocolo texto V6 (`config`, `wifi` legacy) | Soporte completo | Solo `wifi` legacy, `config` eliminado |
| `MODO_CONFIG_ODOO` | Existente | Eliminado |
| BT restart en `config_network` | Inmediato, cortaba conexión | Diferido hasta después de WiFi OK |
| Reconexión WiFi automática | Infinita | Máximo 3 intentos, luego error fijo |
| Limpieza buffer BT durante WiFi | No | Sí (evita saturación) |

### Comandos JSON disponibles (V8+)

| Comando | Descripción |
|---|---|
| `config_ip` | Configura IP estática, gateway, netmask. Guarda y reinicia. [Eliminado en V9] |
| `set_bt_name` | Cambia nombre Bluetooth en caliente. |
| `set_hostname` | Cambia hostname DHCP. Reinicia. |
| `config_network` | Configura WiFi + bt_name + hostname + iot_token en un solo JSON. |

#### `config_ip`

```json
{
    "ip": "192.168.1.200",
    "gateway": "192.168.1.1",
    "netmask": "255.255.255.0"
}
```

- Guarda IP, gateway y netmask en Preferences (claves `static_ip`, `static_gateway`, `static_netmask`)
- En el boot, si existen estas claves, aplica `WiFi.config()` antes de `WiFi.begin()`
- Responde `"CONFIG_OK"` o `"JSON_ERROR"`
- La IP estática se expone en `GET /status` como campo `static_ip`

#### `set_bt_name`

```json
{"action":"set_bt_name","name":"ESP32_Puerta1"}
```

- Cambia el nombre Bluetooth en caliente reiniciando SPP
- Persiste el nombre en Preferences (clave `bt_name`)
- En boot, si existe la clave `bt_name`, lo usa en `SerialBT.begin(bt_name)`
- Responde `{"status":"success","message":"Nombre BT actualizado"}`
- El nombre BT se expone en `GET /status` como campo `bt_name`

#### `set_hostname`

```json
{"action":"set_hostname","hostname":"porton-principal"}
```

- Cambia el hostname DHCP
- Persiste en NVS (clave `hostname`)
- Responde `{"status":"success","message":"Hostname configurado"}`
- **Reinicia el ESP32** para aplicar el cambio

### IP Estática

- **Persistencia:** claves `static_ip`, `static_gateway`, `static_netmask` en NVS (Preferences)
- **Aplicación en boot:** si existen, se llama `WiFi.config(local_ip, gateway, netmask)` antes de `WiFi.begin()`
- **Exposición:** campo `static_ip` en `GET /status`
- **Comando de configuración:** `config_ip` vía Bluetooth

### Bluetooth Name

- **Persistencia:** clave `bt_name` en NVS (Preferences)
- **Aplicación en boot:** si existe, se pasa a `SerialBT.begin(bt_name)`
- **Cambio en caliente:** vía `set_bt_name` o `bt_name` en `config_network`
- **Exposición:** campo `bt_name` en `GET /status`
