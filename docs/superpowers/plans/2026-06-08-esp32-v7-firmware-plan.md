# ESP32 V7 Firmware Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Update the ESP32 WROOM firmware from V6 to V7 with dual protocol support (JSON + legacy text), MAC reporting, non-blocking WiFi connection, and improved auto-discovery.

**Architecture:** Single `.ino` file with a state machine. Add auto-detection of JSON vs text protocol in `MODO_CONFIG_BT`. Add new state `MODO_CONECTANDO_WIFI` for async WiFi connection after JSON config. All V6 states and commands remain unchanged.

**Tech Stack:** Arduino framework for ESP32, BluetoothSerial, WiFi, WebServer, ArduinoJson, Preferences, Adafruit SSD1306 OLED.

**Spec:** `docs/superpowers/specs/2026-06-08-esp32-v7-firmware-design.md`

---

### Task 1: Rename and Prepare File

**Files:**
- Rename: `VerificacionHuellasV6.ino` → `VerificacionHuellasV7.ino`

- [ ] **Step 1: Rename the file and update header**

Rename `VerificacionHuellasV6.ino` to `VerificacionHuellasV7.ino`. Update the top comment block:

```cpp
// ============================================================
//  SISTEMA VEHICULAR - VERSIÓN 7
//  Apertura de portón: señal HTTP desde Odoo → relé
//  Bluetooth: Protocolo dual (JSON + texto V6 legacy)
//  Novedades V7:
//    - Nuevo comando JSON {"action":"config_network",...}
//    - Reporte de MAC address post-configuración WiFi
//    - Conexión WiFi asíncrona (no bloqueante)
//    - Endpoint GET /status con info del ESP32
//    - Auto-discovery mejorado (MAC + IP a Odoo)
// ============================================================
```

- [ ] **Step 2: Commit**

```bash
git mv VerificacionHuellasV6.ino VerificacionHuellasV7.ino
git add VerificacionHuellasV7.ino
git commit -m "feat(esp32): rename V6 to V7 firmware"
```

---

### Task 2: Add V7 Constants, State, and System Struct Fields

**Files:**
- Modify: `VerificacionHuellasV7.ino`

- [ ] **Step 1: Add new state constant**

Add after `#define MODO_WIFI_PASS 4`:

```cpp
#define MODO_CONECTANDO_WIFI 5   // [V7] WiFi.begin no bloqueante
```

- [ ] **Step 2: Add new system struct fields**

Add these fields inside the `sistema` struct (after `int puerto;` and before `// Control asíncrono del relé`):

```cpp
// [V7] Control asíncrono de WiFi
unsigned long wifiStartTime;
bool          wifiConnecting;

// [V7] Almacenar última respuesta MAC para /status
String        macAddress;
```

- [ ] **Step 3: Add new prototype for V7 functions**

Add after `void reportarIP();`:

```cpp
// [V7] Prototipos nuevos
void reportarIPyMAC();
void manejarConexionWiFiAsync();
void agregarEndpointStatus();
String obtenerMacAddress();
```

- [ ] **Step 4: Commit**

```bash
git add VerificacionHuellasV7.ino
git commit -m "feat(esp32): add V7 constants, struct fields, and prototypes"
```

---

### Task 3: Implement JSON Protocol Handler in MODO_CONFIG_BT

**Files:**
- Modify: `VerificacionHuellasV7.ino`

This is the core change. Modify the `MODO_CONFIG_BT` case to detect JSON and route to a new handler.

- [ ] **Step 1: Add JSON detection in MODO_CONFIG_BT**

Replace the existing `if (SerialBT.available())` block inside `case MODO_CONFIG_BT:` with dual detection:

```cpp
if (SerialBT.available()) {
    String cmd = SerialBT.readStringUntil('\n');
    cmd.trim();

    // ── [V7] Auto-detección: JSON vs texto ─────────────────
    if (cmd.length() > 0 && cmd.charAt(0) == '{') {
        // Tratar como JSON
        StaticJsonDocument<256> doc;
        DeserializationError error = deserializeJson(doc, cmd);

        if (error) {
            SerialBT.println("{\"status\":\"error\",\"message\":\"JSON inválido\"}");
            pantalla("ERROR JSON", "Formato inválido");
            sistema.msjDesde = millis();
            sistema.mostrandoMsj = true;
            sistema.estado = ESPERA_CONEXION;
        } else {
            const char* action = doc["action"] | "";

            if (strcmp(action, "config_network") == 0) {
                const char* ssid = doc["ssid"] | "";
                const char* password = doc["password"] | "";

                if (strlen(ssid) == 0) {
                    SerialBT.println("{\"status\":\"error\",\"message\":\"SSID requerido\"}");
                    pantalla("ERROR JSON", "SSID requerido");
                    sistema.msjDesde = millis();
                    sistema.mostrandoMsj = true;
                    sistema.estado = ESPERA_CONEXION;
                } else {
                    // Guardar credenciales
                    prefs.begin("cfg", false);
                    prefs.putString("ssid", ssid);
                    prefs.putString("pass", password);
                    prefs.end();
                    config.ssid = String(ssid);
                    config.pass = String(password);
                    config.wifiConfigurado = true;

                    // Obtener MAC
                    String mac = obtenerMacAddress();

                    // Responder con MAC
                    String jsonResp = "{\"status\":\"success\",\"mac_address\":\""
                                      + mac + "\",\"message\":\"Red configurada\"}";
                    SerialBT.println(jsonResp);

                    pantalla("CONFIG JSON OK", ("MAC: " + mac).c_str());

                    // Desconectar BT para que la app cierre su socket
                    SerialBT.end();

                    // Iniciar conexión WiFi asíncrona
                    sistema.wifiConnecting = true;
                    sistema.wifiStartTime = millis();
                    sistema.estado = MODO_CONECTANDO_WIFI;

                    Serial.println("[V7] JSON config_network OK. MAC: " + mac);
                }
            } else {
                SerialBT.println("{\"status\":\"error\",\"message\":\"Acción desconocida\"}");
                pantalla("ERROR JSON", "Acción: " + String(action));
                sistema.msjDesde = millis();
                sistema.mostrandoMsj = true;
                sistema.estado = ESPERA_CONEXION;
            }
        }
    }
    // ── V6 Legacy: comandos texto ──────────────────────────
    else if (cmd == "config") {
        pantalla("CONFIG ODOO", "Envie JSON:");
        SerialBT.println("OK_CONFIG");
        sistema.estado  = MODO_CONFIG_ODOO;
        sistema.timeout = millis();
    }
    else if (cmd == "wifi") {
        pantalla("CONFIG WIFI", "Envie SSID:");
        SerialBT.println("SSID:");
        sistema.estado  = MODO_WIFI_SSID;
        sistema.timeout = millis();
    }
    else {
        SerialBT.println("CMD_DESCONOCIDO");
        sistema.timeout = millis();
    }
}
```

- [ ] **Step 2: Add `obtenerMacAddress()` helper**

Add at the bottom of the file (before the last closing): 

```cpp
// ============================================================
//  [V7] OBTENER MAC ADDRESS
// ============================================================
String obtenerMacAddress() {
    uint64_t chipid = ESP.getEfuseMac();
    char mac[18];
    snprintf(mac, sizeof(mac), "%02X:%02X:%02X:%02X:%02X:%02X",
             (uint8_t)(chipid >> 40) & 0xFF,
             (uint8_t)(chipid >> 32) & 0xFF,
             (uint8_t)(chipid >> 24) & 0xFF,
             (uint8_t)(chipid >> 16) & 0xFF,
             (uint8_t)(chipid >> 8) & 0xFF,
             (uint8_t)chipid & 0xFF);
    return String(mac);
}
```

- [ ] **Step 3: Commit**

```bash
git add VerificacionHuellasV7.ino
git commit -m "feat(esp32): add JSON protocol handler with config_network and MAC response"
```

---

### Task 4: Implement MODO_CONECTANDO_WIFI (Non-blocking WiFi)

**Files:**
- Modify: `VerificacionHuellasV7.ino`

- [ ] **Step 1: Add the new state handler in the switch statement**

Add before `default:` in the main `switch (sistema.estado)`:

```cpp
// ----------------------------------------------------------
// [V7] MODO_CONECTANDO_WIFI — WiFi no bloqueante
// ----------------------------------------------------------
case MODO_CONECTANDO_WIFI:
    manejarConexionWiFiAsync();
    break;
```

- [ ] **Step 2: Implement `manejarConexionWiFiAsync()`**

Add at the bottom of the file:

```cpp
// ============================================================
//  [V7] CONEXIÓN WIFI ASÍNCRONA (no bloqueante)
// ============================================================
void manejarConexionWiFiAsync() {
    // Iniciar WiFi en la primera llamada
    static bool wifiStarted = false;
    if (!wifiStarted) {
        WiFi.begin(config.ssid.c_str(), config.pass.c_str());
        wifiStarted = true;
        pantalla("CONECTANDO WIFI", config.ssid.c_str());
        Serial.println("[V7] Conectando WiFi async: " + config.ssid);
    }

    // Parpadeo rápido del LED Wait
    if (millis() - sistema.lastBlink > 250) {
        sistema.lastBlink = millis();
        sistema.ledState = !sistema.ledState;
        digitalWrite(PIN_LED_WAIT, sistema.ledState);
    }

    // Verificar estado
    if (WiFi.status() == WL_CONNECTED) {
        wifiStarted = false;
        sistema.wifiConnecting = false;
        digitalWrite(PIN_LED_WAIT, LOW);

        pantalla("WIFI CONECTADO", WiFi.localIP().toString().c_str());
        Serial.println("[V7] WiFi conectado: " + WiFi.localIP().toString());

        // Iniciar servidor y endpoints
        server.on("/abrir", HTTP_GET, []() {
            if (!server.hasArg("token") || server.arg("token") != API_TOKEN) {
                server.send(401, "application/json", "{\"error\":\"No autorizado\"}");
                Serial.println("[SEGURIDAD] Intento de acceso sin token valido");
                return;
            }
            server.send(200, "application/json", "{\"ok\":true,\"msg\":\"Acceso concedido\"}");
            pantalla("SENAL ODOO", "Abriendo porton...");
            digitalWrite(PIN_LED_OK, HIGH);
            digitalWrite(PIN_RELAY_OK, HIGH);
            sistema.releActivo = true;
            sistema.releActivoDesde = millis();
            sistema.msjDesde = millis();
            sistema.mostrandoMsj = true;
            Serial.println("[RELE] Activado por Odoo");
        });

        agregarEndpointStatus();

        server.begin();
        Serial.println("[HTTP] Servidor iniciado en puerto 80");

        // Reportar IP + MAC a Odoo
        reportarIPyMAC();

        sistema.estado = ESPERA_CONEXION;
        sistema.msjDesde = millis();
        sistema.mostrandoMsj = true;
    }
    // Timeout
    else if (millis() - sistema.wifiStartTime > TIEMPO_WIFI_MAX) {
        wifiStarted = false;
        sistema.wifiConnecting = false;
        digitalWrite(PIN_LED_WAIT, LOW);
        digitalWrite(PIN_LED_ERROR, HIGH);

        pantalla("ERROR WIFI", "Timeout 30s");
        Serial.println("[V7] WiFi timeout");
        sistema.msjDesde = millis();
        sistema.mostrandoMsj = true;
        sistema.estado = ESPERA_CONEXION;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add VerificacionHuellasV7.ino
git commit -m "feat(esp32): implement non-blocking WiFi connection state (MODO_CONECTANDO_WIFI)"
```

---

### Task 5: Add GET /status Endpoint

**Files:**
- Modify: `VerificacionHuellasV7.ino`

- [ ] **Step 1: Implement `agregarEndpointStatus()`**

Add at the bottom of the file:

```cpp
// ============================================================
//  [V7] ENDPOINT GET /status
// ============================================================
void agregarEndpointStatus() {
    server.on("/status", HTTP_GET, []() {
        String wifiStatus;
        if (WiFi.status() == WL_CONNECTED) wifiStatus = "connected";
        else if (sistema.wifiConnecting) wifiStatus = "connecting";
        else wifiStatus = "disconnected";

        String json = "{\"mac\":\"" + obtenerMacAddress()
                      + "\",\"wifi\":\"" + wifiStatus + "\"";
        if (WiFi.status() == WL_CONNECTED) {
            json += ",\"ip\":\"" + WiFi.localIP().toString() + "\"";
        }
        json += ",\"uptime\":" + String(millis() / 1000) + "}";

        server.send(200, "application/json", json);
    });
}
```

- [ ] **Step 2: Commit**

```bash
git add VerificacionHuellasV7.ino
git commit -m "feat(esp32): add GET /status endpoint with MAC, WiFi, IP, uptime"
```

---

### Task 6: Update Auto-Discovery to Send MAC + IP

**Files:**
- Modify: `VerificacionHuellasV7.ino`

- [ ] **Step 1: Rename `reportarIP()` to `reportarIPyMAC()` and update payload**

Replace the existing `reportarIP()` function:

```cpp
// ============================================================
//  [V7] AUTO-DISCOVERY: Reporta IP + MAC a Odoo
//  Se llama al conectar WiFi (inicio o post-JSON config)
// ============================================================
void reportarIPyMAC() {
    if (WiFi.status() != WL_CONNECTED || config.odooUrl.length() == 0) return;

    HTTPClient http;
    http.begin(config.odooUrl);
    http.addHeader("Content-Type", "application/json");

    String mac = obtenerMacAddress();
    String payload = "{\"jsonrpc\":\"2.0\",\"method\":\"call\",\"params\":{"
                     "\"ip\":\"" + WiFi.localIP().toString()
                     + "\",\"mac_address\":\"" + mac + "\"}}";

    int code = http.POST(payload);
    if (code > 0) {
        Serial.println("[HTTP] IP+MAC reportada a Odoo. Codigo: " + String(code));
    } else {
        Serial.println("[HTTP] Error reportando IP+MAC. Codigo: " + String(code));
    }

    http.end();
}
```

- [ ] **Step 2: Update calls to the old `reportarIP()`**

In `conectarWiFi()`, replace `reportarIP();` with `reportarIPyMAC();` (the existing call in the WiFi-sync path within `conectarWiFi`).

Also in `MODO_CONFIG_ODOO` case, replace `if (WiFi.status() == WL_CONNECTED) reportarIP();` with `if (WiFi.status() == WL_CONNECTED) reportarIPyMAC();`.

- [ ] **Step 3: Commit**

```bash
git add VerificacionHuellasV7.ino
git commit -m "feat(esp32): enhance auto-discovery to send MAC + IP to Odoo"
```

---

### Task 7: Update OLED Display for V7 States and Messages

**Files:**
- Modify: `VerificacionHuellasV7.ino`

- [ ] **Step 1: Add OLED message for MODO_CONECTANDO_WIFI in `manejarAsincronos()`**

In `manejarAsincronos()`, add inside the "Limpiar mensaje" block — ensure error messages for V7 states also auto-clear after `TIEMPO_MENSAJE`:

No change needed — the existing `mostrandoMsj` logic already handles auto-clear for any state.

- [ ] **Step 2: Update `mostrarIdle()` for V7**

Add the MAC address display in the idle screen. Replace the existing `mostrarIdle()` function:

```cpp
// Pantalla de estado idle: IP + MAC + BT
void mostrarIdle() {
    display.clearDisplay();
    display.setTextSize(1);

    if (WiFi.status() == WL_CONNECTED) {
        display.setCursor(0, 0);
        display.println("SISTEMA LISTO");
        display.drawLine(0, 10, 127, 10, SSD1306_WHITE);

        display.setCursor(0, 14);
        display.println("IP:");
        display.println(WiFi.localIP().toString());

        // [V7] Mostrar MAC
        display.setCursor(0, 33);
        display.println("MAC:");
        display.println(obtenerMacAddress());

        display.drawLine(0, 53, 127, 53, SSD1306_WHITE);
        display.setCursor(0, 55);
        display.print("WiFi: ");
        display.println(config.ssid);

    } else if (sistema.estado != MODO_CONECTANDO_WIFI) {
        display.setCursor(0, 0);
        display.println("SISTEMA LISTO");
        display.drawLine(0, 10, 127, 10, SSD1306_WHITE);
        display.setCursor(0, 14);
        display.println("WiFi: No conectado");
        display.println("");
        display.println("Configure via BT:");
        display.println("  'wifi' o JSON");
    }

    display.display();
}
```

- [ ] **Step 3: Add `SerialBT.begin()` in setup for V7 reinit**

The `SerialBT.end()` call in Task 3 stops Bluetooth. On next reboot, `SerialBT.begin()` in `setup()` will restart it. No change needed — the existing `SerialBT.begin("ESP32_Seguro")` in `setup()` handles this.

- [ ] **Step 4: Initialize V7 system fields in setup**

In `setup()`, after existing system fields initialization, add:

```cpp
sistema.wifiConnecting = false;
sistema.macAddress = obtenerMacAddress();
```

- [ ] **Step 5: Commit**

```bash
git add VerificacionHuellasV7.ino
git commit -m "feat(esp32): update OLED idle screen with MAC and V7 states"
```

---

### Task 8: Final Integration — Wire Everything Together

**Files:**
- Modify: `VerificacionHuellasV7.ino`

- [ ] **Step 1: Add the Endpoints struct for /abrir route in `setup()`**

The `/abrir` endpoint is now set up inside `manejarConexionWiFiAsync()` (Task 4) instead of in `conectarWiFi()`. But we need to make sure that the original WiFi connection path (boot with saved creds via `conectarWiFi()`) still registers `/abrir`.

Update `conectarWiFi()` to NOT register `/abrir` anymore (since the async path handles it). Instead, after `conectarWiFi()` succeeds, call `server.on("/abrir"... )` and `agregarEndpointStatus()`.

Replace the entire `if (WiFi.status() == WL_CONNECTED)` block at the end of `conectarWiFi()` with:

```cpp
if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n[WiFi] Conectado: " + WiFi.localIP().toString());

    // ── Endpoint /abrir ──
    server.on("/abrir", HTTP_GET, []() {
        if (!server.hasArg("token") || server.arg("token") != API_TOKEN) {
            server.send(401, "application/json", "{\"error\":\"No autorizado\"}");
            Serial.println("[SEGURIDAD] Intento de acceso sin token valido");
            return;
        }
        server.send(200, "application/json", "{\"ok\":true,\"msg\":\"Acceso concedido\"}");
        pantalla("SENAL ODOO", "Abriendo porton...");
        digitalWrite(PIN_LED_OK, HIGH);
        digitalWrite(PIN_RELAY_OK, HIGH);
        sistema.releActivo = true;
        sistema.releActivoDesde = millis();
        sistema.msjDesde = millis();
        sistema.mostrandoMsj = true;
        Serial.println("[RELE] Activado por Odoo");
    });

    // ── [V7] Endpoint /status ──
    agregarEndpointStatus();

    server.begin();
    Serial.println("[HTTP] Servidor iniciado en puerto 80");

    reportarIPyMAC();

} else {
    pantalla("ERROR WIFI", "No conectado");
    Serial.println("[WiFi] Error al conectar");
    digitalWrite(PIN_LED_ERROR, HIGH);
    sistema.msjDesde = millis();
    sistema.mostrandoMsj = true;
}
```

- [ ] **Step 2: Verify full structure compiles conceptually**

Check that:
- All prototypes in the header match function definitions
- `obtenerMacAddress()` is defined before any usage (add forward declaration / reorder)
- All state constants are defined
- The switch statement covers all 5 states

- [ ] **Step 3: Commit**

```bash
git add VerificacionHuellasV7.ino
git commit -m "feat(esp32): final integration - unify /abrir and /status endpoints, wire async WiFi"
```

---

### Task 9: Update Documentation References

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/Contrato-ESP32.md`

- [ ] **Step 1: Update AGENTS.md**

Replace reference to `VerificacionHuellasV6.ino` with `VerificacionHuellasV7.ino` in the bullet point about the Arduino companion sketch.

- [ ] **Step 2: Update Contrato-ESP32.md**

Add V7 state machine section (or update the existing one) to document:
- New state `MODO_CONECTANDO_WIFI`
- JSON protocol commands and responses
- `GET /status` endpoint

- [ ] **Step 3: Commit**

```bash
git add AGENTS.md docs/Contrato-ESP32.md
git commit -m "docs: update references from V6 to V7 firmware"
```
