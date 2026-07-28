---
timestamp: 2026-07-27T17-57-42Z
slug: chips-alignment
---
| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 4 | Consistency and Standards | 1 | **[P0]** The "Todas" chip uses a native `FilterChip` while gates use a custom `ChipWithMenu`, resulting in different heights and styles out of the box. |
| 8 | Aesthetic and Minimalist Design | 2 | Chips look misaligned because `LazyRow` lacks vertical centering and the heights are mismatched. |

#### Anti-Patterns Verdict
**LLM assessment**: There is severe **Component Inconsistency**. The "Todas" chip uses Material 3's `FilterChip` (which has a fixed 32.dp minimum height and specific padding), while the custom `ChipWithMenu` calculates its height dynamically based on its padding (vertical = 8.dp). Because they are in a `LazyRow` without `verticalAlignment = Alignment.CenterVertically`, they align to the top and look jagged.

#### Priority Issues
- **[P0] Inconsistencia de Alturas y Alineación**
  - **Why it matters**: Rompe completamente la armonía visual de la barra de filtros.
  - **Fix**: Reemplazar el `FilterChip` nativo de "Todas" por una versión visualmente idéntica al `ChipWithMenu` (pero sin menú), y añadir animaciones de altura (ej: 40dp seleccionado, 32dp deseleccionado) para que el estado seleccionado destaque como pidió el usuario. Además, alinear verticalmente el `LazyRow` al centro.
