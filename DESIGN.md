---
name: EscanQR Admin
description: Sistema integral de control de acceso a portones
colors:
  primary: "#1E293B"
  primary-container: "#E2E8F0"
  secondary: "#0D9488"
  secondary-container: "#CCFBF1"
  tertiary: "#7C3AED"
  background: "#FAFAFA"
  surface: "#FFFFFF"
  surface-variant: "#F1F5F9"
  outline: "#CBD5E1"
  error: "#DC2626"
typography:
  display:
    fontFamily: "Roboto, sans-serif"
    fontWeight: 700
  body:
    fontFamily: "Roboto, sans-serif"
    fontWeight: 400
rounded:
  sm: "8px"
  md: "12px"
  lg: "24px"
  xl: "26px"
spacing:
  sm: "8px"
  md: "16px"
  lg: "24px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "#FFFFFF"
    rounded: "{rounded.md}"
    padding: "16px 24px"
  card:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.lg}"
    padding: "16px"
---

# Design System: EscanQR Admin

## 1. Overview

**Creative North Star: "Fluidez Táctica"**

El sistema visual de EscanQR está diseñado para priorizar las operaciones rápidas y las interacciones fluidas en campo. Es sencillo, minimalista y funcional, aprovechando a fondo las convenciones nativas de Material Design 3 de Android. Rechaza explícitamente cualquier estética web empaquetada o sobrecargada que distraiga del escaneo rápido y la validación de accesos.

**Key Characteristics:**
- Táctil y evidente: botones obvios y áreas de toque extra grandes.
- Alto contraste indispensable.
- Enfoque total en el uso a una sola mano y retroalimentación inmediata.

## 2. Colors

La paleta se centra en tonos industriales con contrastes funcionales claros para éxito, error y progreso.

### Primary
- **Gris Pizarra Profundo** (#1E293B): Énfasis principal, botones y textos destacados. Da una sensación formal, segura y táctica.

### Secondary
- **Verde Seguridad** (#0D9488): Acentos secundarios y estados correctos. Indica validación exitosa de inmediato.

### Tertiary
- **Violeta Secundario** (#7C3AED): Paso 2 de activación, como la generación de QR o indicadores intermedios.

### Neutral
- **Fondo** (#FAFAFA): Fondo principal de las pantallas.
- **Superficie** (#FFFFFF): Tarjetas, diálogos, barras superiores.
- **Bordes** (#CBD5E1): Bordes de inputs y divisores.

### Named Rules
**The Native Contrast Rule.** El color debe ser estrictamente funcional, no decorativo. Cada cambio de estado debe tener un contraste evidente con el fondo para asegurar la visibilidad en exteriores bajo el sol.

## 3. Typography

**Display Font:** Roboto (System Default)
**Body Font:** Roboto (System Default)

**Character:** Sencilla, ultra-legible y nativa del sistema Android. Sin florituras.

### Hierarchy
- **Display** (Bold, 28sp): Valores numéricos o métricas principales de los portones.
- **Title** (Bold, 20sp): Títulos de barras superiores.
- **Body** (Medium/Normal, 14-16sp): Textos descriptivos en tarjetas.
- **Label** (Medium, 12sp): Etiquetas secundarias y texto de apoyo.

## 4. Elevation

El sistema utiliza la convención tonal y estructural de elevación de Material Design 3 de Android.

### Shadow Vocabulary
- **AppCard** (2.dp): Sombra leve para elevar y agrupar visualmente la información dentro del lienzo.
- **Floating Bar** (8.dp): Elevación fuerte para hacer flotar la cápsula de navegación inferior por encima de las pantallas principales.

### Named Rules
**The Flat-By-Default Rule.** Las superficies descansan planas. Las sombras solo aparecen para denotar superposición funcional (barras de navegación, diálogos) o agrupación interactiva.

## 5. Components

### Buttons
- **Shape:** Radio de borde de 12.dp.
- **Primary:** Táctiles y contundentes, color Gris Pizarra Profundo para acciones neutras, Verde Seguridad para apertura.
- **Hover / Focus:** Micro-interacción de reducción de escala (animación spring a 0.92x).

### Cards / Containers
- **Corner Style:** 24.dp (AppCard estándar).
- **Background:** Superficie blanca o clara.
- **Border:** 1.dp, color primario al 10% de opacidad.
- **Internal Padding:** Entre 16.dp y 20.dp.

### Navigation
- **Floating Bottom Bar:** Cápsula flotante, con fondo surfaceVariant, borde redondeado a 26.dp, con padding lateral de 48.dp. El botón central (Escáner) se destaca con fondo Verde Seguridad y rotación asimétrica de -3° al tocarse.

## 6. Do's and Don'ts

### Do:
- **Do** priorizar el contraste alto para garantizar visibilidad en exteriores.
- **Do** diseñar pensando en botones y áreas de toque grandes (operación principal a una sola mano).
- **Do** usar la animación tipo "spring" (escala 0.92x en presionar) para brindar retroalimentación táctil física inmediata a cada toque.

### Don't:
- **Don't** diseñar interfaces que parezcan una página web empaquetada. La app tiene que sentirse 100% nativa Android.
- **Don't** sobrecargar la pantalla con menús complejos ni elementos decorativos que distraigan de las funciones vitales (escanear y validar).
- **Don't** sobrescribir la forma (shape) ni la elevación de `AppCardDefaults` manualmente. Usar siempre las constantes predefinidas.
