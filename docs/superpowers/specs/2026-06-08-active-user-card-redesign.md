# ActiveUserCard Redesign

## Goal
Modernize the collapsed state of `ActiveUserCard` with professional UI/UX: avatar with initials, M3 chip badges for status, proper theme tokens, and visual hierarchy.

## Scope
Only the **collapsed state** of `ActiveUserCard`. Expanded/editing state remains unchanged.

## Design

### 1. Avatar with Initials
- **Size:** 44dp × 44dp
- **Shape:** `RoundedCornerShape(14.dp)`
- **Background:**
  - `VALIDADO` status: horizontal gradient `primary→secondary`
  - Other statuses: `surfaceVariant`
- **Content:** First 2 characters of `user.name`, uppercase
- **Typography:** 16sp, `FontWeight.Bold`
- **Color:** `onPrimary` (gradient bg) or `onSurfaceVariant` (surfaceVariant bg)

### 2. Header Row (Name + Document + Status Badge)
- **Name:** 15sp, `FontWeight.Bold`, `onSurface`
- **Document:** 12sp, `onSurfaceVariant`, 2dp top spacer from name
- **Status badge:** Chip-style container
  - Shape: `RoundedCornerShape(8.dp)`
  - Padding: horizontal 10dp, vertical 4dp
  - Content: leading icon (12dp) + text (10sp, `FontWeight.ExtraBold`)
  - **VALIDADO:** container `secondary` @ 0.12f alpha, content `secondary`, icon `Icons.Default.Done`
  - **RECHAZADO:** container `error` @ 0.12f alpha, content `error`, icon `Icons.Default.Close`
  - **Other:** container `outline` @ 0.12f alpha, content `outline`, icon `Icons.Default.Schedule`
- Layout: name+document in left Column, badge right-aligned, `Arrangement.SpaceBetween`

### 3. Divider + Plate Row
- **Divider:** `HorizontalDivider` with color `outline` @ 0.3f alpha, top/bottom 12dp
- **Label "PLACA":** 10sp, `FontWeight.Bold`, `onSurfaceVariant`
- **Plate value:** 15sp, `FontWeight.ExtraBold`, color `primary`
- **Delete button:** `IconButton` with `Icons.Default.Delete`, tint `error` @ 0.7f alpha, 20dp icon

### 4. Token Compliance
- No hardcoded `Color.Gray` or `SecondaryOrange`
- All colors from `MaterialTheme.colorScheme`
- `secondary` = validated/success
- `error` = rejection/delete
- `outline` = pending/disabled
- `onSurfaceVariant` = secondary text
- `primary` = emphasis text (plate)
- `surface` = card container (via AppCardDefaults)

### 5. AppCard Integration
- Outer container unchanged: `AppCard` with `AppCardDefaults` (24dp radius, 2dp elevation, border primary 0.1f alpha)
- Padding: 16dp internal (was 20dp — tighter, more modern)
- Expand animation: `spring` with `DampingRatioLowBouncy` + `StiffnessLow` (unchanged)

## Files Changed
- `app/src/main/java/com/example/escanqradmin/presentation/ui/home/components/ActiveUserCard.kt`

## Future
- Expanded/edit state redesign is out of scope for this spec.
