package com.example.escanqradmin.presentation.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.escanqradmin.domain.model.GateInfo
import com.example.escanqradmin.domain.repository.BluetoothConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OdooConfigDialog(
    gate: GateInfo,
    connectionStateProvider: () -> BluetoothConnectionState,
    onConnect: (String) -> Unit,
    onSendMessageAndWaitForReply: suspend (String, Long) -> String?,
    onRegisterInOdoo: suspend (String, String) -> Result<Int?>,
    onDismiss: () -> Unit,
    onSuccess: (odooId: Int) -> Unit
) {
    var gateName by remember { mutableStateOf(gate.name) }
    var protocol by remember { mutableStateOf("http") }
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8059") }
    var ipError by remember { mutableStateOf<String?>(null) }
    var phase by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = { Text("Configurar con Odoo") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (result != null) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(result!!, style = MaterialTheme.typography.bodyMedium)
                } else if (phase != null) {
                    Text(phase!!, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(8.dp))
                    if (isWorking) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(
                        value = gateName,
                        onValueChange = { gateName = it },
                        label = { Text("Nombre del portón") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Servidor Odoo", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))

                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = protocol,
                            onValueChange = { protocol = it },
                            label = { Text("Protocolo") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("http", "https").forEach { p ->
                                DropdownMenuItem(text = { Text(p) }, onClick = { protocol = p; expanded = false })
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it; ipError = null },
                        label = { Text("IP del servidor") },
                        isError = ipError != null,
                        supportingText = ipError?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Puerto") },
                        placeholder = { Text("8059") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (result == null) {
                Button(
                    onClick = {
                        ipError = null
                        val valid = isValidIp(ip)
                        if (!valid) {
                            ipError = "IP inválida"
                        }
                        if (valid) {
                            isWorking = true
                            phase = "Registrando en servidor..."
                            scope.launch {
                                val registerResult = onRegisterInOdoo(gateName, gate.macAddress)
                                registerResult.fold(
                                    onSuccess = { odooId ->
                                        if (odooId == null) {
                                            phase = null
                                            result = "Registrado en Odoo, pero no se recibió el ID del portón."
                                            isWorking = false
                                            return@launch
                                        }
                                        phase = "Conectando al ESP32..."
                                        onConnect(gate.macAddress)

                                        val maxWait = System.currentTimeMillis() + 10000
                                        while (System.currentTimeMillis() < maxWait) {
                                            val state = connectionStateProvider()
                                            if (state is BluetoothConnectionState.Connected) break
                                            if (state is BluetoothConnectionState.Error) {
                                                phase = "Error de conexión BT"
                                                isWorking = false
                                                result = "Registrado en Odoo, pero no se pudo enviar la URL al ESP32. Reintenta desde el chip."
                                                return@launch
                                            }
                                            delay(500)
                                        }

                                        if (connectionStateProvider() !is BluetoothConnectionState.Connected) {
                                            phase = "No se pudo conectar BT"
                                            isWorking = false
                                            result = "Registrado en Odoo, pero sin conexión BT al ESP32."
                                            return@launch
                                        }

                                        phase = "Enviando URL de Odoo al ESP32..."
                                        val payload = "{\"protocolo\":\"$protocol\",\"ip_odoo\":\"$ip\",\"port\":$port}"
                                        val reply = onSendMessageAndWaitForReply("config\n$payload\n", 10000)

                                        if (reply != null && reply.contains("CONFIG_OK")) {
                                            phase = "Reiniciando ESP32..."
                                            delay(1500)
                                            result = "Portón configurado con Odoo correctamente"
                                            onSuccess(odooId)
                                        } else {
                                            isWorking = false
                                            result = "Registrado en Odoo, pero el ESP32 no confirmó la URL. Reintenta desde el chip."
                                        }
                                        isWorking = false
                                    },
                                    onFailure = { e ->
                                        phase = null
                                        result = "Error al registrar en Odoo: ${e.message}"
                                        isWorking = false
                                    }
                                )
                            }
                        }
                    },
                    enabled = gateName.isNotBlank() && ip.isNotBlank() && port.isNotBlank() && !isWorking
                ) { Text("CONFIGURAR") }
            }
        },
        dismissButton = {
            if (result == null) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        }
    )
}

private fun isValidIp(value: String): Boolean {
    val octets = value.split(".")
    if (octets.size != 4) return false
    return octets.all { o -> o.toIntOrNull()?.let { it in 0..255 } ?: false }
}
