---
timestamp: 2026-07-27T17-54-21Z
slug: chips-components
---
| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Selected chip is visible, but the state transition is harsh. |
| 2 | Match System / Real World | 4 | |
| 3 | User Control and Freedom | 4 | |
| 4 | Consistency and Standards | 3 | Uses Material colors but lacks Material 3's motion guidelines for state changes. |
| 5 | Error Prevention | 3 | |
| 6 | Recognition Rather Than Recall | 4 | Names are clearly displayed in the chips. |
| 7 | Flexibility and Efficiency | 3 | |
| 8 | Aesthetic and Minimalist Design | 2 | **[P1]** Abrupt visual changes. The selected state color switches instantly with no animation, making the interaction feel cheap rather than premium. |
| 9 | Error Recovery | 3 | |
| 10 | Help and Documentation | 3 | |
| **Total** | | **32/40** | **Good** |

#### Anti-Patterns Verdict
**LLM assessment**: The `ChipWithMenu` components suffer from a lack of **Micro-interactions & Transitions**. When a user selects a chip, the background color, text color, and border color snap instantly to the active state. There is no easing or animation. This violates the "Smooth transitions" rule in the Impeccable polish guidelines. 

#### Overall Impression
La funcionalidad de las chips es correcta (filtran adecuadamente y tienen su propio menú desplegable en pulsación larga), pero la experiencia interactiva se siente seca. El cambio entre no seleccionado y seleccionado es brusco (sin transición de color o tamaño).

#### Priority Issues
- **[P1] Falta de Animaciones de Estado (Micro-interactions)**
  - **Why it matters**: Un cambio de estado sin animación se siente abrupto y de baja calidad. Material Design recomienda transiciones de estado de 150-300ms para que las interacciones se sientan vivas y responsivas.
  - **Fix**: Usar `animateColorAsState` para los colores de fondo, borde y texto. Opcionalmente, agregar una sutil animación de escala (`animateFloatAsState`) para que el chip seleccionado "crezca" un poco o se sienta más prominente (resaltando visualmente la diferencia entre elegir uno u otro portón).
