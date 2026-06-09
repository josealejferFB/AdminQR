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
- **Nombre del dispositivo:** `ESP32_Seguro` (configurado en `SerialBT.begin("ESP32_Seguro")`)
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

## Máquina de Estados del ESP32 (V7)

El ESP32 corre una máquina de estados que determina qué comandos acepta vía Bluetooth.

### Estados

```
ESPERA_CONEXION (0)  →  MODO_CONFIG_BT (1)
                             ├─ "config" → MODO_CONFIG_ODOO (2)
                             └─ "wifi"   → MODO_WIFI_SSID (3)
                                            └─ MODO_WIFI_PASS (4)
                                                 └─ REINICIANDO
```

| Estado | Valor | Descripción |
|---|---|---|
| `ESPERA_CONEXION` | 0 | Idle. Parpadea LED. Espera conexión BT. WebServer HTTP activo. |
| `MODO_CONFIG_BT` | 1 | Cliente BT conectado. Solo acepta los comandos `config` o `wifi`. |
| `MODO_CONFIG_ODOO` | 2 | Espera JSON `{protocolo, ip_odoo, port}`. |
| `MODO_WIFI_SSID` | 3 | Espera SSID de red WiFi. |
| `MODO_WIFI_PASS` | 4 | Espera contraseña WiFi. Guarda y reinicia. |

### Diagrama de flujo

```
            ┌─────────────────┐
            │  ESPERA_CONEXION │ ◄──── WebServer HTTP siempre activo
            └────────┬────────┘
                     │ SerialBT.hasClient() = true
                     ▼
            ┌─────────────────┐
            │  MODO_CONFIG_BT  │
            └───┬─────────┬───┘
       "config" │         │ "wifi"
                ▼         ▼
    ┌──────────────────┐  ┌──────────────────┐
    │ MODO_CONFIG_ODOO │  │ MODO_WIFI_SSID   │
    │ Espera JSON...   │  │ Espera SSID...   │
    └────────┬─────────┘  └────────┬─────────┘
             │                     │
             │                     ▼
             │              ┌──────────────────┐
             │              │ MODO_WIFI_PASS   │
             │              │ Espera Password  │
             │              └────────┬─────────┘
             │                       │ Guarda y reinicia
             ▼                       ▼
      Vuelve a ESPERA_CONEXION    REINICIANDO
      (tras mostrar mensaje)
```

### Timeouts

| Estado | Timeout |
|---|---|
| `MODO_CONFIG_BT` | 30 segundos |
| `MODO_CONFIG_ODOO` | 30 segundos |
| `MODO_WIFI_SSID` | 60 segundos |
| `MODO_WIFI_PASS` | 60 segundos |
| `MODO_CONECTANDO_WIFI` | 30 segundos | [V7] |

Al expirar, el ESP32 envía `"TIMEOUT"` y vuelve a `ESPERA_CONEXION`.

## Comandos y Respuestas

### Comandos que acepta el ESP32

| Comando | Estado Requerido | Descripción |
|---|---|---|
| `config` | `MODO_CONFIG_BT` | Inicia configuración de URL de Odoo |
| `wifi` | `MODO_CONFIG_BT` | Inicia configuración de red WiFi |

Cualquier otro comando en `MODO_CONFIG_BT` recibe `CMD_DESCONOCIDO`.

### Códigos de respuesta del ESP32

| Respuesta | Significado |
|---|---|
| `OK_CONFIG` | Listo para recibir JSON de configuración Odoo |
| `SSID:` | Solicita el nombre de la red WiFi |
| `PASS:` | Solicita la contraseña WiFi |
| `CONFIG_OK` | Configuración Odoo guardada exitosamente |
| `ERROR_IP` | El JSON no incluyó IP (campo `ip_odoo` vacío) |
| `JSON_ERROR` | El JSON enviado no es válido |
| `TIMEOUT` | Se agotó el tiempo de espera para responder |
| `CMD_DESCONOCIDO` | Comando no reconocido en el estado actual |
| `REINICIANDO` | WiFi configurado, el ESP32 se reinicia |
| `SISTEMA LISTO` | Mensaje de estado idle en la pantalla OLED (no se envía por BT) |

## Configuración de Odoo vía Bluetooth (Comando `config`)

1. Admin envía `"config"` → ESP32 responde `"OK_CONFIG"`
2. Admin envía JSON:
   ```json
   {
       "protocolo": "http",
       "ip_odoo": "192.168.1.100",
       "port": 8059
   }
   ```
3. ESP32 construye la URL: `{protocolo}://{ip_odoo}:{port}/api/update_esp_ip`
   - Omite el puerto si es estándar (80 para http, 443 para https)
4. Responde `"CONFIG_OK"` o `"ERROR_IP"`/`"JSON_ERROR"`
5. La URL se guarda en NVS (Preferences, clave `odoo_url`)

## Configuración de WiFi vía Bluetooth (Comando `wifi`)

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

- Se llama al iniciar (si WiFi configurado) y al reconfigurar
- Body: `{"jsonrpc":"2.0","method":"call","params":{"ip":"192.168.x.x"}}`
- La URL de destino es la configurada vía Bluetooth (`odoo_url`)

## Persistencia en el ESP32

El ESP32 usa la librería `Preferences` (NVS) para almacenar:

| Clave | Valor |
|---|---|
| `ssid` | Nombre de red WiFi |
| `pass` | Contraseña WiFi |
| `odoo_url` | URL completa del endpoint Odoo para reporte de IP |

Valor por defecto de `odoo_url`: `http://192.168.1.100:8059/api/update_esp_ip`

## Consola Bluetooth en la App Admin

La pantalla `ESPConfigScreen` implementa una consola tipo terminal para interactuar con el ESP32:

- **Modo libre (IDLE):** barra de comandos donde se puede escribir cualquier texto
- **Modo guiado:** formularios que se muestran automáticamente según la respuesta del ESP32:
  - `WAIT_JSON_CONFIG`: campos Protocolo, IP Odoo, Puerto
  - `WAIT_WIFI_SSID`: campo SSID
  - `WAIT_WIFI_PASS`: campo Password
- **Botones rápidos:** "Config" y "WiFi" para enviar los comandos raíz
- **Consola:** historial de mensajes TX/RX con marcas de tiempo

### Máquina de estados del ViewModel

```kotlin
enum class EspFlowState {
    IDLE,
    WAIT_JSON_CONFIG,   // Espera JSON {protocolo, ip_odoo, port}
    WAIT_WIFI_SSID,     // Espera SSID
    WAIT_WIFI_PASS,     // Espera Password
}
```

El ViewModel escucha los mensajes del ESP32 y avanza automáticamente entre estados:

```kotlin
when (msg) {
    "OK_CONFIG"  -> WAIT_JSON_CONFIG
    "SSID:"      -> WAIT_WIFI_SSID
    "PASS:"      -> WAIT_WIFI_PASS
    // Cualquier terminal vuelve a IDLE
}
```

### Terminales que finalizan el flujo

- `CONFIG_OK`
- `ERROR_IP`
- `JSON_ERROR`
- `TIMEOUT`
- `CMD_DESCONOCIDO`
- `REINICIANDO`
- `SISTEMA LISTO` (por prefijo)

## V8 — Nuevas Funcionalidades

A partir de V8 se añadieron comandos JSON y configuración de IP estática y nombre Bluetooth.

### Comandos JSON (V8)

| Comando | Estado Requerido | Descripción |
|---|---|---|
| `config_ip` | `MODO_CONFIG_BT` | Recibe JSON con `ip`, `gateway`, `netmask`. Guarda en Preferences y reinicia. |
| `set_bt_name` | `MODO_CONFIG_BT` | Recibe JSON con `name`. Cambia nombre BT en caliente y persiste en Preferences. |
| `config_network` | `MODO_CONFIG_BT` | Ahora acepta `bt_name` opcional además de `ssid`/`pass`. |

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
{
    "name": "ESP32_Puerta1"
}
```

- Cambia el nombre Bluetooth en caliente usando `SerialBT.flush()` y reiniciando SPP
- Persiste el nombre en Preferences (clave `bt_name`)
- En boot, si existe la clave `bt_name`, lo usa en `SerialBT.begin(bt_name)`
- Responde `"CONFIG_OK"` o `"JSON_ERROR"`
- El nombre BT se expone en `GET /status` como campo `bt_name`

#### `config_network` (mejorado)

```json
{
    "ssid": "MiRed",
    "pass": "password123",
    "bt_name": "ESP32_Puerta1"
}
```

- `bt_name` es opcional. Si se incluye, actualiza el nombre BT además del WiFi.
- Mantiene compatibilidad hacia atrás con el flujo `wifi` (V7).

### Estados V8

Sin cambios respecto a V7. La máquina de estados sigue siendo:

```
ESPERA_CONEXION (0) → MODO_CONFIG_BT (1)
                         ├─ "config" → MODO_CONFIG_ODOO (2)
                         ├─ "wifi"   → MODO_WIFI_SSID (3)
                         │              └─ MODO_WIFI_PASS (4)
                         │                   └─ REINICIANDO
                         ├─ "config_ip"     → guarda IP y reinicia
                         ├─ "set_bt_name"   → cambia BT name
                         └─ "config_network" → guarda WiFi + BT name
```

### Timeouts V8

Sin cambios respecto a V7.

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

### Persistencia V8 (adicional a V7)

| Clave | Valor |
|---|---|
| `static_ip` | IP estática (String) |
| `static_gateway` | Gateway (String) |
| `static_netmask` | Máscara de red (String) |
| `bt_name` | Nombre Bluetooth personalizado (String) |
