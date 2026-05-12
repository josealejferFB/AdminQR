// ============================
//  SISTEMA VEHICULAR - VERSIÓN ESTABLE V5 (Auditado)
// ============================

#include <BluetoothSerial.h>
#include <Preferences.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <LittleFS.h>
#include <ArduinoJson.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <WebServer.h>

// ========== CONSTANTES ==========
#define TIEMPO_ACCESO_RELE     1000
#define TIEMPO_MENSAJE         3000
#define TIEMPO_WIFI_MAX        30000
#define PIN_LED_ERROR          4
#define PIN_LED_OK             19
#define PIN_LED_WAIT           23
#define PIN_RELAY_OK           0

// Token de seguridad para el WebServer (Recomendación de Auditoría)
#define API_TOKEN "secreto123" 

// ========== CONFIGURACIÓN PANTALLA ==========
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET    -1
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);

// ========== OBJETOS GLOBALES ==========
BluetoothSerial SerialBT;
Preferences prefs;
WebServer server(80);

// ========== ESTRUCTURAS COMPACTAS ==========
struct {
  String ssid, pass;
  String odooUrl;
  bool wifiConfigurado;
} config;

struct {
  uint8_t estado;
  unsigned long timeout;
  unsigned long lastBlink;
  bool ledState;
  String cedulaTemp;
  String usuarioActual;
  String ssidTemp; // Para no bloquear el flujo al pedir wifi
  
  // Variables para no usar delay() bloqueante en el relé y mensajes
  unsigned long releActivoDesde;
  bool releActivo;
  unsigned long msjErrorDesde;
  bool mostrandoError;
} sistema;

// ========== ESTADOS ==========
#define ESPERA_CONEXION  0
#define VERIFICA_MAC     1
#define ESPERA_HUELLA    2
#define MODO_AGREGAR     3
#define MODO_ELIMINAR    4
#define MODO_MODIFICAR   5
#define MODO_CONSULTAR   6
#define MODO_CONFIG_ODOO 7
#define MODO_WIFI_SSID   8
#define MODO_WIFI_PASS   9

// ========== ARCHIVOS ==========
const char* archivoUsers = "/u.json";

// ========== PROTOTIPOS ==========
void pantalla(const char* l1, const char* l2="", const char* l3="", const char* l4="");
void error(const char* msg);
void cargarConfig();
void guardarConfigOdoo(const char* url);
void construirUrlOdoo(const char* proto, const char* ip, int puerto);
void listarUsers();
int contarUsers();
void conectarWiFi();
void reportarIP();
void manejarAsincronos();

// ========== SETUP ==========
void setup() {
  Serial.begin(115200);
  
  cargarConfig();
  // El nombre se mantiene exacto por requerimiento de la app móvil
  SerialBT.begin("ESP32_Seguro"); 
  
  if (!display.begin(SSD1306_SWITCHCAPVCC, 0x3C)) {
    pinMode(PIN_LED_WAIT, OUTPUT);
    while(1) { 
      digitalWrite(PIN_LED_WAIT, HIGH); delay(100); 
      digitalWrite(PIN_LED_WAIT, LOW); delay(100); 
    }
  }
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  
  pinMode(PIN_LED_OK, OUTPUT);
  pinMode(PIN_LED_ERROR, OUTPUT);
  pinMode(PIN_LED_WAIT, OUTPUT);
  pinMode(PIN_RELAY_OK, OUTPUT);
  digitalWrite(PIN_LED_OK, LOW);
  digitalWrite(PIN_LED_ERROR, LOW);
  digitalWrite(PIN_LED_WAIT, LOW);
  digitalWrite(PIN_RELAY_OK, LOW);
  
  // Migración a LittleFS (Mejora de auditoría para evitar corrupción)
  if (!LittleFS.begin(true)) {
    error("LittleFS Error");
    LittleFS.format();
    LittleFS.begin(true);
  }
  if (!LittleFS.exists(archivoUsers)) {
    File f = LittleFS.open(archivoUsers, "w");
    if(f) { f.println("[]"); f.close(); }
  }
  
  conectarWiFi();
  
  sistema.estado = ESPERA_CONEXION;
  sistema.releActivo = false;
  sistema.mostrandoError = false;
  pantalla("SISTEMA OK", "BT: ESP32_Seguro", config.wifiConfigurado ? "WiFi: OK" : "WiFi: No", config.wifiConfigurado ? WiFi.localIP().toString().c_str() : "");
}

// ========== LOOP PRINCIPAL ==========
void loop() {
  server.handleClient();
  manejarAsincronos(); // Sustituye los delays bloqueantes
  
  switch(sistema.estado) {
    case ESPERA_CONEXION:
      if(millis() - sistema.lastBlink > 500) {
        sistema.lastBlink = millis();
        sistema.ledState = !sistema.ledState;
        digitalWrite(PIN_LED_WAIT, sistema.ledState);
      }
      
      if(SerialBT.hasClient()) {
        digitalWrite(PIN_LED_WAIT, HIGH);
        pantalla("CONECTADO", "Comandos:", "agregar eliminar", "listar config");
        // Limpiamos buffer entrante que pueda tener basura de la conexión anterior
        while(SerialBT.available()) SerialBT.read();
        sistema.estado = VERIFICA_MAC;
        sistema.timeout = millis();
      }
      break;
      
    case VERIFICA_MAC:
      if(!SerialBT.hasClient()) { 
        digitalWrite(PIN_LED_WAIT, LOW);
        pantalla("DESCONECTADO", "Esperando...");
        sistema.estado = ESPERA_CONEXION;
        break;
      }
      
      if(millis() - sistema.timeout > 30000) {
        pantalla("TIMEOUT");
        SerialBT.println("TIMEOUT");
        sistema.estado = ESPERA_CONEXION;
        break;
      }
      
      if(SerialBT.available()) {
        String msg = SerialBT.readStringUntil('\n');
        msg.trim();
        
        // Las cadenas de respuesta se mantienen INTACTAS
        if(msg == "agregar") {
          pantalla("AGREGAR", "Envie JSON:");
          SerialBT.println("AGREGAR");
          sistema.estado = MODO_AGREGAR;
          sistema.timeout = millis();
        }
        else if(msg == "eliminar") {
          pantalla("ELIMINAR", "Envie cedula:");
          SerialBT.println("OK_ELIMINAR");
          sistema.estado = MODO_ELIMINAR;
          sistema.timeout = millis();
        }
        else if(msg == "modificar") {
          pantalla("MODIFICAR", "Envie JSON modif:");
          SerialBT.println("OK_MODIFICAR");
          sistema.estado = MODO_MODIFICAR;
          sistema.timeout = millis();
        }
        else if(msg == "consultar") {
          pantalla("CONSULTAR", "Envie cedula:");
          SerialBT.println("OK_CONSULTAR");
          sistema.estado = MODO_CONSULTAR;
          sistema.timeout = millis();
        }
        else if(msg == "listar") {
          listarUsers();
          char buffer[32];
          snprintf(buffer, sizeof(buffer), "Usuarios: %d", contarUsers());
          pantalla("SISTEMA LISTO", buffer);
          sistema.estado = ESPERA_CONEXION;
        }
        else if(msg == "config") {
          pantalla("CONFIG ODOO", "Envie JSON:");
          SerialBT.println("OK_CONFIG");
          sistema.estado = MODO_CONFIG_ODOO;
          sistema.timeout = millis();
        }
        else if(msg == "wifi") {
          pantalla("CONFIG WIFI", "Envie SSID:");
          SerialBT.println("SSID:");
          sistema.estado = MODO_WIFI_SSID;
          sistema.timeout = millis();
        }
        else if(msg.length() > 10) {
          // Lógica optimizada para verificar MAC
          File f = LittleFS.open(archivoUsers, "r");
          bool macEncontrada = false;
          String cedulaEncontrada = "";
          
          if(f) {
            DynamicJsonDocument doc(4096); 
            if(deserializeJson(doc, f) == DeserializationError::Ok) {
              JsonArray arr = doc.as<JsonArray>();
              for(JsonObject obj : arr) {
                if(obj["m"].as<String>() == msg) {
                  macEncontrada = true;
                  cedulaEncontrada = obj["c"].as<String>();
                  break;
                }
              }
            }
            f.close();
          }

          if(macEncontrada) {
            sistema.usuarioActual = cedulaEncontrada;
            pantalla("MAC VALIDA", cedulaEncontrada.c_str(), "Solicitando huella");
            SerialBT.println("PEDIR_HUELLA");
            sistema.estado = ESPERA_HUELLA;
            sistema.timeout = millis();
          } else {
            pantalla("MAC NO REGISTRADA");
            SerialBT.println("MAC_INVALIDA");
            digitalWrite(PIN_LED_ERROR, HIGH);
            sistema.msjErrorDesde = millis();
            sistema.mostrandoError = true;
            
            // Manteniendo el flujo original estricto: desconecta si la MAC es mala
            SerialBT.disconnect();
            sistema.estado = ESPERA_CONEXION;
          }
        }
      }
      break;
      
    case ESPERA_HUELLA:
      if(millis() - sistema.lastBlink > 200) {
        sistema.lastBlink = millis();
        sistema.ledState = !sistema.ledState;
        digitalWrite(PIN_LED_WAIT, sistema.ledState);
      }
      
      if(!SerialBT.hasClient()) {
        sistema.estado = ESPERA_CONEXION;
        break;
      }
      
      if(millis() - sistema.timeout > 15000) {
        pantalla("TIMEOUT HUELLA");
        SerialBT.println("TIMEOUT");
        digitalWrite(PIN_LED_WAIT, LOW);
        SerialBT.disconnect();
        sistema.estado = ESPERA_CONEXION;
        break;
      }
      
      if(SerialBT.available()) {
        String resp = SerialBT.readStringUntil('\n');
        resp.trim();
        digitalWrite(PIN_LED_WAIT, LOW);
        
        if(resp == "HUELLA_OK") {
          pantalla("ACCESO OK", sistema.usuarioActual.c_str());
          SerialBT.println("ACCESO_OK");
          
          // Encendido de relé asíncrono
          digitalWrite(PIN_LED_OK, HIGH);
          digitalWrite(PIN_RELAY_OK, HIGH);
          sistema.releActivo = true;
          sistema.releActivoDesde = millis();
        } else {
          pantalla("HUELLA MAL", "Acceso denegado");
          SerialBT.println("ACCESO_NO");
          digitalWrite(PIN_LED_ERROR, HIGH);
          sistema.msjErrorDesde = millis();
          sistema.mostrandoError = true;
        }
        
        // Manteniendo el flujo original: desconexión después del chequeo de huella
        SerialBT.disconnect();
        sistema.estado = ESPERA_CONEXION;
      }
      break;
      
    case MODO_AGREGAR:
      if(!SerialBT.hasClient()) { sistema.estado = ESPERA_CONEXION; break; }
      if(millis() - sistema.timeout > 30000) { pantalla("TIMEOUT"); SerialBT.println("TIMEOUT"); sistema.estado = ESPERA_CONEXION; break; }
      
      if(SerialBT.available()) {
        String json = SerialBT.readStringUntil('\n');
        json.trim();
        
        DynamicJsonDocument doc(256);
        if(deserializeJson(doc, json) == DeserializationError::Ok) {
          // Auditoría: Validación estricta del payload
          if(doc.containsKey("cedula") && doc.containsKey("mac") && doc.containsKey("placa")) {
            String ced = doc["cedula"].as<String>();
            String mac = doc["mac"].as<String>();
            String pla = doc["placa"].as<String>();
            
            // Lógica unificada (Evita múltiples I/O innecesarios)
            File f = LittleFS.open(archivoUsers, "r");
            DynamicJsonDocument dDb(4096);
            bool existe = false;
            
            if(f) {
              deserializeJson(dDb, f);
              f.close();
              JsonArray arr = dDb.as<JsonArray>();
              for(JsonObject obj : arr) {
                if(obj["c"].as<String>() == ced) {
                  existe = true; break;
                }
              }
            }

            if(!existe) {
              JsonArray arr = dDb.as<JsonArray>();
              if (arr.isNull()) arr = dDb.to<JsonArray>();
              JsonObject nuevo = arr.createNestedObject();
              nuevo["c"] = ced;
              nuevo["m"] = mac;
              nuevo["p"] = pla;
              
              f = LittleFS.open(archivoUsers, "w");
              if(f) { serializeJson(dDb, f); f.close(); }
              
              pantalla("USUARIO OK", ced.c_str());
              SerialBT.println("GUARDADO_OK");
            } else {
              pantalla("ERROR", "Cedula existe");
              SerialBT.println("CEDULA_EXISTE");
            }
          } else {
             pantalla("ERROR", "Datos incompletos");
             SerialBT.println("JSON_ERROR");
          }
        } else {
          pantalla("ERROR", "JSON invalido");
          SerialBT.println("JSON_ERROR");
        }
        sistema.msjErrorDesde = millis();
        sistema.mostrandoError = true;
        sistema.estado = ESPERA_CONEXION;
      }
      break;
      
    case MODO_ELIMINAR:
      if(!SerialBT.hasClient()) { sistema.estado = ESPERA_CONEXION; break; }
      if(millis() - sistema.timeout > 30000) { pantalla("TIMEOUT"); SerialBT.println("TIMEOUT"); sistema.estado = ESPERA_CONEXION; break; }
      
      if(SerialBT.available()) {
        String ced = SerialBT.readStringUntil('\n');
        ced.trim();
        
        File f = LittleFS.open(archivoUsers, "r");
        DynamicJsonDocument doc(4096);
        bool eliminado = false;
        
        if(f) {
          if(deserializeJson(doc, f) == DeserializationError::Ok) {
            JsonArray arr = doc.as<JsonArray>();
            for(int i=0; i<arr.size(); i++) {
              if(arr[i]["c"].as<String>() == ced) {
                arr.remove(i);
                eliminado = true;
                break;
              }
            }
          }
          f.close();
        }

        if(eliminado) {
          f = LittleFS.open(archivoUsers, "w");
          if(f) { serializeJson(doc, f); f.close(); }
          pantalla("ELIMINADO OK", ced.c_str());
          SerialBT.println("ELIMINADO_OK");
        } else {
          pantalla("ERROR", "Cedula no existe");
          SerialBT.println("NO_EXISTE");
        }
        
        sistema.msjErrorDesde = millis();
        sistema.mostrandoError = true;
        sistema.estado = ESPERA_CONEXION;
      }
      break;

    // IMPLEMENTACIÓN NUEVA (Faltante en V4)
    case MODO_MODIFICAR:
      if(!SerialBT.hasClient()) { sistema.estado = ESPERA_CONEXION; break; }
      if(millis() - sistema.timeout > 30000) { pantalla("TIMEOUT"); SerialBT.println("TIMEOUT"); sistema.estado = ESPERA_CONEXION; break; }
      
      if(SerialBT.available()) {
        String json = SerialBT.readStringUntil('\n');
        json.trim();
        
        DynamicJsonDocument docIn(256);
        if(deserializeJson(docIn, json) == DeserializationError::Ok) {
          if(docIn.containsKey("cedula") && docIn.containsKey("mac") && docIn.containsKey("placa")) {
            String ced = docIn["cedula"].as<String>();
            String mac = docIn["mac"].as<String>();
            String pla = docIn["placa"].as<String>();
            
            File f = LittleFS.open(archivoUsers, "r");
            DynamicJsonDocument dDb(4096);
            bool modificado = false;
            
            if(f) {
              if(deserializeJson(dDb, f) == DeserializationError::Ok) {
                JsonArray arr = dDb.as<JsonArray>();
                for(JsonObject obj : arr) {
                  if(obj["c"].as<String>() == ced) {
                    obj["m"] = mac;
                    obj["p"] = pla;
                    modificado = true;
                    break;
                  }
                }
              }
              f.close();
            }

            if(modificado) {
              f = LittleFS.open(archivoUsers, "w");
              if(f) { serializeJson(dDb, f); f.close(); }
              pantalla("MODIFICADO OK", ced.c_str());
              SerialBT.println("MODIFICADO_OK"); // Nueva cadena esperada para éxito
            } else {
              pantalla("ERROR", "No existe");
              SerialBT.println("NO_EXISTE"); // Reutilizamos cadena de ELIMINAR para compatibilidad
            }
          } else {
             pantalla("ERROR", "Datos incompletos");
             SerialBT.println("JSON_ERROR");
          }
        } else {
          pantalla("ERROR", "JSON invalido");
          SerialBT.println("JSON_ERROR");
        }
        sistema.msjErrorDesde = millis();
        sistema.mostrandoError = true;
        sistema.estado = ESPERA_CONEXION;
      }
      break;

    // IMPLEMENTACIÓN NUEVA (Faltante en V4)
    case MODO_CONSULTAR:
      if(!SerialBT.hasClient()) { sistema.estado = ESPERA_CONEXION; break; }
      if(millis() - sistema.timeout > 30000) { pantalla("TIMEOUT"); SerialBT.println("TIMEOUT"); sistema.estado = ESPERA_CONEXION; break; }
      
      if(SerialBT.available()) {
        String ced = SerialBT.readStringUntil('\n');
        ced.trim();
        
        File f = LittleFS.open(archivoUsers, "r");
        DynamicJsonDocument doc(4096);
        bool encontrado = false;
        String respuesta = "";
        
        if(f) {
          if(deserializeJson(doc, f) == DeserializationError::Ok) {
            JsonArray arr = doc.as<JsonArray>();
            for(JsonObject obj : arr) {
              if(obj["c"].as<String>() == ced) {
                encontrado = true;
                // Retornamos el formato string separador con pipes idéntico a LISTAR
                respuesta = obj["c"].as<String>() + "|" + obj["m"].as<String>() + "|" + obj["p"].as<String>();
                break;
              }
            }
          }
          f.close();
        }

        if(encontrado) {
          pantalla("USUARIO", ced.c_str());
          SerialBT.println(respuesta); 
        } else {
          pantalla("ERROR", "No existe");
          SerialBT.println("NO_EXISTE");
        }
        
        sistema.msjErrorDesde = millis();
        sistema.mostrandoError = true;
        sistema.estado = ESPERA_CONEXION;
      }
      break;

    case MODO_CONFIG_ODOO:
      if(!SerialBT.hasClient()) { sistema.estado = ESPERA_CONEXION; break; }
      if(millis() - sistema.timeout > 30000) { pantalla("TIMEOUT"); SerialBT.println("TIMEOUT"); sistema.estado = ESPERA_CONEXION; break; }
      
      if(SerialBT.available()) {
        String json = SerialBT.readStringUntil('\n');
        json.trim();
        
        StaticJsonDocument<256> doc;
        if(deserializeJson(doc, json) == DeserializationError::Ok) {
          const char* proto = doc["protocolo"] | "http";
          const char* ip = doc["ip_odoo"] | "";
          int puerto = doc["port"] | 0;
          
          if(strlen(ip) > 0) {
            construirUrlOdoo(proto, ip, puerto);
            pantalla("CONFIG OK", "URL guardada");
            SerialBT.println("CONFIG_OK");
            if(WiFi.status() == WL_CONNECTED) reportarIP();
          } else {
            pantalla("ERROR", "IP requerida");
            SerialBT.println("ERROR_IP");
          }
        } else {
          pantalla("ERROR", "JSON invalido");
          SerialBT.println("JSON_ERROR");
        }
        sistema.msjErrorDesde = millis();
        sistema.mostrandoError = true;
        sistema.estado = ESPERA_CONEXION;
      }
      break;

    // ELIMINADO EL WHILE BLOQUEANTE DE WIFI
    case MODO_WIFI_SSID:
      if(!SerialBT.hasClient()) { sistema.estado = ESPERA_CONEXION; break; }
      if(millis() - sistema.timeout > 60000) { pantalla("TIMEOUT WIFI"); SerialBT.println("TIMEOUT"); sistema.estado = ESPERA_CONEXION; break; }
      
      if(SerialBT.available()) {
        sistema.ssidTemp = SerialBT.readStringUntil('\n');
        sistema.ssidTemp.trim();
        pantalla("CONFIG WIFI", "Envie Password:");
        SerialBT.println("PASS:"); // Cadena exacta esperada
        sistema.estado = MODO_WIFI_PASS;
        sistema.timeout = millis();
      }
      break;

    case MODO_WIFI_PASS:
      if(!SerialBT.hasClient()) { sistema.estado = ESPERA_CONEXION; break; }
      if(millis() - sistema.timeout > 60000) { pantalla("TIMEOUT WIFI"); SerialBT.println("TIMEOUT"); sistema.estado = ESPERA_CONEXION; break; }
      
      if(SerialBT.available()) {
        String pass = SerialBT.readStringUntil('\n');
        pass.trim();
        
        prefs.begin("cfg", false);
        prefs.putString("ssid", sistema.ssidTemp);
        prefs.putString("pass", pass);
        prefs.end();
        
        pantalla("WIFI GUARDADO", "Reiniciando...");
        SerialBT.println("REINICIANDO"); // Cadena exacta esperada
        delay(1000); 
        ESP.restart();
      }
      break;
  }
  delay(10);
}

// ========== MANEJADOR DE EVENTOS ASÍNCRONOS ==========
void manejarAsincronos() {
  unsigned long currentMillis = millis();
  
  // 1. Apagado del relé de forma asíncrona
  if (sistema.releActivo && (currentMillis - sistema.releActivoDesde > TIEMPO_ACCESO_RELE)) {
    digitalWrite(PIN_LED_OK, LOW);
    digitalWrite(PIN_RELAY_OK, LOW);
    sistema.releActivo = false;
  }
  
  // 2. Limpieza de mensajes de error en pantalla para volver a estado idle
  if (sistema.mostrandoError && (currentMillis - sistema.msjErrorDesde > TIEMPO_MENSAJE)) {
    sistema.mostrandoError = false;
    digitalWrite(PIN_LED_ERROR, LOW);
    if(sistema.estado == ESPERA_CONEXION) {
      pantalla("SISTEMA OK", "BT: ESP32_Seguro", config.wifiConfigurado ? "WiFi: OK" : "WiFi: No", config.wifiConfigurado ? WiFi.localIP().toString().c_str() : "");
    }
  }
}

// ========== FUNCIONES DE PANTALLA ==========

void pantalla(const char* l1, const char* l2, const char* l3, const char* l4) {
  display.clearDisplay();
  display.setCursor(0,0);
  if(strlen(l1)) display.println(l1);
  if(strlen(l2)) display.println(l2);
  if(strlen(l3)) display.println(l3);
  if(strlen(l4)) display.println(l4);
  display.display();
}

void error(const char* msg) {
  pantalla("ERROR", msg);
}

// ========== FUNCIONES DE CONFIGURACIÓN ==========

void cargarConfig() {
  prefs.begin("cfg", true);
  config.ssid = prefs.getString("ssid", "");
  config.pass = prefs.getString("pass", "");
  config.odooUrl = prefs.getString("odoo_url", "");
  config.wifiConfigurado = (config.ssid.length() > 0);
  prefs.end();
  
  if(config.odooUrl.length() == 0) {
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
  if(puerto > 0 && !((String(proto) == "http" && puerto == 80) || (String(proto) == "https" && puerto == 443))) {
    url += ":" + String(puerto);
  }
  url += "/api/update_esp_ip";
  guardarConfigOdoo(url.c_str());
  Serial.println("URL: " + url);
}

// ========== FUNCIONES DE USUARIOS ==========

int contarUsers() {
  File f = LittleFS.open(archivoUsers, "r");
  if(!f) return 0;
  
  DynamicJsonDocument doc(4096);
  deserializeJson(doc, f);
  f.close();
  return doc.as<JsonArray>().size();
}

void listarUsers() {
  File f = LittleFS.open(archivoUsers, "r");
  if(!f) { SerialBT.println("ERROR"); return; }
  
  DynamicJsonDocument doc(4096);
  deserializeJson(doc, f);
  f.close();
  
  int total = doc.as<JsonArray>().size();
  SerialBT.println("=== USUARIOS (" + String(total) + ") ==="); // Formato idéntico esperado
  
  JsonArray arr = doc.as<JsonArray>();
  for(JsonObject obj : arr) {
    SerialBT.println(obj["c"].as<String>() + "|" + obj["m"].as<String>() + "|" + obj["p"].as<String>()); // Formato idéntico
  }
  SerialBT.println("==================");
}

// ========== FUNCIONES DE RED ==========

void conectarWiFi() {
  if(!config.wifiConfigurado) {
    pantalla("SIN WIFI", "Use comando 'wifi'");
    return;
  }
  
  pantalla("Conectando...", config.ssid.c_str());
  WiFi.begin(config.ssid.c_str(), config.pass.c_str());
  
  unsigned long start = millis();
  while(WiFi.status() != WL_CONNECTED && millis() - start < TIEMPO_WIFI_MAX) {
    delay(500);
    Serial.print(".");
  }
  
  if(WiFi.status() == WL_CONNECTED) {
    Serial.println("\nWiFi OK");
    Serial.println(WiFi.localIP());
    reportarIP();
    
    server.on("/abrir", HTTP_GET, []() {
      // Seguridad Agregada: API Token para prevenir accesos no autorizados
      if (!server.hasArg("token") || server.arg("token") != API_TOKEN) {
        server.send(401, "application/json", "{\"error\":\"No autorizado\"}");
        return;
      }
      
      server.send(200, "application/json", "{\"ok\":true}");
      
      // Accionamiento asíncrono del relé
      digitalWrite(PIN_LED_OK, HIGH);
      digitalWrite(PIN_RELAY_OK, HIGH);
      sistema.releActivo = true;
      sistema.releActivoDesde = millis();
    });
    server.begin();
  } else {
    pantalla("ERROR WIFI", "No conectado");
    sistema.msjErrorDesde = millis();
    sistema.mostrandoError = true;
  }
}

void reportarIP() {
  if(WiFi.status() != WL_CONNECTED || config.odooUrl.length() == 0) return;
  
  HTTPClient http;
  http.begin(config.odooUrl);
  http.addHeader("Content-Type", "application/json");
  
  String json = "{\"jsonrpc\":\"2.0\",\"method\":\"call\",\"params\":{\"ip\":\"" + WiFi.localIP().toString() + "\"}}";
  
  int code = http.POST(json);
  if(code > 0) Serial.println("IP reportada");
  else Serial.println("Error reportando IP");
  
  http.end();
}
