# Directrices de Diseño y UI/UX - EscanQR (Material Design 3 - 2026)

Este documento detalla la especificación técnica de diseño implementada en la aplicación administradora de **EscanQR** para que el equipo de desarrollo de la aplicación de usuario pueda replicar el mismo lavado de cara, garantizando consistencia visual y de experiencia de usuario (UI/UX).

---

## 1. Sistema de Colores y Adaptación de Temas

Para lograr una interfaz premium y compatible con el **Tema Oscuro (Dark Mode)**, está prohibido el uso de colores estáticos (como `Color.White`, `Color.Black` o valores hexadecimales fijos) para fondos, textos o bordes. Todos los elementos deben consumir tokens semánticos del esquema de temas.

### 1.1. Tabla de Tokens de Color

| Token Semántico | Valor Tema Claro (Light) | Valor Tema Oscuro (Dark) | Propósito en la UI |
| :--- | :--- | :--- | :--- |
| `primary` | `#000666` (PrimaryBlue) | `#8FA9FF` | Color de énfasis principal, botones de acción clave, textos destacados. |
| `secondary` | `#E28364` (Orange) | `#E28364` (Orange) | Alertas, llamadas a la acción secundarias, estados intermedios. |
| `tertiary` | `#E2E2E2` (SurfaceGrey) | `#2E2E2E` | Fondos de avatares, divisores y elementos deshabilitados. |
| `background` | `#FAFAFA` (Gris claro) | `#121212` (Negro OLED/Gris muy oscuro) | Fondo de las pantallas principales del sistema. |
| `surface` | `#FFFFFF` (Blanco puro) | `#1E1E1E` (Gris elevado) | Fondo de las tarjetas, diálogos y barras flotantes. |
| `onPrimary` | `#FFFFFF` | `#000000` | Color del texto/icono sobre fondo primario. |
| `onBackground` | `#000000` | `#FFFFFF` | Color del texto principal sobre el fondo de pantalla. |
| `onSurface` | `#000000` | `#FFFFFF` | Color del texto principal sobre tarjetas o superficies. |

---

## 2. Estandarización de Tarjetas (`AppCard`)

Todas las tarjetas de la aplicación deben utilizar un diseño unificado basado en los siguientes parámetros de Material Design 3:

* **Corner Radius (Bordes Redondeados):** `24.dp` (esquinas anchas, modernas).
* **Elevación (Sombra):** `2.dp` en estado normal.
* **Borde Contorno (Border Stroke):** `1.dp` de grosor con el color `primary` y una opacidad (alpha) de `0.1f` (`10%`). Esto aporta un contorno sutil que resalta la tarjeta sin sobrecargar la UI.
* **Margen / Padding Interno:** Mínimo `16.dp` a `20.dp` de espaciado interior para respiración de contenidos.

### Código de Referencia en Jetpack Compose

```kotlin
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation: CardElevation = CardDefaults.cardElevation(
        defaultElevation = 2.dp
    ),
    border: BorderStroke? = BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    ),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        content = content
    )
}
```

---

## 3. Animaciones Premium y Micro-interacciones (Estado del Arte 2026)

Para hacer que la aplicación se sienta viva e interactiva, implementamos animaciones no lineales apoyadas en especificaciones de resorte (**Springs**).

### 3.1. Animación del Splash Screen

La pantalla de bienvenida ejecuta tres animaciones paralelas en un intervalo de **2.0 segundos**:

1. **Efecto de Halo Radial (Fondo):** Un degradado radial de la marca se expande suavemente de `0.5f` a `1.2f` en escala y se desvanece de `0%` a `15%` de opacidad con una curva `EaseOutQuart` de 1200ms.
2. **Entrada con Rebote del Logo (`ic_app_logo`):** El logo principal escala desde `0f` hasta `1f` y rota desde `-90°` hasta `0°` usando un interpolador bouncy spring:
   * **Damping Ratio:** `Spring.DampingRatioMediumBouncy` (rebote medio suave).
   * **Stiffness:** `Spring.StiffnessLow` (movimiento fluido y no cortado).
3. **Texto Deslizante y Desvanecido:** El nombre de la aplicación y la descripción aparecen con una opacidad de `0f` a `1f` deslizándose verticalmente desde `40.dp` hacia su posición final con un retardo (delay) de `400ms`.

#### Código de Referencia en Jetpack Compose

```kotlin
val scale = remember { Animatable(0f) }
val rotation = remember { Animatable(-90f) }
val alpha = remember { Animatable(0f) }
val yOffset = remember { Animatable(40f) }

LaunchedEffect(key1 = true) {
    // Bote y giro del logotipo
    launch {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }
    launch {
        rotation.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }
    // Desvanecimiento y subida del texto
    launch {
        delay(400)
        alpha.animateTo(1f, animationSpec = tween(800, easing = EaseOutCubic))
    }
    launch {
        delay(400)
        yOffset.animateTo(0f, animationSpec = spring(stiffness = Spring.StiffnessMedium))
    }
}
```

### 3.2. Anillo de Estado Pulsante (Conectividad / Online)

Para indicar que el usuario o el servidor se encuentra activo ("Estás en línea"), se utiliza un indicador de punto con un anillo expansivo translúcido infinito:

* **Punto Central:** Círculo estático de color verde o primario de `10.dp`.
* **Anillo Pulsante:** Un círculo externo que escala continuamente de `1f` a `2.3f` y desvanece su opacidad de `0.6f` a `0f` en bucle continuo de `1500ms`.

```kotlin
val infiniteTransition = rememberInfiniteTransition(label = "pulse")
val scale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 2.3f,
    animationSpec = infiniteRepeatable(
        animation = tween(1500, easing = EaseOutQuart),
        repeatMode = RepeatMode.Restart
    ),
    label = "scale"
)
val alpha by infiniteTransition.animateFloat(
    initialValue = 0.6f,
    targetValue = 0f,
    animationSpec = infiniteRepeatable(
        animation = tween(1500, easing = EaseOutQuart),
        repeatMode = RepeatMode.Restart
    ),
    label = "alpha"
)
```

---

## 4. Lineamientos de UX y Layout

* **Translucidez en Contenedores Flotantes:** Para pantallas de cámara o scanner, las barras de navegación (`TopBar` / `BottomBar`) deben ser flotantes con esquinas redondeadas y usar un fondo translúcido:
  `MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)` combinado con `Blur` de ventana si la plataforma lo soporta.
* **Top App Bars Minimalistas:** En lugar de barras superiores de color primario sólido, utilizar el color de superficie (`surface`) con textos oscuros o claros dinámicos (`onSurface`), manteniendo un estilo limpio.
* **Feedback Háptico:** Usar llamadas hápticas (`HapticFeedbackType.LongPress` / `TextHandleMove`) al interactuar con botones de navegación inferiores o tarjetas para reforzar la sensación de calidad.
