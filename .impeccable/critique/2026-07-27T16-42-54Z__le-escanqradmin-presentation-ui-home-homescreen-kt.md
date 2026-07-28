---
target: HomeScreen.kt
total_score: 26
p0_count: 1
p1_count: 1
timestamp: 2026-07-27T16-42-54Z
slug: le-escanqradmin-presentation-ui-home-homescreen-kt
---
| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Good empty states and loading skeletons |
| 2 | Match System / Real World | 3 | Good language |
| 3 | User Control and Freedom | 4 | |
| 4 | Consistency and Standards | 2 | Too many different card styles and colors in the top stats area |
| 5 | Error Prevention | 3 | Confirmation dialog on delete |
| 6 | Recognition Rather Than Recall | 2 | Action buttons (Aprovisionar QR, Registrar Portón) lack clear hierarchy |
| 7 | Flexibility and Efficiency | 3 | Gate chip selector is good |
| 8 | Aesthetic and Minimalist Design | 1 | **[P0]** Visual clutter: 3 top cards with different colors, a huge connection panel, and a chip row. The cognitive load is very high. |
| 9 | Error Recovery | 3 | |
| 10 | Help and Documentation | 2 | Onboarding sheet helps, but UI is cluttered |
| **Total** | | **26/40** | **Acceptable** |

#### Anti-Patterns Verdict
**LLM assessment**: The `HomeScreen` suffers from cognitive overload and poor visual hierarchy. The three cards at the top ("Aprovisionar QR", "Registrar Portón", "USUARIOS REGISTRADOS") all compete for attention with equal visual weight but completely different colors (secondary, tertiary). The Bluetooth connection panel and the Gate Chip Selector add to the noise. It feels less like a focused tactical tool and more like an admin dashboard crammed onto a mobile screen.

#### Overall Impression
La pantalla principal (HomeScreen) está sobrecargada. Hay demasiados paneles, tarjetas de colores y secciones compitiendo por la misma jerarquía visual. La carga cognitiva (Cognitive Load) es muy alta para un guardia de seguridad que necesita operar rápido.

#### What's Working
- El uso de `PullToRefresh` y `SkeletonUserCard` para los estados de carga.
- El sistema de navegación de chips para filtrar portones.

#### Priority Issues
- **[P0] Sobrecarga Visual y Jerarquía (Cognitive Overload)**
  - **Why it matters**: El usuario se enfrenta a un "muro de opciones": panel de servidor, selector de chips, panel de Bluetooth, tres tarjetas de acción, barra de búsqueda y lista de usuarios. Es abrumador y viola la regla de "Un enfoque principal" de Material Design.
  - **Fix**: Consolidar las acciones ("Aprovisionar QR" y "Registrar Portón") en un Floating Action Button (FAB) secundario o un menú, y simplificar el estado del Bluetooth/Servidor en una sola tarjeta de estado unificada superior.
  - **Suggested command**: `/impeccable distill` (para reducir el ruido y simplificar la jerarquía visual).

- **[P1] Tarjetas (Cards) inconsistentes**
  - **Why it matters**: Las tarjetas de "Aprovisionar" y "Registrar" usan bordes de colores completos (`secondary`, `tertiary`) y tintes fuertes, rompiendo la regla de "The Flat-By-Default Rule" y compitiendo en prominencia.
  - **Fix**: Usar tarjetas estándar unificadas y depender de la tipografía y de botones primarios para llamar a la acción.
  - **Suggested command**: `/impeccable shape` o `/impeccable polish`.

#### Persona Red Flags
- **Casey (Distracted Mobile User)**: Casey se sentirá perdido intentando encontrar la acción principal entre tantos botones coloridos y paneles de estado diferentes.
- **Jordan (First-Timer)**: La pantalla principal parece el panel de control de un avión en lugar de una simple app de escaneo de accesos.

#### Questions to Consider
- ¿De verdad necesitamos que la tarjeta de "Aprovisionar QR" y "Registrar Portón" estén siempre visibles y ocupando 120dp en la pantalla principal? ¿Podrían moverse a la sección de Configuración o a un menú emergente (Bottom Sheet)?
- ¿Se podría unificar el estado del Servidor (Odoo) y el estado del Portón (Bluetooth) en un solo indicador superior de "Estado del Sistema"?
