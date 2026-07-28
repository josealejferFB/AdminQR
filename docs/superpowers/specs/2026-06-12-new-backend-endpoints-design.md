# New Backend Endpoints: Gate Assignment Diff API + Gate Deletion

**Date:** 2026-06-12
**Status:** Approved

## Motivation

The Odoo backend team updated two endpoints:

1. **Hybrid `/api/control_acceso`** — The `update` action no longer accepts `puertas_autorizadas` (list of MACs) for bulk replace. Instead, it receives `add_gate_ids` / `remove_gate_ids` arrays for incremental, transactional updates.

2. **New `/api/v1/gates/delete`** — Soft-delete (archive) a gate registered in Odoo.

## Scope

- ✅ Gate assignment: incremental diff via `add_gate_ids` + `remove_gate_ids`
- ✅ Gate deletion: soft-delete Odoo-registered gates from chip context menu
- ❌ No changes to QrContent model, GateInfo model, or UI structure

---

## 1. Gate Assignment — Data Flow

### Current (removed)
`updateEntry` sends JSON:
```json
{
  "jsonrpc": "2.0",
  "params": {
    "action": "update",
    "cedula": "12345678",
    "nombre": "...",
    "placas": "...",
    "puertas_autorizadas": [{"mac_address": "AA:BB:CC:DD:EE:FF"}, ...]
  }
}
```

### New
```json
{
  "jsonrpc": "2.0",
  "params": {
    "action": "update",
    "cedula": "12345678",
    "nombre": "...",
    "placas": "...",
    "add_gate_ids": [5, 6],
    "remove_gate_ids": [2]
  }
}
```

Both arrays are optional. Odoo applies them in a single transaction.

### Component flow

```
ActiveUserCard
  │  Tracks initialGates (Set<MacAddress>) when expanded
  │  User toggles FilterChips → selectedGates changes
  │  On "Guardar":
  │    1. addedMACs = selectedGates - initialGates
  │    2. removedMACs = initialGates - selectedGates
  │    3. Resolve addedMACs → addedGateIds via gates list
  │       (skip MACs with null id — not registered in Odoo)
  │    4. Resolve removedMACs → removedGateIds via gates list
  │
  ▼
HomeScreen (callback)
  │  Calls viewModel.updateUser(user, addedGateIds, removedGateIds)
  │
  ▼
HomeViewModel.updateUser()
  │  Creates QrContent (name/plate only, NO authorizedGates)
  │  Calls syncRepository.updateEntry(qrContent, addGateIds, removeGateIds)
  │  On success → updateRecord + snackbar
  │
  ▼
SyncRepositoryImpl.updateEntry()
  │  Builds JSON with add_gate_ids / remove_gate_ids arrays
  │  Removes puertas_autorizadas field entirely
  │  POSTs to /api/control_acceso
```

### `SyncRepository` interface change

```kotlin
suspend fun updateEntry(
    data: QrContent,
    addGateIds: List<Int> = emptyList(),
    removeGateIds: List<Int> = emptyList()
): Result<Unit>
```

No overload kept for the old signature — migration is complete.

---

## 2. Gate Deletion — Data Flow

### API call
```
POST /api/v1/gates/delete
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "params": {
    "gate_id": 5
  }
}
```

Response:
```json
{
  "jsonrpc": "2.0",
  "result": {
    "success": true,
    "message": "Gate deleted successfully"
  }
}
```

### Component flow

```
ChipWithMenu (GateChipRow)
  │  New menu item "Eliminar portón" (only for Odoo-registered gates)
  │  → Shows AlertDialog confirmation
  │  → Confirmed → calls onDeleteGate(gate)
  │
  ▼
HomeScreen → viewModel.deleteGate(gate.id)
  │
  ▼
HomeViewModel.deleteGate()
  │  gateRepository.deleteGate(gateId)
  │  On success → remove from local gates list + snackbar
  │
  ▼
GateRepositoryImpl.deleteGate()
  │  POST to /api/v1/gates/delete
  │  Parse result → Result.success/Result.failure
```

### New constants

```kotlin
// ApiConstants
private var endpointGateDelete: String = "/api/v1/gates/delete"

object Endpoints {
    val GATE_DELETE: String get() = "$BASE_URL${endpointGateDelete}"
}
```

---

## 3. Files Modified

| File | Change |
|---|---|
| `data/network/ApiConstants.kt` | Add `endpointGateDelete`, `KEY_ENDPOINT_GATE_DELETE`, `saveConfig` param, `Endpoints.GATE_DELETE` |
| `domain/repository/GateRepository.kt` | Add `suspend fun deleteGate(gateId: Int): Result<Unit>` |
| `data/repository/GateRepositoryImpl.kt` | Implement `deleteGate()` |
| `domain/repository/SyncRepository.kt` | Change `updateEntry` signature: add `addGateIds: List<Int>`, `removeGateIds: List<Int>` |
| `data/repository/SyncRepositoryImpl.kt` | Change payload: remove `puertas_autorizadas`, add `add_gate_ids`/`remove_gate_ids` arrays |
| `presentation/ui/home/HomeViewModel.kt` | Change `updateUser()` to accept diff lists; add `deleteGate()` |
| `presentation/ui/home/components/ActiveUserCard.kt` | Track initial gates; compute MAC→ID diff on save; change callback to include IDs |
| `presentation/ui/home/HomeScreen.kt` | Update `ActiveUserCard` call site; pass `onDeleteGate` to `ChipWithMenu` |

### Not modified

- `QrContent` — `authorizedGates` field stays but is no longer sent in API payload
- `ActiveUser` — `authorizedGates` stays, still used for local UI state
- `GateInfo` — unchanged
- `ConductoresResponse` / `PuertaAutorizada` — `puertas_autorizadas` stays for response parsing

---

## 4. Error Handling

- Gate deletion fails → snackbar error, no local state change
- If gate ID is null (local-only gate) → skip the add/remove API call for that gate
- If diff results in empty add_gate_ids and remove_gate_ids → skip API call entirely (only update name/plate in local DB)
- Network errors → snackbar with friendly error message

---

## 5. UI Details

### ActiveUserCard — diff computation

```kotlin
// On "Guardar":
val initialMACs = remember { mutableStateOf(user.authorizedGates.toSet()) }
val addedIds = (selectedGates - initialMACs.value)
    .mapNotNull { mac -> gates.find { it.macAddress == mac }?.id }
val removedIds = (initialMACs.value - selectedGates)
    .mapNotNull { mac -> gates.find { it.macAddress == mac }?.id }
onUpdate(user.copy(...), addedIds, removedIds)
```

### ChipWithMenu — new menu item

```kotlin
// Inside DropdownMenu, only for isOdooRegistered:
DropdownMenuItem(
    text = { Text("Eliminar portón", color = MaterialTheme.colorScheme.error) },
    onClick = { ... },
    leadingIcon = { Icon(Icons.Default.Delete, ...) }
)
```
