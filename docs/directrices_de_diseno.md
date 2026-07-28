# Directrices de Diseño y UI/UX - EscanQR (Material Design 3 - 2026)

Este documento detalla la especificación técnica de diseño implementada en las aplicaciones del ecosistema **EscanQR**: la aplicación administradora (EscanQR Admin) y la aplicación del conductor (App Usuario). Ambas aplicaciones comparten el mismo sistema de colores, tipografía, formas y principios de interacción para garantizar una experiencia de usuario coherente en todo el sistema.

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

## 3. Navegación Principal — AppBottomBar

La barra de navegación inferior (AppBottomBar) es un overlay flotante que cubre las 3 pantallas principales: Home, Scanner, Config. Se oculta automáticamente en pantallas de detalle (Result, Splash). Está implementada con `NavigationBar` de Material3 envuelta en un `Surface` redondeado, dentro de un `AnimatedVisibility` alineado al fondo del `Box` raíz.

### 3.0. Arquitectura del contenedor (AppNavigation.kt)

```
Box(Modifier.fillMaxSize)
├── CompositionLocalProvider(LocalSnackbarHostState)
│   ├── Scaffold(sin bottomBar)
│   │   └── NavHost — transiciones crossfade + scale
│   │       ├── Splash*       → sin barra
│   │       ├── Home          → con barra
│   │       ├── Scanner       → con barra
│   │       ├── Result*       → sin barra
│   │       └── Config        → con barra
│   └── SnackbarHost (bottom: 100.dp, horizontal: 16.dp)
└── AnimatedVisibility (align BottomCenter)
    └── FloatingBottomBar
        └── Surface (padding 48.dp lateral, 24.dp fondo)
            └── AppBottomBar (NavigationBar)
                ├── NavigationBarItem: Inicio
                ├── NavigationBarItem: Escáner
                └── NavigationBarItem: Ajustes
```

**Regla de visibilidad:** la barra se muestra solo cuando el destino actual pertenece al conjunto `routesWithBottomBar = setOf(Home::class, Scanner::class, Config::class)`. Se usa `NavDestination.hasRoute()` para la comparación tipada con `@Serializable` routes.

### 3.0.1. Padding del NavHost

El `NavHost` recibe `Modifier.padding(innerPadding)` del `Scaffold`. Como el `Scaffold` no tiene `bottomBar`, `innerPadding` solo incluye los insets de sistema (barra de estado, navegación). **No se aplica padding inferior adicional** — el contenido del NavHost se extiende hasta el fondo y la barra flotante se superpone como overlay.

La snackbar se posiciona con `Modifier.padding(bottom = 100.dp, horizontal = 16.dp)` para flotar sobre la barra sin solaparse. La altura total de la barra desde el borde inferior es `24dp (bottom padding) + 52dp (NavigationBar height) = 76dp`, más la elevación de la sombra. El padding `100.dp` de la snackbar garantiza un espacio de ~24dp entre la snackbar y el borde superior del Surface.

---

### 3.1. FloatingBottomBar — Contenedor exterior (AppNavigation.kt:168-179)

| Propiedad | Valor |
|---|---|
| Padding horizontal | `start = 48.dp, end = 48.dp` |
| Padding inferior | `bottom = 24.dp` |
| Shape | `RoundedCornerShape(26.dp)` |
| Color | `MaterialTheme.colorScheme.surfaceVariant` |
| Tonal elevation | `8.dp` |
| Shadow elevation | `8.dp` |
| Composable interno | `AppBottomBar(navController)` |

El `Surface` hace de "cápsula" que contiene a la `NavigationBar`. Los 48.dp de padding lateral reducen el ancho total de la barra de `fillMaxWidth` a un ancho efectivo de `screenWidth - 96.dp`. El shape de 26.dp redondea las esquinas de la cápsula. Los 24.dp de padding inferior separan la cápsula del borde inferior de la pantalla.

### 3.2. AppBottomBar — NavigationBar (Bars.kt:44-48)

| Propiedad | Valor |
|---|---|
| Height | `52.dp` |
| Container color | `Color.Transparent` |
| Tonal elevation | `0.dp` |
| Window insets | `WindowInsets(0, 0, 0, 0)` — sin insets adicionales |

La `NavigationBar` usa `containerColor = Transparent` para heredar el color del `Surface` padre (`surfaceVariant`). `tonalElevation = 0.dp` evita que la barra aplique su propia elevación tonal (ya la aplica el Surface). `WindowInsets(0,0,0,0)` elimina cualquier padding automático que M3 agregue por los insets de navegación del sistema, ya que los maneja el Box raíz.

### 3.3. NavigationBarItem — Inicio (Bars.kt:50-80)

| Propiedad | Valor |
|---|---|
| Ruta | `Home` (objeto `@Serializable`) |
| Icono | `Icons.Default.Home`, 24.dp |
| Content description | `"Inicio"` |
| Label | Sin label visible |
| onClick | `navigate(Home)` con `popUpTo(startDestination) { saveState = true }`, `launchSingleTop = true`, `restoreState = true` |

**Colores seleccionados:**
- `selectedIconColor` = `MaterialTheme.colorScheme.primary` (Slate-800)
- `selectedTextColor` = `MaterialTheme.colorScheme.primary`
- `indicatorColor` = `MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)`

**Colores no seleccionados:** valores por defecto de M3 (onSurfaceVariant).

**Micro-interacción press:**
| Parámetro | Valor |
|---|---|
| Escala inicial | `1f` |
| Escala en press | `0.92f` |
| Spring damping ratio | `0.7f` |
| Spring stiffness | `450f` |

Implementado con `animateFloatAsState(targetValue = if (isPressed) 0.92f else 1f, animationSpec = spring(...))`, sobre `MutableInteractionSource.collectIsPressedAsState()`. La escala se aplica via `graphicsLayer { scaleX = scale; scaleY = scale }`.

### 3.4. NavigationBarItem — Escáner (Bars.kt:82-124)

| Propiedad | Valor |
|---|---|
| Ruta | `Scanner` (objeto `@Serializable`) |
| onClick | `navigate(Scanner)` con `launchSingleTop = true`, `restoreState = true` |

Este es el **botón central destacado**, visualmente diferente al resto. En lugar de un icono simple, usa un `Box` con fondo y shape propio:

**Contenedor del icono (Box):**
| Propiedad | Valor |
|---|---|
| Tamaño | `44.dp` (ancho y alto) |
| Fondo | `MaterialTheme.colorScheme.secondary` (Teal-600) |
| Shape | `RoundedCornerShape(14.dp)` |
| Alineación | `Alignment.Center` |
| graphicsLayer | Aplica scale + rotation |

**Icono interno:**
| Propiedad | Valor |
|---|---|
| Vector | `Icons.Default.QrCodeScanner` |
| Tamaño | `26.dp` |
| Tint | `Color.White` |
| Content description | `"Escáner"` |

**Colores del NavigationBarItem:**
- `selectedIconColor` = `Color.Transparent` (el icono no usa este color; su color lo define el tint directo en el Icon)
- `selectedTextColor` = `MaterialTheme.colorScheme.primary`
- `indicatorColor` = `Color.Transparent` (sin círculo indicador, para no competir con el Box de fondo)

**Micro-interacción press (doble animación paralela):**
| Parámetro | Escala | Rotación |
|---|---|---|
| Valor reposo | `1f` | `0°` |
| Valor en press | `0.94f` | `-3°` |
| Spring damping ratio | `0.7f` | `0.5f` |
| Spring stiffness | `450f` | `300f` |

Ambas animaciones se ejecutan simultáneamente via dos `animateFloatAsState` independientes, aplicadas en el mismo `graphicsLayer { scaleX; scaleY; rotationZ }`. La rotación es solo en el eje Z (plano 2D, sentido antihorario).

### 3.5. NavigationBarItem — Ajustes (Bars.kt:126-156)

| Propiedad | Valor |
|---|---|
| Ruta | `Config` (objeto `@Serializable`) |
| Icono | `Icons.Default.Settings`, 24.dp |
| Content description | `"Ajustes"` |
| onClick | `navigate(Config)` con `launchSingleTop = true`, `restoreState = true` |

**Colores, indicador y micro-interacción:** idénticos a Inicio (sección 3.3).

### 3.6. Animaciones de la barra flotante

**Mostrar (enter):**
| Propiedad | Fade | Slide vertical |
|---|---|---|
| Tipo | `fadeIn` | `slideInVertically` |
| Duración | 300ms `tween` | Spring |
| Damping ratio | — | `0.85f` |
| Stiffness | — | `400f` |
| Offset inicial | alpha 0 | `initialOffsetY = { it }` (entra desde abajo) |

**Ocultar (exit):**
| Propiedad | Fade | Slide vertical |
|---|---|---|
| Tipo | `fadeOut` | `slideOutVertically` |
| Duración | 200ms `tween` | 200ms `tween` |
| Easing | `LinearEasing` | `FastOutSlowInEasing` |
| Offset final | alpha 0 | `targetOffsetY = { it }` (sale hacia abajo) |

### 3.7. Pantallas objetivo (bottom bar visibility)

| Pantalla | Ruta | Bottom bar | Snackbar |
|---|---|---|---|
| Splash | `Splash` | ❌ oculta | ❌ no |
| Home | `Home` | ✅ visible | ✅ sí |
| Scanner | `Scanner` | ✅ visible | ✅ sí |
| Result | `Result(data class)` | ❌ oculta | ❌ no |
| Config | `Config` | ✅ visible | ✅ sí |

### 3.8. Snackbar positioning

El `SnackbarHost` está en el `Box` raíz, posicionado con `Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp, horizontal = 16.dp)`. Esto lo coloca ~24.dp por encima del borde superior del `Surface` flotante (cuando la barra está visible). Cuando la barra se oculta (ej. Result), la snackbar mantiene su posición; la diferencia visual de 100.dp al fondo es apenas perceptible porque Result no muestra snackbar.

### 3.9. Estructura del NavigationBarItem sin label visible

Los tres `NavigationBarItem` se usan sin `label` (parámetro omitido). M3 renderiza el icono únicamente, sin texto debajo. Esto maximiza el espacio horizontal disponible dentro de la cápsula de 48.dp de padding lateral. La distinción entre seleccionado/no seleccionado se logra únicamente con el `indicatorColor` (círculo semitransparente detrás del icono) para Inicio y Ajustes, y con el fondo del Box para Escáner.

### 3.10. Navegación entre tabs

Cada `onClick` usa `launchSingleTop = true` y `restoreState = true` para preservar el estado de cada tab al alternar entre ellos. Inicio además hace `popUpTo(navController.graph.startDestinationId) { saveState = true }` para limpiar la pila al ir a Home. Este patrón es estándar M3 para bottom navigation.

### Historial de evolución
- **Jun 2026 (actual, doc):** `NavigationBar` flotante con overlay Box, `Surface` cápsula 26dp, padding lateral 48dp, transiciones crossfade+scale en NavHost, micro-interacciones spring. Botón Escáner con Box 44dp + fondo `secondary` + shape 14dp + rotación -3° en press.
- **Jun 2026 (pre-doc):** `NavigationBar` flotante con overlay Box, valores legacy (RoundedCorner 24dp, alpha 0.85, 16dp margin).
- **Antes:** `NavigationBar` M3 nativa fija al fondo dentro del `Scaffold.bottomBar`.
- **Original:** `CustomBottomBar` solo en 2/5 pantallas.

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
`fadeIn` + `scaleIn(0.92→1)`, 350ms, `FastOutSlowInEasing`.
Exit: `fadeOut` + `scaleOut(1→0.95)`, 200ms.
Pop: simétrico.
Antes: `slideInHorizontally` + `fadeIn`, 400ms.

---

## 5. Lineamientos de UX y Composición

- **TopAppBar M3**: usar siempre `TopAppBar` de Material3 (no `Row` custom). Logo + título en `title`, acciones en `actions`.
- **Bottom nav**: única `NavigationBar` flotante (overlay en Box). No incluir barras custom por pantalla.
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
| Floating NavigationBar (Surface) | 24.dp (contenedor) |
| Diálogos | 24.dp |
| Chips / badges | 8.dp |
| ResultSnackbar | 14.dp |

---

## 7. Directrices para la App del Conductor (App Usuario)

Esta sección establece los lineamientos de diseño específicos para la aplicación del conductor. La App Conductor debe compartir el sistema de colores, formas y animaciones definido en las secciones 1–6, adaptando los componentes a su contexto de uso: generación de QR de identidad, escaneo de aprovisionamiento y apertura de portones.

### 7.1. Pantallas y Navegación

La App Conductor tiene una estructura de navegación más simple que la App Admin. Se recomienda una navegación basada en pestañas con `NavigationBar` M3 estándar (no flotante, para diferenciarla de la Admin) con dos o tres destinos:

| Destino | Ícono | Propósito |
|---|---|---|
| Mi QR | `QrCode` | Muestra el código QR de identidad del conductor en tamaño grande, con datos personales debajo (nombre, cédula, placas). Función principal. |
| Portones | `Gate` / `Lock` | Lista de portones autorizados como chips interactivos. Al seleccionar uno, botón de apertura. |
| Ajustes | `Settings` | Información de la app, versión, y opción de re-escanear QR de aprovisionamiento. |

**Reglas:**
- La `NavigationBar` debe fijarse al fondo del `Scaffold` usando el parámetro `bottomBar` (no overlay flotante como en la Admin).
- `containerColor = surfaceVariant`, `tonalElevation = 4.dp`.
- Sin botón central destacado (no hay función de escáner).
- El contenido del `NavHost` debe tener `padding(bottom = 80.dp)` para no solaparse.

### 7.2. Pantalla "Mi QR" — QR de Identidad

Esta es la pantalla principal y más importante de la App Conductor. Sigue este diseño:

```
┌─────────────────────────────────┐
│  AppBar: "Mi Identidad"         │
├─────────────────────────────────┤
│                                 │
│        ┌───────────┐            │
│        │           │            │
│        │   QR de   │            │
│        │ Identidad │            │
│        │           │            │
│        └───────────┘            │
│                                 │
│   Juan Pérez                    │
│   Cédula: 12345678              │
│   Placas: ABC-1234              │
│                                 │
│   [🔄 Actualizar datos]         │
│                                 │
│   "Presenta este código al      │
│    administrador de seguridad   │
│    para registrar tu ingreso"   │
│                                 │
└─────────────────────────────────┘
```

**Especificaciones:**
- QR centrado en un `Card` con `shape = RoundedCornerShape(24.dp)`, `elevation = 2.dp`, `border = primary 10%`.
- Tamaño del QR: mínimo `200.dp` x `200.dp`, preferiblemente `240.dp`.
- Datos personales debajo del QR con tipografía: nombre en `18.sp` `Medium`, cédula y placas en `14.sp` `Normal`, color `onSurface`.
- Botón "Actualizar datos" como `TextButton` con ícono `Sync` y color `secondary`.
- Texto instructivo al pie en `12.sp` `color = onSurfaceVariant`, centrado, con padding horizontal.
- Pull-to-refresh para regenerar el QR.

**Estados:**
| Estado | Comportamiento |
|---|---|
| Cargando | Mostrar `CircularProgressIndicator` en lugar del QR. Texto "Preparando tu identidad..." |
| Datos actualizados | QR visible. Indicador verde breve tipo snackbar "Datos actualizados" |
| Error de red | QR visible (usando última versión en caché). Snackbar "No se pudieron actualizar los datos" |
| Sin conexión al servidor | Mostrar el QR en gris (`alpha = 0.5`) con overlay de advertencia. Tooltip: "Sin conexión al servidor" |

### 7.3. Pantalla de Aprovisionamiento (Primer Inicio)

Esta pantalla aparece solo la primera vez que se abre la app o cuando se solicita "Re-configurar servidor" desde Ajustes.

```
┌─────────────────────────────────┐
│  AppBar: "Configurar App"       │
├─────────────────────────────────┤
│                                 │
│     📱 (Icono grande: QrCode)   │
│                                 │
│  Escanea el código QR           │
│  de aprovisionamiento           │
│                                 │
│  "Solicita al administrador     │
│   que genere un código QR       │
│   desde su aplicación para      │
│   configurar este dispositivo." │
│                                 │
│  ┌─────────────────────────┐    │
│  │  [ ESCANEAR QR ]        │    │
│  └─────────────────────────┘    │
│                                 │
│  ¿No tienes código?             │
│  [ Intentar con datos manuales ]│
│                                 │
└─────────────────────────────────┘
```

**Especificaciones:**
- Diseño centrado con ícono grande (`64.dp`) usando `secondary` tint.
- Título en `20.sp` `Bold`, texto explicativo en `14.sp` `onSurfaceVariant`.
- Botón principal "ESCANEAR QR": `FilledButton` con `containerColor = secondary`, `RoundedCornerShape(12.dp)`. Al presionarlo, abre la cámara en un `ActivityResultLauncher` de `Intent` (no CameraX, es solo un escaneo puntual).
- Link "Intentar con datos manuales": `TextButton` con color `primary`, lleva a un formulario con campos de endpoint URL y token.
- Cuando la cámara detecta un QR válido (`endpoint` + `token`), validar el token y guardar la URL. Mostrar animación de éxito y transición a pantalla Mi QR.

**Estados:**
| Estado | Comportamiento |
|---|---|
| Escaneando | Overlay semitransparente con marco de escáner (esquinas redondeadas) y texto "Enfoca el código QR" |
| Token inválido | Snackbar error "QR inválido. El token no coincide." Botón para reintentar |
| Éxito | Animación de checkmark verde (ícono `CheckCircle`), snackbar "App configurada correctamente", navega a Mi QR |

### 7.4. Pantalla de Portones — Chips Interactivos

Los portones autorizados se muestran como una colección de `FilterChip` de Material3.

```
┌─────────────────────────────────┐
│  AppBar: "Mis Portones"         │
├─────────────────────────────────┤
│                                 │
│  Tus portones autorizados       │
│                                 │
│  ┌──────────┐ ┌──────────┐     │
│  │ Principal│ │Visitantes│     │
│  │          │ │ Norte    │     │
│  └──────────┘ └──────────┘     │
│                                 │
│  ┌──────────┐ ┌──────────┐     │
│  │ Parquead.│ │ Patio 2  │     │
│  └──────────┘ └──────────┘     │
│                                 │
│    [ ABRIR PORTÓN ]             │
│                                 │
│  Última actualización: 10:30   │
└─────────────────────────────────┘
```

**Especificaciones de los chips:**
- Usar `FilterChip` de Material3 (no `AssistChip` ni `SuggestionChip`).
- Leading icon: `Lock` (cerrado) o `LockOpen` (seleccionado/abierto).
- `shape = RoundedCornerShape(8.dp)`.
- Etiqueta a dos líneas: nombre del portón en `13.sp` `Medium`, y abajo texto secundario opcional (ej: "En línea" / "Sin conexión").
- Distribución en `FlowRow` con `horizontalArrangement = spacedBy(8.dp)`, `verticalArrangement = spacedBy(8.dp)`.
- Ancho mínimo del chip: `140.dp`.

**Estados de los chips:**
| Estado | `selected` | `colors` | Icono |
|---|---|---|---|
| No seleccionado | `false` | `filterChipColors()` default | `Lock` |
| Seleccionado | `true` | `containerColor = secondaryContainer`, `labelColor = onSecondaryContainer` | `LockOpen` (tertiary tint) |
| Abriendo | `true` (deshabilitado) | `containerColor = secondaryContainer`, `alpha = 0.6`. Mostrar `CircularProgressIndicator` de 16dp como leading icon. | spinner |
| Error | `false` | `containerColor = errorContainer`, `labelColor = onErrorContainer` | `Lock` con tint error |
| Sin conexión | — | No renderizar como FilterChip. Mostrar como `Text` en `12.sp` `onSurfaceVariant` con ícono `WifiOff`. | — |

**Botón de apertura:**
- `FilledTonalButton` con texto "ABRIR PORTÓN" en `14.sp` `Bold`, `containerColor = secondary`, `RoundedCornerShape(12.dp)`.
- Deshabilitado `enabled = selectedGate != null`.
- Mientras se abre: mostrar `CircularProgressIndicator` de 18dp dentro del botón y texto "ABRIENDO...".

**Pull-to-refresh:** Al hacer pull, refrescar la lista de portones desde Odoo.

### 7.5. Pantalla de Carga Inicial / Sincronizando

Mientras la app obtiene datos de Odoo por primera vez o refresca:

```
┌─────────────────────────────────┐
│                                 │
│      ┌──────────────────┐       │
│      │  CircularProgress│       │
│      │   Indicator       │       │
│      └──────────────────┘       │
│                                 │
│    Sincronizando tus datos...   │
│                                 │
│    "Conectando con el servidor  │
│     de administración"          │
│                                 │
└─────────────────────────────────┘
```

- `CircularProgressIndicator` con `color = secondary`, tamaño `48.dp`.
- Texto en `16.sp` `Medium`, color `onSurface`.
- Si la sincronización falla: mostrar botón "Reintentar" (`FilledTonalButton`) y mensaje de error.

### 7.6. Snackbars y Retroalimentación

| Evento | Snackbar |
|---|---|
| QR de identidad generado | "QR listo. Muéstralo al administrador." | `secondary` |
| Portón abierto | "Portón abierto correctamente" + icono check | `secondary` |
| Error de apertura | "No se pudo abrir el portón. Verifica tu conexión." | `error` |
| Aprovisionamiento exitoso | "App configurada correctamente" | `secondary` |
| Token inválido | "QR inválido: el token no coincide" | `error` |
| Sin conexión al servidor | "Sin conexión al servidor. Usando datos locales." | `onSurfaceVariant` |
| Datos actualizados | "Datos actualizados correctamente" | `secondary` |

### 7.7. Resumen de Componentes Exclusivos de la App Conductor

| Componente | Tipo M3 | Shape | Color |
|---|---|---|---|
| QR de identidad | Card (con QR dentro) | 24.dp | surface, border primary 10% |
| Chip de portón | FilterChip | 8.dp | secondaryContainer (selected) |
| Botón de apertura | FilledTonalButton | 12.dp | secondary |
| Botón escanear QR | FilledButton | 12.dp | secondary |
| Estado sin conexión | Text + icon WifiOff | — | onSurfaceVariant |
| Indicador de sincronización | CircularProgressIndicator | — | secondary |
| Tarjeta de datos personales | Card (AppCard) | 24.dp | surface, border primary 10% |

### 7.8. Mapeo de Colores a Estados (App Conductor)

| Contexto | Token de color |
|---|---|
| QR de identidad válido / listo para escanear | `secondary` (borde del QR card) |
| Portón seleccionado | `secondaryContainer` + `onSecondaryContainer` |
| Apertura exitosa | `secondary` (snackbar, icono) |
| Error de conexión / apertura | `error` (snackbar, chip borde) |
| Sin conexión a Internet | `outline` / `onSurfaceVariant` (chip deshabilitado) |
| Aprovisionamiento — paso pendiente | `tertiary` (icono de QR en pantalla de provisioning) |
| Token inválido | `error` (snackbar, icono) |

### 7.9. Consideraciones de UX para la App Conductor

- **Permiso de cámara**: Solicitar solo cuando el usuario presiona "ESCANEAR QR", no al inicio. Usar `rememberLauncherForActivityResult`.
- **Permiso de Internet**: Esencial desde el inicio. Si no hay conexión, mostrar pantalla de error con opción de reintentar.
- **Cacheo offline**: La app debe funcionar en modo lectura con datos cacheados si no hay conexión. El QR de identidad debe poder generarse sin conexión (usa Android ID local + datos cacheados).
- **Regeneración del QR**: El QR de identidad debe regenerarse cada vez que la pantalla "Mi QR" se hace visible o cuando hay nuevos datos del conductor. No cachear la imagen del QR.
- **Actualización periódica**: Refrescar la lista de portones cada 5 minutos en background. No hacer polling agresivo para no consumir batería.
- **Seguridad en apertura**: Si el ESP32 no responde tras 5 segundos, mostrar error y permitir reintentar. No bloquear la UI más de 10 segundos.
- **Transiciones**: Usar las mismas transiciones `fadeIn + scaleIn` (350ms) y `fadeOut + scaleOut` (200ms) definidas en la sección 3.1 para mantener consistencia con la App Admin.
- **Animaciones**: Las micro-interacciones de press (escala `1→0.92x`, spring) aplican igual a botones y chips en la App Conductor.
