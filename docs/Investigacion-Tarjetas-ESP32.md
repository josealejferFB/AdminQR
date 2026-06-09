# Investigación: Gestión de Placas Controladoras de Portones (ESP32 WROOM)

*Aclaratoria: En este documento, "Tarjeta" se refiere a la placa electrónica (ESP32 WROOM + Relé) que se instala físicamente en cada portón para controlar su apertura.*

El objetivo es crear un sistema modular donde cada portón (controlado por un ESP32) funcione de manera independiente. En Odoo, se mantendrán dos tablas separadas: una para los usuarios/conductores y otra para las placas ESP32 de los portones, vinculándolas dinámicamente según los permisos.

---

## 1. Arquitectura de Base de Datos en Odoo

Para que el sistema sea modular, debemos separar los usuarios de los portones físicos.

### A. Modelo de Portones/Placas ESP32 (`app.gate.controller`)
Se creará un modelo en Odoo para registrar cada placa ESP32 instalada en la infraestructura.
* **Campos sugeridos**:
  * `name`: Nombre descriptivo del portón (ej. "Portón Principal", "Portón Almacén Norte").
  * `mac_address` (o `hardware_id`): Identificador único inmutable de la placa ESP32.
  * `ip_address`: (Opcional) Última IP conocida si se usa comunicación en red local.
  * `is_online`: Estado de conexión actual del portón.
  * `is_active`: Permite inhabilitar un portón completo por mantenimiento.

### B. Relación de Permisos (Usuarios <-> Portones)
La tabla de usuarios (`hr.employee` o el modelo que uses para conductores) se relacionará con la tabla de portones mediante una relación **Muchos a Muchos (Many2many)**.
* Un conductor puede tener permiso para abrir 3 portones distintos.
* Un portón puede ser abierto por 50 conductores distintos.
* Esto permite otorgar o revocar accesos a portones específicos con un solo clic, sin afectar el resto de la configuración del conductor.

---

## 2. Identificación Única de cada Placa ESP32

Para que Odoo sepa exactamente a qué portón enviarle la orden de apertura, o qué portón está recibiendo una solicitud, el ESP32 debe ser único.

**La mejor opción: Dirección MAC del ESP32**
Cada chip ESP32 trae de fábrica una dirección MAC única a nivel mundial grabada a fuego (eFuse). 
* **En el código de Arduino (C++)**, se obtiene fácilmente usando `WiFi.macAddress()`.
* **Ventaja**: No necesitas generar ni guardar identificadores personalizados en la memoria flash (EEPROM/SPIFFS), lo cual simplifica enormemente el código del Arduino. Esta MAC será el `hardware_id` que se guardará en Odoo.

---

## 3. Configuración y Registro del ESP32 vía Bluetooth

Dado que la aplicación del administrador ya se comunica con la placa ESP32 por Bluetooth (Classic) para configurarle la red, aprovecharemos este intercambio de datos para registrar el portón en Odoo.

El flujo de configuración desde la App Administrativa será el siguiente:

1. **Conexión Bluetooth**: El administrador conecta su app al ESP32 por Bluetooth.
2. **Envío de Credenciales (App -> ESP32)**: La app le envía al ESP32 el SSID y la contraseña de la red WiFi a la que debe conectarse en formato JSON.
   
   **Ejemplo de JSON enviado por la App al ESP32:**
   ```json
   {
     "action": "config_network",
     "ssid": "Red_Porton",
     "password": "PasswordSeguro123"
   }
   ```

3. **Lectura de la MAC (ESP32 -> App)**: El ESP32 guarda las credenciales y le responde a la App confirmando el éxito y adjuntando su **Dirección MAC** (`WiFi.macAddress()`). Esto es clave, ya que la MAC será el identificador único del portón.
   
   **Ejemplo de JSON enviado por el ESP32 a la App:**
   ```json
   {
     "status": "success",
     "message": "Red configurada",
     "mac_address": "A1:B2:C3:D4:E5:F6"
   }
   ```

4. **Registro en Odoo (App -> Odoo)**: La app del administrador cierra la conexión Bluetooth y, usando internet, le pide al administrador que asigne un nombre al portón. Luego envía una petición HTTP a Odoo (`POST /api/v1/gates/register`) para guardarlo en la base de datos.
   
   **Ejemplo de JSON que la App envía a Odoo:**
   ```json
   {
     "params": {
       "name": "Portón Sótano 2",
       "mac_address": "A1:B2:C3:D4:E5:F6",
       "description": "Portón de acceso al estacionamiento de empleados"
     }
   }
   ```

5. **Respuesta de Vinculación (Odoo -> App)**: Odoo crea el registro en la tabla `app.gate.controller` y responde confirmando la creación. A partir de este momento, el portón existe en el sistema y se le pueden asignar permisos a los conductores.
   
   **Ejemplo de JSON que Odoo responde:**
   ```json
   {
     "result": {
       "success": true,
       "gate_id": 24,
       "message": "Portón 'Portón Sótano 2' registrado exitosamente."
     }
   }
   ```

---

## 4. Flujo de Apertura del Portón (Comunicación App -> Odoo -> ESP32)

Existen varias formas de hacer que la orden de apertura llegue al relé. Aquí las tres mejores arquitecturas de comunicación:



### Arquitectura B: Odoo comanda al ESP32 por HTTP (Basado en el sistema actual)
Actualmente, el proyecto ya cuenta con una base beta en el módulo `control_acceso` donde el ESP32 reporta su IP a Odoo y Odoo le dispara peticiones HTTP para abrir. El flujo escalado para **múltiples portones** funcionaría de la siguiente manera:

1. **Auto-Discovery (ESP32 -> Odoo)**: Cuando el ESP32 se conecta al WiFi, realiza una petición a Odoo (`POST /api/update_esp_ip`) enviando su `mac_address`, su `ip_local`, y un `iot_token`.
2. **Registro de IP en Odoo**: Odoo busca en la tabla `app.gate.controller` el portón que tenga esa MAC Address y actualiza su campo `ip_address` con la IP local que acaba de reportar. (A diferencia de la versión beta que usaba un parámetro global `ir.config_parameter`, aquí cada portón tendrá su propia IP guardada en su registro).
3. **Petición del Usuario (App -> Odoo)**: El conductor presiona "Abrir Portón X" en la app. La app envía la petición a Odoo.
4. **Validación y Ejecución (Odoo -> ESP32)**: Odoo valida que el conductor tenga permiso para el Portón X (relación Many2many). Si es válido, Odoo toma la `ip_address` del Portón X y envía una petición HTTP (`GET http://<ip_del_porton_x>/abrir?token=secreto123`).
5. **Apertura**: El ESP32 recibe la petición, valida el token y activa el relé.

* **Ventaja**: Reutiliza la lógica y el código que ya tienen programado en el módulo `control_acceso`. Solo se necesita migrar de un `ir.config_parameter` único a múltiples registros en la tabla `app.gate.controller`.
---

## 5. Resumen de Pasos a Seguir
1. **Definir el modelo de Odoo (`app.gate.controller`)**: Crear la tabla para los ESP32 y la relación Many2many con los empleados/conductores.
2. **Definir la arquitectura de comunicación**: Decidir si se usará **MQTT** (lo más estándar en IoT para domótica), **HTTP Local**, o **Bluetooth**.
3. **Actualizar el código Arduino**: Programar el ESP32 para leer su propia MAC, conectarse al WiFi, y escuchar la señal de activación del relé.
