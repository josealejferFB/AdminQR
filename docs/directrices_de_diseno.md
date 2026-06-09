# Directrices de Diseño y UI/UX - EscanQR (Material Design 3 - 2026)

Este documento detalla la especificación técnica de diseño implementada en la aplicación administradora de **EscanQR**.

---

## 1. Sistema de Colores y Adaptación de Temas

Todos los elementos deben consumir tokens semánticos del `MaterialTheme.colorScheme`. Prohibido el uso de colores fijos (`Color.White`, `Color.Black` o hexadecimales) para fondos, textos o bordes — excepto en ESPConfigScreen (uso de `EspColorScheme`).

### 1.1. Tabla de Tokens de Color

| Token Semántico | Tema Claro | Tema Oscuro | Propósito |
|:---|---:|---:|---|
| `primary` | `#1E293B` (Slate-800) | `#94A3B8` (Slate-400) | Énfasis principal, botones, textos destacados |
| `onPrimary` | `#FFFFFF` | `#0F172A` | Texto/icono sobre primary |
| `primaryContainer` | `#E2E8F0` | `#1E293B` | Fondos de indicadores, badges |
| `secondary` | `#0D9488` (Teal-600) | `#2DD4BF` (Teal-400) | Acentos secundarios, estados correctos |
| `onSecondary` | `#FFFFFF` | `#042F2E` | Texto/icono sobre secondary |
| `secondaryContainer` | `#CCFBF1` | `#134E4A` | Fondos de chips de éxito |
| `tertiary` | `#7C3AED` (Violet-600) | `#A78BFA` (Violet-400) | Paso 2 de activación, QR |
| `onTertiary` | `#FFFFFF` | `#1E0A3C` | Texto/icono sobre tertiary |
| `background` | `#FAFAFA` | `#0A0A0A` | Fondo de pantallas |
| `onBackground` | `#0F172A` | `#FAFAFA` | Texto principal sobre fondo |
| `surface` | `#FFFFFF` | `#18181B` | Tarjetas, diálogos, top bar |
| `onSurface` | `#1E293B` | `#E4E4E7` | Texto sobre tarjetas |
| `surfaceVariant` | `#F1F5F9` | `#27272A` | Fondos secundarios (SearchBar) |
| `outline` | `#CBD5E1` | `#3F3F46` | Bordes de inputs, divisores |
| `error` | `#DC2626` | `#FCA5A5` | Estados de error |
| `errorContainer` | `#FEE2E2` | `#7F1D1D` | Fondos de error |

### 1.2. EspColorScheme (Consola ESP32)

ESPCONFIGScreen usa su propio par de esquemas que mantienen aesthetic terminal pero se adaptan al tema claro/oscuro:

| Token | Claro | Oscuro | Constante anterior |
|:---|---:|---:|---|
| `surface` | `#F8FAFC` | `#0D1117` | `ConsoleBg` |
| `surfaceVariant` | `#E2E8F0` | `#161B22` | `ConsolePanel` / `FormBg` |
| `outline` | `#CBD5E1` | `#30363D` | `ConsoleBorder` |
| `onSurface` | `#0F172A` | `#E6EDF3` | texto en consola |
| `onSurfaceVariant` | `#64748B` | `#8B949E` | `MutedText` |
| `primary` | `#1F6FEB` | `#1F6FEB` | `TxColor` / `PromptColor` |
| `secondary` | `#238636` | `#238636` | `RxColor` |
| `tertiary` | `#8957E5` | `#8957E5` | Color de QuickCmd Config |

```kotlin
@Composable
fun EspColorScheme(): EspColorSchemeColors {
    val dark = isSystemInDarkTheme()
    return EspColorSchemeColors(
        surface = if (dark) Color(0xFF0D1117) else Color(0xFFF8FAFC),
        surfaceVariant = if (dark) Color(0xFF161B22) else Color(0xFFE2E8F0),
        onSurface = if (dark) Color(0xFFE6EDF3) else Color(0xFF0F172A),
        // ...
    )
}
```

---

## 2. Estandarización de Tarjetas (`AppCard`)

Parámetros unificados de Material Design 3:

- **Corner Radius:** `24.dp`
- **Elevación:** `2.dp`
- **Borde:** `1.dp`, `primary` al `10%` de opacidad
- **Padding interno mínimo:** `16.dp` – `20.dp`

`AppCardDefaults` expresa estos valores como objetos reutilizables:

```kotlin
object AppCardDefaults {
    val Shape: Shape get() = RoundedCornerShape(24.dp)
    val Elevation: CardElevation get() = CardDefaults.cardElevation(defaultElevation = 2.dp)
    fun border(color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)): BorderStroke
    fun colors(containerColor: Color = MaterialTheme.colorScheme.surface): CardColors
}
```

**NO** sobrescribir `shape` ni `elevation` al usar `AppCard` — usar `AppCardDefaults.*` siempre.

### Excepciones conocidas (intencionales)
- `ResultSnackbar`: shape 14.dp (snackbar flotante, no una card)
- Botones: 12.dp (filled, outlined, tonal)
- Inputs (`OutlinedTextField`): 12.dp – 16.dp
- Diálogos (`AlertDialog`): 24.dp

---

## 3. Navegación Principal

Arquitectura basada en `NavigationBar` de Material3 envuelta en un `Scaffold` único en `AppNavigation.kt`:

```
Scaffold + NavigationBar (3 destinos)
├── Inicio (HomeScreen)
├── Escáner (ScannerScreen) — botón central destacado con primary
├── Ajustes (ConfigScreen)
└── [Detalle] ResultScreen — sin NavigationBar, solo back
└── [Detalle] ESPConfigScreen — sin NavigationBar, solo back
```

- La `NavigationBar` se oculta automáticamente en pantallas de detalle.
- El botón central de escáner usa un `Box` con `RoundedCornerShape(16.dp)` y color `primary` para destacar visualmente.
- No hay `SetSystemBarsVisibility` — la app usa edge-to-edge nativo con insets manejados por M3.
- `SystemUi.kt` eliminado.

### Historial de evolución
- **Antes:** `CustomBottomBar` solo en 2/5 pantallas, `CustomTopBar` flotante, 3 implementaciones distintas de top bar.
- **Ahora:** `NavigationBar` M3 nativa en 3/5 destinos, `TopAppBar` M3 consistente.

---

## 4. Animaciones Premium y Micro-interacciones

### 4.1. Splash Screen
Tres animaciones paralelas (~2.0s):
1. **Halo radial:** degradado primary → transparente, escala 0.5→1.2, alpha 0→15%, 1200ms `EaseOutQuart`.
2. **Logo bounce + rotate:** spring `DampingRatioMediumBouncy` + `StiffnessLow`, escala 0→1, rotación -90°→0°.
3. **Texto slide-up + fade:** delay 400ms, alpha 0→1, offset Y 40dp→0.

### 4.2. Indicador de conectividad
Punto verde + anillo pulsante infinito con `infiniteRepeatable` + `tween(1500ms)`.

### 4.3. Transiciones entre pantallas
`slideInHorizontally` + `fadeIn`, 400ms, `FastOutSlowInEasing`.
Pop: dirección inversa.

---

## 5. Lineamientos de UX y Composición

- **TopAppBar M3**: usar siempre `TopAppBar` de Material3 (no `Row` custom). Logo + título en `title`, acciones en `actions`.
- **Bottom nav**: única `NavigationBar` global. No incluir barras custom por pantalla.
- **Snackbar**: usar `snackbarHost` del `Scaffold`, no posicionamiento manual.
- **Tarjetas de métricas**: usar `AppCard` con iconos de 24dp, texto en 28sp (valor) y 10sp (label).
- **Formularios**: `OutlinedTextField` con `RoundedCornerShape(12.dp)` y borde visible (`focusedBorderColor = primary`, `unfocusedBorderColor = outline`).
- **Cámara (ScannerScreen)**: fullscreen sin top bar propia. La `NavigationBar` global da acceso a otras secciones.
- **Diálogos**: `AlertDialog` con `containerColor = surface`, `shape = RoundedCornerShape(24.dp)`.
- **Colores de estado**: mapear siempre a tokens del `ColorScheme` - `secondary` para éxito/validado, `error` para fallo, `tertiary` para paso secundario, `outline` para pendiente/bloqueado.

---

## 6. Shape Consistency

| Elemento | Radius |
|---|---|
| AppCard / cards | 24.dp |
| Botones (filled, outlined, tonal) | 12.dp |
| OutlinedTextField | 12.dp |
| NavigationBar | 16.dp (top-only, M3 nativo) |
| Diálogos | 24.dp |
| Chips / badges | 8.dp |
| ResultSnackbar | 14.dp |
