# EscanQR Admin — AGENTS.md

## Stack
- **Android**: Jetpack Compose + Material3, Kotlin 2.0.21, AGP 8.7.3, Hilt 2.51.1, KSP
- **Target/compile**: SDK 35, minSdk 26, Java 17
- **Navigation**: Typed `@Serializable` routes + `NavigationBar` M3 global (3 tabs: Home, Scanner, Config). See `presentation/navigation/AppNavigation.kt`
- **Serialization**: `kotlinx.serialization` across the board (no Gson/Moshi)
- **HTTP**: OkHttp directly (no Retrofit). JSON-RPC style calls with `"jsonrpc": "2.0"` payloads see `SyncRepositoryImpl`
- **Bluetooth**: Classic SPP (UUID `00001101-0000-1000-8000-00805F9B34FB`) with reflection fallback
- **QR generation**: ZXing `QRCodeWriter` inline in composables
- **Camera/scan**: CameraX + ML Kit barcode scanning

## Architecture
Clean Architecture: `domain/model/`, `domain/repository/` (interfaces), `data/repository/` (impls), `data/network/`, `di/modules/`, `presentation/ui/` (per feature).

## Documentation
Full docs in `docs/`:
- `Arquitectura.md` — stack, MVVM, folder structure, design guidelines
- `Contrato-Odoo.md` — endpoints, JSON-RPC payloads, URL construction
- `Contrato-App-Usuario.md` — QR format, AES/GCM encryption, provisioning tokens
- `Contrato-ESP32.md` — Bluetooth SPP, V6 state machine, commands, timeouts
- `directrices_de_diseno.md` — color tokens (Slate/Teal/Violet), AppCard, NavigationBar M3, EspColorScheme, animations

## Key conventions
- `ApiConstants` is a singleton with `init(context)` — must be called before use (done in `EscanQRApp.onCreate`)
- All API config (protocol, host, port, endpoints) is stored in SharedPreferences `api_config_prefs`
- ESP32 discovery matches devices whose name starts with `"ESP32"` (case-insensitive)
- Design tokens defined in `docs/directrices_de_diseno.md` — use `AppCard`/`AppCardDefaults` consistently; no hardcoded colors for backgrounds/text
- `ThemeRepository` persists dark mode to `theme_prefs`; exposed as `Flow<Boolean>`
- `HistoryRepository` is **in-memory only** (MutableStateFlow), not persisted
- Server history (config endpoints) persisted in SharedPreferences as JSON array under `server_history_v2`
- Navigation: `AppNavigation.kt` wraps all screens in a `Scaffold` + `NavigationBar` (3 destinations). Detail screens (Result, ESPConfig) auto-hide the bar.
- Theme: new palette since 2026-06-08 redesign — Primary `#1E293B` (Slate-800), Secondary `#0D9488` (Teal-600), Tertiary `#7C3AED` (Violet-600). See `Theme.kt` for full Light/Dark `ColorScheme`. ESPConfigScreen uses its own `EspColorScheme()` composable.
- `AppBottomBar` in `Bars.kt` — M3 `NavigationBar` with central highlighted scanner button.
- `SystemUi.kt` has been **deleted**. No `SetSystemBarsVisibility` — native edge-to-edge via M3 Scaffold insets.
- Color/status mapping: `colorScheme.secondary` = success/validated, `colorScheme.error` = failure, `colorScheme.tertiary` = secondary step, `colorScheme.outline` = pending/disabled.

## Important constraints/quirks
- **No CI/CD** configured
- Tests are stub/example files only (`ExampleUnitTest`, `ExampleInstrumentedTest`)
- Network: cleartext HTTP is allowed via `network_security_config.xml`
- AES key `SHARED_AES_KEY` and provisioning `PROVISIONING_TOKEN` are hardcoded in `SecurityConstants.kt`
- The repo includes an Arduino companion sketch (`VerificacionHuellasV7.ino`) for the ESP32 gate controller
- ESP32 V6 firmware uses a state machine: `ESPERA_CONEXION → MODO_CONFIG_BT → {ODOO | WIFI}` with 30-60s timeouts
- QR between apps uses AES-256-GCM with shared key (`SecurityConstants.SHARED_AES_KEY`)
- Spanish language throughout the codebase (UI strings, comments, variable names)
- `material-icons-extended` is a direct dependency (not via BOM)
- Gradle version catalog at `gradle/libs.versions.toml` — add deps there first

## Commands
```bash
./gradlew assembleDebug          # Build debug APK
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests
```
