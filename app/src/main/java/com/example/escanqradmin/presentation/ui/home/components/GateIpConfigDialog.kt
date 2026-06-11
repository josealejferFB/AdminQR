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
    var ipError by remember { mutableStateOf<String?>(null) }
    var gatewayError by remember { mutableStateOf<String?>(null) }
    var netmaskError by remember { mutableStateOf<String?>(null) }
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
                        onValueChange = { ip = it; ipError = null },
                        label = { Text("IP") },
                        isError = ipError != null,
                        supportingText = ipError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = gateway,
                        onValueChange = { gateway = it; gatewayError = null },
                        label = { Text("Gateway") },
                        isError = gatewayError != null,
                        supportingText = gatewayError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = netmask,
                        onValueChange = { netmask = it; netmaskError = null },
                        label = { Text("Máscara") },
                        isError = netmaskError != null,
                        supportingText = netmaskError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
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
                        ipError = null; gatewayError = null; netmaskError = null

                        val valid = isValidIp(ip) && isValidIp(gateway) && isValidIp(netmask)
                        if (!isValidIp(ip)) {
                            ipError = "Formato inválido (ej: 192.168.1.100)"
                        }
                        if (!isValidIp(gateway)) {
                            gatewayError = "Formato inválido (ej: 192.168.1.1)"
                        }
                        if (!isValidIp(netmask)) {
                            netmaskError = "Formato inválido (ej: 255.255.255.0)"
                        }
                        if (valid) {
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

                            val payload = "{\"action\":\"config_ip\",\"ip\":\"$ip\",\"gateway\":\"$gateway\",\"netmask\":\"$netmask\"}"
                            val reply = onSendMessageAndWaitForReply(payload, 10000)

                            if (reply != null) {
                                statusMessage = "Reiniciando ESP32..."
                                currentPhase = "Reiniciando..."
                                delay(1500)
                                result = "IP configurada correctamente"
                                onSuccess()
                            } else {
                                statusMessage = "Sin respuesta del ESP32"
                                result = "Error: Sin respuesta"
                            }
                            isSending = false
                        }
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

private fun isValidIp(value: String): Boolean {
    val octets = value.split(".")
    if (octets.size != 4) return false
    return octets.all { octet ->
        val num = octet.toIntOrNull()
        num != null && num in 0..255
    }
}
