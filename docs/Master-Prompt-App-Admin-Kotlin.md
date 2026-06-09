# Master Prompt: App Administrativa (Control de Accesos)

**Objetivo:** Adaptar la aplicación administrativa Android (Kotlin) para soportar el nuevo sistema multi-portón (módulos ESP32 independientes) y su vinculación con el backend de Odoo.

---

## 1. Contexto Actual vs. Nuevo Flujo

**Actualmente:** La app se conecta al ESP32 por Bluetooth Clásico, le envía el SSID y la contraseña, y finaliza el proceso de configuración de red.
**Nuevo Requerimiento:** Se necesita aprovechar esa misma conexión Bluetooth para extraer la Dirección MAC (el identificador único) del ESP32 y luego registrar este nuevo portón en Odoo, asociándole un nombre descriptivo.

---

## 2. Requisitos de Implementación (Kotlin)

### Fase A: Comunicación Bluetooth con ESP32
Deben modificar el listener/callback del socket Bluetooth que actualmente envía la configuración.

1. **Enviar Credenciales (App -> ESP32)**:
   Al enviar el JSON con las credenciales de red, háganlo estructurado.
   ```json
   {
     "action": "config_network",
     "ssid": "Red_Wifi",
     "password": "Password123"
   }
   ```

2. **Escuchar Respuesta (ESP32 -> App)**:
   Asegúrense de que el `InputStream` del socket Bluetooth espere una respuesta del ESP32. El hardware devolverá un JSON confirmando la red y enviando su MAC Address.
   *Ejemplo de respuesta esperada:*
   ```json
   {
     "status": "success",
     "mac_address": "A1:B2:C3:D4:E5:F6"
   }
   ```
   **Acción:** Parsee este JSON usando `Gson` o `Moshi` y guarden el valor `mac_address` en una variable temporal o en el `ViewModel`.

### Fase B: Interfaz de Usuario (Registro en Odoo)
Una vez que el ESP32 confirma y la app tiene la MAC:

1. **Mostrar Dialog / Pantalla de Registro**:
   Levanten un `BottomSheetDialogFragment` o una nueva pantalla que indique "Hardware detectado exitosamente" y muestre un `EditText` solicitando el **Nombre del Portón** (Ej. "Portón Visitantes").

2. **Petición HTTP a Odoo**:
   Cuando el admin presione "Guardar", utilicen `Retrofit` o `OkHttp` para hacer un POST al backend de Odoo.

   * **Endpoint (Sugerido)**: `POST /api/v1/gates/register`
   * **Headers**: `Content-Type: application/json`
   * **Body JSON-RPC**:
     ```json
     {
       "params": {
         "name": "Portón Visitantes",
         "mac_address": "A1:B2:C3:D4:E5:F6"
       }
     }
     ```

3. **Manejo de Respuesta**:
   Si Odoo devuelve success (`{"result": {"success": true}}`), muestren un Toast o Snackbar verde indicando que el portón ya está operativo en el sistema y cierren el flujo de configuración.

---

## 3. Consideraciones Técnicas
* **Permisos Bluetooth**: Recuerden que a partir de Android 12+, se requieren los permisos `BLUETOOTH_SCAN` y `BLUETOOTH_CONNECT` explícitamente.
* **Corrutinas**: Realicen las llamadas Bluetooth y HTTP usando `viewModelScope.launch(Dispatchers.IO)` para no bloquear el hilo principal (UI Thread).
