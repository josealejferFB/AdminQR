package com.example.escanqradmin.presentation.ui.espconfig

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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

// ── Design tokens ─────────────────────────────────────────────────
private val ConsoleBg      = Color(0xFF0D1117)
private val ConsolePanel   = Color(0xFF161B22)
private val ConsoleBorder  = Color(0xFF30363D)
private val FormBg         = Color(0xFF1C2128)
private val TxColor        = Color(0xFF1F6FEB)
private val RxColor        = Color(0xFF238636)
private val PromptColor    = Color(0xFF58A6FF)
private val MutedText      = Color(0xFF8B949E)

// ── Quick command definitions ─────────────────────────────────────
private data class QuickCmd(val label: String, val cmd: String, val icon: ImageVector, val color: Color)

private val quickCmds = listOf(
    QuickCmd("Agregar",   "agregar",   Icons.Default.PersonAdd,    Color(0xFF238636)),
    QuickCmd("Eliminar",  "eliminar",  Icons.Default.PersonRemove, Color(0xFFDA3633)),
    QuickCmd("Modificar", "modificar", Icons.Default.Edit,         Color(0xFFD29922)),
    QuickCmd("Consultar", "consultar", Icons.Default.Search,       Color(0xFF1F6FEB)),
)

// ── Screen ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ESPConfigScreen(
    navController: NavHostController,
    viewModel: ESPConfigViewModel = hiltViewModel()
) {
    val st by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val isIdle = st.flowState == EspFlowState.IDLE

    LaunchedEffect(st.messages.size) {
        if (st.messages.isNotEmpty()) listState.animateScrollToItem(st.messages.size - 1)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .statusBarsPadding()
                    .displayCutoutPadding(),
                title = {
                    Column {
                        Text("ESP32 Console", fontWeight = FontWeight.Bold, color = Color.White)
                        AnimatedContent(
                            targetState = st.activeMode ?: "Bluetooth Serial Monitor",
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "mode"
                        ) { Text(it, style = MaterialTheme.typography.labelSmall, color = MutedText) }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(RxColor))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Conectado", color = RxColor, fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ConsolePanel)
            )
        },
        containerColor = ConsoleBg
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // ── Quick Commands ──────────────────────────────────
            QuickCommandBar(enabled = isIdle, onSend = { viewModel.sendQuickCommand(it) })

            HorizontalDivider(color = ConsoleBorder)

            // ── Console log ─────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                if (st.messages.isEmpty()) {
                    EmptyConsole()
                } else {
                    LazyColumn(
                        state   = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(st.messages) { MessageBubble(it) }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }

            // ── Guided form (slides up based on flow state) ─────
            AnimatedContent(
                targetState = st.flowState,
                transitionSpec = {
                    (slideInVertically { it } + fadeIn()) togetherWith
                    (slideOutVertically { it } + fadeOut())
                },
                label = "form_panel"
            ) { flow ->
                when (flow) {
                    EspFlowState.WAIT_JSON_AGREGAR -> FormAgregar(
                        form     = st.form,
                        onCedula = viewModel::onCedulaChange,
                        onMac    = viewModel::onMacChange,
                        onPlaca  = viewModel::onPlacaChange,
                        onSubmit = viewModel::submitForm,
                        onCancel = viewModel::dismissForm
                    )
                    EspFlowState.WAIT_CEDULA_ELIMINAR -> FormCedula(
                        label    = "Cédula del usuario a eliminar",
                        icon     = Icons.Default.PersonRemove,
                        iconColor = Color(0xFFDA3633),
                        value    = st.form.cedula,
                        onChange = viewModel::onCedulaChange,
                        onSubmit = viewModel::submitForm,
                        onCancel = viewModel::dismissForm
                    )
                    EspFlowState.WAIT_CEDULA_CONSULTAR -> FormCedula(
                        label    = "Cédula del usuario a consultar",
                        icon     = Icons.Default.Search,
                        iconColor = Color(0xFF1F6FEB),
                        value    = st.form.cedula,
                        onChange = viewModel::onCedulaChange,
                        onSubmit = viewModel::submitForm,
                        onCancel = viewModel::dismissForm
                    )
                    EspFlowState.WAIT_CEDULA_MODIFICAR -> FormCedula(
                        label    = "Cédula del usuario a modificar",
                        icon     = Icons.Default.Edit,
                        iconColor = Color(0xFFD29922),
                        value    = st.form.cedula,
                        onChange = viewModel::onCedulaChange,
                        onSubmit = viewModel::submitForm,
                        onCancel = viewModel::dismissForm
                    )
                    EspFlowState.WAIT_JSON_MODIFICAR -> FormModificarDatos(
                        form     = st.form,
                        onMac    = viewModel::onMacChange,
                        onPlaca  = viewModel::onPlacaChange,
                        onSubmit = viewModel::submitForm,
                        onCancel = viewModel::dismissForm
                    )
                    EspFlowState.IDLE -> FreeInputBar(
                        value    = st.freeCommand,
                        onChange = viewModel::onFreeCommandChange,
                        onSend   = viewModel::sendFreeCommand
                    )
                }
            }
        }
    }
}

// ── Quick Commands Bar ────────────────────────────────────────────
@Composable
private fun QuickCommandBar(enabled: Boolean, onSend: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsolePanel)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = if (enabled) "Comandos rápidos" else "Sesión activa – responde al formulario abajo",
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MutedText else Color(0xFFD29922),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickCmds.forEach { cmd ->
                FilledTonalButton(
                    onClick  = { onSend(cmd.cmd) },
                    enabled  = enabled,
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.filledTonalButtonColors(
                        containerColor        = cmd.color.copy(alpha = 0.15f),
                        contentColor          = cmd.color,
                        disabledContainerColor = ConsoleBorder.copy(alpha = 0.15f),
                        disabledContentColor   = MutedText
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

// ── Form: Agregar (cedula + mac + placa) ──────────────────────────
@Composable
private fun FormAgregar(
    form: FormFields,
    onCedula: (String) -> Unit,
    onMac: (String) -> Unit,
    onPlaca: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    FormContainer(
        title     = "Agregar usuario",
        icon      = Icons.Default.PersonAdd,
        iconColor = Color(0xFF238636),
        onCancel  = onCancel
    ) {
        EspField("Cédula",     value = form.cedula,  onChange = onCedula, placeholder = "12345678")
        Spacer(Modifier.height(10.dp))
        EspField("MAC",        value = form.mac,     onChange = onMac,    placeholder = "AA:BB:CC:DD:EE:FF")
        Spacer(Modifier.height(10.dp))
        EspField("Placa",      value = form.placa,   onChange = onPlaca,  placeholder = "ABC-123")
        Spacer(Modifier.height(14.dp))
        SubmitButton(
            label   = "Agregar en ESP32",
            enabled = form.cedula.isNotBlank() && form.mac.isNotBlank() && form.placa.isNotBlank(),
            color   = Color(0xFF238636),
            onClick = onSubmit
        )
    }
}

// ── Form: single cedula ───────────────────────────────────────────
@Composable
private fun FormCedula(
    label: String,
    icon: ImageVector,
    iconColor: Color,
    value: String,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    FormContainer(title = label, icon = icon, iconColor = iconColor, onCancel = onCancel) {
        EspField("Cédula", value = value, onChange = onChange, placeholder = "12345678")
        Spacer(Modifier.height(14.dp))
        SubmitButton(label = "Enviar", enabled = value.isNotBlank(), color = iconColor, onClick = onSubmit)
    }
}

// ── Form: Modificar datos (mac + placa) ───────────────────────────
@Composable
private fun FormModificarDatos(
    form: FormFields,
    onMac: (String) -> Unit,
    onPlaca: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    FormContainer(
        title     = "Nuevos datos del usuario",
        icon      = Icons.Default.Edit,
        iconColor = Color(0xFFD29922),
        onCancel  = onCancel
    ) {
        EspField("Nueva MAC",   value = form.mac,   onChange = onMac,   placeholder = "AA:BB:CC:DD:EE:FF")
        Spacer(Modifier.height(10.dp))
        EspField("Nueva Placa", value = form.placa, onChange = onPlaca, placeholder = "ABC-123")
        Spacer(Modifier.height(14.dp))
        SubmitButton(
            label   = "Guardar cambios",
            enabled = form.mac.isNotBlank() && form.placa.isNotBlank(),
            color   = Color(0xFFD29922),
            onClick = onSubmit
        )
    }
}

// ── Shared form container ─────────────────────────────────────────
@Composable
private fun FormContainer(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    onCancel: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    HorizontalDivider(color = ConsoleBorder)
    Surface(color = FormBg) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = MutedText, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ── Reusable labeled text field ───────────────────────────────────
@Composable
private fun EspField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String
) {
    Column {
        Text(label, color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value         = value,
            onValueChange = onChange,
            placeholder   = { Text(placeholder, color = ConsoleBorder, fontSize = 13.sp, fontFamily = FontFamily.Monospace) },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            textStyle     = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                color      = Color.White,
                fontSize   = 14.sp
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = PromptColor,
                unfocusedBorderColor = ConsoleBorder,
                cursorColor          = PromptColor
            ),
            shape = RoundedCornerShape(10.dp)
        )
    }
}

// ── Shared submit button ──────────────────────────────────────────
@Composable
private fun SubmitButton(label: String, enabled: Boolean, color: Color, onClick: () -> Unit) {
    Button(
        onClick  = onClick,
        enabled  = enabled,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape    = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor        = color,
            disabledContainerColor = ConsoleBorder.copy(alpha = 0.3f)
        )
    ) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

// ── Free-form input (visible only when IDLE) ──────────────────────
@Composable
private fun FreeInputBar(value: String, onChange: (String) -> Unit, onSend: () -> Unit) {
    HorizontalDivider(color = ConsoleBorder)
    Surface(color = ConsolePanel) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$ ", color = PromptColor, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextField(
                value         = value,
                onValueChange = onChange,
                singleLine    = true,
                placeholder   = { Text("Comando libre...", color = MutedText, fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                modifier      = Modifier.weight(1f),
                textStyle     = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace, color = Color.White, fontSize = 14.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor   = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor   = PromptColor.copy(alpha = 0.5f),
                    unfocusedIndicatorColor = ConsoleBorder,
                    cursorColor             = PromptColor
                )
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick  = onSend,
                enabled  = value.isNotBlank(),
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier.size(48.dp),
                colors   = IconButtonDefaults.filledIconButtonColors(
                    containerColor        = TxColor,
                    contentColor          = Color.White,
                    disabledContainerColor = ConsoleBorder.copy(alpha = 0.4f)
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ── Message bubbles ───────────────────────────────────────────────
@Composable
private fun MessageBubble(msg: ConsoleMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isSent) Arrangement.End else Arrangement.Start
    ) {
        if (!msg.isSent) {
            Text("ESP›", color = RxColor, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                modifier = Modifier.padding(end = 6.dp).align(Alignment.Bottom))
        }
        Column(
            horizontalAlignment = if (msg.isSent) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Text(
                "${msg.timestamp} · ${if (msg.isSent) "TX" else "RX"}",
                color = MutedText, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Surface(
                shape = RoundedCornerShape(
                    topStart    = 12.dp, topEnd     = 12.dp,
                    bottomStart = if (msg.isSent) 12.dp else 2.dp,
                    bottomEnd   = if (msg.isSent) 2.dp  else 12.dp
                ),
                color = if (msg.isSent) TxColor else ConsolePanel,
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
            Text("›APP", color = TxColor, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                modifier = Modifier.padding(start = 6.dp).align(Alignment.Bottom))
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────
@Composable
private fun EmptyConsole() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Terminal, contentDescription = null, tint = ConsoleBorder, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(10.dp))
            Text("Consola vacía", color = MutedText, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
            Text("Selecciona un comando arriba", color = ConsoleBorder, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
