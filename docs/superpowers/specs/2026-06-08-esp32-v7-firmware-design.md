# ESP32 V7 Firmware — Protocolo Dual y Registro de Portones

**Date:** 2026-06-08
**Status:** Approved Design
**Based on:** `VerificacionHuellasV6.ino`, `docs/Investigacion-Tarjetas-ESP32.md`, `docs/Master-Prompt-App-Admin-Kotlin.md`, `docs/Contrato-ESP32.md`, `docs/superpowers/specs/2026-06-08-gate-registration-design.md`

---

## 1. Objetivo

Actualizar el firmware ESP32 WROOM de V6 a V7 para:

- Soportar el nuevo protocolo JSON (`config_network`) para configuración WiFi, usado por el flujo de registro de portones de la app Admin
- Mantener compatibilidad total con el protocolo texto V6 (usado por la pantalla ESPConfig existente)
- Reportar la dirección MAC al finalizar la configuración WiFi para que la app pueda registrar el portón en Odoo
- Mejorar el auto-discovery (ESP32 → Odoo) enviando MAC + IP

## 2. Hardware Target

| Componente | Especificación |
|---|---|
| Microcontrolador | ESP32 WROOM |
| Conectividad | Bluetooth Classic SPP + WiFi 2.4GHz |
| Pantalla | OLED SSD1306 (128×64, I2C dirección 0x3C) |
| Relé | Pin GPIO 0 (activo alto, 1 segundo) |
| LED OK | Pin GPIO 19 |
| LED Error | Pin GPIO 4 |
| LED Wait | Pin GPIO 23 |

## 3. Estrategia: Protocolo Dual con Auto-Detección

El ESP32 mantiene una sola máquina de estados. Al recibir un mensaje por Bluetooth en `MODO_CONFIG_BT`:

- **Si el primer carácter es `{`** → se enruta al handler JSON
- **Si no** → se enruta al handler texto V6 existente

Esto permite que ambos flujos (ESPConfig screen legacy y GateRegistrationDialog nuevo) funcionen sin cambios en la app.

## 4. Máquina de Estados V7

### Estados

| # | Nombre | Descripción |
|---|---|---|
| 0 | `ESPERA_CONEXION` | Idle. Parpadea LED. WebServer HTTP activo si WiFi conectado. Espera conexión BT. |
| 1 | `MODO_CONFIG_BT` | Cliente BT conectado. Acepta comandos texto V6 (`wifi`, `config`) o JSON (`{"action":"config_network",...}`). |
| 2 | `MODO_CONFIG_ODOO` | [V6] Espera JSON `{protocolo, ip_odoo, port}`. |
| 3 | `MODO_WIFI_SSID` | [V6] Espera SSID de red WiFi. |
| 4 | `MODO_WIFI_PASS` | [V6] Espera contraseña WiFi. Guarda y reinicia. |
| **5** | `MODO_CONECTANDO_WIFI` | **[NUEVO]** WiFi.begin no bloqueante. Verifica `WiFi.status()` en cada loop. Timeout 30s. |

### Diagrama de flujo

```
             ┌─────────────────┐
             │  ESPERA_CONEXION │ ◄──── WebServer HTTP siempre activo si WiFi OK
             └────────┬────────┘
                      │ SerialBT.hasClient() = true
                      ▼
             ┌─────────────────┐
             │  MODO_CONFIG_BT  │
             └───┬──┬───┬──┬──┘
     ┌───────────┘  │   │  └────────────┐
     │ (texto)      │   │ (JSON)        │
     ▼              │   ▼               │
  ┌──────┐         │ ┌────────────────┐ │
  │"wifi"│         │ │"config_network"│ │
  └──┬───┘         │ └───────┬────────┘ │
     ▼             │         ▼          │
  MODO_WIFI_SSID   │  ┌────────────────┐│
     ▼             │  │ Guarda creds   ││
  MODO_WIFI_PASS   │  │ Responde MAC   ││
     ▼             │  │ Disconnect BT  ││
  REINICIANDO      │  │ → MODO_5       ││
                   │  └────────────────┘│
                   ▼                    │
             ┌──────────┐              │
             │"config"  │              │
             └──┬───────┘              │
                ▼                      │
         MODO_CONFIG_ODOO              │
                │                      │
                ▼                      │
         ESPERA_CONEXION ◄─────────────┘

  ┌────────────────────────────────────────────┐
  │         MODO_CONECTANDO_WIFI (5)           │
  │                                            │
  │  loop():                                    │
  │    if WiFi.status() == WL_CONNECTED:        │
  │      server.begin()                         │
  │      reportarIPyMAC()                       │
  │      → ESPERA_CONEXION                      │
  │    elif timeout > 30s:                      │
  │      pantalla("ERROR WIFI")                 │
  │      → ESPERA_CONEXION                      │
  └────────────────────────────────────────────┘
```

### Timeouts

| Estado | Timeout |
|---|---|
| `MODO_CONFIG_BT` | 30 segundos |
| `MODO_CONFIG_ODOO` | 30 segundos |
| `MODO_WIFI_SSID` | 60 segundos |
| `MODO_WIFI_PASS` | 60 segundos |
| **`MODO_CONECTANDO_WIFI`** | **30 segundos** |

## 5. Protocolo JSON `config_network`

### 5.1 Mensaje de la App al ESP32

```json
{
  "action": "config_network",
  "ssid": "Red_WiFi",
  "password": "PasswordSeguro123"
}
```

- `action`: string, obligatorio, debe ser `"config_network"`
- `ssid`: string, obligatorio, no vacío
- `password`: string, opcional (red abierta si se omite o vacío)

### 5.2 Respuesta Exitosa del ESP32

```json
{
  "status": "success",
  "mac_address": "A1:B2:C3:D4:E5:F6",
  "message": "Red configurada"
}
```

- `mac_address`: string, 17 caracteres, formato `XX:XX:XX:XX:XX:XX`, obtenido vía `WiFi.macAddress()`

### 5.3 Respuestas de Error

```json
{"status": "error", "message": "JSON inválido"}
{"status": "error", "message": "Acción desconocida"}
{"status": "error", "message": "SSID requerido"}
{"status": "error", "message": "Timeout"}
```

### 5.4 Flujo Post-Respuesta Exitosa

1. Guarda SSID y password en Preferences (claves `ssid`, `pass`)
2. Envía respuesta JSON con MAC por Bluetooth
3. Ejecuta `SerialBT.end()` para liberar Bluetooth (luego de enviar la respuesta, BT se detiene; en el próximo arranque `SerialBT.begin()` lo reinicia)
4. Transiciona a `MODO_CONECTANDO_WIFI`
5. En `MODO_CONECTANDO_WIFI`: llama `WiFi.begin(ssid, pass)` una vez, luego verifica estado en cada loop
6. Al conectar: inicia WebServer, reporta IP+MAC a Odoo, transiciona a `ESPERA_CONEXION`
7. Timeout 30s: muestra error en OLED, transiciona a `ESPERA_CONEXION`

## 6. Compatibilidad hacia atrás (V6)

### 6.1 Comando `wifi`

Funciona exactamente como en V6:
1. App envía `"wifi"` → ESP32 responde `"SSID:"`
2. App envía SSID → ESP32 responde `"PASS:"`
3. App envía password → ESP32 guarda en Preferences y ejecuta `ESP.restart()`

### 6.2 Comando `config`

Funciona exactamente como en V6:
1. App envía `"config"` → ESP32 responde `"OK_CONFIG"`
2. App envía JSON `{protocolo, ip_odoo, port}` → ESP32 guarda URL y responde `"CONFIG_OK"`

### 6.3 Comandos y Respuestas V6 (sin cambios)

| Comando | Estado Requerido | Descripción |
|---|---|---|
| `config` | `MODO_CONFIG_BT` | Inicia configuración de URL de Odoo |
| `wifi` | `MODO_CONFIG_BT` | Inicia configuración de red WiFi |

| Respuesta | Significado |
|---|---|
| `OK_CONFIG` | Listo para recibir JSON de configuración Odoo |
| `SSID:` | Solicita el nombre de la red WiFi |
| `PASS:` | Solicita la contraseña WiFi |
| `CONFIG_OK` | Configuración Odoo guardada exitosamente |
| `ERROR_IP` | El JSON no incluyó IP |
| `JSON_ERROR` | El JSON enviado no es válido |
| `TIMEOUT` | Se agotó el tiempo de espera |
| `CMD_DESCONOCIDO` | Comando no reconocido |
| `REINICIANDO` | WiFi configurado, el ESP32 se reinicia |

## 7. WebServer HTTP

### 7.1 `GET /abrir?token=secreto123`

Sin cambios respecto a V6.

- **Requiere:** Parámetro `token` = `API_TOKEN` (`"secreto123"`)
- **Respuesta 200:** `{"ok":true,"msg":"Acceso concedido"}` — activa relé 1000ms, LED OK
- **Respuesta 401:** `{"error":"No autorizado"}`

### 7.2 `GET /status` (NUEVO)

**Propósito:** Endpoint de debug/verificación sin autenticación.

**Respuesta 200:**
```json
{
  "mac": "A1:B2:C3:D4:E5:F6",
  "wifi": "connected",
  "ip": "192.168.1.42",
  "uptime": 3600
}
```

- `mac`: dirección MAC del ESP32
- `wifi`: `"connected"`, `"disconnected"`, `"connecting"`
- `ip`: IP local (solo si wifi = connected)
- `uptime`: segundos desde el último reset

## 8. Auto-Discovery (ESP32 → Odoo)

### 8.1 Payload mejorado

V6 enviaba solo `{"params": {"ip": "..."}}`. V7 envía:

```json
{
  "jsonrpc": "2.0",
  "method": "call",
  "params": {
    "ip": "192.168.1.42",
    "mac_address": "A1:B2:C3:D4:E5:F6"
  }
}
```

### 8.2 Cuándo se ejecuta

1. Al iniciar (setup), si ya hay WiFi configurado y conecta
2. Al completar conexión WiFi después de JSON `config_network`
3. Al reconfigurar vía comando `config` V6 (si WiFi ya estaba conectado)

## 9. Pantalla OLED — Nuevos Mensajes

| Evento | Línea 1 | Línea 2 | Línea 3 |
|---|---|---|---|
| JSON config recibido | "CONFIG JSON OK" | `MAC: XX:XX:XX:XX:XX:XX` | — |
| Conectando WiFi (async) | "CONECTANDO WIFI" | (SSID) | — |
| WiFi conectado (post-JSON) | "WIFI CONECTADO" | IP | — |
| WiFi timeout (30s) | "ERROR WIFI" | "Timeout 30s" | — |
| JSON inválido | "ERROR JSON" | "Formato inválido" | — |

## 10. LEDs

| Estado | LED Wait (GPIO23) | LED Error (GPIO4) | LED OK (GPIO19) |
|---|---|---|---|
| ESPERA_CONEXION (idle) | Parpadeo lento 500ms | OFF | OFF |
| MODO_CONFIG_BT (conectado) | ON fijo | OFF | OFF |
| MODO_CONECTANDO_WIFI | Parpadeo rápido 250ms | OFF | OFF |
| WiFi conectado / idle | OFF | OFF | Según estado relé |
| Error / timeout | OFF | ON 3s | OFF |

LED OK y Relé (GPIO0): sin cambios respecto a V6.

## 11. Almacenamiento Persistente

Preferences (NVS) — sin cambios de claves respecto a V6:

| Clave | Tipo | Valor |
|---|---|---|
| `ssid` | String | Nombre red WiFi |
| `pass` | String | Contraseña WiFi |
| `odoo_url` | String | URL endpoint Odoo para reporte IP |

Default `odoo_url`: `http://192.168.1.100:8059/api/update_esp_ip`

## 12. Manejo de Errores

| Escenario | Comportamiento |
|---|---|
| JSON mal formado (parse error) | Responde `{"status":"error","message":"JSON inválido"}` |
| `action` desconocido | Responde `{"status":"error","message":"Acción desconocida"}` |
| SSID vacío o ausente | Responde `{"status":"error","message":"SSID requerido"}` |
| Timeout en MODO_CONFIG_BT | Si es JSON: `{"status":"error","message":"Timeout"}`; si es V6: `"TIMEOUT"` |
| Timeout en MODO_CONECTANDO_WIFI | Pantalla "ERROR WIFI Timeout 30s", vuelve a ESPERA_CONEXION |
| Falla de conexión WiFi | Pantalla error, reintenta en próximo arranque |
| Pérdida de cliente BT en medio de flujo | Vuelve a ESPERA_CONEXION automáticamente |

## 13. Archivos Modificados

| Archivo | Acción |
|---|---|
| `VerificacionHuellasV6.ino` → `VerificacionHuellasV7.ino` | **RENOMBRAR + MODIFICAR** |

Todos los cambios son sobre el mismo archivo .ino. No se añaden nuevos archivos.

## 14. Preguntas Abiertas (póst-implementación)

- Determinar si el endpoint Odoo `POST /api/update_esp_ip` acepta el nuevo campo `mac_address` (depende del backend)
- Confirmar que `SerialBT.end()` + `SerialBT.begin()` es el mecanismo correcto para reiniciar BT después del JSON config
- Evaluar si el OLED necesita más información durante `MODO_CONECTANDO_WIFI` (ej. contador de tiempo transcurrido)
