# ESP32 V8 Firmware Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add static IP, configurable BT name, and BT auto-reconnect after WiFi to the ESP32 firmware.

**Architecture:** Modifications to `VerificacionHuellasV8.ino` adding 2 new JSON commands (`config_ip`, `set_bt_name`), persistent storage in Preferences, static IP applied in `conectarWiFi()`, and BT restart after async WiFi connection.

**Tech Stack:** Arduino ESP32, ArduinoJson v7, Preferences, WiFi

---

### Task A1: Almacenamiento persistente — claves IP + BT name

**Files:**
- Modify: `VerificacionHuellasV8.ino`

- [ ] **1.1: Modificar struct config**

Añadir campos al struct `config`:
```cpp
  // [V8] IP estática
  String staticIp;
  String staticGateway;
  String staticNetmask;
  // [V8] Nombre Bluetooth configurable
  String btName;
```

- [ ] **1.2: Modificar `cargarConfig()`**

Añadir después de `config.wifiConfigurado = ...`:
```cpp
  config.staticIp  = prefs.getString("static_ip", "");
  config.staticGateway = prefs.getString("static_gw", "");
  config.staticNetmask = prefs.getString("static_mask", "");
  config.btName = prefs.getString("bt_name", "ESP32_Seguro");
```

- [ ] **1.3: Commit**

```bash
git add VerificacionHuellasV8.ino
git commit -m "feat(esp32): add V8 persistent config fields for static IP and BT name"
```

---

### Task A2: Boot con IP estática

**Files:**
- Modify: `VerificacionHuellasV8.ino`

- [ ] **2.1: Modificar `conectarWiFi()`**

Antes de `WiFi.begin()`, añadir:
```cpp
  if (config.staticIp.length() > 0) {
    IPAddress ip, gw, mask;
    if (ip.fromString(config.staticIp.c_str()) &&
        gw.fromString(config.staticGateway.c_str()) &&
        mask.fromString(config.staticNetmask.c_str())) {
      WiFi.config(ip, gw, mask);
      Serial.println("[V8] IP estática: " + config.staticIp);
    }
  }
```

- [ ] **2.2: Commit**

```bash
git add VerificacionHuellasV8.ino
git commit -m "feat(esp32): apply static IP config before WiFi.begin on boot"
```

---

### Task A3: Comando JSON `config_ip`

**Files:**
- Modify: `VerificacionHuellasV8.ino`

- [ ] **3.1: Añadir handler en MODO_CONFIG_BT**

Dentro del bloque `if/else if` de acciones JSON, añadir:
```cpp
  else if (strcmp(action, "config_ip") == 0) {
    const char* ip      = doc["ip"]      | "";
    const char* gateway = doc["gateway"] | "";
    const char* netmask = doc["netmask"] | "";

    if (strlen(ip) == 0 || strlen(gateway) == 0 || strlen(netmask) == 0) {
      SerialBT.println("{\"status\":\"error\",\"message\":\"IP, gateway y netmask requeridos\"}");
      pantalla("ERROR IP", "Campos incompletos");
    } else {
      prefs.begin("cfg", false);
      prefs.putString("static_ip",  ip);
      prefs.putString("static_gw",  gateway);
      prefs.putString("static_mask", netmask);
      prefs.end();

      SerialBT.println("{\"status\":\"success\",\"message\":\"IP estática configurada\"}");
      pantalla("IP GUARDADA", "Reiniciando...");
      delay(1000);
      ESP.restart();
    }
    sistema.msjDesde = millis();
    sistema.mostrandoMsj = true;
    sistema.estado = ESPERA_CONEXION;
  }
```

- [ ] **3.2: Commit**

```bash
git add VerificacionHuellasV8.ino
git commit -m "feat(esp32): add config_ip JSON command for static IP configuration"
```

---

### Task A4: Comando JSON `set_bt_name`

**Files:**
- Modify: `VerificacionHuellasV8.ino`

- [ ] **4.1: Añadir handler en MODO_CONFIG_BT**

```cpp
  else if (strcmp(action, "set_bt_name") == 0) {
    const char* name = doc["name"] | "";

    if (strlen(name) == 0) {
      SerialBT.println("{\"status\":\"error\",\"message\":\"Nombre requerido\"}");
      pantalla("ERROR", "Nombre BT vacío");
    } else {
      prefs.begin("cfg", false);
      prefs.putString("bt_name", name);
      prefs.end();
      config.btName = String(name);

      SerialBT.println("{\"status\":\"success\",\"message\":\"Nombre BT actualizado\"}");
      pantalla("BT NAME OK", name);

      SerialBT.end();
      SerialBT.begin(config.btName.c_str());
      Serial.println("[V8] BT name cambiado a: " + config.btName);
    }
    sistema.msjDesde = millis();
    sistema.mostrandoMsj = true;
    sistema.estado = ESPERA_CONEXION;
  }
```

- [ ] **4.2: `config_network` aceptar `bt_name` opcional**

En el handler existente de `config_network`, después de guardar ssid/pass, añadir:
```cpp
    const char* btName = doc["bt_name"] | "";
    if (strlen(btName) > 0) {
      prefs.putString("bt_name", btName);
      config.btName = String(btName);
    }
```

- [ ] **4.3: Commit**

```bash
git add VerificacionHuellasV8.ino
git commit -m "feat(esp32): add set_bt_name command and bt_name in config_network"
```

---

### Task A5: BT auto-reconnect después de WiFi

**Files:**
- Modify: `VerificacionHuellasV8.ino`

- [ ] **5.1: Modificar `manejarConexionWiFiAsync()`**

Cuando WiFi se conecta (`WiFi.status() == WL_CONNECTED`), antes de cambiar estado a `ESPERA_CONEXION`, añadir:
```cpp
    SerialBT.end();
    delay(500);
    SerialBT.begin(config.btName.c_str());
    Serial.println("[V8] BT reiniciado como: " + config.btName);
```

- [ ] **5.2: Commit**

```bash
git add VerificacionHuellasV8.ino
git commit -m "feat(esp32): auto-restart BT after WiFi connection with configured name"
```

---

### Task A6: Actualizar `GET /status` con IP estática y BT name

**Files:**
- Modify: `VerificacionHuellasV8.ino`

- [ ] **6.1: Modificar la respuesta de status**

```cpp
    json += ",\"static_ip\":\"" + config.staticIp + "\"";
    json += ",\"bt_name\":\"" + config.btName + "\"";
```

- [ ] **6.2: Commit**

```bash
git add VerificacionHuellasV8.ino
git commit -m "feat(esp32): expose static_ip and bt_name in /status endpoint"
```

---

### Task A7: Actualizar documentación

**Files:**
- Modify: `docs/Contrato-ESP32.md`
- Modify: `AGENTS.md`

- [ ] **7.1: Actualizar Contrato-ESP32.md**

Añadir sección de comandos V8 (`config_ip`, `set_bt_name`), actualizar estados y timeouts.

- [ ] **7.2: Commit**

```bash
git add docs/Contrato-ESP32.md AGENTS.md
git commit -m "docs: add V8 firmware documentation"
```
