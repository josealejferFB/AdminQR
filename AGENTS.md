# EscanQR Admin — AGENTS.md

## Stack
- **Android**: Jetpack Compose + Material3, Kotlin 2.0.21, AGP 8.7.3, Hilt 2.51.1, KSP
- **Target/compile**: SDK 35, minSdk 26, Java 17
- **Navigation**: Typed `@Serializable` routes (not string-based). See `presentation/navigation/NavDestinations.kt`
- **Serialization**: `kotlinx.serialization` across the board (no Gson/Moshi)
- **HTTP**: OkHttp directly (no Retrofit). JSON-RPC style calls with `"jsonrpc": "2.0"` payloads see `SyncRepositoryImpl`
- **Bluetooth**: Classic SPP (UUID `00001101-0000-1000-8000-00805F9B34FB`) with reflection fallback
- **QR generation**: ZXing `QRCodeWriter` inline in composables
- **Camera/scan**: CameraX + ML Kit barcode scanning

## Architecture
Clean Architecture: `domain/model/`, `domain/repository/` (interfaces), `data/repository/` (impls), `data/network/`, `di/modules/`, `presentation/ui/` (per feature).

## Key conventions
- `ApiConstants` is a singleton with `init(context)` — must be called before use (done in `EscanQRApp.onCreate`)
- All API config (protocol, host, port, endpoints) is stored in SharedPreferences `api_config_prefs`
- ESP32 discovery matches devices whose name starts with `"ESP32"` (case-insensitive)
- Design tokens defined in `directrices_de_diseno.md` — use `AppCard`/`AppCardDefaults` consistently; no hardcoded colors for backgrounds/text
- `ThemeRepository` persists dark mode to `theme_prefs`; exposed as `Flow<Boolean>`
- `HistoryRepository` is **in-memory only** (MutableStateFlow), not persisted
- Server history (config endpoints) persisted in SharedPreferences as JSON array under `server_history_v2`

## Important constraints/quirks
- **No CI/CD** configured
- Tests are stub/example files only (`ExampleUnitTest`, `ExampleInstrumentedTest`)
- Network: cleartext HTTP is allowed via `network_security_config.xml`
- AES key `SHARED_AES_KEY` and provisioning `PROVISIONING_TOKEN` are hardcoded in `SecurityConstants.kt`
- App uses immersive mode (`SetSystemBarsVisibility(false)`) in `MainActivity` — system bars hidden across all screens
- The repo includes an Arduino companion sketch (`VerificacionHuellasV6.ino`) for the ESP32 gate controller
- Spanish language throughout the codebase (UI strings, comments, variable names)
- `material-icons-extended` is a direct dependency (not via BOM)
- Gradle version catalog at `gradle/libs.versions.toml` — add deps there first

## Commands
```bash
./gradlew assembleDebug          # Build debug APK
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests
```
