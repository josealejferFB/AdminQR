# Contrato App Usuario — EscanQR Admin

## Contexto

**EscanQR Admin** es la aplicación administradora del sistema de control de acceso vehicular. Se comunica con la **App de Conductor** (dispositivo del usuario final) exclusivamente a través de **códigos QR**.

No hay comunicación directa entre la app Admin y la app de Conductor (ni Bluetooth, ni red local, ni API). El QR es el único canal de intercambio de información.

## Flujo de Escaneo

```
App Conductor genera QR (en su dispositivo)
        │
        ▼
App Admin escanea QR con cámara (CameraX + ML Kit)
        │
        ▼
ScannerViewModel.processBarcode(rawValue)
        │
        ├─ Deserializa JSON → UserData
        ├─ Descifra androidId (AES/GCM)
        └─ Crea QrContent → navega a ResultScreen
```

## Formato del QR

### Estructura del dato escaneado

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

El `aid` contiene el identificador único del dispositivo del conductor cifrado con **AES-256 en modo GCM** (Galois/Counter Mode).

#### Clave compartida

```kotlin
const val SHARED_AES_KEY = "GabyQrSecureKey12345678901234567"
```

Es una clave de 32 bytes (256 bits) hardcodeada en `SecurityConstants.kt`. Debe ser idéntica en ambas aplicaciones (Admin y Conductor) para que el descifrado funcione.

#### Formato del `aid`

El `aid` puede venir en dos formatos:

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

#### Proceso de descifrado

```kotlin
val keyBytes = SHARED_AES_KEY.toByteArray()  // 32 bytes
val secretKeySpec = SecretKeySpec(keyBytes, "AES")

// Extraer IV (12 bytes) y ciphertext
val cipher = Cipher.getInstance("AES/GCM/NoPadding")
val spec = GCMParameterSpec(128, ivBytes)  // 128 bits de tag
cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, spec)
val decryptedBytes = cipher.doFinal(encryptedBytes)
return String(decryptedBytes, Charsets.UTF_8)
```

- **Tag GCM:** 128 bits
- **IV:** 12 bytes (96 bits) — recomendación NIST
- **Padding:** NoPadding (GCM es un modo de cifrado autenticado que no requiere padding)

## QR de Aprovisionamiento (Provisioning)

### Propósito

Permitir que la **App de Conductor** configure automáticamente la URL del servidor Odoo escaneando un código QR generado por la app Admin.

### Generación

```kotlin
// QrUtils.kt
fun buildProvisioningJson(): String =
    """{"endpoint":"${ApiConstants.BASE_URL}","token":"${SecurityConstants.PROVISIONING_TOKEN}"}"""
```

### Contenido del QR

```json
{
    "endpoint": "http://172.17.12.119:8059",
    "token": "ALCARAVAN_2025"
}
```

- **endpoint:** URL base del servidor Odoo configurado en la app Admin
- **token:** Token de provisionamiento compartido (`PROVISIONING_TOKEN` en `SecurityConstants.kt`)

### Uso

1. Admin va a Home → toca tarjeta "Aprovisionar Conductor"
2. Se muestra un QR con el JSON anterior
3. Conductor escanea con su app → su app extrae `endpoint` y `token` → configura automáticamente la conexión

### Implementación del QR

```kotlin
// En HomeScreen.kt, usando ZXing:
@Composable
fun QrCodeBox(content: String, size: Dp) {
    // Genera Bitmap con QRCodeWriter de ZXing
    val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    // Renderiza como Image composable
}
```

## Modelo Interno (QrContent)

Una vez procesado el escaneo, los datos se normalizan en:

```kotlin
data class QrContent(
    val androidId: String,   // Android ID descifrado
    val userName: String,    // Nombre del conductor
    val cedula: String,      // Cédula de identidad
    val plate: String        // Placas concatenadas (join ", ")
)
```

## Flujo de Registro

1. Admin escanea QR → datos descifrados → vista previa en `ResultScreen`
2. Admin confirma → `syncRepository.syncEntry(data)` envía a Odoo
3. Si Odoo responde OK → se guarda en `HistoryRepository` (memoria) y se muestra QR de acceso
4. El QR de acceso puede ser escaneado por el ESP32 para abrir el portón (aunque actualmente el ESP32 abre vía HTTP desde Odoo, no escanea QR)

## Tokens y Claves (Resumen)

| Constante | Valor | Propósito |
|---|---|---|
| `SHARED_AES_KEY` | `GabyQrSecureKey12345678901234567` | Descifrado de Android ID en QR escaneado |
| `PROVISIONING_TOKEN` | `ALCARAVAN_2025` | Token de provisionamiento en QR de configuración |

> **⚠️ Importante:** Ambas claves están hardcodeadas en el código fuente. Cualquier cambio en estas claves debe ser reflejado en ambas aplicaciones (Admin y Conductor) simultáneamente.
