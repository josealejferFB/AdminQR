package com.example.escanqradmin.presentation.ui.espconfig

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.escanqradmin.domain.repository.BluetoothConnectionState
import com.example.escanqradmin.presentation.theme.theme.EspColorScheme
import com.example.escanqradmin.presentation.theme.theme.EspColorSchemeColors

private data class QuickCmd(val label: String, val cmd: String, val icon: ImageVector, val color: Color)

private val quickCmds = listOf(
    QuickCmd("Config", "config", Icons.Default.Settings, Color(0xFF8957E5)),
    QuickCmd("WiFi",   "wifi",   Icons.Default.Wifi,     Color(0xFF58A6FF))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ESPConfigScreen(
    navController: NavHostController,
    viewModel: ESPConfigViewModel = hiltViewModel()
) {
    val st by viewModel.uiState.collectAsState()
    val btState by viewModel.connectionState.collectAsState()
    val listState = rememberLazyListState()
    val isIdle = st.flowState == EspFlowState.IDLE
    val espColors = EspColorScheme()

    LaunchedEffect(st.messages.size) {
        if (st.messages.isNotEmpty()) listState.animateScrollToItem(st.messages.size - 1)
    }

    LaunchedEffect(btState) {
        if (btState !is BluetoothConnectionState.Connected && st.flowState != EspFlowState.IDLE) {
            viewModel.dismissForm()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ESP32 Console", fontWeight = FontWeight.Bold, color = espColors.onSurface)
                        AnimatedContent(
                            targetState = st.activeMode ?: "Bluetooth Serial Monitor",
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "mode"
                        ) { Text(it, style = MaterialTheme.typography.labelSmall, color = espColors.onSurfaceVariant) }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = espColors.onSurface)
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        val statusColor = when (btState) {
                            is BluetoothConnectionState.Connected -> espColors.secondary
                            is BluetoothConnectionState.Error -> Color(0xFFD32F2F)
                            else -> espColors.onSurfaceVariant
                        }
                        val statusText = when (btState) {
                            is BluetoothConnectionState.Connected -> "Conectado"
                            is BluetoothConnectionState.Error -> "Error"
                            is BluetoothConnectionState.Connecting -> "Conectando..."
                            else -> "Desconectado"
                        }
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(statusText, color = statusColor, fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = espColors.surfaceVariant)
            )
        },
        containerColor = espColors.surface
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            QuickCommandBar(enabled = isIdle, onSend = { viewModel.sendQuickCommand(it) }, espColors = espColors)

            HorizontalDivider(color = espColors.outline)

            Box(modifier = Modifier.weight(1f)) {
                if (st.messages.isEmpty()) {
                    EmptyConsole(espColors = espColors)
                } else {
                    LazyColumn(
                        state   = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(st.messages) { MessageBubble(it, espColors) }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }

            AnimatedContent(
                targetState = st.flowState,
                transitionSpec = {
                    (slideInVertically { it } + fadeIn()) togetherWith
                    (slideOutVertically { it } + fadeOut())
                },
                label = "form_panel"
            ) { flow ->
                when (flow) {
                    EspFlowState.WAIT_JSON_CONFIG -> FormConfig(
                        form         = st.form,
                        onProtocolo  = viewModel::onProtocoloChange,
                        onIpOdoo     = viewModel::onIpOdooChange,
                        onPort       = viewModel::onPortChange,
                        onSubmit     = viewModel::submitForm,
                        onCancel     = viewModel::dismissForm,
                        espColors    = espColors
                    )
                    EspFlowState.WAIT_WIFI_SSID -> FormSingleField(
                        label     = "Nombre de la Red (SSID)",
                        icon      = Icons.Default.Wifi,
                        iconColor = Color(0xFF58A6FF),
                        value     = st.form.ssid,
                        onChange  = viewModel::onSsidChange,
                        onSubmit  = viewModel::submitForm,
                        onCancel  = viewModel::dismissForm,
                        espColors = espColors
                    )
                    EspFlowState.WAIT_WIFI_PASS -> FormSingleField(
                        label     = "Contraseña de WiFi",
                        icon      = Icons.Default.Lock,
                        iconColor = Color(0xFF58A6FF),
                        value     = st.form.password,
                        onChange  = viewModel::onPasswordChange,
                        onSubmit  = viewModel::submitForm,
                        onCancel  = viewModel::dismissForm,
                        espColors = espColors
                    )
                    EspFlowState.IDLE -> FreeInputBar(
                        value    = st.freeCommand,
                        onChange = viewModel::onFreeCommandChange,
                        onSend   = viewModel::sendFreeCommand,
                        espColors = espColors
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickCommandBar(enabled: Boolean, onSend: (String) -> Unit, espColors: EspColorSchemeColors) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(espColors.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = if (enabled) "Comandos rápidos" else "Sesión activa – responde al formulario abajo",
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) espColors.onSurfaceVariant else Color(0xFFD29922),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickCmds.forEach { cmd ->
                FilledTonalButton(
                    onClick  = { onSend(cmd.cmd) },
                    enabled  = enabled,
                    modifier = Modifier.width(100.dp).height(60.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.filledTonalButtonColors(
                        containerColor        = cmd.color.copy(alpha = 0.15f),
                        contentColor          = cmd.color,
                        disabledContainerColor = espColors.outline.copy(alpha = 0.15f),
                        disabledContentColor   = espColors.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(cmd.icon, contentDescription = cmd.label, modifier = Modifier.size(20.dp))
                        Text(cmd.label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FormConfig(
    form: FormFields,
    onProtocolo: (String) -> Unit,
    onIpOdoo: (String) -> Unit,
    onPort: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    espColors: EspColorSchemeColors
) {
    FormContainer(
        title     = "Configuración de red Odoo",
        icon      = Icons.Default.Settings,
        iconColor = Color(0xFF8957E5),
        onCancel  = onCancel,
        espColors = espColors
    ) {
        EspField("Protocolo", value = form.protocolo, onChange = onProtocolo, placeholder = "http o https", espColors = espColors)
        Spacer(Modifier.height(10.dp))
        EspField("IP Odoo",   value = form.ip_odoo,   onChange = onIpOdoo,   placeholder = "192.168.1.100", espColors = espColors)
        Spacer(Modifier.height(10.dp))
        EspField("Puerto",    value = form.port,      onChange = onPort,     placeholder = "80", espColors = espColors)
        Spacer(Modifier.height(14.dp))
        SubmitButton(
            label   = "Enviar Configuración",
            enabled = form.ip_odoo.isNotBlank(),
            color   = Color(0xFF8957E5),
            onClick = onSubmit,
            espColors = espColors
        )
    }
}

@Composable
private fun FormSingleField(
    label: String,
    icon: ImageVector,
    iconColor: Color,
    value: String,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    espColors: EspColorSchemeColors
) {
    FormContainer(title = label, icon = icon, iconColor = iconColor, onCancel = onCancel, espColors = espColors) {
        EspField(label, value = value, onChange = onChange, placeholder = "", espColors = espColors)
        Spacer(Modifier.height(14.dp))
        SubmitButton(label = "Enviar", enabled = value.isNotBlank(), color = iconColor, onClick = onSubmit, espColors = espColors)
    }
}

@Composable
private fun FormContainer(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    onCancel: () -> Unit,
    espColors: EspColorSchemeColors,
    content: @Composable ColumnScope.() -> Unit
) {
    HorizontalDivider(color = espColors.outline)
    Surface(color = espColors.surfaceVariant) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(title, color = espColors.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = espColors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun EspField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    espColors: EspColorSchemeColors
) {
    Column {
        Text(label, color = espColors.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value         = value,
            onValueChange = onChange,
            placeholder   = { Text(placeholder, color = espColors.outline, fontSize = 13.sp, fontFamily = FontFamily.Monospace) },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            textStyle     = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                color      = espColors.onSurface,
                fontSize   = 14.sp
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = espColors.primary,
                unfocusedBorderColor = espColors.outline,
                cursorColor          = espColors.primary
            ),
            shape = RoundedCornerShape(10.dp)
        )
    }
}

@Composable
private fun SubmitButton(label: String, enabled: Boolean, color: Color, onClick: () -> Unit, espColors: EspColorSchemeColors) {
    Button(
        onClick  = onClick,
        enabled  = enabled,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape    = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor        = color,
            disabledContainerColor = espColors.outline.copy(alpha = 0.3f)
        )
    ) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FreeInputBar(value: String, onChange: (String) -> Unit, onSend: () -> Unit, espColors: EspColorSchemeColors) {
    HorizontalDivider(color = espColors.outline)
    Surface(color = espColors.surfaceVariant) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$ ", color = espColors.primary, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextField(
                value         = value,
                onValueChange = onChange,
                singleLine    = true,
                placeholder   = { Text("Comando libre...", color = espColors.onSurfaceVariant, fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                modifier      = Modifier.weight(1f),
                textStyle     = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace, color = espColors.onSurface, fontSize = 14.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor   = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor   = espColors.primary.copy(alpha = 0.5f),
                    unfocusedIndicatorColor = espColors.outline,
                    cursorColor             = espColors.primary
                )
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick  = onSend,
                enabled  = value.isNotBlank(),
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier.size(48.dp),
                colors   = IconButtonDefaults.filledIconButtonColors(
                    containerColor        = espColors.primary,
                    contentColor          = Color.White,
                    disabledContainerColor = espColors.outline.copy(alpha = 0.4f)
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ConsoleMessage, espColors: EspColorSchemeColors) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isSent) Arrangement.End else Arrangement.Start
    ) {
        if (!msg.isSent) {
            Text("ESP\u203A", color = espColors.secondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                modifier = Modifier.padding(end = 6.dp).align(Alignment.Bottom))
        }
        Column(
            horizontalAlignment = if (msg.isSent) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Text(
                "${msg.timestamp} \u00B7 ${if (msg.isSent) "TX" else "RX"}",
                color = espColors.onSurfaceVariant, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Surface(
                shape = RoundedCornerShape(
                    topStart    = 12.dp, topEnd     = 12.dp,
                    bottomStart = if (msg.isSent) 12.dp else 2.dp,
                    bottomEnd   = if (msg.isSent) 2.dp  else 12.dp
                ),
                color = if (msg.isSent) espColors.primary else espColors.surfaceVariant,
                tonalElevation = if (msg.isSent) 0.dp else 2.dp
            ) {
                Text(
                    msg.text,
                    modifier   = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color      = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize   = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
        if (msg.isSent) {
            Text("\u203AAP", color = espColors.primary, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                modifier = Modifier.padding(start = 6.dp).align(Alignment.Bottom))
        }
    }
}

@Composable
private fun EmptyConsole(espColors: EspColorSchemeColors) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Terminal, contentDescription = null, tint = espColors.outline, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(10.dp))
            Text("Consola vacía", color = espColors.onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
            Text("Selecciona un comando arriba", color = espColors.outline, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
