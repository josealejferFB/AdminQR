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
void reportarIP();

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

  // Nombre BT mantenido por compatibilidad con la app de configuración
  SerialBT.begin("ESP32_Seguro");

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
  sistema.releActivo   = false;
  sistema.mostrandoMsj = false;
  sistema.lastBlink    = 0;
  sistema.ledState     = false;

  // [V7] Inicializar campos nuevos
  sistema.wifiConnecting = false;
  sistema.macAddress     = obtenerMacAddress();

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
        pantalla("BT CONECTADO", "Comandos:", "  config", "  wifi");
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
                prefs.begin("cfg", false);
                prefs.putString("ssid", ssid);
                prefs.putString("pass", password);
                prefs.end();
                config.ssid = String(ssid);
                config.pass = String(password);
                config.wifiConfigurado = true;

                String mac = obtenerMacAddress();

                String jsonResp = "{\"status\":\"success\",\"mac_address\":\""
                                  + mac + "\",\"message\":\"Red configurada\"}";
                SerialBT.println(jsonResp);

                pantalla("CONFIG JSON OK", ("MAC: " + mac).c_str());

                SerialBT.end();

                sistema.wifiConnecting = true;
                sistema.wifiStartTime = millis();
                sistema.estado = MODO_CONECTANDO_WIFI;

                Serial.println("[V7] JSON config_network OK. MAC: " + mac);
              }
            } else {
              SerialBT.println("{\"status\":\"error\",\"message\":\"Acción desconocida\"}");
              pantalla("ERROR JSON", ("Acción: " + String(action)));
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
            if (WiFi.status() == WL_CONNECTED) reportarIP();
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

// Pantalla de estado idle: IP destacada con separador visual
void mostrarIdle() {
  display.clearDisplay();
  display.setTextSize(1);

  if (WiFi.status() == WL_CONNECTED) {
    // --- Título ---
    display.setCursor(0, 0);
    display.println("SISTEMA LISTO");

    // --- Separador horizontal ---
    display.drawLine(0, 10, 127, 10, SSD1306_WHITE);

    // --- IP destacada ---
    display.setCursor(0, 14);
    display.println("IP:");
    display.println(WiFi.localIP().toString());

    // --- Separador inferior ---
    display.drawLine(0, 39, 127, 39, SSD1306_WHITE);

    // --- Info Bluetooth al pie ---
    display.setCursor(0, 43);
    display.println("BT: ESP32_Seguro");
    display.print("WiFi: ");
    display.println(config.ssid);

  } else {
    // --- Sin WiFi ---
    display.setCursor(0, 0);
    display.println("SISTEMA LISTO");
    display.drawLine(0, 10, 127, 10, SSD1306_WHITE);
    display.setCursor(0, 14);
    display.println("WiFi: No conectado");
    display.println("");
    display.println("Configure via BT:");
    display.println("  Envie 'wifi'");
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
  WiFi.begin(config.ssid.c_str(), config.pass.c_str());

  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < TIEMPO_WIFI_MAX) {
    delay(500);
    Serial.print(".");
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n[WiFi] Conectado: " + WiFi.localIP().toString());

    // --------------------------------------------------------
    // Endpoint /abrir — Odoo llama aquí cuando la huella es OK
    // Requiere: GET /abrir?token=secreto123
    // --------------------------------------------------------
    server.on("/abrir", HTTP_GET, []() {
      // Validación del token de seguridad
      if (!server.hasArg("token") || server.arg("token") != API_TOKEN) {
        server.send(401, "application/json", "{\"error\":\"No autorizado\"}");
        Serial.println("[SEGURIDAD] Intento de acceso sin token valido");
        return;
      }

      // Respuesta inmediata a Odoo antes de activar el hardware
      server.send(200, "application/json", "{\"ok\":true,\"msg\":\"Acceso concedido\"}");

      // Feedback visual en pantalla y activación asíncrona del relé
      pantalla("SENAL ODOO", "Abriendo porton...");
      digitalWrite(PIN_LED_OK,   HIGH);
      digitalWrite(PIN_RELAY_OK, HIGH);
      sistema.releActivo      = true;
      sistema.releActivoDesde = millis();
      sistema.msjDesde        = millis();
      sistema.mostrandoMsj    = true;

      Serial.println("[RELE] Activado por Odoo");
    });

    server.begin();
    Serial.println("[HTTP] Servidor iniciado en puerto 80");

    // Reportar la IP al Odoo para que sepa dónde llamar
    reportarIP();

  } else {
    pantalla("ERROR WIFI", "No conectado");
    Serial.println("[WiFi] Error al conectar");
    sistema.msjDesde     = millis();
    sistema.mostrandoMsj = true;
  }
}

// Notifica a Odoo la IP actual del ESP32 (se llama al arrancar y al reconfigurar)
void reportarIP() {
  if (WiFi.status() != WL_CONNECTED || config.odooUrl.length() == 0) return;

  HTTPClient http;
  http.begin(config.odooUrl);
  http.addHeader("Content-Type", "application/json");

  String payload = "{\"jsonrpc\":\"2.0\",\"method\":\"call\",\"params\":{\"ip\":\""
                   + WiFi.localIP().toString() + "\"}}";

  int code = http.POST(payload);
  if (code > 0) Serial.println("[HTTP] IP reportada a Odoo. Codigo: " + String(code));
  else          Serial.println("[HTTP] Error reportando IP. Codigo: " + String(code));

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

    pantalla("WIFI CONECTADO", WiFi.localIP().toString().c_str());
    Serial.println("[V7] WiFi conectado: " + WiFi.localIP().toString());

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

    pantalla("ERROR WIFI", "Timeout 30s");
    Serial.println("[V7] WiFi timeout");
    sistema.msjDesde = millis();
    sistema.mostrandoMsj = true;
    sistema.estado = ESPERA_CONEXION;
  }
}
