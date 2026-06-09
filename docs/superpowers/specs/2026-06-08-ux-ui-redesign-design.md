# UX/UI Redesign — EscanQR Admin

**Date:** 2026-06-08
**Status:** Approved
**Audit:** docs/Auditoria-UXUI.md (preliminary findings embedded in the conversation)

---

## 1. Navigation Architecture

### Current
- `CustomBottomBar` only in HomeScreen and ScannerScreen
- `CustomTopBar` floating in ScannerScreen, custom Row in HomeScreen, M3 TopAppBar elsewhere
- `SetSystemBarsVisibility(false)` with manual padding per screen
- Mixed: 3 different top bar implementations, 2/5 screens with bottom bar

### Target
- Single `Scaffold` wrapper in `AppNavigation.kt` with M3 `NavigationBar`
- 3 destinations: **Home**, **Scanner** (center FAB), **Config**
- ResultScreen and ESPConfigScreen are detail screens (no NavigationBar — only top back arrow)
- `SetSystemBarsVisibility(false)` removed. `enableEdgeToEdge()` remains for native edge-to-edge
- `SystemUi.kt` deleted
- `Bars.kt` refactored: `CustomTopBar` removed, `CustomBottomBar` → `AppBottomBar` using M3 `NavigationBar` + `NavigationBarItem`, `CustomSnackbar` preserved
- NavigationBar visibility: shown only on `Home`, `Scanner`, `Config` routes; hidden on detail screens

### Transitions
- M3 default: `slideInHorizontally` + `fadeIn` for enter, reverse for pop
- Duration: 300–400ms, `FastOutSlowInEasing`
- Scanner → Result: slide-up/fade (detail push)

---

## 2. Color Palette

### Replace Color.kt constants with ColorScheme

| Token | Light (Material) | Dark (Material) |
|-------|------------------|-----------------|
| primary | `#1E293B` (Slate-800) | `#94A3B8` (Slate-400) |
| onPrimary | `#FFFFFF` | `#0F172A` |
| primaryContainer | `#E2E8F0` (Slate-200) | `#1E293B` (Slate-800) |
| onPrimaryContainer | `#0F172A` | `#F1F5F9` |
| secondary | `#0D9488` (Teal-600) | `#2DD4BF` (Teal-400) |
| onSecondary | `#FFFFFF` | `#042F2E` |
| secondaryContainer | `#CCFBF1` (Teal-100) | `#134E4A` (Teal-900) |
| onSecondaryContainer | `#042F2E` | `#CCFBF1` |
| tertiary | `#7C3AED` (Violet-600) | `#A78BFA` (Violet-400) |
| onTertiary | `#FFFFFF` | `#1E0A3C` |
| background | `#FAFAFA` | `#0A0A0A` |
| onBackground | `#0F172A` | `#FAFAFA` |
| surface | `#FFFFFF` | `#18181B` |
| onSurface | `#1E293B` | `#E4E4E7` |
| surfaceVariant | `#F1F5F9` (Slate-100) | `#27272A` (Zinc-800) |
| onSurfaceVariant | `#475569` | `#A1A1AA` |
| outline | `#CBD5E1` (Slate-300) | `#3F3F46` (Zinc-700) |
| outlineVariant | `#E2E8F0` | `#52525B` |
| error | `#DC2626` | `#FCA5A5` |
| onError | `#FFFFFF` | `#450A0A` |
| errorContainer | `#FEE2E2` | `#7F1D1D` |
| onErrorContainer | `#450A0A` | `#FEE2E2` |

### ESPConfigScreen — Custom ColorScheme
Dual scheme `EspLightColorScheme` / `EspDarkColorScheme` in Theme.kt:

| Token | Light (terminal) | Dark (terminal) |
|-------|------------------|-----------------|
| surface | `#F8FAFC` (Slate-50) | `#0D1117` |
| surfaceVariant | `#E2E8F0` | `#161B22` |
| outline | `#CBD5E1` | `#30363D` |
| onSurface | `#0F172A` | `#E6EDF3` |
| primary | `#1F6FEB` | `#1F6FEB` |
| secondary | `#238636` | `#238636` |
| tertiary | `#8957E5` | `#8957E5` |
| onSurfaceVariant | `#64748B` | `#8B949E` |

---

## 3. Component Unification

### AppCard (no changes — already correct)
- `RoundedCornerShape(24.dp)`, elevation 2.dp, border primary 10%

### StatCard — Rewrite
- Wraps content in `AppCard` instead of raw `Box`
- Inherits 24dp radius, 2dp elevation, primary border
- Compact padding (12dp instead of 16dp internally)

### HistoryCatalogItem — Fix overrides
- Remove `shape = RoundedCornerShape(16.dp)` → use `AppCardDefaults.Shape`
- Remove `elevation = 1.dp` → use `AppCardDefaults.Elevation`
- Protocol icon `Box` with `CircleShape` + `background` stays (visual distinctiveness)

### ConfigurationCard — Fix elevation
- Remove `elevation = 4.dp` → use `AppCardDefaults.Elevation`

### DeviceItem — Fix overrides
- Remove `shape = RoundedCornerShape(16.dp)` → use `AppCardDefaults.Shape`
- Keep green/connected special border

### SearchBar — Add border
- `focusedBorderColor = MaterialTheme.colorScheme.primary`
- `unfocusedBorderColor = MaterialTheme.colorScheme.outline`
- Remove `focusedContainerColor`/`unfocusedContainerColor` overrides (let theme decide)

### EmptyHistoryPlaceholder — Wrap in AppCard
- Same icon/text but inside `AppCard` with `onClick = null`
- Matches `EmptyConsole` style

### ResultSnackbar — Theme migration
- Loading: `primaryContainer` bg, `onPrimaryContainer` text/icon
- Success: `secondaryContainer` bg, `onSecondaryContainer` text/icon
- Error: `errorContainer` bg, `onErrorContainer` text/icon
- Shape: `AppCardDefaults.Shape` (24dp → matches main style)

### ResultScreen — Theme migration
- `StepGreen` → `colorScheme.secondary`
- `StepRed` → `colorScheme.error`
- `StepGray` → `colorScheme.outline`
- `StepPurple` → `colorScheme.tertiary`
- `UserInfoCard` VÁLIDO badge: `colorScheme.primaryContainer` bg, `colorScheme.primary` text
- `StepDoneChip`: `colorScheme.secondaryContainer` bg, `colorScheme.secondary` text/icon
- `ServerStepContent` button: `containerColor = colorScheme.primary`
- `QrStepContent` button: `containerColor = colorScheme.tertiary`

### Shape consistency
| Element | Radius |
|---------|--------|
| AppCard / cards | 24dp |
| Buttons (filled, outlined, tonal) | 12dp |
| OutlinedTextField | 12dp |
| FAB/NavigationBar | 16dp top-only |
| Dialogs | 24dp |
| Chips / badges | 8dp |

---

## 4. Screen-by-Screen Changes

### MainActivity
- Remove `SetSystemBarsVisibility(false)` call
- Remove `import com.example.escanqradmin.presentation.common.util.SetSystemBarsVisibility`
- Keep `enableEdgeToEdge()`

### SystemUi.kt
- Delete entire file

### AppNavigation.kt
- Restructure: Scaffold → NavigationBar + NavHost
- NavigationBar with 3 items using `NavigationBarItem`
- Scanner item with custom styling (highlighted center)
- Visibility logic: hide on Result and ESPConfig
- Animated transitions (slide + fade, 400ms)

### HomeScreen
- Replace custom Row top bar with M3 `TopAppBar`
  - Title: logo + "EscanQR"
  - Actions: theme toggle icon button
- Remove manual status bar padding (Scaffold handles it)
- Replace `SecondaryOrange` with `colorScheme.secondary`
- Keep `PullToRefreshBox`, AppCard structure, ActiveUserCard feed
- Replace metric Row with AppCard wrapping (StatCard is already fixed)

### ScannerScreen
- Remove `CustomTopBar` and `CustomBottomBar` from screen
- Keep camera preview, overlay, ManualEntryDialog
- NavigationBar from AppNavigation covers bottom

### ConfigScreen
- `HistoryCatalogItem` uses AppCardDefaults (no shape/elevation override)
- `ConfigurationCard` uses AppCardDefaults elevation
- `EmptyHistoryPlaceholder` wrapped in AppCard
- `formatTimestamp` migrated to `java.time.Instant + Duration` (fixes remaining G2)
- NavigationBar visible (global)

### ResultScreen
- All hardcoded StepColors → colorScheme tokens
- `StepDoneChip`, `ServerStepContent`, `QrStepContent` buttons → theme colors
- `UserInfoCard` badge → theme

### ResultSnackbar
- bgColor from hardcoded hex → `MaterialTheme.colorScheme.{primaryContainer, secondaryContainer, errorContainer}`

### ESPConfigScreen
- Import `EspColorScheme` from theme
- Replace all `ConsoleBg`, `ConsolePanel`, `ConsoleBorder`, `FormBg`, `TxColor`, `RxColor`, `PromptColor`, `MutedText` → `EspColorScheme.*`
- Colors adapt to light/dark theme automatically
- Quick command colors remain fixed (brand colors for distinction)

### Bars.kt
- Remove `CustomTopBar`
- Rename `CustomBottomBar` → `AppBottomBar`
  - Internally use M3 `NavigationBar` + `NavigationBarItem` instead of custom `Surface` + `Row`
  - Keep `BottomNavItem` for the visual highlight
  - Central scanner button: use `NavigationBarItem` with custom `icon` composable
- Keep `CustomSnackbar` as is

### SplashScreen
- Minor: increase logo container to 120dp, adjust typography
- All theme colors already correct

---

## 5. Animation Additions

- **NavigationBar item switch**: M3 default `animateColorAsState` for icon/tint (already partially implemented in BottomNavItem)
- **Pull-to-refresh**: Already implemented with M3 PullToRefreshBox
- **Scanner line**: Already animated (tween 2000ms infinite)
- **ResultScreen step connector**: Already animated (expandVertically)
- **ActiveUserCard expand/collapse**: Already animated with spring
- **New**: Add `animateContentSize` to ESPConfig form panels
- **New**: Navigation transitions in NavHost (already partially there, improve timing)

---

## 6. Files Changed

| File | Action |
|------|--------|
| `presentation/theme/color/Color.kt` | Simplify (or remove if unused elsewhere) |
| `presentation/theme/theme/Theme.kt` | New LightColorScheme + DarkColorScheme + EspSchemes |
| `presentation/common/util/SystemUi.kt` | **DELETE** |
| `presentation/common/sharedcomponents/Bars.kt` | Remove CustomTopBar, refactor CustomBottomBar → AppBottomBar |
| `presentation/navigation/AppNavigation.kt` | Restructure with NavigationBar |
| `app/host/MainActivity.kt` | Remove SetSystemBarsVisibility |
| `presentation/ui/home/HomeScreen.kt` | TopAppBar, theme colors, remove manual padding |
| `presentation/ui/home/components/StatCard.kt` | Rewrite using AppCard |
| `presentation/ui/home/components/SearchBar.kt` | Add visible borders |
| `presentation/ui/scanner/ScannerScreen.kt` | Remove CustomTopBar/CustomBottomBar |
| `presentation/ui/config/ConfigScreen.kt` | Fix card overrides, EmptyHistoryPlaceholder, SimpleDateFormat |
| `presentation/ui/result/ResultScreen.kt` | Theme colors for StepColors |
| `presentation/ui/result/components/ResultSnackbar.kt` | Theme colors |
| `presentation/ui/espconfig/ESPConfigScreen.kt` | EspColorScheme |

---

## 7. Out of Scope (for this iteration)
- Adding new features (screens, functionality)
- Rewriting ViewModels or Repository layer
- Changing business logic
- Refactoring network layer
- Adding tests
