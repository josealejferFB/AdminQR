# Rediseño Menú de Configuración de Portones

## Problemas
1. `GateRepositoryImpl.getGates()` nunca setea `isOdooRegistered = true` → gates del backend siempre muestran "No configurada"
2. Menú de tres puntos tiene opción "Ver detalles" que es un TODO vacío
3. Sin indicador visual de estado de registro en los chips
4. "Configurar con Odoo" mezcla registro API + configuración Bluetooth del ESP32 sin separación clara

## Diseño

### Chips (GateChipRow)
- Gate con `id != null` (registrado en Odoo): `isOdooRegistered = true`, icono check verde sutil, sin subtítulo
- Gate sin `id` (local): `isOdooRegistered = false`, subtítulo "No configurado" en gris, icono outline

### Long-press → DropdownMenu (reemplaza el IconButton de tres puntos)
- Se activa con `combinedClickable(onLongClick = showMenu)` en el chip
- Mismo `DropdownMenu` actual pero con items reorganizados

### Items del menú por estado

| Gate registrado (Odoo) | Gate local (no registrado) |
|---|---|
| Estado: "Registrado (ID {n})" badge verde | Estado: "Local" badge outline |
| "Reenviar URL Odoo al ESP32" | "Registrar en Odoo" (solo API) |
| "Renombrar" | "Enviar URL Odoo al ESP32" (solo BT) |
| "Cambiar Hostname" | "Cambiar Hostname" |
| — | "Eliminar" |

### "Registrar en Odoo" (nuevo diálogo simple)
- Solo pide nombre (opcional, pre-llenado)
- Botón "Registrar"
- Llama `registerGateInOdoo()` 
- Muestra resultado: éxito con ID o error
- No involucra Bluetooth

### "Enviar URL Odoo al ESP32" (nuevo diálogo simplificado)
- Solo campos: protocolo, IP, puerto del servidor Odoo
- Conecta BT, envía comando `config`, muestra resultado
- Sin registro en Odoo (asume ya registrado)

### Fix: `GateRepositoryImpl.getGates()`
- Al mapear `GateDto` → `GateInfo`, si `dto.id != null`, setear `isOdooRegistered = true`

## Archivos a modificar
- `data/repository/GateRepositoryImpl.kt` — fix isOdooRegistered
- `presentation/ui/home/HomeScreen.kt` — GateChipRow: long-press, menú reorganizado
- Posible nuevo: `OdooRegisterSimpleDialog.kt` (solo API) y `EspOdooUrlDialog.kt` (solo BT)
