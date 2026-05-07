
//---------------------------Bibliotecas-----------------------------------//
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
//-----------------------------------------------------------------------//

//-----------------Configuracion para la conexion con Odoo---------------//
WebServer server(80);
const char* ip_computadora = "10.73.157.247"; // <-- Tu IP de Ubuntu (USB)
const char* odoo_url = "/api/sync_vehicular";
const char* odoo_token = "Vultur_Secreto_123";

//-------------------Configuración Wi-Fi y Memoria----------------------//

Preferences preferenciasWifi;
String redSSID;
String redPass;

//-------------------Configuración Bluetooth----------------------------//

BluetoothSerial SerialBT;
String nombreDispositivoBT = "ESP32_Seguro";

//---------------Configuración de la Pantalla OLED----------------------//

#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET    -1
#define SCREEN_ADDRESS 0x3C
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);

//-----------------------Pines de los LEDs------------------------------------//

const int PIN_LED_ERROR = 4;      // LED para huella incorrecta
const int PIN_LED_OK = 19;        // LED para huella correcta
const int PIN_LED_WAIT = 23;      // LED para estado de espera
const int PIN_RELAY_OK = 0;       // (Ojo, el 0 a veces se usa para el botón BOOT, si te da lío, cámbialo al 5)

//----------------------Estado del sistema-----------------------------------//

enum EstadoSistema {
  ESPERANDO_CONEXION,
  VERIFICANDO_MAC,
  ESPERANDO_HUELLA,
  MODO_AGREGAR_USUARIO,
  MODO_ELIMINAR_USUARIO,
  MODO_MODIFICAR_USUARIO,
  MODO_CONSULTAR_USUARIO
};
EstadoSistema estado = ESPERANDO_CONEXION;

//----------------------Variables de control -------------------------------//

unsigned long tiempoEsperaRespuesta = 0;
const unsigned long TIMEOUT_RESPUESTA = 15000; // 15 segundos
String macDispositivoConectado = "";
String usuarioActual = "";
String cedulaTemp = "";        // Para almacenar cédula temporal en operaciones

//-----------------Archivo para almacenar usuarios----------------------//

const char* archivoUsuarios = "/usuarios.json";

//------------------Prototipos de funciones-----------------------------//

void actualizarPantalla(String linea1, String linea2, String linea3, String linea4);
void mostrarError(String mensaje);
void iniciarSPIFFS();
bool guardarUsuario(String cedula, String mac, String placa);
bool eliminarUsuario(String cedula);
bool modificarUsuario(String cedula, String nuevaMac, String nuevaPlaca);
bool buscarUsuarioPorMAC(String mac, JsonDocument &docSalida);
bool buscarUsuarioPorCedula(String cedula, JsonDocument &docSalida);
String consultarUsuario(String cedula);
int contarUsuarios();
void listarUsuarios();

//------------------------------------------------------------------------//

void setup() {
  Serial.begin(115200);
  Serial.println("Iniciando ESP32 - Sistema Hibrido Dinámico");

  // Iniciar el bluethooth primero
  SerialBT.begin(nombreDispositivoBT);
  Serial.println("Bluetooth iniciado: " + nombreDispositivoBT);

  //-----------------Gestion de Memoria (Preferencias)----------------------//

  // Gestion de Memoria (Preferencias)
  preferenciasWifi.begin("config", false);
  redSSID = preferenciasWifi.getString("ssid", "");
  redPass = preferenciasWifi.getString("pass", "");
  preferenciasWifi.end();

  Serial.println("SSID a usar: " + (redSSID == "" ? "Ninguno" : redSSID));

  //--------------------Inicializar pantalla OLED----------------------------//

  if (!display.begin(SSD1306_SWITCHCAPVCC, SCREEN_ADDRESS)) {
    Serial.println(F("Error: No se encuentra la pantalla OLED"));
    pinMode(PIN_LED_WAIT, OUTPUT);
    for (;;) { // Bucle de error visual
      digitalWrite(PIN_LED_WAIT, HIGH); delay(100);
      digitalWrite(PIN_LED_WAIT, LOW); delay(100);
    }
  }

  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.display();

  //----------------------Configuracion de Pines-------------------------------//

  pinMode(PIN_LED_OK, OUTPUT);
  pinMode(PIN_LED_ERROR, OUTPUT);
  pinMode(PIN_LED_WAIT, OUTPUT);
  pinMode(PIN_RELAY_OK, OUTPUT);

  digitalWrite(PIN_LED_OK, LOW);
  digitalWrite(PIN_LED_ERROR, LOW);
  digitalWrite(PIN_LED_WAIT, LOW);

  //---------------------Inicializar Archivos  y otros-------------------------//

  iniciarSPIFFS();
  estado = ESPERANDO_CONEXION;

  //---------------------Intento de Conexion Wifi------------------------------//

  if (redSSID == "") {
    // CASO 1: El ESP32 es nuevo o no tiene red configurada
    Serial.println("No hay Wi-Fi guardado en memoria.");
    actualizarPantalla("NO HAY WIFI", "Red no registrada", "Use BT (cmd: wifi)", "para configurar");
    delay(4000); 
  } 
  else {
    // CASO 2: Sí tiene una red guardada, intentamos conectarnos
    actualizarPantalla("Conectando a:", redSSID, "Por favor espere", "");
    Serial.print("Conectando a Wi-Fi: " + redSSID);

    WiFi.begin(redSSID.c_str(), redPass.c_str());

    int intentos = 0;
    while (WiFi.status() != WL_CONNECTED && intentos < 60) {
      delay(500);
      Serial.print(".");
      intentos++;
    }

    Serial.println("");
    if (WiFi.status() == WL_CONNECTED) {
      Serial.println("¡Wi-Fi conectado!");
      Serial.print("IP: "); Serial.println(WiFi.localIP());
      actualizarPantalla("WIFI CONECTADO", "IP:", WiFi.localIP().toString(), "Esperando BT...");
      delay(3000);
      
      // --- Escuchar a Odoo (Lo encendemos SOLO si hay Wi-Fi) ---
      server.on("/abrir", HTTP_GET, []() {
        Serial.println("¡Orden remota recibida desde Odoo!");
        server.send(200, "application/json", "{\"status\":\"success\",\"mensaje\":\"Porton Abierto\"}");

        actualizarPantalla("ABRIENDO PORTON", "Orden del", "Vigilante (Odoo)", "");
        digitalWrite(PIN_LED_OK, HIGH);
        digitalWrite(PIN_RELAY_OK, HIGH);
        delay(3000); 
        digitalWrite(PIN_LED_OK, LOW);
        digitalWrite(PIN_RELAY_OK, LOW);

        actualizarPantalla("SISTEMA LISTO", "Usuarios: " + String(contarUsuarios()), "IP: " + WiFi.localIP().toString(), "Esperando BT...");
      });

      server.begin(); // Arrancar el servidor
      
    } else {
      // CASO 3: Tiene red guardada pero no la alcanza (se fue la luz, cambiaron la clave, etc)
      Serial.println("No se pudo conectar al Wi-Fi.");
      actualizarPantalla("ERROR DE WIFI", "No alcanzo la red", "Use BT (cmd: wifi)", "para cambiar");
      delay(4000);
    }
  }

  // Feedback final en pantalla para que el sistema quede "Ready"
  String ipStr = (WiFi.status() == WL_CONNECTED) ? "IP: " + WiFi.localIP().toString() : "Wi-Fi: Sin Configurar";
  actualizarPantalla("SISTEMA LISTO", "Nombre BT:", nombreDispositivoBT, ipStr);

}

//----------------------------------------------------------------------------//

void loop() {

  server.handleClient();

  //---------------------Manteniendo el servidor activo--------------------------//


  switch (estado) {

    case ESPERANDO_CONEXION:
      {
        // LED_WAIT parpadea lentamente
        static unsigned long parpadeoAnterior = 0;
        if (millis() - parpadeoAnterior > 500) {
          parpadeoAnterior = millis();
          digitalWrite(PIN_LED_WAIT, !digitalRead(PIN_LED_WAIT));
        }
        
        if (SerialBT.hasClient()) {
          digitalWrite(PIN_LED_WAIT, HIGH);
          Serial.println("¡Dispositivo conectado!");
          
          // --- MENÚ ESTÉTICO ---
          actualizarPantalla(
            "=== CONECTADO ===",
            "Cmd: wifi | listar",
            "Usuarios: agregar,",
            "elim, modif, cons"
          );
          
          estado = VERIFICANDO_MAC;
          tiempoEsperaRespuesta = millis();
        }
      }
      break;
    //----------------------------------------------------------------------------//

    case VERIFICANDO_MAC:
      {
        if (!SerialBT.hasClient()) {
          Serial.println("Cliente desconectado");
          digitalWrite(PIN_LED_WAIT, LOW);
          actualizarPantalla("DESCONECTADO", "Esperando", "nueva", "conexion...");
          estado = ESPERANDO_CONEXION;
          break;
        }

        if (SerialBT.available()) {
          String mensaje = SerialBT.readStringUntil('\n');
          mensaje.trim();
          Serial.println("Mensaje recibido: " + mensaje);

          //---------------Modo Conexion Manual al Wifi por terminal de BT---------------------//

          if (mensaje == "wifi") {
            Serial.println("Modo configuracion Wi-Fi activado");

            // 1. Pedir SSID
            actualizarPantalla("CONFIG. WIFI", "Escriba el SSID", "(Nombre de red)", "en la app BT");
            SerialBT.println("\n--- CONFIGURACION WI-FI ---");
            SerialBT.println("1. Ingrese el nombre de la red Wi-Fi (SSID):");

            while (!SerialBT.available()) {
              delay(50);  // Espera hasta recibir respuesta
            }
            String nuevoSsid = SerialBT.readStringUntil('\n');
            nuevoSsid.trim();

            // 2. Pedir Contraseña
            actualizarPantalla("CONFIG. WIFI", "Red: " + nuevoSsid, "Escriba la clave", "en la app BT");
            SerialBT.println("2. Ingrese la contrasena:");

            while (!SerialBT.available()) {
              delay(50);  // Espera hasta recibir respuesta
            }
            String nuevaClave = SerialBT.readStringUntil('\n');
            nuevaClave.trim();

         
            // Abrimos la memoria en modo lectura/escritura (false = read/write)
            preferenciasWifi.begin("config", false); 
            
            // Guardamos los datos
            preferenciasWifi.putString("ssid", nuevoSsid);
            preferenciasWifi.putString("pass", nuevaClave);
            
            // Cerramos la memoria para que se guarde físicamente
            preferenciasWifi.end(); 
            // --------------------------------------

            Serial.println("Nuevo SSID guardado: " + nuevoSsid);
            Serial.println("Nueva clave guardada.");

            // 3. Reiniciar
            actualizarPantalla("WIFI GUARDADO", "Reiniciando", "el sistema", "para aplicar...");
            SerialBT.println("Datos guardados correctamente. Reiniciando ESP32...");
            delay(2000);
            ESP.restart(); // Reinicia la placa para conectarse a la nueva red
          }
          //------------------------------LLamado a Agregar-----------------------------------------//

          else if (mensaje == "agregar") {
            Serial.println("Modo agregar usuario activado");
            actualizarPantalla(
              "MODO AGREGAR",
              "Envie JSON con:",
              "{'cedula':...",
              "'mac':...,'placa':...}"
            );
            SerialBT.println("LISTO_PARA_AGREGAR");
            estado = MODO_AGREGAR_USUARIO;
            tiempoEsperaRespuesta = millis();
            break;
          }
          //------------------------------LLamado a Eliminar----------------------------------------//

          else if (mensaje == "eliminar") {
            Serial.println("Modo eliminar usuario activado");
            actualizarPantalla(
              "MODO ELIMINAR",
              "Envie la cedula",
              "del usuario a",
              "eliminar"
            );
            SerialBT.println("LISTO_PARA_ELIMINAR");
            estado = MODO_ELIMINAR_USUARIO;
            tiempoEsperaRespuesta = millis();
            break;
          }

          //------------------------------LLamado a Modificar-----------------------------------------//

          else if (mensaje == "modificar") {
            Serial.println("Modo modificar usuario activado");
            actualizarPantalla(
              "MODO MODIFICAR",
              "Envie cedula del",
              "usuario a",
              "modificar"
            );
            SerialBT.println("LISTO_PARA_MODIFICAR");
            estado = MODO_MODIFICAR_USUARIO;
            tiempoEsperaRespuesta = millis();
            break;
          }
          //------------------------------LLamado a Consultar-------------------------------------------//

          else if (mensaje == "consultar") {
            Serial.println("Modo consultar usuario activado");
            actualizarPantalla(
              "MODO CONSULTAR",
              "Envie la cedula",
              "del usuario a",
              "consultar"
            );
            SerialBT.println("LISTO_PARA_CONSULTAR");
            estado = MODO_CONSULTAR_USUARIO;
            tiempoEsperaRespuesta = millis();
            break;
          }

          //--------------------------LLamado a Listar---------------------------------------------//


          else if (mensaje == "listar") {  
            Serial.println("Modo listar usuarios activado");
            actualizarPantalla("MODO LISTAR", "Enviando lista", "por Bluetooth...", "");
            listarUsuarios(); 
            delay(2000); 
            String ipString = (WiFi.status() == WL_CONNECTED) ? "IP: " + WiFi.localIP().toString() : "Wi-Fi: Sin conexion";
            actualizarPantalla("SISTEMA LISTO", "Usuarios: " + String(contarUsuarios()), ipString, "Esperando conexion...");
            estado = ESPERANDO_CONEXION; 
            break;
          }
         
          macDispositivoConectado = mensaje;
          Serial.println("MAC recibida: " + macDispositivoConectado);

          StaticJsonDocument<512> doc;
          if (buscarUsuarioPorMAC(macDispositivoConectado, doc)) {
            String cedula = doc["cedula"].as<String>();
            String placa = doc["placa"].as<String>();
            usuarioActual = cedula;

            Serial.println("Usuario encontrado - Cédula: " + cedula + ", Placa: " + placa);
            actualizarPantalla("USUARIO VALIDO", "Cedula: " + cedula, "Solicitando", "huella...");

            SerialBT.println("SOLICITUD_HUELLA");
            estado = ESPERANDO_HUELLA;
            tiempoEsperaRespuesta = millis();

          } else {
            Serial.println("MAC no registrada: " + macDispositivoConectado);
            actualizarPantalla("ACCESO DENEGADO", "MAC no", "registrada", "");

            SerialBT.println("MAC_NO_REGISTRADA");
            digitalWrite(PIN_LED_ERROR, HIGH);
            delay(2000);
            digitalWrite(PIN_LED_ERROR, LOW);

            actualizarPantalla("Desconectando...", "Esperando", "nueva", "conexion");
            SerialBT.disconnect();
            estado = ESPERANDO_CONEXION;
          }
        }

        //--------------------------Tiempo de Espera Limite---------------------------------------------//


        if (millis() - tiempoEsperaRespuesta > 50000) {
          Serial.println("Timeout esperando MAC/Comando");
          actualizarPantalla("TIMEOUT", "Sin respuesta", "Reintentar", "");
          SerialBT.println("TIMEOUT");
          estado = ESPERANDO_CONEXION;
        }
      }
      break;

    //------------------------------Metodo de Esperar Huella------------------------------------------//

    case ESPERANDO_HUELLA:
      {
        static unsigned long parpadeoRapido = 0;
        if (millis() - parpadeoRapido > 200) {
          parpadeoRapido = millis();
          digitalWrite(PIN_LED_WAIT, !digitalRead(PIN_LED_WAIT));
        }

        if (!SerialBT.hasClient()) {
          Serial.println("Cliente desconectado");
          digitalWrite(PIN_LED_WAIT, LOW);
          actualizarPantalla("DESCONECTADO", "Esperando", "nueva", "conexion...");
          estado = ESPERANDO_CONEXION;
          break;
        }

        if (millis() - tiempoEsperaRespuesta > TIMEOUT_RESPUESTA) {
          Serial.println("Timeout esperando huella");
          actualizarPantalla("TIMEOUT", "Sin respuesta", "de huella", "");
          SerialBT.println("TIMEOUT_HUELLA");
          digitalWrite(PIN_LED_WAIT, LOW);
          estado = ESPERANDO_CONEXION;
          break;
        }

        if (SerialBT.available()) {
          String respuesta = SerialBT.readStringUntil('\n');
          respuesta.trim();
          Serial.println("Respuesta huella: " + respuesta);

          digitalWrite(PIN_LED_WAIT, LOW);

          if (respuesta == "HUELLA_OK") {
            Serial.println("¡Huella correcta! Usuario: " + usuarioActual);
            digitalWrite(PIN_LED_OK, HIGH);
            digitalWrite(PIN_RELAY_OK, HIGH);
            delay(1 * 1000);



            //--------------------Enviar al Odoo-----------------------------------//

            actualizarPantalla("HUELLA OK", "Enviando datos", "a Odoo...", "");
            enviarAOdoo(usuarioActual, "apertura_porton");

            //---------------------------------------------------------------------//


            actualizarPantalla("HUELLA CORRECTA", "Usuario:", usuarioActual, "Acceso concedido");
            SerialBT.println("ACCESO_CONCEDIDO");
            delay(3000);
            digitalWrite(PIN_LED_OK, LOW);
            digitalWrite(PIN_RELAY_OK, LOW);

          } else if (respuesta == "HUELLA_MAL") {
            Serial.println("Huella incorrecta");
            digitalWrite(PIN_LED_ERROR, HIGH);

            actualizarPantalla("HUELLA INCORRECTA", "Acceso", "denegado", "");
            SerialBT.println("ACCESO_DENEGADO");
            delay(3000);
            digitalWrite(PIN_LED_ERROR, LOW);
          }

          actualizarPantalla("Procesado", "Desconectando...", "Esperando", "nueva conexion");
          SerialBT.disconnect();
          estado = ESPERANDO_CONEXION;
        }
      }
      break;

    //-----------------------------------------Metodo Agregar Usuario---------------------------------------------------------//

    case MODO_AGREGAR_USUARIO:
      {
        if (!SerialBT.hasClient()) {
          Serial.println("Cliente desconectado en modo agregar");
          estado = ESPERANDO_CONEXION;
          break;
        }

        if (millis() - tiempoEsperaRespuesta > 30000) {
          Serial.println("Timeout en modo agregar usuario");
          actualizarPantalla("TIMEOUT", "Modo agregar", "cancelado", "");
          SerialBT.println("TIMEOUT_AGREGAR");
          estado = ESPERANDO_CONEXION;
          break;
        }

        if (SerialBT.available()) {
          String jsonStr = SerialBT.readStringUntil('\n');
          jsonStr.trim();
          Serial.println("JSON recibido: " + jsonStr);

          StaticJsonDocument<256> doc;
          DeserializationError error = deserializeJson(doc, jsonStr);

          if (error) {
            Serial.println("Error parsing JSON");
            actualizarPantalla("ERROR", "JSON invalido", "Intente", "nuevamente");
            SerialBT.println("ERROR_JSON");
            break;
          }

          String cedula = doc["cedula"].as<String>();
          String mac = doc["mac"].as<String>();
          String placa = doc["placa"].as<String>();

          StaticJsonDocument<512> docExistente;
          if (buscarUsuarioPorCedula(cedula, docExistente)) {
            Serial.println("Error: Cédula ya existe");
            actualizarPantalla("ERROR", "Cedula ya", "existe", "");
            SerialBT.println("ERROR_CEDULA_EXISTE");
            break;
          }

          if (guardarUsuario(cedula, mac, placa)) {
            Serial.println("Usuario guardado exitosamente");
            actualizarPantalla("USUARIO GUARDADO", "Cedula: " + cedula, "MAC: " + mac.substring(0, 10) + "...", "Placa: " + placa);
            SerialBT.println("USUARIO_GUARDADO");
          } else {
            Serial.println("Error al guardar usuario");
            actualizarPantalla("ERROR", "No se pudo", "guardar", "usuario");
            SerialBT.println("ERROR_GUARDAR");
          }

          delay(3000);
          String ipString = (WiFi.status() == WL_CONNECTED) ? "IP: " + WiFi.localIP().toString() : "Wi-Fi: Sin conexion";
          actualizarPantalla("SISTEMA LISTO", "Usuarios: " + String(contarUsuarios()), ipString, "Esperando conexion...");
          estado = ESPERANDO_CONEXION;
        }
      }
      break;

    //-----------------------------------------Metodo Eliminar Usuario---------------------------------------------------------//


    case MODO_ELIMINAR_USUARIO:
      {
        if (!SerialBT.hasClient()) {
          Serial.println("Cliente desconectado en modo eliminar");
          estado = ESPERANDO_CONEXION;
          break;
        }

        if (millis() - tiempoEsperaRespuesta > 30000) {
          Serial.println("Timeout en modo eliminar usuario");
          actualizarPantalla("TIMEOUT", "Modo eliminar", "cancelado", "");
          SerialBT.println("TIMEOUT_ELIMINAR");
          estado = ESPERANDO_CONEXION;
          break;
        }

        if (SerialBT.available()) {
          String cedulaEliminar = SerialBT.readStringUntil('\n');
          cedulaEliminar.trim();
          Serial.println("Cédula a eliminar: " + cedulaEliminar);

          StaticJsonDocument<512> docExistente;
          if (!buscarUsuarioPorCedula(cedulaEliminar, docExistente)) {
            Serial.println("Error: Cédula no encontrada");
            actualizarPantalla("ERROR", "Cedula no", "encontrada", "");
            SerialBT.println("ERROR_CEDULA_NO_EXISTE");
            break;
          }

          if (eliminarUsuario(cedulaEliminar)) {
            Serial.println("Usuario eliminado exitosamente");
            actualizarPantalla("USUARIO ELIMINADO", "Cedula: " + cedulaEliminar, "", "");
            SerialBT.println("USUARIO_ELIMINADO");
          } else {
            Serial.println("Error al eliminar usuario");
            actualizarPantalla("ERROR", "No se pudo", "eliminar", "usuario");
            SerialBT.println("ERROR_ELIMINAR");
          }

          delay(3000);
          String ipString = (WiFi.status() == WL_CONNECTED) ? "IP: " + WiFi.localIP().toString() : "Wi-Fi: Sin conexion";
          actualizarPantalla("SISTEMA LISTO", "Usuarios: " + String(contarUsuarios()), ipString, "Esperando conexion...");
          estado = ESPERANDO_CONEXION;
        }
      }
      break;

    //-----------------------------------------Metodo Modificar Usuario---------------------------------------------------------//


    case MODO_MODIFICAR_USUARIO:
      {
        if (!SerialBT.hasClient()) {
          Serial.println("Cliente desconectado en modo modificar");
          estado = ESPERANDO_CONEXION;
          break;
        }

        if (millis() - tiempoEsperaRespuesta > 30000) {
          Serial.println("Timeout en modo modificar usuario");
          actualizarPantalla("TIMEOUT", "Modo modificar", "cancelado", "");
          SerialBT.println("TIMEOUT_MODIFICAR");
          estado = ESPERANDO_CONEXION;
          break;
        }

        if (cedulaTemp == "") {
          if (SerialBT.available()) {
            cedulaTemp = SerialBT.readStringUntil('\n');
            cedulaTemp.trim();
            Serial.println("Cédula a modificar: " + cedulaTemp);

            StaticJsonDocument<512> docExistente;
            if (!buscarUsuarioPorCedula(cedulaTemp, docExistente)) {
              Serial.println("Error: Cédula no encontrada");
              actualizarPantalla("ERROR", "Cedula no", "encontrada", "");
              SerialBT.println("ERROR_CEDULA_NO_EXISTE");
              cedulaTemp = "";
              estado = ESPERANDO_CONEXION;
              break;
            }

            String macActual = docExistente["mac"].as<String>();
            String placaActual = docExistente["placa"].as<String>();

            actualizarPantalla("MODIFICAR USUARIO", "Cedula: " + cedulaTemp, "MAC: " + macActual, "PLACA: " + placaActual);
            SerialBT.println("DATOS_ACTUALES:" + macActual + "," + placaActual);
            actualizarPantalla("Envie nuevos datos", "Formato JSON:", "{'mac':'...',", "'placa':'...'}");
            SerialBT.println("ENVIE_NUEVOS_DATOS");
          }
        }
        else {
          if (SerialBT.available()) {
            String jsonStr = SerialBT.readStringUntil('\n');
            jsonStr.trim();
            Serial.println("Nuevos datos JSON: " + jsonStr);

            StaticJsonDocument<256> doc;
            DeserializationError error = deserializeJson(doc, jsonStr);

            if (error) {
              Serial.println("Error parsing JSON");
              actualizarPantalla("ERROR", "JSON invalido", "Intente", "nuevamente");
              SerialBT.println("ERROR_JSON");
              cedulaTemp = "";
              estado = ESPERANDO_CONEXION;
              break;
            }

            String nuevaMac = doc["mac"].as<String>();
            String nuevaPlaca = doc["placa"].as<String>();

            if (modificarUsuario(cedulaTemp, nuevaMac, nuevaPlaca)) {
              Serial.println("Usuario modificado exitosamente");
              actualizarPantalla("USUARIO MODIFICADO", "Cedula: " + cedulaTemp, "Nueva MAC: " + nuevaMac, "Nueva PLACA: " + nuevaPlaca);
              SerialBT.println("USUARIO_MODIFICADO");
            } else {
              Serial.println("Error al modificar usuario");
              actualizarPantalla("ERROR", "No se pudo", "modificar", "usuario");
              SerialBT.println("ERROR_MODIFICAR");
            }

            cedulaTemp = "";
            delay(3000);
            String ipString = (WiFi.status() == WL_CONNECTED) ? "IP: " + WiFi.localIP().toString() : "Wi-Fi: Sin conexion";
            actualizarPantalla("SISTEMA LISTO", "Usuarios: " + String(contarUsuarios()), ipString, "Esperando conexion...");
            estado = ESPERANDO_CONEXION;
          }
        }
      }
      break;

    //-----------------------------------------Metodo Consultar Usuario---------------------------------------------------------//


    case MODO_CONSULTAR_USUARIO:
      {
        if (!SerialBT.hasClient()) {
          Serial.println("Cliente desconectado en modo consultar");
          estado = ESPERANDO_CONEXION;
          break;
        }

        if (millis() - tiempoEsperaRespuesta > 30000) {
          Serial.println("Timeout en modo consultar usuario");
          actualizarPantalla("TIMEOUT", "Modo consultar", "cancelado", "");
          SerialBT.println("TIMEOUT_CONSULTAR");
          estado = ESPERANDO_CONEXION;
          break;
        }

        if (SerialBT.available()) {
          String cedulaConsultar = SerialBT.readStringUntil('\n');
          cedulaConsultar.trim();
          Serial.println("Cédula a consultar: " + cedulaConsultar);

          String resultado = consultarUsuario(cedulaConsultar);

          if (resultado != "") {
            Serial.println("Usuario encontrado: " + resultado);
            actualizarPantalla("USUARIO ENCONTRADO", "Cedula: " + cedulaConsultar, resultado.substring(0, resultado.indexOf(",")), resultado.substring(resultado.indexOf(",") + 1));
            SerialBT.println("RESULTADO_CONSULTA:" + resultado);
          } else {
            Serial.println("Usuario no encontrado");
            actualizarPantalla("NO ENCONTRADO", "Cedula: " + cedulaConsultar, "no existe", "en la base");
            SerialBT.println("USUARIO_NO_EXISTE");
          }

          delay(3000);
          String ipString = (WiFi.status() == WL_CONNECTED) ? "IP: " + WiFi.localIP().toString() : "Wi-Fi: Sin conexion";
          actualizarPantalla("SISTEMA LISTO", "Usuarios: " + String(contarUsuarios()), ipString, "Esperando conexion...");
          estado = ESPERANDO_CONEXION;
        }
      }
      break;
  }
  delay(50);
}

//-----------------------------------------Manejo de Archivos---------------------------------------------------------//


void iniciarSPIFFS() {
  if (!SPIFFS.begin(true)) {
    Serial.println("Error montando SPIFFS");
    mostrarError("Error SPIFFS");
    return;
  }
  Serial.println("SPIFFS montado correctamente");

  if (!SPIFFS.exists(archivoUsuarios)) {
    File file = SPIFFS.open(archivoUsuarios, "w");
    if (file) {
      file.println("[]");
      file.close();
      Serial.println("Archivo de usuarios creado");
    }
  }
}

//-----------------------------------------Para Guardar---------------------------------------------------------//


bool guardarUsuario(String cedula, String mac, String placa) {
  File file = SPIFFS.open(archivoUsuarios, "r");
  if (!file) return false;
  StaticJsonDocument<2048> doc;
  DeserializationError error = deserializeJson(doc, file);
  file.close();
  if (error) return false;

  JsonObject nuevoUsuario = doc.createNestedObject();
  nuevoUsuario["cedula"] = cedula;
  nuevoUsuario["mac"] = mac;
  nuevoUsuario["placa"] = placa;

  file = SPIFFS.open(archivoUsuarios, "w");
  if (!file) return false;
  serializeJson(doc, file);
  file.close();
  return true;
}

//-----------------------------------------Para Eliminar---------------------------------------------------------//

bool eliminarUsuario(String cedula) {
  File file = SPIFFS.open(archivoUsuarios, "r");
  if (!file) return false;
  StaticJsonDocument<2048> doc;
  DeserializationError error = deserializeJson(doc, file);
  file.close();
  if (error) return false;

  StaticJsonDocument<2048> docNuevo;
  JsonArray nuevoArray = docNuevo.to<JsonArray>();
  JsonArray usuarios = doc.as<JsonArray>();
  bool encontrado = false;

  for (JsonObject usuario : usuarios) {
    if (usuario["cedula"] != cedula) {
      JsonObject nuevoUsuario = nuevoArray.createNestedObject();
      nuevoUsuario["cedula"] = usuario["cedula"].as<String>();
      nuevoUsuario["mac"] = usuario["mac"].as<String>();
      nuevoUsuario["placa"] = usuario["placa"].as<String>();
    } else {
      encontrado = true;
    }
  }
  if (!encontrado) return false;

  file = SPIFFS.open(archivoUsuarios, "w");
  if (!file) return false;
  serializeJson(docNuevo, file);
  file.close();
  return true;
}

//-----------------------------------------Para Modificar---------------------------------------------------------//


bool modificarUsuario(String cedula, String nuevaMac, String nuevaPlaca) {
  File file = SPIFFS.open(archivoUsuarios, "r");
  if (!file) return false;
  StaticJsonDocument<2048> doc;
  DeserializationError error = deserializeJson(doc, file);
  file.close();
  if (error) return false;

  JsonArray usuarios = doc.as<JsonArray>();
  bool encontrado = false;

  for (JsonObject usuario : usuarios) {
    if (usuario["cedula"] == cedula) {
      usuario["mac"] = nuevaMac;
      usuario["placa"] = nuevaPlaca;
      encontrado = true;
      break;
    }
  }
  if (!encontrado) return false;

  file = SPIFFS.open(archivoUsuarios, "w");
  if (!file) return false;
  serializeJson(doc, file);
  file.close();
  return true;
}

//-----------------------------------------Para Consultar---------------------------------------------------------//


String consultarUsuario(String cedula) {
  File file = SPIFFS.open(archivoUsuarios, "r");
  if (!file) return "";
  StaticJsonDocument<2048> doc;
  DeserializationError error = deserializeJson(doc, file);
  file.close();
  if (error) return "";

  JsonArray usuarios = doc.as<JsonArray>();
  for (JsonObject usuario : usuarios) {
    if (usuario["cedula"] == cedula) {
      String mac = usuario["mac"].as<String>();
      String placa = usuario["placa"].as<String>();
      return mac + "," + placa;
    }
  }
  return "";
}

//-----------------------------Para Buscar Usuarios por Mac y Cedula--------------------------------------------------------//


bool buscarUsuarioPorMAC(String mac, JsonDocument &docSalida) {
  File file = SPIFFS.open(archivoUsuarios, "r");
  if (!file) return false;

  // Usamos memoria dinámica para no colapsar la RAM
  DynamicJsonDocument doc(4096);
  DeserializationError error = deserializeJson(doc, file);
  file.close();

  if (error) {
    Serial.println("Error leyendo JSON en buscarUsuarioPorMAC");
    return false;
  }

  JsonArray usuarios = doc.as<JsonArray>();
  for (JsonObject usuario : usuarios) {
    if (usuario["mac"] == mac) {
      docSalida.set(usuario);
      return true;
    }
  }
  return false;
}

bool buscarUsuarioPorCedula(String cedula, JsonDocument &docSalida) {
  File file = SPIFFS.open(archivoUsuarios, "r");
  if (!file) return false;
  StaticJsonDocument<2048> doc;
  DeserializationError error = deserializeJson(doc, file);
  file.close();
  if (error) return false;

  JsonArray usuarios = doc.as<JsonArray>();
  for (JsonObject usuario : usuarios) {
    if (usuario["cedula"] == cedula) {
      docSalida.set(usuario);
      return true;
    }
  }
  return false;
}

//----------------------------------------- Contar ---------------------------------------------------------//


int contarUsuarios() {
  File file = SPIFFS.open(archivoUsuarios, "r");
  if (!file) return 0;
  StaticJsonDocument<2048> doc;
  DeserializationError error = deserializeJson(doc, file);
  file.close();
  if (error) return 0;
  return doc.as<JsonArray>().size();
}

//----------------------------------------- Listar ---------------------------------------------------------//


void listarUsuarios() {
  File file = SPIFFS.open(archivoUsuarios, "r");
  if (!file) {
    SerialBT.println("ERROR: No se pudo abrir la base de datos.");
    Serial.println("ERROR: No se pudo abrir la base de datos.");
    return;
  }

  StaticJsonDocument<2048> doc;
  DeserializationError error = deserializeJson(doc, file);
  file.close();

  if (error) {
    SerialBT.println("ERROR: Archivo JSON corrupto o vacio.");
    Serial.println("ERROR: Archivo JSON corrupto o vacio.");
    return;
  }

  JsonArray usuarios = doc.as<JsonArray>();
  int total = usuarios.size();

  // Armamos el texto y disparamos a ambos lados
  String encabezado = "\n=== USUARIOS REGISTRADOS (" + String(total) + ") ===";
  SerialBT.println(encabezado);
  Serial.println(encabezado);

  if (total == 0) {
    SerialBT.println("No hay usuarios registrados en el sistema.");
    Serial.println("No hay usuarios registrados en el sistema.");
  } else {
    for (JsonObject usuario : usuarios) {
      String ced = usuario["cedula"].as<String>();
      String mac = usuario["mac"].as<String>();
      String pla = usuario["placa"].as<String>();

      String linea = "Cedula: " + ced + " | MAC: " + mac + " | Placa: " + pla;
      
      SerialBT.println(linea); // Va para el teléfono
      Serial.println(linea);   // Va para la computadora
      delay(20); // Respiro para los buffers
    }
  }
  
  String pie = "========================================\n";
  SerialBT.println(pie);
  Serial.println(pie);
}

//------------------------------------Actualizar la pantalla-----------------------------------------------//


void actualizarPantalla(String linea1, String linea2, String linea3, String linea4) {
  display.clearDisplay();
  display.setCursor(0, 0);
  display.println(linea1);
  display.println(linea2);
  display.println(linea3);
  display.println(linea4);
  display.display();
}

void mostrarError(String mensaje) {
  actualizarPantalla("ERROR", mensaje, "Reiniciando...", "");
  delay(2000);
}

//-----------------------------------Enviar A Odoo--------------------------------------------------------------//

void enviarAOdoo(String cedula, String accion) {
  if (WiFi.status() == WL_CONNECTED) {

    WiFiClient client; // Cliente explícito
    HTTPClient http;
    String full_url = "http://" + String(ip_computadora) + ":8069" + String(odoo_url);

    http.begin(client, full_url);

    // Trucos de robustez para redes inestables
    http.useHTTP10(true);
    http.setTimeout(10000);
    http.setReuse(false);

    // --- LOS CAMBIOS IMPORTANTES DE SEGURIDAD ---
    http.addHeader("Content-Type", "application/json"); // El nuevo Odoo pide JSON
    http.addHeader("X-API-KEY", "sync_secret_2024");    // ¡LA LLAVE MAESTRA DE TU COMPAÑERO!
    http.addHeader("Connection", "close");

    DynamicJsonDocument doc(1024);
    doc["jsonrpc"] = "2.0";
    doc["method"] = "call";

    JsonObject params = doc.createNestedObject("params");
    params["cedula"] = "SOY_EL_ESP32_" + cedula;
    params["accion"] = accion;
    params["token_seguridad"] = odoo_token;

    String jsonStr;
    serializeJson(doc, jsonStr);

    Serial.println("--- ENVIANDO DATOS A ODOO ---");
    int httpResponseCode = http.POST(jsonStr);

    if (httpResponseCode > 0) {
      Serial.println("Odoo respondió: " + String(httpResponseCode));
      Serial.println("Mensaje: " + http.getString());
    } else {
      Serial.println("Fallo de conexión: " + http.errorToString(httpResponseCode));
    }

    http.end();
    client.stop(); // Cerramos el socket desde la raíz
    Serial.println("--- CONEXIÓN CERRADA ---");

  } else {
    Serial.println("ERROR: Sin Wi-Fi para enviar a Odoo.");
  }
}
