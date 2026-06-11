# Rediseño Cartas y Sección Usuarios Activos

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Rediseñar las cartas de usuario y toda la sección de usuarios activos para visualizar `estado` real del backend y `puertas_autorizadas`, con diseño consistente con Material3/AppCard.

**Architecture:** Tres cambios en cadena: extender `QrContent` con `estado` (domain), mapearlo desde `ConductorDto` en el repository, y consumirlo en ViewModel y UI. Las cartas pasan de mostrar datos mínimos a una card informativa con nombre, cédula, placa, estado real del backend, y puertas autorizadas resueltas por nombre.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, AppCard, kotlinx.serialization, OkHttp

---

### Task 1: Agregar `estado` al modelo de dominio `QrContent`

**Files:**
- Modify: `app/src/main/java/com/example/escanqradmin/domain/model/QrContent.kt`

- [ ] **Step 1: Agregar campo `estado` a `QrContent`**

```kotlin
data class QrContent(
    val androidId: String,
    val userName: String,
    val cedula: String,
    val plate: String,
    val estado: String = "",
    val authorizedGates: List<String> = emptyList()
)
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`

---

### Task 2: Mapear `estado` y `puertas_autorizadas` desde `ConductorDto` en ambos flujos

**Files:**
- Modify: `app/src/main/java/com/example/escanqradmin/data/repository/SyncRepositoryImpl.kt`

**En `refreshConductores()` (línea 112):**
- [ ] **Step 1: Agregar `estado` al mapeo**

```kotlin
val qrContents = conductoresResponse.data.map { dto ->
    QrContent(
        androidId = dto.id?.toString() ?: "",
        userName = dto.nombre ?: "",
        cedula = dto.cedula ?: "",
        plate = dto.placas ?: "",
        estado = dto.estado ?: "",
        authorizedGates = dto.puertasAutorizadas?.map { it.macAddress } ?: emptyList()
    )
}
```

**En `getGateUsers()` (línea 300):**
- [ ] **Step 2: Agregar `estado` y `puertas_autorizadas` al mapeo**

```kotlin
val qrContents = conductoresResponse.data.map { dto ->
    QrContent(
        androidId = dto.id?.toString() ?: "",
        userName = dto.nombre ?: "",
        cedula = dto.cedula ?: "",
        plate = dto.placas ?: "",
        estado = dto.estado ?: "",
        authorizedGates = dto.puertasAutorizadas?.map { it.macAddress } ?: emptyList()
    )
}
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`

---

### Task 3: Usar `estado` real del backend en HomeViewModel + pasar puertas resueltas

**Files:**
- Modify: `app/src/main/java/com/example/escanqradmin/presentation/ui/home/HomeViewModel.kt`

**Cambios:**
1. `observeHistory()` — usar `qr.estado` en vez de hardcode "VALIDADO"
2. ActiveUser puede necesitar nombres de puertas. Se resuelven con `_uiState.value.gates` al crear los ActiveUser

- [ ] **Step 1: Actualizar `observeHistory()` para usar `estado` real y resolver nombres de puertas**

```kotlin
private fun observeHistory() {
    viewModelScope.launch {
        repository.getHistory().collect { history ->
            val gates = _uiState.value.gates
            val activeUsers = history.map { qr ->
                val resolvedGates = qr.authorizedGates.mapNotNull { mac ->
                    gates.firstOrNull { it.macAddress == mac }?.name ?: mac
                }
                ActiveUser(
                    id       = qr.androidId,
                    name     = qr.userName,
                    document = qr.cedula,
                    status   = qr.estado.ifEmpty { "VALIDADO" },
                    plate    = qr.plate,
                    authorizedGates = qr.authorizedGates,
                    authorizedGateNames = resolvedGates
                )
            }
            _uiState.update {
                it.copy(
                    activeUsers = activeUsers,
                    totalUsers  = activeUsers.size,
                    totalScans  = activeUsers.size,
                    isServerOnline = true
                )
            }
        }
    }
}
```

**Nota:** `observeHistory()` se ejecuta en un `launch` separado, pero `_uiState.value.gates` puede estar vacío el primer collect (antes de que `loadGates` termine). Los nombres se resolverán en el siguiente collect cuando cambie el history (no hay re-emisión automática de history tras gates). Para resolver esto, también debemos asegurar que los nombres se actualicen cuando cambien los gates.

- [ ] **Step 2: Agregar re-emisión de activeUsers cuando se actualicen los gates, para resolver nombres correctamente**

Agregar un observer de `_uiState` para los gates que re-resuelva los nombres cuando cambie la lista:

```kotlin
init {
    _localGates.value = loadLocalGates()
    observeHistory()
    observeBluetoothConnection()
    refreshData()
    observeGatesForUserResolution()
}

private fun observeGatesForUserResolution() {
    viewModelScope.launch {
        _uiState.map { it.gates }.distinctUntilChanged().collect { gates ->
            val currentUsers = _uiState.value.activeUsers
            if (currentUsers.isEmpty()) return@collect
            val updated = currentUsers.map { user ->
                val resolvedGates = user.authorizedGates.mapNotNull { mac ->
                    gates.firstOrNull { it.macAddress == mac }?.name ?: mac
                }
                user.copy(authorizedGateNames = resolvedGates)
            }
            _uiState.update { it.copy(activeUsers = updated) }
        }
    }
}
```

- [ ] **Step 3: Actualizar `selectGate()` para filtrar usando `authorizedGates` (ya funciona correctamente, solo verificar)**

- [ ] **Step 4: Build to verify**

Run: `./gradlew assembleDebug`

---

### Task 4: Rediseñar `ActiveUserCard`

**Files:**
- Modify: `app/src/main/java/com/example/escanqradmin/presentation/ui/home/components/ActiveUserCard.kt`

**Nuevo diseño:**
- Avatar circular con iniciales (fondo según estado)
- Nombre como título principal
- Cédula y placa como metadatos
- Badge de estado real con código de colores: "activo" → secondary (teal), "inactivo"/"rechazado" → error, otros → outline
- Chips de puertas autorizadas (si tiene)
- Sección expandible para editar (como ahora, pero más limpia)

- [ ] **Step 1: Reemplazar `statusBadgeInfo` con soporte para valores reales del backend**

```kotlin
private fun statusBadgeInfo(status: String): BadgeInfo {
    return when (status.lowercase()) {
        "activo", "validado" -> BadgeInfo(Icons.Default.Done, MaterialTheme.colorScheme.secondary)
        "inactivo", "rechazado" -> BadgeInfo(Icons.Default.Close, MaterialTheme.colorScheme.error)
        else -> BadgeInfo(Icons.Default.Schedule, MaterialTheme.colorScheme.outline)
    }
}
```

- [ ] **Step 2: Agregar sección de puertas autorizadas en la card (vista colapsada)**

Después del divider y antes de la fila de placa:

```kotlin
if (user.authorizedGateNames.isNotEmpty()) {
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = "PUERTAS AUTORIZADAS",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(6.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        user.authorizedGateNames.forEach { gateName ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                modifier = Modifier.padding(0.dp)
            ) {
                Text(
                    text = gateName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
```

**Nota:** `FlowRow` requiere `import androidx.compose.foundation.layout.FlowRow` (disponible desde Compose Foundation 1.4+). Verificar disponibilidad.

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`

---

### Task 5: Rediseñar sección de usuarios activos en HomeScreen

**Files:**
- Modify: `app/src/main/java/com/example/escanqradmin/presentation/ui/home/HomeScreen.kt`

**Cambios:**
1. El header "Usuarios Activos" ahora muestra el badge con conteo real (total vs filtrados por puerta)
2. Reemplazar el `SearchBar` existente por uno que también filtre por placa y puerta
3. La sección de usuarios filtrados por gate ahora se integra mejor visualmente

- [ ] **Step 1: Actualizar header de usuarios activos con mejor diseño**

```kotlin
item {
    Column {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showActiveUsers = !showActiveUsers },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Usuarios",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${filteredUsers.size} en línea",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Icon(
                imageVector = if (showActiveUsers) Icons.Default.KeyboardArrowUp
                              else Icons.Default.KeyboardArrowDown,
                contentDescription = if (showActiveUsers) "Contraer sección" else "Expandir sección",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`

---

### Task 6: Agregar campo `authorizedGateNames` a `ActiveUser`

**Files:**
- Modify: `app/src/main/java/com/example/escanqradmin/presentation/ui/home/HomeViewModel.kt`

- [ ] **Step 1: Agregar `authorizedGateNames` a `ActiveUser`**

```kotlin
data class ActiveUser(
    val id: String,
    val name: String,
    val document: String,
    val status: String,
    val plate: String,
    val authorizedGates: List<String> = emptyList(),
    val authorizedGateNames: List<String> = emptyList()
)
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`

---

### Task 7: Verificación final y commit

- [ ] **Step 1: Build completo**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verificar que no hay imports faltantes (FlowRow, etc.)**

Run: `./gradlew assembleDebug` ya lo verifica

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: rediseñar cartas de usuario y sección activos

- Agregar campo estado a QrContent y mapeo desde ConductorDto
- Usar estado real del backend en ActiveUserCard (activo/inactivo)
- Mostrar puertas autorizadas por nombre en cada card
- Rediseñar header de usuarios activos en HomeScreen
- Resolver nombres de puertas cuando cambia la lista de gates"
```
