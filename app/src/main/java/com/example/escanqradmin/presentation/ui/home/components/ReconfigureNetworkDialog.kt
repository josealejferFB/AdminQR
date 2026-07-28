package com.example.escanqradmin.presentation.ui.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.escanqradmin.data.network.ApiConstants
import com.example.escanqradmin.domain.model.GateInfo
import com.example.escanqradmin.domain.model.SecurityConstants
import com.example.escanqradmin.domain.repository.BluetoothConnectionState
import com.example.escanqradmin.presentation.theme.shape.AppShapes
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class ReconfigureStep {
    Input,
    Submitting,
    Success,
    Error
}

@Composable
fun ReconfigureNetworkDialog(
    gate: GateInfo,
    connectionState: BluetoothConnectionState,
    onConnect: () -> Unit,
    onSendMessageAndWaitForReply: suspend (String, Long) -> String?,
    onDisconnect: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(ReconfigureStep.Input) }
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val isConnected = connectionState is BluetoothConnectionState.Connected && connectionState.deviceAddress.equals(gate.macAddress, ignoreCase = true)
    val isConnecting = connectionState is BluetoothConnectionState.Connecting && connectionState.deviceAddress.equals(gate.macAddress, ignoreCase = true)
    val connectionError = if (connectionState is BluetoothConnectionState.Error) connectionState.message else null

    Dialog(onDismissRequest = { 
        if (step != ReconfigureStep.Submitting) {
            onDisconnect()
            onDismiss() 
        }
    }) {
        Surface(
            shape = AppShapes.Pill,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Configurar Red",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = gate.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (step != ReconfigureStep.Submitting) {
                        IconButton(
                            onClick = {
                                onDisconnect()
                                onDismiss()
                            },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                AnimatedContent(
                    targetState = step,
                    transitionSpec = { fadeIn() togetherWith fadeOut() }
                ) { currentStep ->
                    when (currentStep) {
                        ReconfigureStep.Input -> {
                            Column {
                                Text(
                                    "Ingresa las nuevas credenciales WiFi para que el ESP32 se conecte a la red.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(16.dp))
                                OutlinedTextField(
                                    value = ssid,
                                    onValueChange = { ssid = it },
                                    label = { Text("SSID") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AppShapes.Button
                                )
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Contraseña") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = AppShapes.Button,
                                    visualTransformation = PasswordVisualTransformation()
                                )
                                Spacer(Modifier.height(16.dp))
                                
                                if (!isConnected) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    "Debes estar conectado a este portón por Bluetooth.",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                            if (connectionError != null) {
                                                Text(
                                                    connectionError,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.padding(top = 4.dp, start = 24.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                }

                                Button(
                                    onClick = {
                                        if (!isConnected) {
                                            onConnect()
                                            return@Button
                                        }
                                        step = ReconfigureStep.Submitting
                                        scope.launch {
                                            val safeHostname = gate.hostname ?: gate.name.lowercase()
                                                .replace(Regex("[^a-z0-9-]"), "-")
                                                .trim('-')
                                                .take(63)
                                                .ifEmpty { "gate" }

                                            val payload = buildJsonObject {
                                                put("action", "config_network")
                                                put("ssid", ssid)
                                                put("password", password)
                                                put("bt_name", gate.name)
                                                put("hostname", safeHostname)
                                                put("iot_token", SecurityConstants.IOT_TOKEN)
                                                put("odoo_url", "${ApiConstants.BASE_URL}/api/update_esp_ip")
                                            }.toString()

                                            val response = onSendMessageAndWaitForReply(payload, 40000L)
                                            if (response == null) {
                                                errorMessage = "No se recibió respuesta del ESP32."
                                                step = ReconfigureStep.Error
                                                return@launch
                                            }
                                            
                                            try {
                                                val jsonElement = Json { ignoreUnknownKeys = true }.parseToJsonElement(response)
                                                val status = jsonElement.jsonObject["status"]?.jsonPrimitive?.content ?: "error"
                                                if (status == "success") {
                                                    step = ReconfigureStep.Success
                                                } else {
                                                    errorMessage = jsonElement.jsonObject["message"]?.jsonPrimitive?.content ?: "Error devuelto por el ESP32."
                                                    step = ReconfigureStep.Error
                                                }
                                            } catch (e: Exception) {
                                                errorMessage = "Respuesta inválida del ESP32."
                                                step = ReconfigureStep.Error
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = AppShapes.Input,
                                    enabled = (!isConnected && !isConnecting) || (isConnected && ssid.isNotBlank()),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isConnected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    if (isConnecting) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onError)
                                        Spacer(Modifier.width(12.dp))
                                        Text("CONECTANDO...", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError)
                                    } else {
                                        Text(if (isConnected) "ENVIAR AL ESP32" else "CONECTAR BLUETOOTH", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        ReconfigureStep.Submitting -> {
                            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        "Enviando configuración...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        ReconfigureStep.Success -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "Configuración Enviada",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "El ESP32 ha recibido la configuración y se conectará al WiFi. Puedes cerrar este cuadro.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(Modifier.height(24.dp))
                                Button(
                                    onClick = {
                                        onDisconnect()
                                        onDismiss()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = AppShapes.Input
                                ) {
                                    Text("CERRAR", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        ReconfigureStep.Error -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "Error",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(errorMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(24.dp))
                                Button(
                                    onClick = { step = ReconfigureStep.Input },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = AppShapes.Input
                                ) {
                                    Text("REINTENTAR", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
