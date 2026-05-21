# EscanQR Admin

Aplicación Android de consola de administración para el sistema de control de acceso vehicular **EscanQR**. Gestiona usuarios, sincroniza con un servidor **Odoo** vía JSON-RPC, y configura dispositivos **ESP32** por Bluetooth para apertura automatizada de portones.

> **Repositorio compañero:** [EscanQR User App](https://github.com/tuusuario/escanqr-user) — Aplicación de conductor que lee el QR de aprovisionamiento.

---

## Tabla de Contenidos

- [Arquitectura](#arquitectura)
- [Stack Tecnológico](#stack-tecnológico)
- [Flujo de Navegación](#flujo-de-navegación)
- [Pantallas](#pantallas)
  - [Splash](#-splash-screen)
  - [Home — Panel de Control](#-home--panel-de-control)
  - [Scanner — Lector QR](#-scanner--lector-qr)
  - [Result — Sincronización](#-result--sincronización)
  - [Config — Configuración de Red](#-config--configuración-de-red)
  - [ESPConfig — Consola Bluetooth](#-espconfig--consola-bluetooth)
- [Comunicaciones](#comunicaciones)
  - [Con Odoo (HTTP JSON-RPC)](#con-odoo-http-json-rpc)
  - [Con ESP32 (Bluetooth SPP)](#con-esp32-bluetooth-spp)
  - [Con ESP32 (HTTP local)](#con-esp32-http-local)
- [Modelo de Seguridad](#modelo-de-seguridad)
- [Flujo de Datos — Escenario Completo](#flujo-de-datos--escenario-completo)
- [Desarrollo](#desarrollo)
- [Estructura del Proyecto](#estructura-del-proyecto)
- [Dispositivo ESP32 — Sketch Arduino](#dispositivo-esp32--sketch-arduino)
- [Configuración](#configuración)

---

## Arquitectura

Clean Architecture con tres capas, inyección de dependencias vía **Hilt** y UI reactiva con **Jetpack Compose + Material3**.

```
┌──────────────────────────────────────────────────────────┐
│                     PRESENTATION                          │
│  Splash · Home · Scanner · Result · Config · ESPConfig   │
│  ViewModels → StateFlow → Compose (Material3)            │
├──────────────────────────────────────────────────────────┤
│                       DOMAIN                              │
│  Modelos: QrContent, UserData, ActiveUser, etc.          │
│  Interfaces: Repository (definiciones)                    │
├──────────────────────────────────────────────────────────┤
│                        DATA                               │
│  Implementaciones de repositorios                         │
│  Network: OkHttp (JSON-RPC), DTOs                        │
│  Bluetooth: Classic SPP + reflection fallback            │
│  Persistencia: SharedPreferences + in-memory StateFlow   │
├──────────────────────────────────────────────────────────┤
│                      DI (Hilt)                            │
│  RepositoryModule · AppModule · ViewModelModule          │
└──────────────────────────────────────────────────────────┘
```

### Principios aplicados

- **Single Activity**: `MainActivity` con NavHost y rutas tipadas `@Serializable`
- **ViewModel por feature**: cada pantalla tiene su propio ViewModel inyectado por Hilt
- **Unidirectional Data Flow**: UI → Event → ViewModel → State → UI
- **SharedFlow para eventos one-shot** (snackbars, navegación)
- **StateFlow para estado persistente**: expuesto con `stateIn(WhileSubscribed(5000))`

---

## Stack Tecnológico

| Componente | Versión |
|---|---|
| Kotlin | 2.0.21 |
| Android Gradle Plugin | 8.7.3 |
| KSP | 2.0.21-1.0.25 |
| Compile / Target SDK | 35 |
| Min SDK | 26 |
| Java | 17 |
| Hilt (DI) | 2.51.1 |
| Jetpack Compose BOM | 2024.09.00 |
| Material 3 | via BOM |
| Navigation Compose | 2.8.3 |
| CameraX | 1.3.4 |
| ML Kit Barcode Scanning | 17.3.0 |
| OkHttp (con logging interceptor) | 4.12.0 |
| ZXing core | 3.5.3 |
| kotlinx-serialization | 1.7.3 |
| hilt-navigation-compose | 1.2.0 |

---

## Flujo de Navegación

```
Splash ──(2s delay)──▶ Home
                          │
                          ├──▶ Scanner ──▶ Result
                          │                   │
                          │                   └──▶ Home (back)
                          │
                          ├──▶ Config
                          │
                          └──▶ ESPConfig
```

Todas las rutas están definidas como clases `@Serializable` en `presentation/navigation/NavDestinations.kt`, evitando rutas basadas en strings.

---

## Pantallas

### 🎬 Splash Screen

Animación de entrada con efecto de rebote y halo radial (`graphicsLayer` + `alpha` + `scale`). Estado manejado por `SplashViewModel` con un `SealedInterface` de tres estados: `Idle → Animating → Completed`. Al completar, navega a `Home` y limpia el back stack.

### 🏠 Home — Panel de Control

Pantalla principal con las siguientes secciones:

| Sección | Descripción |
|---|---|
| **Estado del servidor** | Indicador online/offline con el endpoint configurado |
| **Conexión Bluetooth** | Botón para vincular/desconectar un ESP32 (`BluetoothAdapter` + `Bond` / `removeBond`) |
| **Métricas** | Tarjetas `StatCard` con escaneos totales y usuarios registrados |
| **Aprovisionamiento** | Diálogo `ProvisioningQrDialog` que genera un QR con endpoint + token para la app de conductor (ZXing `QRCodeWriter`) |
| **Búsqueda y CRUD** | `SearchBar` + lista de `ActiveUserCard` con expansión animada para editar nombre/placa, o eliminar usuario |
| **Pull-to-refresh** | `SwipeToRefresh` que re-sincroniza con el servidor |
| **Dark Mode** | Toggle persistente en `ThemeRepository` (SharedPreferences `theme_prefs`), expuesto como `Flow<Boolean>` |

### 📷 Scanner — Lector QR

Cámara en tiempo real con detección de códigos QR mediante **CameraX** + **ML Kit Barcode Scanning**.

**Características:**

- **Overlay de escaneo**: recuadro animado con línea de barrido y destellos en las esquinas
- **Linterna**: botón de toggle que activa `cameraControl.enableTorch()` en la instancia de `Camera` almacenada
- **Ingreso manual**: `ManualEntryDialog` con campos para *Android ID / Nombre / Cédula / Placa* cuando el QR no puede ser leído
- **Descifrado**: el `androidId` extraído del QR se descifra con AES-256-GCM vía `SecurityConstants.SHARED_AES_KEY`
- **Snackbar de error**: `SharedFlow<String>` emite `"QR inválido: <razón>"` ante fallo de parseo o descifrado
- **Modo continuo**: `setAnalyzer(executor, analyzer)` con detección continua; la pantalla no se cierra automáticamente al leer

### ✅ Result — Sincronización

Flujo de dos pasos posterior al escaneo:

1. **Registro en servidor**: envía `android_id`, `card_id`, `name`, `last_name`, `cedula`, `plate` al endpoint Odoo vía JSON-RPC
2. **QR de aprovisionamiento**: genera un QR que la app de conductor escaneará para obtener la URL del servidor

**Estados de sincronización** (`SyncStatus`):

```
Idle ──▶ Loading ──▶ Success
                └──▶ Error(mensaje)
```

La barra de estado animada (`ResultSnackbar`) se desliza desde arriba con color codificado: azul (cargando), verde (éxito), rojo (error).

### ⚙️ Config — Configuración de Red

Permite configurar la conexión al servidor Odoo:

- **Protocolo**: HTTP / HTTPS
- **Host**: dirección IP o dominio
- **Puerto**: numérico
- **Endpoints**: rutas personalizables para `sync` y `conductores`

La URL se construye en vivo y se muestra como previsualización. El historial de servidores se persiste en `SharedPreferences` como JSON array bajo la clave `server_history_v2`. `HistoryRepository` (in-memory) mantiene el estado actual.

### 🔧 ESPConfig — Consola Bluetooth

Interfaz de terminal para configurar un ESP32 conectado por Bluetooth SPP.

**Características:**

- **Conexión Bluetooth**: máquina de estados replicada en `connectionState: StateFlow<BtConnectionState>` con valores `Disconnected`, `Scanning`, `Connecting`, `Connected`, `Error(message)`
- **Indicador en TopBar**: muestra el estado real de la conexión con colores (verde = Conectado, rojo = Error, gris = Desconectado)
- **Comandos**:
  - `wifi` → ingresa SSID → ingresa password → envía línea por línea
  - `config` → edita `ip_odoo:port` en JSON → envía el JSON al ESP32
- **Interfaz tipo terminal**: burbujas de mensajes TX (azul) y RX (gris) con scroll automático (`LazyColumn` + `animateTo` + `scrollToItem`)
- **Auto-reset**: al desconectarse Bluetooth durante un flujo activo, el formulario se resetea automáticamente vía `LaunchedEffect`
- **Seguridad contra inyección**: los comandos se construyen con `buildJsonObject {}` en lugar de interpolación de strings

---

## Comunicaciones

### Con Odoo (HTTP JSON-RPC)

```
POST {protocolo}://{host}:{puerto}/{endpoint}
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "method": "call",
  "params": {
    "android_id": "cifrado_aes256",
    "card_id": "...",
    "name": "...",
    "cedula": "...",
    "plate": "..."
  },
  "id": 1
}
```

**Implementación**: `SyncRepositoryImpl` con OkHttp directo (sin Retrofit). Las URLs se construyen desde `ApiConstants` cuyos valores provienen de `SharedPreferences` (`api_config_prefs`).

**Logging**: `HttpLoggingInterceptor` a nivel `BODY` en builds debug, `NONE` en release (determinado por `ApplicationInfo.flags & FLAG_DEBUGGABLE`).

### Con ESP32 (Bluetooth SPP)

| Aspecto | Detalle |
|---|---|
| Perfil | Bluetooth Classic SPP |
| UUID | `00001101-0000-1000-8000-00805F9B34FB` (SPP estándar) |
| Fallback | `createRfcommSocketToServiceRecord` + reflexión a canal 1 |
| Descubrimiento | Filtro por nombre que empiece con `"ESP32"` (case-insensitive) |
| Hilo | `BluetoothRepositoryImpl` opera en `Dispatchers.IO` dentro de un `CoroutineScope(SupervisorJob())` inyectado |

**Protocolo de comandos:**

```
Admin → ESP32: "config"
ESP32 → Admin: "Send config data:"
Admin → ESP32: {"ip_odoo": "192.168.1.100:8069"}

Admin → ESP32: "wifi"
ESP32 → Admin: "Send wifi ssid:"
Admin → ESP32: "MiRedWifi"
ESP32 → Admin: "Send wifi pass:"
Admin → ESP32: "MiClaveWifi"
```

### Con ESP32 (HTTP local)

El ESP32 expone un servidor HTTP en puerto 80. Odoo envía peticiones a `http://{ip_esp32}/abrir?token={API_TOKEN}` para activar el relé de apertura.

---

## Modelo de Seguridad

| Aspecto | Mecanismo |
|---|---|
| **Datos en QR** | Android ID cifrado con **AES-256-GCM** usando `SecurityConstants.SHARED_AES_KEY` |
| **Aprovisionamiento** | QR contiene `{ "endpoint": "...", "token": "PROVISIONING_TOKEN" }` para app de conductor |
| **API ESP32** | Token hardcodeado (`API_TOKEN`) validado en cada petición HTTP al ESP32 |
| **Red local** | Tráfico HTTP en red local (sin TLS); `network_security_config.xml` permite cleartext |
| **Persistencia** | Historial de servidores en SharedPreferences como JSON (no cifrado) |

> ⚠️ Las claves `SHARED_AES_KEY`, `PROVISIONING_TOKEN` y `API_TOKEN` están actualmente hardcodeadas en `SecurityConstants.kt`. En una versión de producción se recomienda migrar a **Android Keystore** o un sistema de secrets management.

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
      │                 │  (registrar)     │                │
      │                 │─────────────────▶│                │
      │                 │                  │                │
      │ 4. Recibe QR    │◀─────────────────│                │
      │    aprovision.  │    success       │                │
      │◀────────────────│                  │                │
      │                 │                  │                │
      │ 5. Conecta BT   │                  │                │
      │    al ESP32     │                  │                │
      │────────────────▶│                  │                │
      │                 │  6. "config"     │                │
      │                 │  {"ip_odoo":...} │                │
      │                 │─────────────────────────────────▶│
      │                 │                  │                │
      │                 │                  │  7. HTTP GET   │
      │                 │                  │  /abrir?token= │
      │                 │                  │────────────────▶│
      │                 │                  │                │
```

---

## Desarrollo

### Prerrequisitos

- Android Studio Hedgehog (2023.1.1+) o Koala
- JDK 17
- Gradle 8.x (wrapper incluido)
- Dispositivo físico con Android 8.0+ (API 26) para Bluetooth y cámara

### Comandos

```bash
# Build APK debug
./gradlew assembleDebug

# Ejecutar tests unitarios
./gradlew test

# Ejecutar tests instrumentados
./gradlew connectedAndroidTest

# Limpiar build
./gradlew clean
```

### Convenciones

- **Ramas**: `main` (estable), `Test` (desarrollo activo), `Endpoints-Vía-BT`, `Endpoints-Vía-QR`, `Web-Only-Version`
- **Commits**: en español, descriptivos del cambio
- **Estilo**: seguir el código existente; no agregar comentarios triviales
- **Dependencias**: agregar primero en `gradle/libs.versions.toml` (version catalog)

---

## Estructura del Proyecto

```
app/src/main/java/com/example/escanqradmin/
├── EscanQRApp.kt                          # Application class (Hilt entry point)
├── MainActivity.kt                        # Single Activity (NavHost + system bars)
│
├── data/
│   ├── network/
│   │   ├── ApiConstants.kt                # Endpoints + init(context)
│   │   └── dto/
│   │       ├── SyncRequestDto.kt          # JSON-RPC request body
│   │       └── SyncResponseDto.kt         # JSON-RPC response
│   └── repository/
│       ├── BluetoothRepositoryImpl.kt     # BT SPP + discovery + connect
│       ├── HistoryRepositoryImpl.kt       # In-memory StateFlow
│       ├── ServerConfigRepositoryImpl.kt  # SharedPreferences
│       ├── SyncRepositoryImpl.kt          # OkHttp JSON-RPC
│       └── ThemeRepositoryImpl.kt         # SharedPreferences dark mode
│
├── di/modules/
│   ├── AppModule.kt                       # Application + SharedPreferences
│   └── RepositoryModule.kt                # Hilt @Provides (OkHttpClient, repos, scope)
│
├── domain/
│   ├── model/
│   │   ├── ActiveUser.kt                  # Usuario activo (name, document, plate, status)
│   │   ├── BluetoothDeviceDomain.kt       # BT device model
│   │   ├── QrContent.kt                  # Contenido parseado del QR
│   │   ├── ServerConfig.kt               # Protocolo + host + puerto + endpoints
│   │   └── UserData.kt                   # Datos de usuario completos
│   └── repository/
│       ├── BluetoothRepository.kt         # Interface BT
│       ├── HistoryRepository.kt           # Interface history
│       ├── ServerConfigRepository.kt      # Interface config
│       ├── SyncRepository.kt             # Interface sync
│       └── ThemeRepository.kt            # Interface theme
│
└── presentation/
    ├── common/
    │   ├── sharedcomponents/
    │   │   ├── AppCard.kt                 # Card reutilizable con defaults
    │   │   ├── BarraSuperior.kt           # TopAppBar genérica
    │   │   └── QrCodeImage.kt             # Composable QR unificado (ZXing)
    │   └── util/
    │       ├── QrUtils.kt                 # buildProvisioningJson()
    │       └── SystemBarsVisibility.kt    # Immersive mode helper
    │
    ├── navigation/
    │   └── NavDestinations.kt             # Rutas @Serializable (Home, Scanner, Result, Config, ESPConfig)
    │
    ├── theme/
    │   ├── Color.kt                       # Paleta Material3
    │   ├── Theme.kt                       # EscanQRTheme (dark/light)
    │   └── Type.kt                        # Typography (15 slots Material3)
    │
    └── ui/
        ├── splash/
        │   ├── SplashScreen.kt            # Animación + timer
        │   ├── SplashUiState.kt           # Idle / Animating / Completed
        │   └── SplashViewModel.kt
        │
        ├── home/
        │   ├── HomeScreen.kt              # Dashboard principal
        │   ├── HomeViewModel.kt
        │   └── components/
        │       ├── ActiveUserCard.kt      # Card expandible (editar/borrar)
        │       ├── SearchBar.kt           # Buscador estilizado
        │       └── StatCard.kt            # Tarjeta de métrica
        │
        ├── scanner/
        │   ├── ScannerScreen.kt           # Cámara + overlay + linterna + diálogo manual
        │   ├── ScannerViewModel.kt
        │   └── components/
        │       └── ScanOverlay.kt         # Recuadro + línea de barrido + esquinas
        │
        ├── result/
        │   ├── ResultScreen.kt            # Paso 1 (sync) + Paso 2 (QR)
        │   ├── ResultViewModel.kt
        │   └── components/
        │       └── ResultSnackbar.kt      # Barra animada (loading/success/error)
        │
        ├── config/
        │   ├── ConfigScreen.kt            # Formulario de configuración de red
        │   └── ConfigViewModel.kt
        │
        └── espconfig/
            ├── ESPConfigScreen.kt         # Consola Bluetooth: comandos wifi/config
            └── ESPConfigViewModel.kt      # Estado BT + envío de comandos
```

### Archivos de recursos

| Ruta | Propósito |
|---|---|
| `res/xml/network_security_config.xml` | Permite tráfico HTTP cleartext |
| `res/drawable/` | Assets gráficos (logos, fondos) |
| `res/values/strings.xml` | String `app_name` |
| `res/values/colors.xml` | Colores legacy (Material3 vía Theme.kt) |

---

## Dispositivo ESP32 — Sketch Arduino

El repositorio incluye el sketch `VerificacionHuellasV6.ino` para el ESP32 companion, que implementa:

| Componente | Función |
|---|---|
| **OLED SSD1306** | Display I2C para estado y dirección IP |
| **BluetoothSerial** | Recepción de comandos `wifi` y `config` |
| **WiFi** | Conexión a la red configurada vía BT |
| **WebServer** | Puerto 80, endpoint `GET /abrir?token=...` |
| **Relé** | Activación digital para apertura de portón/barra |
| **Token de seguridad** | Validación en cada petición HTTP entrante |

**Flujo de encendido:** Bluetooth → espera configuración WiFi → WiFi conectado → muestra IP en OLED → sirve endpoint HTTP.

---

## Configuración

### Inicialización

`ApiConstants` requiere que se llame `init(context)` antes de su uso. Esto se realiza en `EscanQRApp.onCreate()`.

### Persistencia

| Archivo | Clave(s) | Propósito |
|---|---|---|
| `api_config_prefs` | protocol, host, port, syncEndpoint, conductoresEndpoint | Configuración de red |
| `theme_prefs` | isDarkMode | Estado del tema oscuro |
| `api_config_prefs` | `server_history_v2` | JSON array de servidores usados |
| — | `MutableStateFlow<List<ServerConfig>>` | Historial en memoria (no persistido) |

---

## Licencia

Propietario — EscanQR. Todos los derechos reservados.

---

*Documentación generada a partir del código fuente en la rama `Test`. Última actualización: mayo 2026.*
