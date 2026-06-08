# Arquitectura — EscanQR Admin

## Stack Tecnológico

| Componente | Versión |
|---|---|
| Kotlin | 2.0.21 |
| AGP | 8.7.3 |
| Hilt | 2.51.1 |
| KSP | 2.0.21-1.0.27 |
| Compile SDK | 35 |
| Min SDK | 26 |
| Target SDK | 35 |
| Java | 17 |

## Librerías Principales

| Propósito | Librería |
|---|---|
| UI | Jetpack Compose + Material3 (BOM gestionado) |
| DI | Hilt + Hilt Navigation Compose |
| Navegación | Navigation Compose con rutas `@Serializable` |
| Serialización | `kotlinx.serialization` (sin Gson ni Moshi) |
| HTTP | OkHttp 4.x directo (sin Retrofit) |
| Cámara | CameraX (core, camera2, lifecycle, view) |
| Escaneo QR | ML Kit barcode scanning |
| Generación QR | ZXing core 3.5.3 |
| Bluetooth | Classic SPP (API nativa de Android) |
| Iconos | `material-icons-extended` (dependencia directa) |
| Logging HTTP | OkHttp logging interceptor |

## Arquitectura General

Clean Architecture en 3 capas + DI:

```
app/                             ← Application + MainActivity
di/modules/                     ← Hilt DI (RepositoryModule)
domain/
  ├── model/                    ← Entidades de negocio
  ├── repository/               ← Interfaces de repositorio
data/
  ├── network/                  ← DTOs de red + ApiConstants
  └── repository/               ← Implementaciones de repositorios
presentation/
  ├── navigation/               ← Rutas tipadas + NavHost
  ├── theme/                    ← Colores, temas, tipografía
  ├── common/
  │   ├── sharedcomponents/     ← AppCard, Bars, QrCodeImage
  │   └── util/                 ← QrUtils, SystemUi
  └── ui/                       ← Pantallas (una carpeta por feature)
```

## MVVM

Cada feature tiene un `ViewModel` inyectado con Hilt que expone:

- **`StateFlow<UiState>`** — estado reactivo de la UI
- **`SharedFlow<Event>`** — eventos únicos (snackbar, navegación)
- **Métodos públicos** — acciones del usuario

Los composables observan estos `Flow` con `collectAsState()` y `LaunchedEffect`.

### Ciclo de vida típico

```
Composable (Screen)
    ↕ collectAsState()
ViewModel (HiltViewModel)
    ↕ inyección
Repository (impl)
    ↕
Fuente de datos (OkHttp / SharedPreferences / BluetoothSocket / MutableStateFlow)
```

## Navegación

Rutas definidas en `NavDestinations.kt` con `kotlinx.serialization`:

```kotlin
@Serializable object Splash
@Serializable object Home
@Serializable object Scanner
@Serializable data class Result(androidId, userName, cedula, plate)
@Serializable object ESPConfig
@Serializable object Config
```

`AppNavigation.kt` contiene el `NavHost` con transiciones entre pantallas.

## Flujo de Datos General

1. **Splash** → animación de bienvenida, navega a Home
2. **Home** → panel con métricas, lista de usuarios activos, botón escanear
3. **Scanner** → CameraX + ML Kit detecta QR → deserializa JSON → descifra `aid` (AES/GCM) → navega a Result
4. **Result** → muestra datos del usuario → sync con Odoo → muestra QR de acceso
5. **Config** → configuración de host/puerto/endpoints para Odoo
6. **ESPConfig** → consola Bluetooth para configurar red del ESP32

## Inyección de Dependencias (Hilt)

`RepositoryModule` (`@InstallIn(SingletonComponent::class)`):

- **Binds:** SyncRepository, BluetoothRepository, ThemeRepository, HistoryRepository
- **Provides:** OkHttpClient (timeouts 15s, logging condicional), CoroutineScope (SupervisorJob + IO)

## Persistencia

| Dato | Backend |
|---|---|
| Config API | `SharedPreferences` (`api_config_prefs`) |
| Historial de servidores | `SharedPreferences` JSON array (`server_history_v2`) |
| Tema oscuro | `SharedPreferences` (`theme_prefs`) |
| Historial de escaneos | En memoria (`MutableStateFlow`) — no persistido |

## Seguridad de Red

- Tráfico HTTP plano permitido vía `network_security_config.xml`
- Sin certificados SSL configurados
- Claves hardcodeadas (AES, tokens de provisionamiento)

## Diseño UI/UX

Ver `docs/directrices_de_diseno.md` para especificación completa:

- Sistema de colores con tokens semánticos (no colores fijos)
- `AppCard` estandarizada (border radius 24dp, elevación 2dp, borde primary al 10%)
- Animaciones Spring (splash, indicador pulsante de conexión)
- Modo oscuro nativo
- Barras flotantes translúcidas en pantalla de cámara
