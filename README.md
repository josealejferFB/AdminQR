# EscanQR Admin

Aplicación Android de consola de administración para el sistema de control de acceso vehicular **EscanQR**. Gestiona usuarios, sincroniza con un servidor **Odoo** vía JSON-RPC, y configura dispositivos **ESP32** por Bluetooth para apertura automatizada de portones.

> **Documentación completa en [`docs/`](docs/):** Arquitectura, contratos de API con Odoo, contrato entre apps (QR + cifrado), y contrato de comunicación con ESP32.

---

## Tabla de Contenidos

- [Stack Tecnológico](#stack-tecnológico)
- [Arquitectura](#arquitectura)
- [Flujo de Navegación](#flujo-de-navegación)
- [Pantallas](#pantallas)
- [Comunicaciones](#comunicaciones)
- [Modelo de Seguridad](#modelo-de-seguridad)
- [Flujo de Datos](#flujo-de-datos--escenario-completo)
- [Desarrollo](#desarrollo)
- [Dispositivo ESP32](#dispositivo-esp32--sketch-arduino)
- [Documentación](#documentación)

---

## Stack Tecnológico

| Componente | Versión |
|---|---|
| Kotlin | 2.0.21 |
| Android Gradle Plugin | 8.7.3 |
| KSP | 2.0.21-1.0.27 |
| Compile / Target SDK | 35 |
| Min SDK | 26 |
| Java | 17 |
| Hilt (DI) | 2.51.1 |
| Jetpack Compose BOM | 2024.09.00 |
| Navigation Compose | 2.8.3 |
| CameraX | 1.3.4 |
| ML Kit Barcode Scanning | 17.3.0 |
| OkHttp | 4.12.0 |
| ZXing core | 3.5.3 |
| kotlinx-serialization | 1.7.3 |

---

## Arquitectura

Clean Architecture con tres capas, MVVM, e inyección de dependencias vía **Hilt**. UI reactiva con **Jetpack Compose + Material3**.

```
┌──────────────────────────────────────────────────────────────┐
│                    PRESENTATION (Compose + ViewModels)        │
│  Splash · Home · Scanner · Result · Config · ESPConfig       │
│  ViewModel → StateFlow/SharedFlow → Composable               │
├──────────────────────────────────────────────────────────────┤
│                        DOMAIN                                │
│  Modelos: QrContent, UserData, ProvisioningPayload, etc.     │
│  Interfaces: SyncRepository, BluetoothRepository, etc.       │
├──────────────────────────────────────────────────────────────┤
│                        DATA                                  │
│  OkHttp (JSON-RPC) · Bluetooth Classic SPP · SharedPrefs     │
│  ApiConstants (singleton, config en api_config_prefs)        │
├──────────────────────────────────────────────────────────────┤
│                    DI (Hilt — SingletonComponent)             │
│  RepositoryModule: Binds (4) + Provides (OkHttpClient, Scope)│
└──────────────────────────────────────────────────────────────┘
```

**Principios:**
- Single Activity con NavHost y rutas `@Serializable`
- Unidirectional Data Flow: UI → Event → ViewModel → State → UI
- `StateFlow` para estado persistente, `SharedFlow` para eventos one-shot (snackbars)
- ViewModel por feature, inyectado con Hilt

---

## Flujo de Navegación

```
Splash ──(2s delay)──▶ Home
                          │
                          ├──▶ Scanner ──▶ Result(datos)
                          │                   │
                          │                   └──▶ Home
                          ├──▶ Config
                          └──▶ ESPConfig (vía Bluetooth)
```

Las 6 rutas están definidas como clases `@Serializable` en `presentation/navigation/NavDestinations.kt`.

---

## Pantallas

### Splash Screen
Animación de entrada con halo radial, logo con rebote Spring, y texto deslizante. Tres estados: `Idle → Animating → Completed`. Navega a Home y limpia el back stack.

### Home — Panel de Control
Dashboard principal con:
- Indicador de conexión al servidor (online/offline)
- Panel de conexión Bluetooth (vincular/desconectar ESP32)
- Métricas: escaneos totales + usuarios registrados
- Aprovisionamiento: QR con endpoint + token para la app de conductor
- Lista de usuarios activos con búsqueda, edición y eliminación
- Pull-to-refresh que re-sincroniza con Odoo
- Toggle de modo oscuro (persistente en `theme_prefs`)

### Scanner — Lector QR
Cámara en tiempo real con **CameraX + ML Kit Barcode Scanning**.
- Overlay de escaneo con recuadro animado y línea de barrido
- Linterna toggle
- Ingreso manual como fallback
- Descifrado AES-256-GCM del `androidId` extraído del QR
- Snackbar de error en QR inválido

### Result — Sincronización
Flujo de dos pasos: (1) registra en Odoo vía JSON-RPC, (2) muestra QR de acceso. Estados: `Idle → Loading → Success | Error`.

### Config — Configuración de Red
Formulario para configurar protocolo, host, puerto y endpoints del servidor Odoo. Historial de servidores persistido en `SharedPreferences` como JSON (`server_history_v2`).

### ESPConfig — Consola Bluetooth
Terminal interactiva para configurar un ESP32 conectado por Bluetooth SPP.
- Comandos rápidos: `config` (configurar URL de Odoo) y `wifi` (configurar WiFi)
- Formularios guiados según la respuesta del ESP32
- Consola con burbujas TX/RX y scroll automático
- Auto-reset al desconectarse

---

## Comunicaciones

### Con Odoo (HTTP JSON-RPC)

```
POST {protocolo}://{host}:{port}{endpoint}
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "method": "call",
  "params": { "action": "create", "cedula": "...", "nombre": "...", "placas": "..." }
}
```

| Endpoint | Método | Propósito |
|---|---|---|
| `/api/control_acceso` | POST | CRUD de registros (create, update, delete) |
| `/api/get_conductores` | POST | Obtener lista de conductores |

La URL se construye desde `ApiConstants` con valores de `SharedPreferences` (`api_config_prefs`). Valores por defecto: `http://172.17.12.119:8059`.

### Con ESP32 (Bluetooth SPP)

| Aspecto | Detalle |
|---|---|
| Perfil | Bluetooth Classic SPP (UUID `00001101-0000-1000-8000-00805F9B34FB`) |
| Fallback | Reflexión a canal 1 |
| Descubrimiento | Filtro por nombre que empiece con `"ESP32"` (case-insensitive) |
| Nombre BT del ESP32 | `ESP32_Seguro` |

**Máquina de estados del protocolo:** solo acepta `config` y `wifi`. Cada comando inicia un flujo guiado con timeouts de 30-60s. Ver `docs/Contrato-ESP32.md` para el detalle completo.

### Con ESP32 (HTTP local — del Odoo al ESP32)

El ESP32 corre un WebServer en puerto 80. Odoo llama a `GET /abrir?token=secreto123` para activar el relé (1s). El ESP32 también reporta su IP a Odoo vía `POST /api/update_esp_ip` al iniciar o reconfigurar.

---

## Modelo de Seguridad

| Aspecto | Mecanismo |
|---|---|
| **Datos en QR** | Android ID cifrado con AES-256-GCM (`SHARED_AES_KEY`) |
| **Aprovisionamiento** | QR con `{"endpoint":"...","token":"PROVISIONING_TOKEN"}` |
| **API ESP32** | Token `"secreto123"` validado en cada GET `/abrir` |
| **Red local** | HTTP plano (cleartext permitido vía `network_security_config.xml`) |
| **Persistencia** | SharedPreferences sin cifrado |

> ⚠️ Claves hardcodeadas (`SHARED_AES_KEY`, `PROVISIONING_TOKEN`, `API_TOKEN`). Migrar a Android Keystore para producción.

---

## Flujo de Datos — Escenario Completo

```
┌──────────┐    ┌──────────────┐    ┌──────────┐    ┌───────────┐
│  Admin    │    │  App Admin    │    │  Odoo     │    │  ESP32     │
│  (humano) │    │  (Android)    │    │  Server   │    │  (físico)  │
└─────┬─────┘    └──────┬───────┘    └────┬─────┘    └─────┬─────┘
      │                 │                  │                │
      │ 1. Configura    │                  │                │
      │    endpoint     │                  │                │
      │────────────────▶│                  │                │
      │                 │                  │                │
      │ 2. Escanea QR   │                  │                │
      │    del conductor │                  │                │
      │────────────────▶│                  │                │
      │                 │  3. POST JSON-RPC│                │
      │                 │  /api/control_acceso             │
      │                 │─────────────────▶│                │
      │                 │                  │                │
      │ 4. Recibe QR    │◀─────────────────│                │
      │    de acceso    │    success       │                │
      │◀────────────────│                  │                │
      │                 │                  │                │
      │ 5. Conecta BT   │                  │                │
      │    al ESP32     │                  │                │
      │────────────────▶│                  │                │
      │                 │  6. "config"     │                │
      │                 │  {"ip_odoo":...} │                │
      │                 │─────────────────────────────────▶│
      │                 │                  │                │
      │                 │                  │  7. GET /abrir │
      │                 │                  │  ?token=...    │
      │                 │                  │────────────────▶│
      │                 │                  │                │
      │                 │                  │  8. POST       │
      │                 │                  │  /api/update_esp_ip │
      │                 │                  │◀────────────────│
```

---

## Desarrollo

### Prerrequisitos

- Android Studio Hedgehog (2023.1.1+) o Koala
- JDK 17
- Dispositivo físico con Android 8.0+ (API 26) para Bluetooth y cámara

### Comandos

```bash
./gradlew assembleDebug          # Build APK debug
./gradlew test                   # Tests unitarios
./gradlew connectedAndroidTest   # Tests instrumentados
./gradlew clean                  # Limpiar build
```

### Convenciones

- Código y commits en español
- Seguir estilo existente; no agregar comentarios triviales
- Dependencias nuevas primero en `gradle/libs.versions.toml`

---

## Dispositivo ESP32 — Sketch Arduino

El repositorio incluye `VerificacionHuellasV6.ino` (en la raíz) que implementa:

| Componente | Función |
|---|---|
| OLED SSD1306 | Display I2C para estado y IP |
| BluetoothSerial | Recepción de comandos `wifi` y `config` |
| WiFi | Conexión a red configurada vía BT |
| WebServer | Puerto 80, endpoint `GET /abrir?token=...` |
| Relé | GPIO 0, activación por 1s |
| NVS (Preferences) | Persistencia de SSID, pass, odoo_url |

**Estados del firmware:** `ESPERA_CONEXION → MODO_CONFIG_BT → {MODO_CONFIG_ODOO | MODO_WIFI_SSID → MODO_WIFI_PASS}` con timeouts de 30-60s.

---

## Documentación

Toda la documentación técnica está en [`docs/`](docs/):

| Archivo | Contenido |
|---|---|
| [`docs/Arquitectura.md`](docs/Arquitectura.md) | Stack, Clean Architecture, MVVM, estructura de carpetas, librerías, diseño UI/UX |
| [`docs/Contrato-Odoo.md`](docs/Contrato-Odoo.md) | Endpoints JSON-RPC, payloads, construcción de URLs, WebServer del ESP32 |
| [`docs/Contrato-App-Usuario.md`](docs/Contrato-App-Usuario.md) | Formato QR, cifrado AES/GCM, aprovisionamiento, tokens y claves |
| [`docs/Contrato-ESP32.md`](docs/Contrato-ESP32.md) | Bluetooth SPP, máquina de estados V6, comandos, console UI |
| [`docs/directrices_de_diseno.md`](docs/directrices_de_diseno.md) | Tokens de color, AppCard, animaciones Spring, dark mode |

---

*Documentación actualizada: junio 2026.*
