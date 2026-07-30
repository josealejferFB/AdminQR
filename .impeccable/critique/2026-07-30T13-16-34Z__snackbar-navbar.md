---
target: snackbar navbar
total_score: 27
p0_count: 0
p1_count: 1
timestamp: 2026-07-30T13-16-34Z
slug: snackbar-navbar
---
Method: ⚠️ DEGRADED: single-context (no sub-agent tool exposed)

### Design Health Score

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 2 | Notificaciones ocultas o solapadas por el navbar |
| 2 | Match System / Real World | 4 | Textos claros |
| 3 | User Control and Freedom | 3 | - |
| 4 | Consistency and Standards | 2 | El z-index y anclaje de componentes flotantes falla |
| 5 | Error Prevention | 3 | - |
| 6 | Recognition Rather Than Recall | 3 | - |
| 7 | Flexibility and Efficiency | 3 | - |
| 8 | Aesthetic and Minimalist Design | 2 | Elementos flotantes colisionando rompe la estética |
| 9 | Error Recovery | 2 | Es difícil recuperarse si no se lee el error tapado |
| 10 | Help and Documentation | 3 | - |
| **Total** | | **27/40** | **[Needs Work]** |

### Anti-Patterns Verdict

**LLM assessment**: Existe un problema clásico de "State Hoisting" y componentes flotantes desvinculados. Al desacoplar la barra de navegación del `Scaffold` principal, el `Scaffold` de `HomeScreen` asume que el borde inferior de la pantalla está libre, renderizando el `SnackbarHost` justo donde está el navbar. Además, hay duplicación de estado: `HomeScreen` crea su propio `SnackbarHostState` en lugar de usar el `LocalSnackbarHostState` global provisto por `AppNavigation`.

### Overall Impression
El solapamiento de notificaciones es un problema funcional severo (P1) porque oculta feedback crítico al usuario. La arquitectura de la barra flotante (que es muy buena) requiere ajustar manualmente los insets de cualquier otro elemento anclado al fondo, como los Snackbars y los FABs.

### Priority Issues

- **[P1] Functional**: Solapamiento de Snackbar y Navbar
  - **Why it matters**: Las notificaciones de error y éxito aparecen tapadas por el `FloatingBottomBar`, impidiendo que el usuario vea el feedback del sistema.
  - **Fix**: Modificar el `SnackbarHost` dentro de `HomeScreen.kt` (y cualquier otra pantalla con un Scaffold propio) añadiéndole un `modifier = Modifier.padding(bottom = 100.dp)` para elevarlo por encima de la barra flotante. Alternativamente, usar el `LocalSnackbarHostState.current` global que ya tiene el padding correcto en `AppNavigation.kt`.
  - **Suggested command**: `/impeccable polish`

### Persona Red Flags

**Sam (Accessibility-Dependent User)**: Sam no puede ver el borde del snackbar, y el lector de pantalla podría confundirse al tener elementos interactivos superpuestos en el mismo espacio visual (z-index). Es una barrera de accesibilidad grave.

### Questions to Consider
- ¿Debería el Snackbar aparecer *encima* del FloatingBottomBar (elevándolo con padding) o *delante* de él (tapando el navbar temporalmente)? Normalmente, elevarlo es la convención en Material Design cuando se trata de Bottom Navigation.
- ¿Por qué `HomeScreen.kt` declara su propio `SnackbarHostState` local en lugar de inyectar el global `LocalSnackbarHostState.current` provisto en `AppNavigation.kt`? Unificar esto simplificaría la arquitectura.
