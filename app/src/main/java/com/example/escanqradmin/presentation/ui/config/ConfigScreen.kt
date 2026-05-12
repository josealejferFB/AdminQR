package com.example.escanqradmin.presentation.ui.config

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import com.example.escanqradmin.presentation.theme.color.PrimaryBlue
import com.example.escanqradmin.presentation.theme.color.SecondaryOrange
import com.example.escanqradmin.presentation.theme.color.SurfaceGrey
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    navController: NavHostController,
    viewModel: ConfigViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showHistory by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .statusBarsPadding()
                    .displayCutoutPadding(),
                title = {
                    Text(
                        "Configuración de Red",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = Color.DarkGray
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = PrimaryBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF8F9FA) // Subtle off-white for better contrast
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // URL Preview & Protocol Selection
            item {
                ConfigurationCard(
                    protocol = uiState.protocol,
                    host = uiState.host,
                    port = uiState.port,
                    endpointSync = uiState.endpointSync,
                    endpointConductores = uiState.endpointConductores,
                    isLoading = uiState.isLoading,
                    onProtocolChange = viewModel::onProtocolChange,
                    onHostChange = viewModel::onHostChange,
                    onPortChange = viewModel::onPortChange,
                    onEndpointSyncChange = viewModel::onEndpointSyncChange,
                    onEndpointConductoresChange = viewModel::onEndpointConductoresChange,
                    onSave = viewModel::saveConfig
                )
            }

            // History Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Storage,
                            contentDescription = null,
                            tint = SecondaryOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Catálogo de Servidores",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray
                        )
                    }
                    TextButton(onClick = { showHistory = !showHistory }) {
                        Text(
                            if (showHistory) "Ocultar" else "Ver todo",
                            color = PrimaryBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // History List
            if (showHistory) {
                if (uiState.serverHistory.isEmpty()) {
                    item {
                        EmptyHistoryPlaceholder()
                    }
                } else {
                    items(uiState.serverHistory, key = { it.timestamp }) { history ->
                        HistoryCatalogItem(
                            history = history,
                            onSelect = { viewModel.selectFromHistory(it) },
                            onDelete = { viewModel.removeFromHistory(it) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
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
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PrimaryBlue.copy(alpha = 0.05f))
                    .border(1.dp, PrimaryBlue.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        "Resumen de conexión",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val previewUrl = if (host.isEmpty()) "Ingrese un servidor..." else "$protocol://$host${if (port.isEmpty()) "" else ":$port"}"
                    Text(
                        text = previewUrl,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (host.isEmpty()) Color.Gray else Color.DarkGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (host.isNotEmpty()) {
                        Text(
                            text = "Endpoints: $endpointSync, $endpointConductores",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
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
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceGrey.copy(alpha = 0.5f))
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
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                leadingIcon = {
                    Icon(
                        if (host.matches(Regex("^\\d.*"))) Icons.Default.Router else Icons.Default.Language,
                        contentDescription = null,
                        tint = if (host.isNotEmpty()) PrimaryBlue else Color.Gray
                    )
                }
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Port Input
                OutlinedTextField(
                    value = port,
                    onValueChange = onPortChange,
                    label = { Text("Puerto") },
                    placeholder = { Text("Opcional") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    leadingIcon = {
                        Icon(Icons.Default.Cable, null, tint = if (port.isNotEmpty()) SecondaryOrange else Color.Gray)
                    }
                )
            }

            Divider(color = Color.LightGray.copy(alpha = 0.2f), thickness = 1.dp)

            Text(
                "Rutas de API (Endpoints)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )

            // Endpoint Sync
            OutlinedTextField(
                value = endpointSync,
                onValueChange = onEndpointSyncChange,
                label = { Text("Ruta Control Acceso") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Upload, null, tint = Color.Gray)
                }
            )

            // Endpoint Conductores
            OutlinedTextField(
                value = endpointConductores,
                onValueChange = onEndpointConductoresChange,
                label = { Text("Ruta Conductores") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Download, null, tint = Color.Gray)
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
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
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color.White else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (isSelected) PrimaryBlue else Color.Gray,
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelect(history) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                    .background(if (history.protocol == "https") Color(0xFFE8F5E9) else Color(0xFFFFF3E0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (history.protocol == "https") Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = if (history.protocol == "https") Color(0xFF4CAF50) else Color(0xFFFF9800),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = history.host,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${history.protocol.uppercase()}${if (history.port.isEmpty()) "" else " : ${history.port}"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.LightGray))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatTimestamp(history.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
            }

            IconButton(onClick = { onDelete(history) }) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Eliminar",
                    tint = Color.LightGray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyHistoryPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.History,
            contentDescription = null,
            tint = Color.LightGray.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Sin historial reciente",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Hace un momento"
        diff < 3600_000 -> "Hace ${diff / 60_000} min"
        diff < 86400_000 -> "Hoy, ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))}"
        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
    }
}