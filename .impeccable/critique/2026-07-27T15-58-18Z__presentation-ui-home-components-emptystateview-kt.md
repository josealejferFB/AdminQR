---
target: EmptyStateView.kt
total_score: 30
p0_count: 1
p1_count: 0
timestamp: 2026-07-27T15-58-18Z
slug: presentation-ui-home-components-emptystateview-kt
---
| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Good empty states |
| 2 | Match System / Real World | 3 | Language is clear |
| 3 | User Control and Freedom | 4 | |
| 4 | Consistency and Standards | 1 | Raster PNGs with solid backgrounds break native Android theme |
| 5 | Error Prevention | 4 | |
| 6 | Recognition Rather Than Recall | 4 | |
| 7 | Flexibility and Efficiency | 3 | |
| 8 | Aesthetic and Minimalist Design | 2 | Solid backgrounds on images clash with UI |
| 9 | Error Recovery | 3 | Empty states suggest actions |
| 10 | Help and Documentation | 3 | |
| **Total** | | **30/40** | **Good** |

#### Anti-Patterns Verdict
**LLM assessment**: Definitivo problema de Slop AI. Las ilustraciones generadas por IA suelen guardarse en formato PNG o JPG rasterizado con fondos sólidos (blancos o de otro color pastel). En Android, donde el modo oscuro y los temas dinámicos (Material You) son la norma, una imagen con fondo sólido destaca horriblemente, rompiendo la consistencia del tema y viéndose como una página web mal diseñada en lugar de una app nativa Android limpia.

**Deterministic scan**: No aplica (Android Native view). 

#### Overall Impression
Los textos del estado vacío son muy buenos y claros, pero el uso de recursos visuales PNG no transparentes es un anti-patrón de diseño nativo grave para Android.

#### What's Working
- Textos directos y útiles.
- El espaciado general es correcto y respeta la proporción Material.

#### Priority Issues
- **[P0] Fondo sólido en ilustraciones (AI Slop)**
  - **Why it matters**: Rompe completamente la inmersión nativa y el modo oscuro. Las ilustraciones parecen parches pegados en la pantalla.
  - **Fix**: Reemplazar los PNG generados por IA por iconos vectoriales nativos (Material Icons) envueltos en un contenedor circular con fondo suave (`secondaryContainer`).
  - **Suggested command**: `/impeccable onboard` o `/impeccable polish` (para rediseñar el estado vacío con Material Icons).

#### Persona Red Flags
**Alex (Power User)**: Notará inmediatamente que la app no es nativa de calidad si ve fondos sólidos blancos en modo oscuro.

#### Minor Observations
- Se podría añadir un botón de "Refrescar" o "Añadir portón" directamente en el `EmptyStateView` para hacerlo interactivo.

#### Questions to Consider
- ¿Qué tal si usamos iconos vectoriales de Material Design tintados con los colores de tu marca (Verde Seguridad) en vez de imágenes generadas?
