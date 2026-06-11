// ============================================================
//  SISTEMA VEHICULAR - VERSIÓN 8
//  Apertura de portón: señal HTTP desde Odoo → relé
//  Bluetooth: Protocolo dual (JSON + texto V6 legacy)
//  Novedades V8:
//    - Comandos JSON: config_network, config_ip, set_bt_name, set_hostname
//    - IP estática configurable vía BT (config_ip)
//    - Nombre Bluetooth configurable vía set_bt_name
//    - Hostname DHCP configurable vía set_hostname
//    - BT se reinicia automáticamente tras conexión WiFi
//    - GET /status expone static_ip, bt_name y hostname
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
  bool   wifiConfigurado;
  // [V8] IP estática
  String staticIp;
  String staticGateway;
  String staticNetmask;
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
  String        ssidTemp;         // SSID temporal durante config WiFi vía BT

  // [V7] Control asíncrono de WiFi
  unsigned long wifiStartTime;
  bool          wifiConnecting;

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
} sistema;

// ========== ESTADOS ==========
#define ESPERA_CONEXION   0   // Idle: BT parpadeando, WebServer activo
#define MODO_CONFIG_BT    1   // BT conectado: espera comando "config" o "wifi"
#define MODO_CONFIG_ODOO  2   // Espera JSON con datos de Odoo
#define MODO_WIFI_SSID    3   // Espera SSID por BT
#define MODO_WIFI_PASS    4   // Espera contraseña WiFi por BT
#define MODO_CONECTANDO_WIFI 5   // [V7] WiFi.begin no bloqueante

// ========== PROTOTIPOS ==========
void pantalla(const char* l1, const char* l2 = "", const char* l3 = "", const char* l4 = "");
void mostrarIdle();
void cargarConfig();
void guardarConfigOdoo(const char* url);
void construirUrlOdoo(const char* proto, const char* ip, int puerto);
void conectarWiFi();
// [V7] Prototipos nuevos
void reportarIPyMAC();
void manejarConexionWiFiAsync();
void agregarEndpointStatus();
String obtenerMacAddress();

void manejarAsincronos();

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
      if (millis() - sistema.lastBlink > 500) {
        sistema.lastBlink = millis();
        sistema.ledState  = !sistema.ledState;
        digitalWrite(PIN_LED_WAIT, sistema.ledState);
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
    // MODO_CONFIG_BT — Solo acepta: "config" | "wifi"
    // ----------------------------------------------------------
    case MODO_CONFIG_BT:
      if (!SerialBT.hasClient()) {
        digitalWrite(PIN_LED_WAIT, LOW);
        mostrarIdle();
        sistema.estado = ESPERA_CONEXION;
        break;
      }

      if (millis() - sistema.timeout > 30000) {
        SerialBT.println("TIMEOUT");
        pantalla("TIMEOUT BT");
        sistema.msjDesde     = millis();
        sistema.mostrandoMsj = true;
        sistema.estado       = ESPERA_CONEXION;
        break;
      }

      if (SerialBT.available()) {
        String cmd = SerialBT.readStringUntil('\n');
        cmd.trim();

        // ── [V7] Auto-detección: JSON vs texto ─────────────────
        if (cmd.length() > 0 && cmd.charAt(0) == '{') {
          StaticJsonDocument<512> doc;
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
                prefs.begin("cfg", false);
                prefs.putString("ssid", ssid);
                prefs.putString("pass", password);
                prefs.end();
                config.ssid = String(ssid);
                config.pass = String(password);
                config.wifiConfigurado = true;

                const char* btName = doc["bt_name"] | "";
                if (strlen(btName) > 0) {
                  prefs.begin("cfg", false);
                  prefs.putString("bt_name", btName);
                  prefs.end();
                  config.btName = String(btName);
                }

                const char* hostname = doc["hostname"] | "";
                if (strlen(hostname) > 0) {
                  prefs.begin("cfg", false);
                  prefs.putString("hostname", hostname);
                  prefs.end();
                  config.hostname = String(hostname);
                }

                // Reiniciar BT con el nuevo nombre inmediatamente
                if (strlen(btName) > 0) {
                  SerialBT.flush();
                  delay(100);
                  SerialBT.end();
                  SerialBT.begin(config.btName.c_str());
                }

                String mac = obtenerMacAddress();

                {
                  StaticJsonDocument<128> resp;
                  resp["status"] = "success";
                  resp["mac_address"] = mac;
                  resp["message"] = "Red configurada";
                  String jsonResp;
                  serializeJson(resp, jsonResp);
                  SerialBT.println(jsonResp);
                }
                SerialBT.flush();
                delay(200);

                pantalla("CONFIG JSON OK", ("MAC: " + mac).c_str());

                SerialBT.end();

                sistema.wifiConnecting = true;
                sistema.wifiStartTime = millis();
                sistema.estado = MODO_CONECTANDO_WIFI;

                Serial.println("[V7] JSON config_network OK. MAC: " + mac);
              }
            } else if (strcmp(action, "config_ip") == 0) {
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
                SerialBT.flush();
                delay(200);
                pantalla("IP GUARDADA", "Reiniciando...");
                delay(1000);
                ESP.restart();
              }
              sistema.msjDesde = millis();
              sistema.mostrandoMsj = true;
              sistema.estado = ESPERA_CONEXION;
            } else if (strcmp(action, "set_bt_name") == 0) {
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
                SerialBT.flush();
                delay(200);
                pantalla("BT NAME OK", name);

                SerialBT.end();
                SerialBT.begin(config.btName.c_str());
                Serial.println("[V8] BT name cambiado a: " + config.btName);
              }
              sistema.msjDesde = millis();
              sistema.mostrandoMsj = true;
              sistema.estado = ESPERA_CONEXION;
            } else if (strcmp(action, "set_hostname") == 0) {
              const char* hostname = doc["hostname"] | "";

              if (strlen(hostname) == 0) {
                SerialBT.println("{\"status\":\"error\",\"message\":\"Hostname requerido\"}");
                pantalla("ERROR", "Hostname vacío");
              } else {
                prefs.begin("cfg", false);
                prefs.putString("hostname", hostname);
                prefs.end();
                config.hostname = String(hostname);

                SerialBT.println("{\"status\":\"success\",\"message\":\"Hostname configurado\"}");
                SerialBT.flush();
                pantalla("HOSTNAME OK", hostname);
                delay(1000);
                ESP.restart();
              }
            } else {
              SerialBT.println("{\"status\":\"error\",\"message\":\"Acción desconocida\"}");
              pantalla("ERROR JSON", (String("Acción: ") + action).c_str());
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
      break;

    // ----------------------------------------------------------
    // MODO_CONFIG_ODOO — Recibe JSON: {protocolo, ip_odoo, port}
    // ----------------------------------------------------------
    case MODO_CONFIG_ODOO:
      if (!SerialBT.hasClient()) { sistema.estado = ESPERA_CONEXION; break; }

      if (millis() - sistema.timeout > 30000) {
        SerialBT.println("TIMEOUT");
        sistema.estado = ESPERA_CONEXION;
        break;
      }

      if (SerialBT.available()) {
        String json = SerialBT.readStringUntil('\n');
        json.trim();

        StaticJsonDocument<256> doc;
        if (deserializeJson(doc, json) == DeserializationError::Ok) {
          const char* proto  = doc["protocolo"] | "http";
          const char* ip     = doc["ip_odoo"]   | "";
          int         puerto = doc["port"]       | 0;

          if (strlen(ip) > 0) {
            construirUrlOdoo(proto, ip, puerto);
            pantalla("CONFIG OK", "URL guardada");
            SerialBT.println("CONFIG_OK");
            if (WiFi.status() == WL_CONNECTED) reportarIPyMAC();
          } else {
            pantalla("ERROR", "IP requerida");
            SerialBT.println("ERROR_IP");
          }
        } else {
          pantalla("ERROR", "JSON invalido");
          SerialBT.println("JSON_ERROR");
        }

        sistema.msjDesde     = millis();
        sistema.mostrandoMsj = true;
        sistema.estado       = ESPERA_CONEXION;
      }
      break;

    // ----------------------------------------------------------
    // MODO_WIFI_SSID — Recibe SSID por BT
    // ----------------------------------------------------------
    case MODO_WIFI_SSID:
      if (!SerialBT.hasClient()) { sistema.estado = ESPERA_CONEXION; break; }

      if (millis() - sistema.timeout > 60000) {
        SerialBT.println("TIMEOUT");
        sistema.estado = ESPERA_CONEXION;
        break;
      }

      if (SerialBT.available()) {
        sistema.ssidTemp = SerialBT.readStringUntil('\n');
        sistema.ssidTemp.trim();
        pantalla("CONFIG WIFI", "Envie Password:");
        SerialBT.println("PASS:");
        sistema.estado  = MODO_WIFI_PASS;
        sistema.timeout = millis();
      }
      break;

    // ----------------------------------------------------------
    // MODO_WIFI_PASS — Recibe contraseña, guarda y reinicia
    // ----------------------------------------------------------
    case MODO_WIFI_PASS:
      if (!SerialBT.hasClient()) { sistema.estado = ESPERA_CONEXION; break; }

      if (millis() - sistema.timeout > 60000) {
        SerialBT.println("TIMEOUT");
        sistema.estado = ESPERA_CONEXION;
        break;
      }

      if (SerialBT.available()) {
        String pass = SerialBT.readStringUntil('\n');
        pass.trim();

        prefs.begin("cfg", false);
        prefs.putString("ssid", sistema.ssidTemp);
        prefs.putString("pass", pass);
        prefs.end();

        pantalla("WIFI GUARDADO", "Reiniciando...");
        SerialBT.println("REINICIANDO");
        delay(1000);
        ESP.restart();
      }
      break;

    // ----------------------------------------------------------
    // [V7] MODO_CONECTANDO_WIFI — WiFi no bloqueante
    // ----------------------------------------------------------
    case MODO_CONECTANDO_WIFI:
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
  config.wifiConfigurado = (config.ssid.length() > 0);
  config.staticIp      = prefs.getString("static_ip", "");
  config.staticGateway = prefs.getString("static_gw", "");
  config.staticNetmask = prefs.getString("static_mask", "");
  config.btName        = prefs.getString("bt_name", "ESP32_Seguro");
  config.hostname      = prefs.getString("hostname", "");
  prefs.end();

  // URL por defecto si no hay configuración guardada
  if (config.odooUrl.length() == 0) {
    config.odooUrl = "http://192.168.1.100:8059/api/update_esp_ip";
  }
}

void guardarConfigOdoo(const char* url) {
  prefs.begin("cfg", false);
  prefs.putString("odoo_url", url);
  prefs.end();
  config.odooUrl = String(url);
}

void construirUrlOdoo(const char* proto, const char* ip, int puerto) {
  String url = String(proto) + "://" + String(ip);

  // Omitir puerto si es el estándar del protocolo
  bool puertoEstandar = ((String(proto) == "http"  && puerto == 80) ||
                         (String(proto) == "https" && puerto == 443));
  if (puerto > 0 && !puertoEstandar) {
    url += ":" + String(puerto);
  }

  url += "/api/update_esp_ip";
  guardarConfigOdoo(url.c_str());
  Serial.println("[CONFIG] URL Odoo: " + url);
}

// ============================================================
//  RED — WiFi + WebServer + Reporte de IP
// ============================================================
void conectarWiFi() {
  if (!config.wifiConfigurado) {
    pantalla("SIN WIFI", "Conecte por BT", "y use 'wifi'");
    return;
  }

  pantalla("Conectando WiFi...", config.ssid.c_str());

  if (config.staticIp.length() > 0) {
    IPAddress ip, gw, mask;
    if (ip.fromString(config.staticIp.c_str()) &&
        gw.fromString(config.staticGateway.c_str()) &&
        mask.fromString(config.staticNetmask.c_str())) {
      WiFi.config(ip, gw, mask);
      Serial.println("[V8] IP estática: " + config.staticIp);
    }
  }

  WiFi.setHostname(config.hostname.c_str());
  WiFi.begin(config.ssid.c_str(), config.pass.c_str());

  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < TIEMPO_WIFI_MAX) {
    delay(500);
    Serial.print(".");
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n[WiFi] Conectado: " + WiFi.localIP().toString());

    if (!sistema.handlersRegistered) {
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

      sistema.handlersRegistered = true;
    }

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
  static bool wifiStarted = false;
  if (!wifiStarted) {
    WiFi.setHostname(config.hostname.c_str());
    WiFi.begin(config.ssid.c_str(), config.pass.c_str());
    wifiStarted = true;
    pantalla("CONECTANDO WIFI", config.ssid.c_str());
    Serial.println("[V7] Conectando WiFi async: " + config.ssid);
  }

  if (millis() - sistema.lastBlink > 250) {
    sistema.lastBlink = millis();
    sistema.ledState = !sistema.ledState;
    digitalWrite(PIN_LED_WAIT, sistema.ledState);
  }

  if (WiFi.status() == WL_CONNECTED) {
    wifiStarted = false;
    sistema.wifiConnecting = false;
    digitalWrite(PIN_LED_WAIT, LOW);

    SerialBT.end();
    delay(500);
    SerialBT.begin(config.btName.c_str());
    Serial.println("[V8] BT reiniciado como: " + config.btName);

    pantalla("WIFI CONECTADO", WiFi.localIP().toString().c_str());
    Serial.println("[V7] WiFi conectado: " + WiFi.localIP().toString());

    if (!sistema.handlersRegistered) {
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

    server.begin();
    Serial.println("[HTTP] Servidor iniciado en puerto 80");

    reportarIPyMAC();

    sistema.estado = ESPERA_CONEXION;
    sistema.msjDesde = millis();
    sistema.mostrandoMsj = true;
  }
  else if (millis() - sistema.wifiStartTime > TIEMPO_WIFI_MAX) {
    wifiStarted = false;
    sistema.wifiConnecting = false;
    digitalWrite(PIN_LED_WAIT, LOW);
    digitalWrite(PIN_LED_ERROR, HIGH);

    SerialBT.begin(config.btName.c_str());
    Serial.println("[V8] BT reiniciado tras fallo WiFi: " + config.btName);

    pantalla("ERROR WIFI", "Timeout 30s");
    Serial.println("[V7] WiFi timeout");
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
    StaticJsonDocument<256> doc;
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
    doc["static_ip"] = config.staticIp;
    doc["bt_name"] = config.btName;
    doc["hostname"] = config.hostname;

    String json;
    serializeJson(doc, json);
    server.send(200, "application/json", json);
  });
}
