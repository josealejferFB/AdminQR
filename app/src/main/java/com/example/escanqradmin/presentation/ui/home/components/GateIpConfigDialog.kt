package com.example.escanqradmin.presentation.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.escanqradmin.domain.model.GateInfo
import com.example.escanqradmin.domain.repository.BluetoothConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GateIpConfigDialog(
    gate: GateInfo,
    connectionStateProvider: () -> BluetoothConnectionState,
    onConnect: (String) -> Unit,
    onSendMessageAndWaitForReply: suspend (String, Long) -> String?,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var ip by remember { mutableStateOf("") }
    var gateway by remember { mutableStateOf("") }
    var netmask by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var currentPhase by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = { Text("Configurar IP - ${gate.name}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (result != null) {
                    Text(result!!, color = MaterialTheme.colorScheme.primary)
                } else if (statusMessage != null) {
                    Text(statusMessage!!, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isSending) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it },
                        label = { Text("IP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = gateway,
                        onValueChange = { gateway = it },
                        label = { Text("Gateway") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = netmask,
                        onValueChange = { netmask = it },
                        label = { Text("Máscara") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isSending) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(currentPhase, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            if (result == null) {
                Button(
                    onClick = {
                        isSending = true
                        statusMessage = "Conectando al ESP32..."
                        currentPhase = "Conectando..."
                        scope.launch {
                            onConnect(gate.macAddress)

                            val maxWait = System.currentTimeMillis() + 10000
                            while (System.currentTimeMillis() < maxWait) {
                                val state = connectionStateProvider()
                                if (state is BluetoothConnectionState.Connected) {
                                    break
                                }
                                if (state is BluetoothConnectionState.Error) {
                                    statusMessage = "Error de conexión: ${state.message}"
                                    isSending = false
                                    return@launch
                                }
                                delay(500)
                            }

                            if (connectionStateProvider() !is BluetoothConnectionState.Connected) {
                                statusMessage = "No se pudo conectar al ESP32"
                                isSending = false
                                result = "Error: No se pudo conectar"
                                return@launch
                            }

                            statusMessage = "Enviando configuración IP..."
                            currentPhase = "Enviando..."

                            val payload = "{\"jsonrpc\":\"2.0\",\"action\":\"config_ip\",\"ip\":\"$ip\",\"gateway\":\"$gateway\",\"netmask\":\"$netmask\"}"
                            val reply = onSendMessageAndWaitForReply(payload, 10000)

                            if (reply != null) {
                                statusMessage = "Configuración enviada: $reply"
                                result = "IP configurada correctamente"
                                onSuccess()
                            } else {
                                statusMessage = "Sin respuesta del ESP32"
                                result = "Error: Sin respuesta"
                            }
                            isSending = false
                        }
                    },
                    enabled = !isSending
                ) { Text("Enviar") }
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
