# ActiveUserCard Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the collapsed state of ActiveUserCard with professional UI: avatar initials, M3 status badge chips, proper theme tokens, visual hierarchy.

**Architecture:** Single-file change to `ActiveUserCard.kt`. The card uses `AppCard` (unchanged), with a redesigned `Column` content. Three sections: avatar+header row, divider, plate+delete row.

**Tech Stack:** Jetpack Compose, Material3, kotlinx.serialization (no changes), Hilt (no changes)

---

### Task 1: Redesign ActiveUserCard collapsed state

**Files:**
- Modify: `app/src/main/java/com/example/escanqradmin/presentation/ui/home/components/ActiveUserCard.kt`

- [ ] **Step 1: Update imports**

Current imports need `Brush` and `HorizontalDivider` added. Replace the entire import block:

```kotlin
package com.example.escanqradmin.presentation.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.escanqradmin.presentation.common.sharedcomponents.AppCard
import com.example.escanqradmin.presentation.common.sharedcomponents.AppCardDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import com.example.escanqradmin.presentation.ui.home.ActiveUser
```

- [ ] **Step 2: Add helper function `statusBadgeInfo`**

Add before the `ActiveUserCard` composable:

```kotlin
private data class BadgeInfo(val icon: ImageVector, val color: Color)

private fun statusBadgeInfo(status: String): BadgeInfo {
    return when (status) {
        "VALIDADO" -> BadgeInfo(Icons.Default.Done, MaterialTheme.colorScheme.secondary)
        "RECHAZADO" -> BadgeInfo(Icons.Default.Close, MaterialTheme.colorScheme.error)
        else -> BadgeInfo(Icons.Default.Schedule, MaterialTheme.colorScheme.outline)
    }
}
```

- [ ] **Step 3: Replace ActiveUserCard collapsed content**

Modify the `AppCard` content block (lines 53-229). Change `Column(modifier = Modifier.padding(20.dp))` to `Column(modifier = Modifier.padding(16.dp))`. Update:

**a) Avatar** (lines 60-75): Replace Security icon Box with initials Box (44dp, 14dp rounded, gradient bg if VALIDADO else surfaceVariant):

```kotlin
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                brush = if (user.status == "VALIDADO") {
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                } else null,
                                color = if (user.status != "VALIDADO") MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                shape = RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.name.take(2).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (user.status == "VALIDADO") MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
```

**b) Document text** (line 98): Change `Color.Gray` to `MaterialTheme.colorScheme.onSurfaceVariant`.

**c) Status badge** (lines 105-111): Replace the plain status Text with the M3 chip-style badge:

```kotlin
                    val badge = statusBadgeInfo(user.status)
                    Box(
                        modifier = Modifier
                            .background(
                                color = badge.color.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = badge.icon,
                                contentDescription = null,
                                tint = badge.color,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = user.status,
                                color = badge.color,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            )
                        }
                    }
```

**d) Remove `SecondaryOrange` import** — replace with `MaterialTheme.colorScheme.secondary` where needed (the badge logic handles this via `statusBadgeInfo`).

**e) Collapsed plate section** (lines 122-156): Add `HorizontalDivider` above plate, fix color tokens:

```kotlin
            AnimatedVisibility(visible = !isExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "PLACA",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = user.plate,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Borrar registro",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
```

**f) Expanded edit form** (lines 158-228): Replace `contentColor = Color.Red` with `contentColor = MaterialTheme.colorScheme.error` (outlined "Borrar" button).

Remove unused imports: `BorderStroke`, `com.example.escanqradmin.presentation.theme.color.*`, `Icons.Default.Security`.

- [ ] **Step 4: Build and verify**

```bash
cd /home/analista/AndroidStudioProjects/EscanQRAdmin && ./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/escanqradmin/presentation/ui/home/components/ActiveUserCard.kt
git commit -m "feat: redesign ActiveUserCard collapsed state with avatar initials and badge chips"
```
