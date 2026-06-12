package com.example.escanqradmin.presentation.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.escanqradmin.domain.model.GateInfo
import com.example.escanqradmin.presentation.common.sharedcomponents.AppCard
import com.example.escanqradmin.presentation.common.sharedcomponents.AppCardDefaults
import kotlinx.coroutines.launch

@Composable
fun OdooConfigDialog(
    gate: GateInfo,
    onRegisterInOdoo: suspend (String, String) -> Result<Int?>,
    onDismiss: () -> Unit,
    onSuccess: (odooId: Int?) -> Unit
) {
    var gateName by remember { mutableStateOf(gate.name) }
    var phase by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val isRegistered = gate.isOdooRegistered

    Dialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
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
                            enabled = gateName.isNotBlank() && !isWorking
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
