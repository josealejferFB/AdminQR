// ============================================================
//  SISTEMA VEHICULAR - VERSIÓN 10
//  Apertura de portón: señal HTTP desde Odoo → relé
//  Bluetooth: Solo protocolo JSON (config_network, set_bt_name, set_hostname)
//  Novedades V10:
//    - Eliminado protocolo texto legacy (wifi SSID/PASS manual)
//    - Auto-desconexión BT tras completar configuración
//    - Helper volverAEspera() para transiciones limpias
//    - NVS single-open para config_network
//    - Eliminados MODO_WIFI_SSID y MODO_WIFI_PASS
//  Legado V9:
//    - Auto-discovery: ESP32 reporta IP a Odoo tras WiFi connect
//    - IoT token configurable vía config_network
//    - Comandos JSON: config_network, set_bt_name, set_hostname
// ============================================================

#include <BluetoothSerial.h>
#include <Preferences.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <ArduinoJson.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <WebServer.h>

// ========== CONSTANTES ==========
#define TIEMPO_ACCESO_RELE  1000   // ms que permanece activo el relé
#define TIEMPO_MENSAJE      3000   // ms que se muestra un mensaje de estado
#define TIEMPO_WIFI_MAX     30000  // ms máximo esperando conexión WiFi
#define PIN_LED_ERROR       4
#define PIN_LED_OK          19
#define PIN_LED_WAIT        23
#define PIN_RELAY_OK        0

// Token de seguridad compartido con Odoo.
// Odoo debe incluirlo en cada petición: GET /abrir?token=secreto123
// Sin token válido, el ESP32 responde 401 No autorizado.
#define API_TOKEN "secreto123"

// Token compartido con Odoo para auto-discovery IoT.
// El ESP32 lo envía en cada reporte de IP para que Odoo
// valide que la petición viene de un dispositivo autorizado.
#define IOT_TOKEN "iot_secret_2024"

// ========== PANTALLA OLED ==========
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET   -1
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);

// ========== OBJETOS GLOBALES ==========
BluetoothSerial SerialBT;
Preferences     prefs;
WebServer       server(80);

// ========== CONFIGURACIÓN PERSISTENTE ==========
struct {
  String ssid, pass;
  String odooUrl;
  String iotToken;
  bool   wifiConfigurado;
  // [V8] Nombre Bluetooth configurable
  String btName;
  // [V8] Nombre de host para DHCP
  String hostname;
} config;

// ========== ESTADO DEL SISTEMA ==========
struct {
  uint8_t       estado;
  unsigned long timeout;
  unsigned long lastBlink;
  bool          ledState;

  // [V7] Control asíncrono de WiFi
  unsigned long wifiStartTime;
  bool          wifiConnecting;
  uint8_t       wifiRetryCount;
  bool          wifiStarted;        // [V10] Movido de static local a global para BUG-6

  // [V7] Almacenar última respuesta MAC para /status
  String        macAddress;

  // Control asíncrono del relé
  unsigned long releActivoDesde;
  bool          releActivo;

  // Control asíncrono de mensajes en pantalla
  unsigned long msjDesde;
  bool          mostrandoMsj;

  // [V8] Evita acumular handlers HTTP duplicados en RAM
  bool          handlersRegistered;

  // [report_ip] Flag para re-envío asíncrono de IP a Odoo
  bool          reportPending;
} sistema;

// ========== ESTADOS ==========
#define ESPERA_CONEXION      0   // Idle: BT parpadeando, WebServer activo
#define MODO_CONFIG_BT       1   // BT conectado: espera comando JSON
#define MODO_CONECTANDO_WIFI 5   // [V7] WiFi.begin no bloqueante

// ========== PROTOTIPOS ==========
void pantalla(const char* l1, const char* l2 = "", const char* l3 = "", const char* l4 = "");
void mostrarIdle();
void cargarConfig();
void conectarWiFi();
// [V7] Prototipos nuevos
void reportarIPyMAC();
void manejarConexionWiFiAsync();
void agregarEndpointStatus();
String obtenerMacAddress();

void manejarAsincronos();
void volverAEspera(const char* msg1, const char* msg2 = "");

// ============================================================
//  SETUP
// ============================================================
void setup() {
  Serial.begin(115200);

  cargarConfig();

  // Nombre BT desde configuración guardada (por defecto "ESP32_Seguro")
  SerialBT.begin(config.btName.c_str());

  // Inicialización pantalla OLED
  if (!display.begin(SSD1306_SWITCHCAPVCC, 0x3C)) {
    // Si la pantalla falla, parpadear LED indefinidamente como señal de error
    pinMode(PIN_LED_WAIT, OUTPUT);
    while (1) {
      digitalWrite(PIN_LED_WAIT, HIGH); delay(100);
      digitalWrite(PIN_LED_WAIT, LOW);  delay(100);
    }
  }
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);

  // Pines de salida
  pinMode(PIN_LED_OK,    OUTPUT);
  pinMode(PIN_LED_ERROR, OUTPUT);
  pinMode(PIN_LED_WAIT,  OUTPUT);
  pinMode(PIN_RELAY_OK,  OUTPUT);
  digitalWrite(PIN_LED_OK,    LOW);
  digitalWrite(PIN_LED_ERROR, LOW);
  digitalWrite(PIN_LED_WAIT,  LOW);
  digitalWrite(PIN_RELAY_OK,  LOW);

  // Estado inicial
  sistema.estado       = ESPERA_CONEXION;
  sistema.releActivo         = false;
  sistema.mostrandoMsj       = false;
  sistema.lastBlink          = 0;
  sistema.ledState           = false;
  sistema.handlersRegistered = false;

  // [V7] Inicializar campos nuevos
  sistema.wifiConnecting = false;
  sistema.reportPending  = false;
  sistema.wifiRetryCount = 0;
  sistema.wifiStarted    = false;
  sistema.macAddress     = obtenerMacAddress();

  // Hostname único por dispositivo (basado en MAC)
  if (config.hostname.length() == 0) {
    String mac = sistema.macAddress;
    mac.replace(":", "");
    mac.toLowerCase();
    config.hostname = "esp32-" + mac.substring(6);  // ej: esp32-4a5b6c
  }

  conectarWiFi();
  mostrarIdle();
}

// ============================================================
//  LOOP PRINCIPAL
// ============================================================
void loop() {
  server.handleClient();   // Atiende peticiones HTTP de Odoo
  manejarAsincronos();     // Relé y mensajes de pantalla sin delay()

  switch (sistema.estado) {

    // ----------------------------------------------------------
    // ESPERA_CONEXION — Idle. Parpadeo BT, WebServer activo.
    // ----------------------------------------------------------
    case ESPERA_CONEXION:
      // [report_ip] Envío asíncrono de IP a Odoo (no bloquea el loop)
      if (sistema.reportPending) {
        sistema.reportPending = false;
        reportarIPyMAC();
        if (sistema.estado == ESPERA_CONEXION) mostrarIdle();
      }

      if (millis() - sistema.lastBlink > 500) {
        sistema.lastBlink = millis();
        sistema.ledState  = !sistema.ledState;
        digitalWrite(PIN_LED_WAIT, sistema.ledState);
      }

      // Reconexión WiFi automática si hay credenciales guardadas (max 3 intentos)
      if (config.wifiConfigurado && WiFi.status() != WL_CONNECTED &&
          !sistema.wifiConnecting && !SerialBT.hasClient()) {
        sistema.wifiRetryCount++;
        if (sistema.wifiRetryCount > 3) {
          pantalla("WIFI ERROR", "Sin conexion", "Reintente via BT");
          digitalWrite(PIN_LED_ERROR, HIGH);
          break;
        }
        pantalla("RECONECTANDO", ("Intento " + String(sistema.wifiRetryCount) + "/3").c_str());
        sistema.wifiConnecting = true;
        sistema.wifiStartTime = millis();
        sistema.estado = MODO_CONECTANDO_WIFI;
        break;
      }

      if (SerialBT.hasClient()) {
        digitalWrite(PIN_LED_WAIT, HIGH);
        pantalla("BT CONECTADO", "Comandos:", "  config / wifi", "  o JSON");
        // Limpiar basura de conexión anterior
        while (SerialBT.available()) SerialBT.read();
        sistema.estado  = MODO_CONFIG_BT;
        sistema.timeout = millis();
      }
      break;

    // ----------------------------------------------------------
    // MODO_CONFIG_BT — Solo acepta comandos JSON
    // ----------------------------------------------------------
    case MODO_CONFIG_BT:
      if (!SerialBT.hasClient()) {
        digitalWrite(PIN_LED_WAIT, LOW);
        mostrarIdle();
        sistema.estado = ESPERA_CONEXION;
        break;
      }

      if (millis() - sistema.timeout > 30000) {
        SerialBT.println("{\"status\":\"error\",\"message\":\"Timeout BT\"}");
        volverAEspera("TIMEOUT BT");
        break;
      }

      if (SerialBT.available()) {
        String cmd = SerialBT.readStringUntil('\n');
        cmd.trim();

        // Solo JSON (debe empezar con '{')
        if (cmd.length() == 0 || cmd.charAt(0) != '{') {
          SerialBT.println("{\"status\":\"error\",\"message\":\"Solo JSON\"}");
          sistema.timeout = millis();  // Reiniciar timeout
          break;
        }

        JsonDocument doc;
        DeserializationError error = deserializeJson(doc, cmd);

        if (error) {
          SerialBT.println("{\"status\":\"error\",\"message\":\"JSON inválido\"}");
          volverAEspera("ERROR JSON", "Formato inválido");
          break;
        }

        const char* action = doc["action"] | "";

        // ── config_network ──────────────────────────────────
        if (strcmp(action, "config_network") == 0) {
          const char* ssid = doc["ssid"] | "";
          const char* password = doc["password"] | "";

          if (strlen(ssid) == 0) {
            SerialBT.println("{\"status\":\"error\",\"message\":\"SSID requerido\"}");
            volverAEspera("ERROR JSON", "SSID requerido");
            break;
          }

          // [V10] Single NVS open/close (BUG-13 fix)
          prefs.begin("cfg", false);
          prefs.putString("ssid", ssid);
          prefs.putString("pass", password);

          const char* btName = doc["bt_name"] | "";
          if (strlen(btName) > 0) {
            prefs.putString("bt_name", btName);
            config.btName = String(btName);
          }
          const char* hostname = doc["hostname"] | "";
          if (strlen(hostname) > 0) {
            prefs.putString("hostname", hostname);
            config.hostname = String(hostname);
          }
          const char* iotToken = doc["iot_token"] | "";
          if (strlen(iotToken) > 0) {
            prefs.putString("iot_token", iotToken);
            config.iotToken = String(iotToken);
          }
          const char* odooUrl = doc["odoo_url"] | "";
          if (strlen(odooUrl) > 0) {
            prefs.putString("odoo_url", odooUrl);
            config.odooUrl = String(odooUrl);
          }
          prefs.end();

          config.ssid = String(ssid);
          config.pass = String(password);
          config.wifiConfigurado = true;

          // Respuesta "processing" — la app sabe que estamos trabajando
          {
            JsonDocument resp;
            resp["status"] = "processing";
            resp["message"] = "Conectando a WiFi...";
            String jsonResp;
            serializeJson(resp, jsonResp);
            SerialBT.println(jsonResp);
          }
          SerialBT.flush();
          delay(200);

          pantalla("CONFIG JSON", "Conectando...");

          sistema.wifiConnecting = true;
          sistema.wifiRetryCount = 0;
          sistema.wifiStarted    = false;  // Reset for clean start
          sistema.wifiStartTime  = millis();
          sistema.estado         = MODO_CONECTANDO_WIFI;

          Serial.println("[V10] JSON config_network OK.");

        // ── set_bt_name ─────────────────────────────────────
        } else if (strcmp(action, "set_bt_name") == 0) {
          const char* name = doc["name"] | "";
          if (strlen(name) == 0) {
            SerialBT.println("{\"status\":\"error\",\"message\":\"Nombre requerido\"}");
            volverAEspera("ERROR", "Nombre BT vacío");
          } else {
            prefs.begin("cfg", false);
            prefs.putString("bt_name", name);
            prefs.end();
            config.btName = String(name);

            SerialBT.println("{\"status\":\"success\",\"message\":\"Nombre BT actualizado\"}");
            pantalla("BT NAME OK", name);
            Serial.println("[V10] BT name cambiado a: " + config.btName);

            // Auto-desconexión tras config completada
            volverAEspera("BT NAME OK", name);

            // Reiniciar BT con nuevo nombre
            SerialBT.end();
            SerialBT.begin(config.btName.c_str());
          }

        // ── set_hostname ────────────────────────────────────
        } else if (strcmp(action, "set_hostname") == 0) {
          const char* hostname = doc["hostname"] | "";
          if (strlen(hostname) == 0) {
            SerialBT.println("{\"status\":\"error\",\"message\":\"Hostname requerido\"}");
            volverAEspera("ERROR", "Hostname vacío");
          } else {
            prefs.begin("cfg", false);
            prefs.putString("hostname", hostname);
            prefs.end();
            config.hostname = String(hostname);

            SerialBT.println("{\"status\":\"success\",\"message\":\"Hostname configurado\"}");
            SerialBT.flush();
            delay(200);
            pantalla("HOSTNAME OK", hostname);
            delay(1000);
            ESP.restart();
          }

        // ── report_ip ───────────────────────────────────────
        } else if (strcmp(action, "report_ip") == 0) {
          if (WiFi.status() != WL_CONNECTED) {
            SerialBT.println("{\"status\":\"error\",\"message\":\"WiFi no conectado\"}");
            volverAEspera("ERROR", "WiFi no conectado");
          } else {
            SerialBT.println("{\"status\":\"success\",\"message\":\"Reportando IP a Odoo...\"}");
            sistema.reportPending = true;
            // Auto-desconexión tras respuesta
            volverAEspera("REPORTANDO IP", "A Odoo...");
          }

        // ── Acción desconocida ───────────────────────────────
        } else {
          SerialBT.println("{\"status\":\"error\",\"message\":\"Acción desconocida\"}");
          volverAEspera("ERROR JSON", (String("Acción: ") + action).c_str());
        }
      }
      break;

    // ----------------------------------------------------------
    // [V7] MODO_CONECTANDO_WIFI — WiFi no bloqueante
    // ----------------------------------------------------------
    case MODO_CONECTANDO_WIFI:
      while (SerialBT.available()) SerialBT.read();
      manejarConexionWiFiAsync();
      break;
  }

  delay(10);
}

// ============================================================
//  MANEJADOR ASÍNCRONO — Relé y mensajes sin delay()
// ============================================================
void manejarAsincronos() {
  unsigned long now = millis();

  // Apagar relé después de TIEMPO_ACCESO_RELE ms
  if (sistema.releActivo && (now - sistema.releActivoDesde > TIEMPO_ACCESO_RELE)) {
    digitalWrite(PIN_LED_OK,   LOW);
    digitalWrite(PIN_RELAY_OK, LOW);
    sistema.releActivo = false;
    Serial.println("[RELE] Desactivado");
  }

  // Limpiar mensaje de pantalla y LED de error tras TIEMPO_MENSAJE ms
  if (sistema.mostrandoMsj && (now - sistema.msjDesde > TIEMPO_MENSAJE)) {
    sistema.mostrandoMsj = false;
    digitalWrite(PIN_LED_ERROR, LOW);
    if (sistema.estado == ESPERA_CONEXION) mostrarIdle();
  }
}

// ============================================================
//  HELPER: Transición limpia a ESPERA_CONEXION con disconnect BT
// ============================================================
void volverAEspera(const char* msg1, const char* msg2) {
  SerialBT.flush();
  delay(200);
  SerialBT.disconnect();
  pantalla(msg1, msg2);
  sistema.msjDesde     = millis();
  sistema.mostrandoMsj = true;
  sistema.estado       = ESPERA_CONEXION;
}

// ============================================================
//  HELPER: Registra endpoints HTTP una sola vez
// ============================================================
void registrarEndpoints() {
  if (sistema.handlersRegistered) return;

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
  sistema.handlersRegistered = true;
}

// ============================================================
//  PANTALLA OLED
// ============================================================
void pantalla(const char* l1, const char* l2, const char* l3, const char* l4) {
  display.clearDisplay();
  display.setCursor(0, 0);
  if (strlen(l1)) display.println(l1);
  if (strlen(l2)) display.println(l2);
  if (strlen(l3)) display.println(l3);
  if (strlen(l4)) display.println(l4);
  display.display();
}

// Pantalla de estado idle: hostname + IP + MAC + WiFi
void mostrarIdle() {
  display.clearDisplay();
  display.setTextSize(1);

  display.setCursor(0, 0);
  display.println("SISTEMA LISTO");
  display.setCursor(0, 8);
  display.println(config.hostname);
  display.drawLine(0, 17, 127, 17, SSD1306_WHITE);

  if (WiFi.status() == WL_CONNECTED) {
    display.setCursor(0, 20);
    display.print("IP ");
    display.println(WiFi.localIP().toString());
    display.setCursor(0, 28);
    display.print("MAC ");
    display.println(obtenerMacAddress());
    display.setCursor(0, 36);
    display.print("WiFi ");
    display.println(config.ssid);

  } else if (sistema.estado != MODO_CONECTANDO_WIFI) {
    display.setCursor(0, 20);
    display.println("WiFi No conectado");
    display.setCursor(0, 28);
    display.println("Configure via BT:");
    display.setCursor(0, 36);
    display.println("  'wifi' o JSON");
  }

  display.display();
}

// ============================================================
//  CONFIGURACIÓN PERSISTENTE (Preferences)
// ============================================================
void cargarConfig() {
  prefs.begin("cfg", true);
  config.ssid            = prefs.getString("ssid", "");
  config.pass            = prefs.getString("pass", "");
  config.odooUrl         = prefs.getString("odoo_url", "");
  config.iotToken        = prefs.getString("iot_token", IOT_TOKEN);
  config.wifiConfigurado = (config.ssid.length() > 0);
  config.btName        = prefs.getString("bt_name", "ESP32_Seguro");
  config.hostname      = prefs.getString("hostname", "");
  prefs.end();

  // URL por defecto si no hay configuración guardada
  if (config.odooUrl.length() == 0) {
    config.odooUrl = "http://192.168.1.100:8059/api/update_esp_ip";
  }
}

// ============================================================
//  RED — WiFi + WebServer + Reporte de IP
// ============================================================
void conectarWiFi() {
  if (!config.wifiConfigurado) {
    pantalla("SIN WIFI", "Configure por BT");
    return;
  }

  pantalla("Conectando WiFi...", config.ssid.c_str());

  WiFi.setHostname(config.hostname.c_str());
  WiFi.begin(config.ssid.c_str(), config.pass.c_str());

  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < TIEMPO_WIFI_MAX) {
    delay(500);
    Serial.print(".");
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n[WiFi] Conectado: " + WiFi.localIP().toString());

    registrarEndpoints();

    server.begin();
    Serial.println("[HTTP] Servidor iniciado en puerto 80");

    reportarIPyMAC();

  } else {
    pantalla("ERROR WIFI", "No conectado");
    Serial.println("[WiFi] Error al conectar");
    sistema.msjDesde     = millis();
    sistema.mostrandoMsj = true;
  }
}

// ============================================================
//  [V7] AUTO-DISCOVERY: Reporta IP + MAC a Odoo
//  Se llama al conectar WiFi (inicio o post-JSON config)
// ============================================================
void reportarIPyMAC() {
  if (WiFi.status() != WL_CONNECTED || config.odooUrl.length() == 0) return;

  JsonDocument doc;
  doc["jsonrpc"] = "2.0";
  JsonObject params = doc["params"].to<JsonObject>();
  params["iot_token"]    = config.iotToken;
  params["mac_address"]  = obtenerMacAddress();
  params["ip"]           = WiFi.localIP().toString();
  params["hostname"]     = config.hostname;

  String payload;
  serializeJson(doc, payload);

  // Retry con backoff: 3 intentos, 5s / 15s / 30s
  int delays[] = {5000, 15000, 30000};
  int maxRetries = 3;

  for (int i = 0; i < maxRetries; i++) {
    HTTPClient http;
    http.begin(config.odooUrl);
    http.addHeader("Content-Type", "application/json");
    http.setTimeout(10000);

    int code = http.POST(payload);
    if (code == 200) {
      Serial.println("[HTTP] Auto-reporte OK a Odoo. Intento " + String(i + 1));
      http.end();
      return;
    }

    Serial.println("[HTTP] Error en auto-reporte (intento " + String(i + 1) +
                   "/" + String(maxRetries) + "). Codigo: " + String(code));
    http.end();

    if (i < maxRetries - 1) delay(delays[i]);
  }

  Serial.println("[HTTP] Auto-reporte fallo tras " + String(maxRetries) + " intentos");
}

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

// ============================================================
//  [V7] CONEXIÓN WIFI ASÍNCRONA (no bloqueante)
// ============================================================
void manejarConexionWiFiAsync() {
  if (!sistema.wifiStarted) {
    WiFi.setHostname(config.hostname.c_str());
    WiFi.begin(config.ssid.c_str(), config.pass.c_str());
    sistema.wifiStarted = true;
    pantalla("CONECTANDO WIFI", config.ssid.c_str());
    Serial.println("[V10] Conectando WiFi async: " + config.ssid);
  }

  if (millis() - sistema.lastBlink > 250) {
    sistema.lastBlink = millis();
    sistema.ledState = !sistema.ledState;
    digitalWrite(PIN_LED_WAIT, sistema.ledState);
  }

  if (WiFi.status() == WL_CONNECTED) {
    sistema.wifiStarted    = false;
    sistema.wifiConnecting = false;
    sistema.wifiRetryCount = 0;
    digitalWrite(PIN_LED_WAIT, LOW);

    String mac = obtenerMacAddress();
    JsonDocument resp;
    resp["status"] = "success";
    resp["mac_address"] = mac;
    resp["message"] = "WiFi conectado";
    String jsonResp;
    serializeJson(resp, jsonResp);

    if (SerialBT.hasClient()) {
      SerialBT.println(jsonResp);
      SerialBT.flush();
      delay(200);
      SerialBT.disconnect();  // [V10] Auto-desconexión limpia
    }

    // Reiniciar BT solo para aplicar posible cambio de nombre y asegurar estado
    SerialBT.end();
    delay(200);
    SerialBT.begin(config.btName.c_str());
    Serial.println("[V10] BT reiniciado como: " + config.btName);

    pantalla("WIFI CONECTADO", WiFi.localIP().toString().c_str());
    Serial.println("[V10] WiFi conectado: " + WiFi.localIP().toString());

    registrarEndpoints();

    server.begin();
    Serial.println("[HTTP] Servidor iniciado en puerto 80");

    reportarIPyMAC();

    sistema.estado = ESPERA_CONEXION;
    sistema.msjDesde = millis();
    sistema.mostrandoMsj = true;
  }
  else if (millis() - sistema.wifiStartTime > TIEMPO_WIFI_MAX) {
    sistema.wifiStarted    = false;
    sistema.wifiConnecting = false;
    digitalWrite(PIN_LED_WAIT, LOW);
    digitalWrite(PIN_LED_ERROR, HIGH);

    WiFi.disconnect();

    JsonDocument resp;
    resp["status"] = "error";
    resp["message"] = "Fallo de conexión WiFi";
    String jsonResp;
    serializeJson(resp, jsonResp);

    if (SerialBT.hasClient()) {
      SerialBT.println(jsonResp);
      SerialBT.flush();
      delay(200);
      SerialBT.disconnect();  // [V10] Auto-desconexión en error
    }

    Serial.println("[V10] Fallo WiFi. BT desconectado.");

    pantalla("ERROR WIFI", "Timeout 30s");
    sistema.msjDesde = millis();
    sistema.mostrandoMsj = true;
    sistema.estado = ESPERA_CONEXION;
  }
}

// ============================================================
//  [V7] ENDPOINT GET /status
// ============================================================
void agregarEndpointStatus() {
  server.on("/status", HTTP_GET, []() {
    JsonDocument doc;
    doc["mac"] = obtenerMacAddress();

    if (WiFi.status() == WL_CONNECTED) {
      doc["wifi"] = "connected";
      doc["ip"] = WiFi.localIP().toString();
    } else if (sistema.wifiConnecting) {
      doc["wifi"] = "connecting";
    } else {
      doc["wifi"] = "disconnected";
    }

    doc["uptime"] = millis() / 1000;
    doc["bt_name"] = config.btName;
    doc["hostname"] = config.hostname;

    String json;
    serializeJson(doc, json);
    server.send(200, "application/json", json);
  });
}
