# Contrato App Usuario (Conductor) — Sistema EscanQR

## Visión General

La **App Conductor** es la aplicación que corre en el dispositivo del usuario final (conductor/residente). Su responsabilidad es:

1. **Auto-identificarse** ante el sistema mediante su Android ID
2. **Obtener de Odoo** sus datos personales, placas y lista de portones a los que tiene acceso
3. **Generar un código QR de identidad** que la App Admin escanea para registrar al conductor
4. **Mostrar los portones disponibles** como chips interactivos para abrirlos
5. **Solicitar apertura de portón** — vía HTTP al ESP32 directamente (o a través de Odoo)

No hay comunicación directa entre la App Conductor y la App Admin excepto mediante QR.

---

## 1. Flujo de Registro Completo

```
App Conductor registra al usuario localmente
       │
       ▼
App Conductor genera QR de identidad (u, c, p, aid cifrado)
       │
       ▼
El conductor muestra el QR al administrador
       │
       ▼
App Admin escanea el QR (cámara / ML Kit)
       │
       ├─ Descifra aid con AES-256-GCM
       ├─ Carga datos del conductor (nombre, cédula, placas)
       └─ Muestra pantalla de Resultado (ResultScreen)
              │
              ▼
Admin confirma y envía a Odoo (POST /api/control_acceso)
       │
       ▼
App Admin muestra QR de aprovisionamiento
  (endpoint + token) para que el conductor
  configure su app
       │
       ▼
App Conductor escanea el QR de aprovisionamiento
       │
       └─ Guarda URL base de Odoo + valida token
```

El flujo de registro es **idéntico al descrito anteriormente**: el conductor se registra desde su app, muestra el QR al admin, se sube a Odoo, y se le aprovisiona desde la App Admin un QR con los endpoints.

---

## 2. Aprovisionamiento Inicial (Provisioning)

### Propósito

Configurar la URL del servidor Odoo en la App Conductor sin intervención manual.

### Cuándo ocurre

1. **Flujo principal:** Inmediatamente después de que el Admin escanea el QR del conductor y confirma el registro en Odoo (en `ResultScreen`). La App Admin genera automáticamente un QR de aprovisionamiento.
2. **Flujo secundario (manual):** Desde `HomeScreen`, el Admin puede tocar "Aprovisionar" para generar un QR de aprovisionamiento para conductores ya registrados que necesiten reconfigurar su app.

### Formato del QR de Provisioning

```json
{
    "endpoint": "http://172.17.12.119:8059",
    "token": "ALCARAVAN_2025"
}
```

- **endpoint:** URL base del servidor Odoo (protocolo + host + puerto)
- **token:** Token compartido definido en `SecurityConstants.PROVISIONING_TOKEN`

### Validación

La App Conductor debe verificar que el `token` del QR coincide con el `PROVISIONING_TOKEN` hardcodeado. Si no coincide, rechazar la configuración.

### Persistencia

La URL de Odoo se guarda localmente (SharedPreferences, DataStore o similar) para usarse en todos los llamados posteriores.

---

## 3. Identificación del Dispositivo

### Android ID

Cada dispositivo Android tiene un identificador único de 64 bits (Android ID / SSAID) que se obtiene con:

```kotlin
val androidId = Settings.Secure.getString(
    contentResolver, Settings.Secure.ANDROID_ID
)
```

**Reglas:**
- El Android ID se obtiene una vez al iniciar la app por primera vez
- Se almacena localmente para usarlo en cada generación de QR
- Se envía cifrado dentro del QR (`aid`) para que la App Admin lo descifre
- Odoo lo usa como identificador único del conductor

> **⚠️ Importante:** El Android ID puede cambiar en ciertos escenarios (restauración de fábrica, cambio de ROM). En producción, se recomienda UUID generado por la app y registrado en Odoo.

---

## 4. Comunicación con Odoo

### Endpoints que consume la App Conductor

#### 4.1 `POST /api/get_conductores`

Obtiene los datos del conductor y sus portones autorizados.

**Frecuencia:** Al iniciar sesión / al refrescar manualmente.

**Request:**
```json
{
    "jsonrpc": "2.0",
    "method": "call",
    "params": {
        "cedula": "12345678"
    }
}
```

> **Nota:** El endpoint original no filtra por cédula; devuelve todos los conductores. La app conductor debe filtrar localmente, o el backend debe implementar filtrado por cédula.

**Response exitosa:**
```json
{
    "jsonrpc": "2.0",
    "result": {
        "success": true,
        "message": "ok",
        "data": [
            {
                "id": 1,
                "nombre": "Juan Pérez",
                "cedula": "12345678",
                "placas": "ABC-1234",
                "estado": "activo",
                "puertas_autorizadas": [
                    {"mac_address": "A1:B2:C3:D4:E5:F6"},
                    {"mac_address": "11:22:33:44:55:66"}
                ]
            }
        ]
    }
}
```

**Campos que la App Conductor debe extraer:**
- `nombre` — Nombre del conductor
- `cedula` — Cédula de identidad
- `placas` — Placas del vehículo
- `estado` — Estado en el sistema (activo/inactivo)
- `puertas_autorizadas` — Lista de MAC addresses de los portones a los que tiene acceso

#### 4.2 `POST /api/control_acceso` (acción: `create`)

Registra un nuevo ingreso en Odoo. Se llama después de que la App Admin escanea el QR del conductor y confirma el registro.

> **Nota:** Este endpoint lo llama la **App Admin**, no la App Conductor directamente. La App Conductor solo genera el QR; la Admin decide si registra.

---

## 5. El QR del Conductor (Identidad)

### Propósito

El QR de identidad que la App Conductor genera en pantalla contiene la información necesaria para que la App Admin identifique al conductor, verifique sus datos y sepa qué portones tiene autorizados.

### Formato

```kotlin
@Serializable
data class UserData(
    val u: String,          // Nombre del conductor
    val c: String,          // Cédula de identidad
    val p: List<String>,    // Lista de placas de vehículos
    val aid: String         // Android ID cifrado (AES/GCM)
)
```

**Ejemplo de JSON en el QR:**
```json
{
    "u": "Juan Pérez",
    "c": "12345678",
    "p": ["ABC-1234"],
    "aid": "aGVsbG93b3JsZA==:cGFzc3dvcmQxMjM0NTY3ODkw"
}
```

### Campo `aid` — Android ID Cifrado

El `aid` contiene el Android ID del dispositivo del conductor cifrado con AES-256-GCM.

#### Clave compartida

```kotlin
const val SHARED_AES_KEY = "GabyQrSecureKey12345678901234567"
```

Es una clave de 32 bytes (256 bits). **Debe ser idéntica en ambas aplicaciones** (Admin y Conductor).

#### Formato del `aid`

Puede venir en dos formatos para compatibilidad:

1. **IV:Payload separado por `:`**
   ```
   ivBase64:ciphertextBase64
   ```
   Ejemplo: `"aGVsbG93b3JsZA==:cGFzc3dvcmQxMjM0NTY3ODkw"`

2. **IV + Payload contiguos en Base64**
   ```
   base64(iv12bytes + ciphertext)
   ```
   Un solo blob Base64 con los primeros 12 bytes como IV y el resto como texto cifrado.

#### Proceso de cifrado (App Conductor)

```kotlin
val keyBytes = SHARED_AES_KEY.toByteArray()  // 32 bytes
val secretKeySpec = SecretKeySpec(keyBytes, "AES")

val cipher = Cipher.getInstance("AES/GCM/NoPadding")
cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
val iv = cipher.iv  // 12 bytes generados automáticamente
val encryptedBytes = cipher.doFinal(androidId.toByteArray())

// Formato: base64(iv) + ":" + base64(ciphertext)
val aid = Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
          Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
```

#### Parámetros GCM

| Parámetro | Valor |
|---|---|
| Algoritmo | AES/GCM/NoPadding |
| Clave | 256 bits (32 bytes) |
| IV | 12 bytes (96 bits) — generado por `Cipher.getIV()` |
| Tag | 128 bits |
| Modo | GCM (Galois/Counter Mode) — cifrado autenticado |

---

## 6. Flujo de Generación del QR de Identidad

```
App Conductor inicia sesión / refresca datos
       │
       ▼
Llama a POST /api/get_conductores (o usa datos cacheados)
       │
       ▼
Construye UserData con nombre, cédula, placas, aid cifrado
       │
       ▼
Serializa a JSON con kotlinx.serialization
       │
       ▼
Genera QR Code con ZXing (QRCodeWriter.encode)
       │
       ▼
Muestra el QR en pantalla para que la Admin lo escanee
```

### Almacenamiento local

La App Conductor debe cachear localmente:
- URL del servidor Odoo (del provisioning)
- Android ID
- Nombre, cédula, placas (últimos datos conocidos)
- Lista de portones autorizados con sus MACs

Para evitar llamadas HTTP innecesarias, puede refrescar los datos cada N minutos o al pull-to-refresh.

---

## 7. Portones Autorizados — UI de Chips

### Qué debe mostrar la App Conductor

La App Conductor debe mostrar los portones a los que tiene acceso como **chips interactivos** (Material3 `FilterChip` o `AssistChip`), siguiendo el mismo patrón que la App Admin usa en `ActiveUserCard`.

### Diseño de la UI

```
┌─────────────────────────────────────┐
│  Tus Portones Autorizados           │
│                                     │
│  ┌──────────┐  ┌──────────┐        │
│  │ Principal │  │ Visit.   │        │
│  │           │  │ Norte    │        │
│  └──────────┘  └──────────┘        │
│                                     │
│  ┌──────────┐  ┌──────────┐        │
│  │  Patio 2 │  │ Emerg.   │        │
│  │           │  │          │        │
│  └──────────┘  └──────────┘        │
│                                     │
│  [ Botón: "Abrir Portón" ]         │
└─────────────────────────────────────┘
```

### Especificación de los chips

- Cada chip representa un portón al que el conductor tiene acceso
- Se obtienen de `puertas_autorizadas` en la respuesta de `get_conductores`
- La App Conductor debe **resolver la MAC a un nombre** usando el endpoint `POST /api/v1/gates/list` (o teniendo los nombres cacheados localmente)
- Al tocar un chip, se selecciona ese portón para abrir
- Un chip seleccionado cambia de color (usando `selected` state de FilterChip)
- Solo un portón puede estar seleccionado a la vez (o múltiples, según diseño)

### Resolución MAC → Nombre

La App Conductor puede:
1. Llamar a `GET /api/v1/gates/list` (o POST según implementación) para obtener la lista completa de portones con nombre + MAC
2. Cachear localmente el mapeo MAC → nombre
3. Hacer lookup local al mostrar los chips

### Actualización de la lista

La lista de portones autorizados se refresca:
- Al abrir la app
- Al pull-to-refresh
- Cada 5 minutos en background (opcional)

---

## 8. Apertura de Portón

### Opción A: Vía Odoo (recomendada)

La App Conductor solicita la apertura a Odoo, que a su vez llama al ESP32:

```
App Conductor
    │  POST /api/control_acceso?action=abrir&cedula=...&gate_mac=...
    ▼
Odoo
    │  GET http://{esp32_ip}/abrir?token=secreto123
    ▼
ESP32 activa relé por 1 segundo
```

### Opción B: Directa al ESP32 (sin Odoo)

La App Conductor se conecta al ESP32 vía HTTP a su IP local y llama al endpoint `/abrir`:

```
App Conductor
    │  GET http://{esp32_ip}/abrir?token=secreto123
    ▼
ESP32 activa relé por 1 segundo
```

**Requisitos:**
- La App Conductor debe conocer la IP del ESP32 (obtenida de Odoo vía `GET /api/v1/gates/list`)
- Debe estar en la misma red WiFi que el ESP32
- El token `secreto123` está hardcodeado en el firmware del ESP32

### Visualización de estado

Mientras se solicita la apertura:
- Mostrar indicador de carga en el chip seleccionado
- Mostrar feedback visual de éxito (verde) o error (rojo)
- Puede incluir animación de "abriendo..." con temporizador

---

## 9. Mapa Completo de Endpoints

### Endpoints que consume la App Conductor

| Endpoint | Método | Propósito | Quién llama |
|---|---|---|---|
| `POST /api/get_conductores` | HTTP POST | Obtener datos del conductor y portones autorizados | App Conductor |
| `GET /api/v1/gates/list` | HTTP GET/POST | Obtener lista de portones (nombre + MAC + IP) | App Conductor |
| `GET http://{esp32_ip}/abrir?token=...` | HTTP GET | Abrir portón directamente (sin Odoo) | App Conductor |
| `POST /api/control_acceso` | HTTP POST | (Futuro) Solicitar apertura vía Odoo | App Conductor |

### QRs que intercambian las apps

| QR | Generado por | Consumido por | Contenido |
|---|---|---|---|
| QR de identidad | App Conductor | App Admin (cámara) | u, c, p, aid cifrado |
| QR de aprovisionamiento | App Admin (ResultScreen o HomeScreen) | App Conductor | endpoint + token |

---

## 10. Consideraciones Técnicas

### Stack sugerido para la App Conductor

| Componente | Recomendación |
|---|---|
| Lenguaje | Kotlin |
| UI | Jetpack Compose + Material3 |
| Serialización | `kotlinx.serialization` |
| HTTP | OkHttp (o Ktor Client) |
| QR Generation | ZXing `core` |
| QR Scanning | ML Kit Barcode Scanning o ZXing `android-core` |
| Cifrado | `javax.crypto.Cipher` (AES/GCM/NoPadding) |
| Almacenamiento local | SharedPreferences o DataStore |

### Seguridad

- El Android ID viaja cifrado en el QR (AES-256-GCM)
- La clave AES está hardcodeada — **debe coincidir** con la de la App Admin
- El token de provisioning (`ALCARAVAN_2025`) debe coincidir
- El token del ESP32 (`secreto123`) está hardcodeado en el firmware
- No hay autenticación adicional en los endpoints de Odoo (beta)
- Se recomienda implementar JWT en producción

### Manejo de errores

| Escenario | Acción |
|---|---|
| No hay URL de Odoo configurada | Mostrar pantalla de provisioning (escanear QR) |
| Odoo no responde | Mostrar error de conexión, usar datos cacheados |
| Conductor no encontrado en Odoo | Mostrar "Usuario no registrado. Contacte al administrador." |
| ESP32 no responde | Mostrar "Portón no disponible. Intente más tarde." |
| Token de provisioning inválido | Rechazar configuración, mostrar "QR inválido" |

---

## 11. Novedades respecto a la versión anterior

| Aspecto | Anterior | Nueva versión |
|---|---|---|
| QR del conductor | Solo u, c, p, aid (sin gates) | Mismos campos (la MAC se resuelve del lado de la app) |
| Lista de portones | No disponible | Obtenida de `puertas_autorizadas` en `get_conductores` |
| UI de portones | No existía | Chips interactivos (FilterChip) con selección y apertura |
| Apertura de portón | No disponible | Vía HTTP directo al ESP32 o a través de Odoo |
| Provisioning | Solo endpoint (desde HomeScreen) | ResultScreen tras registro + HomeScreen manual |
| Flujo de registro | No documentado explícitamente | Identidad QR → escaneo → Odoo → provisioning QR |
| Cache de datos | No especificado | Cache local de datos + portones con refresco periódico |
