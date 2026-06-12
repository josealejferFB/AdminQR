package com.example.escanqradmin.presentation.ui.home.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.escanqradmin.domain.model.GateInfo
import com.example.escanqradmin.presentation.common.sharedcomponents.AppCard
import com.example.escanqradmin.presentation.common.sharedcomponents.AppCardDefaults
import com.example.escanqradmin.presentation.ui.config.ServerHistory
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OdooConfigDialog(
    gate: GateInfo,
    onRegisterInOdoo: suspend (String, String) -> Result<Int?>,
    onDismiss: () -> Unit,
    onSuccess: (odooId: Int?) -> Unit
) {
    var gateName by remember { mutableStateOf(gate.name) }
    var protocol by remember { mutableStateOf("http") }
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8059") }
    var ipError by remember { mutableStateOf<String?>(null) }
    var phase by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val prefs = context.getSharedPreferences("api_config_prefs", Context.MODE_PRIVATE)
    val historyJson = prefs.getString("server_history_v2", "[]") ?: "[]"
    val json = remember { Json { ignoreUnknownKeys = true; coerceInputValues = true } }
    val serverHistory = remember(historyJson) {
        try { json.decodeFromString<List<ServerHistory>>(historyJson) }
        catch (_: Exception) { emptyList() }
    }.filter { it.host.isNotBlank() }

    val isRegistered = gate.isOdooRegistered

    Dialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                colors = AppCardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ── Header ──────────────────────────────
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Router,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Registrar Portón",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                gate.name,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // ── Progress / Result ────────────────────
                    if (result != null) {
                        Icon(
                            imageVector = if (result!!.contains("correctamente") || result!!.contains("éxito"))
                                Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally),
                            tint = if (result!!.contains("correctamente") || result!!.contains("éxito"))
                                MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            result!!,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Cerrar") }
                        return@Column
                    }

                    if (phase != null) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (isWorking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.height(16.dp))
                            }
                            Text(
                                phase!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Medium
                            )
                            if (isWorking) {
                                Spacer(Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        return@Column
                    }

                    // ── Name Field ──────────────────────────
                    OutlinedTextField(
                        value = gateName,
                        onValueChange = { gateName = it },
                        label = { Text("Nombre del portón") },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color.Gray,
                                modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary
                        )
                    )

                    // ── Connection Summary ──────────────────
                    if (ip.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                                .padding(14.dp)
                        ) {
                            Column {
                                Text(
                                    "Resumen de conexión",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "$protocol://$ip${if (port.isNotEmpty()) ":$port" else ""}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // ── Protocol ────────────────────────────
                    Column {
                        Text(
                            "Protocolo",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(3.dp)
                        ) {
                            ProtocolTab(
                                label = "HTTP",
                                isSelected = protocol == "http",
                                modifier = Modifier.weight(1f),
                                onClick = { protocol = "http" }
                            )
                            ProtocolTab(
                                label = "HTTPS",
                                isSelected = protocol == "https",
                                modifier = Modifier.weight(1f),
                                onClick = { protocol = "https" }
                            )
                        }
                    }

                    // ── IP Field ────────────────────────────
                    Column {
                        OutlinedTextField(
                            value = ip,
                            onValueChange = { ip = it; ipError = null },
                            label = { Text("IP del servidor Odoo") },
                            placeholder = { Text("ej. 192.168.1.100") },
                            isError = ipError != null,
                            supportingText = if (ipError != null) {{ Text(ipError!!) }} else null,
                            leadingIcon = {
                                Icon(Icons.Default.Router, contentDescription = null, tint = Color.Gray,
                                    modifier = Modifier.size(18.dp))
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                errorBorderColor = MaterialTheme.colorScheme.error
                            )
                        )

                        // ── Recent Servers ──────────────────
                        if (serverHistory.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showHistory = !showHistory }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Servidores recientes",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.Gray
                                    )
                                }
                                Icon(
                                    imageVector = if (showHistory) Icons.Default.KeyboardArrowUp
                                                  else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            AnimatedVisibility(visible = showHistory) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    serverHistory.forEach { h ->
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    protocol = h.protocol
                                                    ip = h.host
                                                    port = h.port
                                                },
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (h.protocol == "https") Color(0xFF4CAF50)
                                                            else Color(0xFFFF9800)
                                                        )
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        h.host,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        "${h.protocol.uppercase()} : ${h.port} · ${formatTimestamp(h.timestamp)}",
                                                        fontSize = 10.sp,
                                                        color = Color.Gray
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Port Field ──────────────────────────
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Puerto") },
                        placeholder = { Text("8059") },
                        leadingIcon = {
                            Icon(Icons.Default.Cable, contentDescription = null, tint = Color.Gray,
                                modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary
                        )
                    )

                    // ── Info ────────────────────────────────
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isRegistered) "Portón ya registrado en Odoo. El ESP32 reportará su IP automáticamente."
                                else "El portón se registrará en Odoo. El ESP32 reportará su IP automáticamente al conectarse.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // ── Buttons ─────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isWorking
                        ) {
                            Text("Cancelar")
                        }
                        Button(
                            onClick = {
                                ipError = null
                                val valid = isValidIp(ip)
                                if (!valid) { ipError = "IP inválida"; return@Button }
                                isWorking = true
                                phase = if (!isRegistered) "Registrando portón en Odoo..."
                                        else "Portón ya registrado"
                                scope.launch {
                                    val registerResult = if (!isRegistered) {
                                        onRegisterInOdoo(gateName, gate.macAddress)
                                    } else {
                                        Result.success(gate.id)
                                    }
                                    registerResult.fold(
                                        onSuccess = { odooId ->
                                            if (odooId == null && !isRegistered) {
                                                phase = null; result = "Registrado, pero no se recibió el ID."
                                                isWorking = false; return@launch
                                            }
                                            val effectiveId = odooId ?: gate.id
                                            phase = null
                                            result = "Portón registrado correctamente"
                                            isWorking = false
                                            onSuccess(effectiveId)
                                        },
                                        onFailure = { e ->
                                            phase = null
                                            result = "Error: ${e.message}"
                                            isWorking = false
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            enabled = gateName.isNotBlank() && ip.isNotBlank() && port.isNotBlank() && !isWorking
                        ) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "REGISTRAR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtocolTab(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
            fontSize = 13.sp
        )
    }
}

private fun isValidIp(value: String): Boolean {
    val octets = value.split(".")
    if (octets.size != 4) return false
    return octets.all { o -> o.toIntOrNull()?.let { it in 0..255 } ?: false }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val now = Instant.now()
    val ts = Instant.ofEpochMilli(timestamp)
    val diff = Duration.between(ts, now)
    return when {
        diff.toMinutes() < 1 -> "Ahora"
        diff.toHours() < 1 -> "Hace ${diff.toMinutes()} min"
        diff.toDays() < 1 -> "Hoy, ${ts.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))}"
        else -> ts.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM"))
    }
}
