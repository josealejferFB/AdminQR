// ============================
//  SISTEMA VEHICULAR - VERSIÓN ESTABLE
// ============================

#include <BluetoothSerial.h>
#include <Preferences.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <SPIFFS.h>
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

// ========== ARCHIVOS ==========
const char* archivoUsers = "/u.json";

// ========== PROTOTIPOS ==========
void pantalla(const char* l1, const char* l2="", const char* l3="", const char* l4="");
void error(const char* msg);
void cargarConfig();
void guardarConfigOdoo(const char* url);
void construirUrlOdoo(const char* proto, const char* ip, int puerto);
void guardarUsuario(String ced, String mac, String pla);
bool buscarMac(String mac, String& ced, String& pla);
bool buscarCedula(String ced, String& mac, String& pla);
void listarUsers();
int contarUsers();
void conectarWiFi();
void reportarIP();

// ========== SETUP ==========
void setup() {
  Serial.begin(115200);
  
  cargarConfig();
  SerialBT.begin("ESP32_Seguro");
  
  if (!display.begin(SSD1306_SWITCHCAPVCC, 0x3C)) {
    pinMode(PIN_LED_WAIT, OUTPUT);
    while(1) { 
      digitalWrite(PIN_LED_WAIT, HIGH); 
      delay(100); 
      digitalWrite(PIN_LED_WAIT, LOW); 
      delay(100); 
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
  
  if (!SPIFFS.begin(true)) error("SPIFFS");
  if (!SPIFFS.exists(archivoUsers)) {
    File f = SPIFFS.open(archivoUsers, "w");
    if(f) { f.println("[]"); f.close(); }
  }
  
  conectarWiFi();
  
  sistema.estado = ESPERA_CONEXION;
  pantalla("SISTEMA OK", "BT: ESP32_Seguro", config.wifiConfigurado ? "WiFi: OK" : "WiFi: No", "");
}

// ========== LOOP PRINCIPAL ==========
void loop() {
  server.handleClient();
  
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
        sistema.estado = ESPERA_CONEXION;
        break;
      }
      
      if(SerialBT.available()) {
        String msg = SerialBT.readStringUntil('\n');
        msg.trim();
        
        if(msg == "agregar") {
          pantalla("AGREGAR", "Envie JSON:");
          SerialBT.println("OK_AGREGAR");
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
          pantalla("MODIFICAR", "Envie cedula:");
          SerialBT.println("OK_MODIFICAR");
          sistema.cedulaTemp = "";
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
          pantalla("CONFIG ODOO", "Envie JSON:", "{'proto':'http'", "'ip':'192.168.1.1'");
          SerialBT.println("OK_CONFIG");
          sistema.estado = MODO_CONFIG_ODOO;
          sistema.timeout = millis();
        }
        else if(msg == "wifi") {
          pantalla("CONFIG WIFI", "SSID:");
          SerialBT.println("SSID:");
          while(!SerialBT.available()) delay(50);
          String ssid = SerialBT.readStringUntil('\n');
          ssid.trim();
          
          pantalla("CONFIG WIFI", "Password:");
          SerialBT.println("PASS:");
          while(!SerialBT.available()) delay(50);
          String pass = SerialBT.readStringUntil('\n');
          pass.trim();
          
          prefs.begin("cfg", false);
          prefs.putString("ssid", ssid);
          prefs.putString("pass", pass);
          prefs.end();
          
          pantalla("WIFI GUARDADO", "Reiniciando...");
          SerialBT.println("REINICIANDO");
          delay(2000);
          ESP.restart();
        }
        else if(msg.length() > 10) {
          String ced, pla;
          if(buscarMac(msg, ced, pla)) {
            sistema.usuarioActual = ced;
            pantalla("MAC VALIDA", ced.c_str(), "Solicitando huella");
            SerialBT.println("PEDIR_HUELLA");
            sistema.estado = ESPERA_HUELLA;
            sistema.timeout = millis();
          } else {
            pantalla("MAC NO REGISTRADA");
            SerialBT.println("MAC_INVALIDA");
            digitalWrite(PIN_LED_ERROR, HIGH);
            delay(2000);
            digitalWrite(PIN_LED_ERROR, LOW);
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
        sistema.estado = ESPERA_CONEXION;
        break;
      }
      
      if(SerialBT.available()) {
        String resp = SerialBT.readStringUntil('\n');
        resp.trim();
        digitalWrite(PIN_LED_WAIT, LOW);
        
        if(resp == "HUELLA_OK") {
          digitalWrite(PIN_LED_OK, HIGH);
          digitalWrite(PIN_RELAY_OK, HIGH);
          pantalla("ACCESO OK", sistema.usuarioActual.c_str());
          SerialBT.println("ACCESO_OK");
          delay(TIEMPO_ACCESO_RELE);
          digitalWrite(PIN_LED_OK, LOW);
          digitalWrite(PIN_RELAY_OK, LOW);
        } else {
          digitalWrite(PIN_LED_ERROR, HIGH);
          pantalla("HUELLA MAL", "Acceso denegado");
          SerialBT.println("ACCESO_NO");
          delay(TIEMPO_MENSAJE);
          digitalWrite(PIN_LED_ERROR, LOW);
        }
        
        SerialBT.disconnect();
        sistema.estado = ESPERA_CONEXION;
      }
      break;
      
    case MODO_AGREGAR:
      if(!SerialBT.hasClient()) { sistema.estado = ESPERA_CONEXION; break; }
      if(millis() - sistema.timeout > 30000) { pantalla("TIMEOUT"); sistema.estado = ESPERA_CONEXION; break; }
      
      if(SerialBT.available()) {
        String json = SerialBT.readStringUntil('\n');
        json.trim();
        
        StaticJsonDocument<256> doc;
        if(deserializeJson(doc, json) == DeserializationError::Ok) {
          String ced = doc["cedula"].as<String>();
          String mac = doc["mac"].as<String>();
          String pla = doc["placa"].as<String>();
          
          String aux1, aux2;
          if(!buscarCedula(ced, aux1, aux2)) {
            guardarUsuario(ced, mac, pla);
            pantalla("USUARIO OK", ced.c_str());
            SerialBT.println("GUARDADO_OK");
          } else {
            pantalla("ERROR", "Cedula existe");
            SerialBT.println("CEDULA_EXISTE");
          }
        } else {
          pantalla("ERROR", "JSON invalido");
          SerialBT.println("JSON_ERROR");
        }
        delay(TIEMPO_MENSAJE);
        sistema.estado = ESPERA_CONEXION;
      }
      break;
      
    case MODO_ELIMINAR:
      if(!SerialBT.hasClient()) { sistema.estado = ESPERA_CONEXION; break; }
      if(millis() - sistema.timeout > 30000) { pantalla("TIMEOUT"); sistema.estado = ESPERA_CONEXION; break; }
      
      if(SerialBT.available()) {
        String ced = SerialBT.readStringUntil('\n');
        ced.trim();
        
        String aux1, aux2;
        if(buscarCedula(ced, aux1, aux2)) {
          File f = SPIFFS.open(archivoUsers, "r");
          if(f) {
            DynamicJsonDocument doc(2048);
            deserializeJson(doc, f);
            f.close();
            
            JsonArray arr = doc.as<JsonArray>();
            for(int i=0; i<arr.size(); i++) {
              if(arr[i]["cedula"] == ced) {
                arr.remove(i);
                break;
              }
            }
            
            f = SPIFFS.open(archivoUsers, "w");
            if(f) { serializeJson(doc, f); f.close(); }
          }
          pantalla("ELIMINADO OK", ced.c_str());
          SerialBT.println("ELIMINADO_OK");
        } else {
          pantalla("ERROR", "Cedula no existe");
          SerialBT.println("NO_EXISTE");
        }
        delay(TIEMPO_MENSAJE);
        sistema.estado = ESPERA_CONEXION;
      }
      break;
      
    case MODO_CONFIG_ODOO:
      if(!SerialBT.hasClient()) { sistema.estado = ESPERA_CONEXION; break; }
      if(millis() - sistema.timeout > 30000) { pantalla("TIMEOUT"); sistema.estado = ESPERA_CONEXION; break; }
      
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
        delay(TIEMPO_MENSAJE);
        sistema.estado = ESPERA_CONEXION;
      }
      break;
      
    case MODO_MODIFICAR:
    case MODO_CONSULTAR:
      sistema.estado = ESPERA_CONEXION;
      break;
  }
  delay(10);
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
  delay(2000);
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

void guardarUsuario(String ced, String mac, String pla) {
  File f = SPIFFS.open(archivoUsers, "r");
  if(!f) return;
  
  DynamicJsonDocument doc(2048);
  deserializeJson(doc, f);
  f.close();
  
  JsonObject obj = doc.createNestedObject();
  obj["c"] = ced;
  obj["m"] = mac;
  obj["p"] = pla;
  
  f = SPIFFS.open(archivoUsers, "w");
  if(f) { serializeJson(doc, f); f.close(); }
}

bool buscarMac(String mac, String& ced, String& pla) {
  File f = SPIFFS.open(archivoUsers, "r");
  if(!f) return false;
  
  DynamicJsonDocument doc(2048);
  deserializeJson(doc, f);
  f.close();
  
  JsonArray arr = doc.as<JsonArray>();
  for(JsonObject obj : arr) {
    if(obj["m"].as<String>() == mac) {
      ced = obj["c"].as<String>();
      pla = obj["p"].as<String>();
      return true;
    }
  }
  return false;
}

bool buscarCedula(String ced, String& mac, String& pla) {
  File f = SPIFFS.open(archivoUsers, "r");
  if(!f) return false;
  
  DynamicJsonDocument doc(2048);
  deserializeJson(doc, f);
  f.close();
  
  JsonArray arr = doc.as<JsonArray>();
  for(JsonObject obj : arr) {
    if(obj["c"].as<String>() == ced) {
      mac = obj["m"].as<String>();
      pla = obj["p"].as<String>();
      return true;
    }
  }
  return false;
}

int contarUsers() {
  File f = SPIFFS.open(archivoUsers, "r");
  if(!f) return 0;
  
  DynamicJsonDocument doc(2048);
  deserializeJson(doc, f);
  f.close();
  return doc.as<JsonArray>().size();
}

void listarUsers() {
  File f = SPIFFS.open(archivoUsers, "r");
  if(!f) { SerialBT.println("ERROR"); return; }
  
  DynamicJsonDocument doc(2048);
  deserializeJson(doc, f);
  f.close();
  
  int total = contarUsers();
  SerialBT.println("=== USUARIOS (" + String(total) + ") ===");
  
  JsonArray arr = doc.as<JsonArray>();
  for(JsonObject obj : arr) {
    SerialBT.println(obj["c"].as<String>() + "|" + obj["m"].as<String>() + "|" + obj["p"].as<String>());
  }
  SerialBT.println("==================");
}

// ========== FUNCIONES DE RED ==========

void conectarWiFi() {
  if(!config.wifiConfigurado) {
    pantalla("SIN WIFI", "Use comando 'wifi'");
    delay(3000);
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
      server.send(200, "application/json", "{\"ok\":true}");
      digitalWrite(PIN_LED_OK, HIGH);
      digitalWrite(PIN_RELAY_OK, HIGH);
      delay(TIEMPO_ACCESO_RELE);
      digitalWrite(PIN_LED_OK, LOW);
      digitalWrite(PIN_RELAY_OK, LOW);
    });
    server.begin();
  } else {
    pantalla("ERROR WIFI", "No conectado");
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
