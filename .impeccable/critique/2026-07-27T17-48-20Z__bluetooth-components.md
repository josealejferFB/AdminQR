---
timestamp: 2026-07-27T17-48-20Z
slug: bluetooth-components
---
| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 4 | Excellent use of the `PulseDot` and Connection States (Connecting, Paired). |
| 2 | Match System / Real World | 3 | Good terminology ("Vincular", "Conectar"). |
| 3 | User Control and Freedom | 4 | Ability to disconnect and unpair at any time is well implemented. |
| 4 | Consistency and Standards | 2 | **[P1]** Typography drift: Widespread use of hardcoded `fontSize` (9.sp, 10.sp, 11.sp) instead of Material Theme Type Scale. |
| 5 | Error Prevention | 3 | |
| 6 | Recognition Rather Than Recall | 3 | Clear iconography used across device connection states. |
| 7 | Flexibility and Efficiency | 3 | Collapsible/expandable panel helps save screen real estate. |
| 8 | Aesthetic and Minimalist Design | 2 | **[P0]** Visual clutter in `GateConnectionCard`: The card crams too many text buttons ("Reenviar IP", "Desconectar", "Conectar", "Desvincular") into small rows, creating a dense, overwhelming interaction area. |
| 9 | Error Recovery | 3 | Shows error feedback inside the Dialog. |
| 10 | Help and Documentation | 2 | Technical details (Service UUID) leak into the UI. |
| **Total** | | **29/40** | **Good, needs polish** |

#### Anti-Patterns Verdict
**LLM assessment**: Both `BluetoothConnectionPanel.kt` and `BluetoothDialog.kt` suffer from **Typography Drift** (hardcoded `sp` values ignoring the design system) and **Aesthetic Clutter** inside the connection items. The `GateConnectionCard` packs too many tiny buttons (`9.sp`, `10.sp`) into a single row, making touch targets uncomfortably small and violating the 44x44px minimum touch target heuristic.

#### Overall Impression
La funcionalidad base (estados de Bluetooth, animaciones como `PulseDot`, separación entre vinculados y otros dispositivos) es robusta y muy completa. Sin embargo, la interfaz de las tarjetas individuales está demasiado comprimida. Se priorizó meter todos los botones posibles ("Reenviar IP", "Desconectar", "Desvincular") en una sola fila en lugar de usar menús o acciones jerárquicas, lo que hace que los botones sean muy pequeños y difíciles de tocar en un apuro.

#### What's Working
- **Feedback de estado**: El `PulseDot` y los íconos dinámicos (`BluetoothConnected`, `BluetoothSearching`) proveen excelente visibilidad del sistema.
- **Micro-interacciones**: La expansión animada (`animateContentSize` con `spring`) del panel es fluida y agradable.

#### Priority Issues
- **[P0] Touch Targets y Botones Apretados (Aesthetic & Functional Clutter)**
  - **Why it matters**: En `GateConnectionCard`, los botones de acción ("Conectar", "Desvincular", "Reenviar IP", "Desconectar") usan textos minúsculos de `9.sp` y `10.sp` colocados uno al lado del otro. Esto viola el mínimo de 44x44px de área táctil de Material Design y es muy propenso a toques accidentales por parte del guardia de seguridad.
  - **Fix**: Reemplazar la fila densa de botones por un botón de acción primaria grande (ej. "Conectar") y ocultar las acciones secundarias ("Desvincular", "Reenviar IP") detrás de un botón de menú de 3 puntos (Overflow Menu), o usar iconos en lugar de textos largos.

- **[P1] Typography Drift (Consistencia de Diseño)**
  - **Why it matters**: Al igual que en la versión anterior de `HomeScreen`, estos archivos ignoran el sistema de diseño (`MaterialTheme.typography`) utilizando de forma repetitiva tamaños fijos (`14.sp`, `13.sp`, `12.sp`, `11.sp`, `10.sp`, `9.sp`, `8.sp`). Esto impide que el texto escale con las preferencias de accesibilidad del dispositivo.
  - **Fix**: Reemplazar los `fontSize = XX.sp` con la escala tipográfica correcta (`labelSmall`, `labelMedium`, `bodySmall`, etc.).

#### Persona Red Flags
- **Casey (Distracted Mobile User)**: A Casey le resultará difícil tocar el botón de "Desconectar" sin tocar accidentalmente "Reenviar IP" debido a que están muy juntos y son minúsculos (9.sp).

#### Questions to Consider
- ¿Es estrictamente necesario mostrar el *Service UUID* (00001101...) en la parte inferior del diálogo de Bluetooth para un usuario que no es desarrollador?
- ¿Podemos consolidar las acciones secundarias de las tarjetas (como "Desvincular" o "Reenviar IP") en un menú de tres puntos (DropdownMenu) para limpiar el espacio visual?
