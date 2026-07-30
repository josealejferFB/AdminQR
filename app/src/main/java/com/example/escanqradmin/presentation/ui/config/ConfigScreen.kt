package com.example.escanqradmin.presentation.ui.config

import androidx.compose.animation.*
import com.example.escanqradmin.presentation.theme.shape.AppShapes
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.escanqradmin.domain.model.GateInfo
import com.example.escanqradmin.presentation.common.sharedcomponents.AppCard
import com.example.escanqradmin.presentation.common.sharedcomponents.AppCardDefaults
import com.example.escanqradmin.presentation.navigation.LocalSnackbarHostState
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    navController: NavHostController,
    viewModel: ConfigViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = LocalSnackbarHostState.current
    var showHistory by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                title = {
                    Text(
                        "Configuración de Red",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // URL Preview & Protocol Selection
            item {
                ConfigurationCard(
                    protocol = uiState.protocol,
                    host = uiState.host,
                    port = uiState.port,
                    endpointSync = uiState.endpointSync,
                    endpointConductores = uiState.endpointConductores,
                    endpointRegisterGate = uiState.endpointRegisterGate,
                    endpointGatesList = uiState.endpointGatesList,
                    endpointGateUpdate = uiState.endpointGateUpdate,
                    endpointGateUsers = uiState.endpointGateUsers,
                    isLoading = uiState.isLoading,
                    onProtocolChange = viewModel::onProtocolChange,
                    onHostChange = viewModel::onHostChange,
                    onPortChange = viewModel::onPortChange,
                    onEndpointSyncChange = viewModel::onEndpointSyncChange,
                    onEndpointConductoresChange = viewModel::onEndpointConductoresChange,
                    onEndpointRegisterGateChange = viewModel::onEndpointRegisterGateChange,
                    onEndpointGatesListChange = viewModel::onEndpointGatesListChange,
                    onEndpointGateUpdateChange = viewModel::onEndpointGateUpdateChange,
                    onEndpointGateUsersChange = viewModel::onEndpointGateUsersChange,
                    onSave = viewModel::saveConfig
                )
            }

            // History Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showHistory = !showHistory }
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Catálogo de Servidores",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Icon(
                        imageVector = if (showHistory) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (showHistory) "Contraer sección" else "Expandir sección",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // History List
            item {
                AnimatedVisibility(
                    visible = showHistory,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        if (uiState.serverHistory.isEmpty()) {
                            EmptyHistoryPlaceholder()
                        } else {
                            uiState.serverHistory.forEach { history ->
                                HistoryCatalogItem(
                                    history = history,
                                    onSelect = { viewModel.selectFromHistory(it) },
                                    onDelete = { viewModel.removeFromHistory(it) }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }

            // Gate listing
            item {
                GateListCard(
                    gates = uiState.gates,
                    isLoading = uiState.isLoadingGates,
                    onFetch = viewModel::fetchGates
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun GateListCard(
    gates: List<GateInfo>,
    isLoading: Boolean,
    onFetch: () -> Unit
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Sensors,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Portones registrados",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                FilledTonalButton(
                    onClick = onFetch,
                    enabled = !isLoading,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("Listar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (gates.isEmpty() && !isLoading) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Presiona \"Listar\" para obtener los portones desde Odoo",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                )
            }
            if (gates.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(Modifier.height(8.dp))
                gates.forEach { gate ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (gate.isOnline) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.outline,
                                    CircleShape
                                )
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(gate.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                "MAC: ${gate.macAddress}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (gate.hostname.isNotEmpty()) {
                            Text(
                                gate.hostname,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    if (gate != gates.last()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 18.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationCard(
    protocol: String,
    host: String,
    port: String,
    isLoading: Boolean,
    onProtocolChange: (String) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    endpointSync: String,
    onEndpointSyncChange: (String) -> Unit,
    endpointConductores: String,
    onEndpointConductoresChange: (String) -> Unit,
    endpointRegisterGate: String,
    onEndpointRegisterGateChange: (String) -> Unit,
    endpointGatesList: String,
    onEndpointGatesListChange: (String) -> Unit,
    endpointGateUpdate: String,
    onEndpointGateUpdateChange: (String) -> Unit,
    endpointGateUsers: String,
    onEndpointGateUsersChange: (String) -> Unit,
    onSave: () -> Unit
) {
    var showAdvanced by remember { mutableStateOf(false) }
    AppCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.Input)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), AppShapes.Input)
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        "Resumen de conexión",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val previewUrl = if (host.isEmpty()) "Ingrese un servidor..." else "$protocol://$host${if (port.isEmpty()) "" else ":$port"}"
                    Text(
                        text = previewUrl,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (host.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (host.isNotEmpty()) {
                        Text(
                            text = "Endpoints: $endpointSync, $endpointConductores, $endpointRegisterGate",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Protocol Selection
            Column {
                Text(
                    "Protocolo",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(AppShapes.Button)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp)
                ) {
                    ProtocolOption(
                        label = "HTTP",
                        isSelected = protocol == "http",
                        modifier = Modifier.weight(1f),
                        onClick = { onProtocolChange("http") }
                    )
                    ProtocolOption(
                        label = "HTTPS",
                        isSelected = protocol == "https",
                        modifier = Modifier.weight(1f),
                        onClick = { onProtocolChange("https") }
                    )
                }
            }

            // Host Input
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text("Servidor / IP") },
                placeholder = { Text("ej. api.servidor.com") },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.Input,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                leadingIcon = {
                    Icon(
                        if (host.matches(Regex("^\\d.*"))) Icons.Default.Router else Icons.Default.Language,
                        contentDescription = null,
                        tint = if (host.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.secondary
                )
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Port Input
                OutlinedTextField(
                    value = port,
                    onValueChange = onPortChange,
                    label = { Text("Puerto") },
                    placeholder = { Text("Opcional") },
                    modifier = Modifier.weight(1f),
                    shape = AppShapes.Input,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    leadingIcon = {
                        Icon(Icons.Default.Cable, null, tint = if (port.isNotEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.secondary
                    )
                )
            }

            
            TextButton(
                onClick = { showAdvanced = !showAdvanced },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (showAdvanced) "Ocultar configuración avanzada" else "Configuración avanzada")
            }
            AnimatedVisibility(visible = showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 1.dp)

            Text(
                "Rutas de API (Endpoints)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Endpoint Sync
            OutlinedTextField(
                value = endpointSync,
                onValueChange = onEndpointSyncChange,
                label = { Text("Ruta Control Acceso") },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.Input,
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Upload, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.secondary
                )
            )

            // Endpoint Conductores
            OutlinedTextField(
                value = endpointConductores,
                onValueChange = onEndpointConductoresChange,
                label = { Text("Ruta Conductores") },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.Input,
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.secondary
                )
            )

            // Endpoint Register Gate
            OutlinedTextField(
                value = endpointRegisterGate,
                onValueChange = onEndpointRegisterGateChange,
                label = { Text("Ruta Registrar Portón") },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.Input,
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.AddCircle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.secondary
                )
            )

            // Endpoint Gates List
            OutlinedTextField(
                value = endpointGatesList,
                onValueChange = onEndpointGatesListChange,
                label = { Text("Ruta Listar Portones") },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.Input,
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.List, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.secondary
                )
            )

            // Endpoint Gate Update
            OutlinedTextField(
                value = endpointGateUpdate,
                onValueChange = onEndpointGateUpdateChange,
                label = { Text("Ruta Actualizar Portón") },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.Input,
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.secondary
                )
            )

            // Endpoint Gate Users
            OutlinedTextField(
                value = endpointGateUsers,
                onValueChange = onEndpointGateUsersChange,
                label = { Text("Ruta Usuarios de Portón") },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.Input,
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.secondary
                )
            )

            
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = AppShapes.Input,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.CloudSync, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("GUARDAR Y ACTIVAR", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun ProtocolOption(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(AppShapes.Chip)
            .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
    }
}

@Composable
fun HistoryCatalogItem(
    history: ServerHistory,
    onSelect: (ServerHistory) -> Unit,
    onDelete: (ServerHistory) -> Unit
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onSelect(history) }
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Protocol Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (history.protocol == "https") {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (history.protocol == "https") Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = if (history.protocol == "https") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = history.host,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${history.protocol.uppercase()}${if (history.port.isEmpty()) "" else " : ${history.port}"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatTimestamp(history.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            IconButton(onClick = { onDelete(history) }) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyHistoryPlaceholder() {
    AppCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Sin historial reciente",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val now = Instant.now()
    val ts = Instant.ofEpochMilli(timestamp)
    val diff = Duration.between(ts, now)
    
    return when {
        diff.toMinutes() < 1 -> "Hace un momento"
        diff.toHours() < 1 -> "Hace ${diff.toMinutes()} min"
        diff.toDays() < 1 -> "Hoy, ${ts.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))}"
        else -> ts.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM"))
    }
}