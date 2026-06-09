# BT Connection Panel — Redesign for Multi-Gate

## Problem

The current "Lector Físico (ESP32)" card assumes a single ESP32 device. With multi-gate support, the app needs a UI that reflects multiple gates with individual Bluetooth connection status, while respecting that only one SPP connection can be active at a time.

## Solution

Replace the single device card with a **Bluetooth Connections Panel** — an `AppCard` listing all gates from `uiState.gates`, each with its connection status and action button.

## Schema

### Layout

```
┌──────────────────────────────────────────────┐
│  🔵 Conexiones Bluetooth    ● 1 conectada    │  ← header row
├──────────────────────────────────────────────┤
│  ●  Portón Principal                  ○▶     │  ← gate row (connected)
│     MAC: AA:BB:CC:DD:EE:FF                   │
│  ○  Entrada Secundaria             ○▶     │  ← gate row (disconnected, paired)
│     BT: ESP32_Seguro                          │
│  ○  Salida Emergencia         ✦ Vincular│  ← gate row (not paired)
│     MAC: 11:22:33:44:55:66                   │
├──────────────────────────────────────────────┤
│  [➕ Registrar nueva tarjeta]                 │  ← footer
└──────────────────────────────────────────────┘
```

### States

| State | Indicator | Action |
|---|---|---|
| Connected | Green dot + pulse | "Desconectar" (red text) |
| Disconnected + paired | Gray dot | "Conectar" (secondary button) |
| Not paired | Gray dot | "Vincular" (opens BluetoothDialog) |
| Connecting | Yellow dot + spinner | (disabled, showing spinner) |

### Empty state (uiState.gates is empty)

```
┌──────────────────────────────────────────────┐
│  🔵 Conexiones Bluetooth                      │
├──────────────────────────────────────────────┤
│              📡                               │
│     No hay tarjetas registradas               │
│                                               │
│  [➕ Registrar primera tarjeta]               │
└──────────────────────────────────────────────┘
```

## Components

### `BluetoothConnectionPanel` (inline in HomeScreen.kt or separate file)

- **Parameters**: `gates`, `connectionState`, `pairedDevices`, `onConnect(gate)`, `onDisconnect()`, `onPair(gate)`, `onRegisterGate()`
- Renders the header, gate list, and footer
- Each gate row checks `pairedDevices` to determine if MAC is paired
- Handles single-connection constraint: connecting to a new gate replaces the existing connection

### HomeViewModel changes

- `connectToGate(gate: GateInfo)` — resolves MAC from gate, calls `connectToDevice(mac)`
- `isGatePaired(gate: GateInfo)` — checks if gate's MAC exists in `pairedDevices` flow
- `getConnectedGateName() -> String?` — returns name of the connected gate (if any) by matching MAC

### HomeScreen changes

- Remove the old "Lector Físico (ESP32)" card (lines 308-452)
- Insert `BluetoothConnectionPanel` in its place
- The "Registrar Portón" card stays as-is (it opens GateRegistrationDialog)

## Data Flow

1. `HomeViewModel.loadGates()` populates `uiState.gates`
2. `pairedDevices` flow from `BluetoothRepository` is observed in HomeScreen
3. `BluetoothConnectionPanel` renders each gate row:
   - If gate's MAC exists in `pairedDevices` → shows "Conectar" / "Desconectar"
   - If not → shows "Vincular"
4. "Conectar" → `viewModel.connectToDevice(gate.macAddress)` → `connectionState` updates
5. "Vincular" → opens `BluetoothDialog` (existing component) scoped to pair the target ESP32
6. "Desconectar" → `viewModel.disconnect()`

## Dependencies

- Existing: `BluetoothRepository`, `BluetoothConnectionState`, `BluetoothDialog`, `GateRepository`
- New/modified: `BluetoothConnectionPanel`, `HomeViewModel.connectToGate()`

## Out of Scope

- Managing multiple concurrent BT connections (not supported by hardware)
- BT auto-reconnect logic (already handled by ESP32 V8 firmware)
- Visual companion / animated transitions
