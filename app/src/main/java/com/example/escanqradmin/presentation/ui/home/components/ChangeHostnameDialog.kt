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
fun ChangeHostnameDialog(
    gate: GateInfo,
    connectionStateProvider: () -> BluetoothConnectionState,
    onConnect: (String) -> Unit,
    onSendMessageAndWaitForReply: suspend (String, Long) -> String?,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var hostname by remember { mutableStateOf(gate.btName.ifBlank { "ESP32-Gate" }) }
    var isSending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = { Text("Hostname - ${gate.name}") },
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
                    Text(
                        "Nombre que aparecerá en el router para identificar este ESP32.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = hostname,
                        onValueChange = { hostname = it },
                        label = { Text("Hostname") },
                        placeholder = { Text("ESP32-Gate") },
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
                        isSending = true
                        statusMessage = "Conectando al ESP32..."
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

                            statusMessage = "Enviando hostname..."
                            val payload = "{\"action\":\"set_hostname\",\"hostname\":\"$hostname\"}"
                            val reply = onSendMessageAndWaitForReply(payload, 10000)

                            if (reply != null) {
                                statusMessage = "Hostname enviado: $reply"
                                result = "Hostname configurado. El ESP32 se reiniciará."
                                onSuccess()
                            } else {
                                statusMessage = "Sin respuesta del ESP32"
                                result = "Error: Sin respuesta"
                            }
                            isSending = false
                        }
                    },
                    enabled = hostname.isNotBlank() && !isSending
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
