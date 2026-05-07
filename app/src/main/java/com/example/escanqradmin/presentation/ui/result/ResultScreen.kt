package com.example.escanqradmin.presentation.ui.result

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.escanqradmin.domain.model.QrContent
import com.example.escanqradmin.presentation.navigation.UserSync
import com.example.escanqradmin.presentation.theme.color.PrimaryBlue

// ── Design tokens ─────────────────────────────────────────────────
private val StepGreen  = Color(0xFF2E7D32)
private val StepRed    = Color(0xFFC62828)
private val StepGray   = Color(0xFFBDBDBD)
private val StepPurple = Color(0xFF7B1FA2)
private val SurfaceCard = Color.White
private val PageBg     = Color(0xFFF5F7FA)

// ── Screen ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    navController: NavHostController,
    onScanAgain: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel()
) {
    val qrData  by viewModel.qrData.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // Recibe resultado del paso 2 (UserSyncScreen) via savedStateHandle
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    LaunchedEffect(savedStateHandle) {
        savedStateHandle?.getStateFlow("sync_success", false)?.collect { success ->
            if (success) {
                viewModel.markUserSyncCompleted()
                savedStateHandle.set("sync_success", false)
            }
        }
    }

    Scaffold(
        containerColor = PageBg,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding().displayCutoutPadding(),
                title = { Text("Activación de Usuario", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onScanAgain) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryBlue)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            qrData?.let { data ->

                // ── Tarjeta del usuario escaneado ─────────────────
                UserInfoCard(data)
                Spacer(Modifier.height(24.dp))

                // ── PASO 1: ESP32 ────────────────────────────────
                StepCard(
                    number = 1,
                    title = "Registrar en ESP32",
                    subtitle = "Guarda el acceso en la tarjeta de control físico",
                    stepColor = PrimaryBlue,
                    status = when (val s = uiState.espUploadStatus) {
                        is EspUploadStatus.Idle    -> StepStatus.Pending
                        is EspUploadStatus.Loading -> StepStatus.Loading(s.step)
                        is EspUploadStatus.Success -> StepStatus.Done
                        is EspUploadStatus.Error   -> StepStatus.Failed(s.message)
                    }
                ) {
                    Esp32StepContent(uiState, viewModel)
                }

                // ── Conector ─────────────────────────────────────
                StepConnector(done = uiState.step1Done)

                // ── PASO 2: Sincronizar App Usuario ───────────────
                StepCard(
                    number = 2,
                    title = "Sincronizar App de Usuario",
                    subtitle = "Envía endpoint y MAC del ESP32 por Bluetooth",
                    stepColor = StepPurple,
                    isLocked = !uiState.stepsUnlocked,
                    status = when {
                        !uiState.stepsUnlocked     -> StepStatus.Locked
                        uiState.userSyncCompleted  -> StepStatus.Done
                        else                       -> StepStatus.Pending
                    }
                ) {
                    UserSyncStepContent(
                        unlocked = uiState.stepsUnlocked,
                        completed = uiState.userSyncCompleted,
                        data = data,
                        navController = navController
                    )
                }

                // ── Conector ─────────────────────────────────────
                StepConnector(done = uiState.userSyncCompleted)

                // ── PASO 3: Servidor ──────────────────────────────
                StepCard(
                    number = 3,
                    title = "Registrar en servidor",
                    subtitle = "Guarda el registro en el backend de Alcaraván",
                    stepColor = StepGreen,
                    isLocked = !uiState.stepsUnlocked,
                    status = when (val s = uiState.syncStatus) {
                        is SyncStatus.Idle    -> if (uiState.stepsUnlocked) StepStatus.Pending else StepStatus.Locked
                        is SyncStatus.Loading -> StepStatus.Loading("Sincronizando con el servidor...")
                        is SyncStatus.Success -> StepStatus.Done
                        is SyncStatus.Error   -> StepStatus.Failed(s.message)
                    }
                ) {
                    ServerStepContent(
                        unlocked = uiState.stepsUnlocked,
                        syncStatus = uiState.syncStatus,
                        userSyncDone = uiState.userSyncCompleted,
                        viewModel = viewModel,
                        onScanAgain = onScanAgain
                    )
                }

                Spacer(Modifier.height(20.dp))

                // ── Botón secundario: nuevo escaneo ───────────────
                OutlinedButton(
                    onClick = onScanAgain,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.QrCodeScanner, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ESCANEAR OTRO QR", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(Modifier.height(8.dp))

            } ?: run {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay datos disponibles", color = Color.Gray)
                }
            }
        }
    }
}

// ── Tarjeta de usuario ────────────────────────────────────────────
@Composable
private fun UserInfoCard(data: QrContent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(52.dp).background(PrimaryBlue.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.userName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color.Black
                )
                Text(
                    text = "Cédula: ${data.cedula}",
                    fontSize = 13.sp,
                    color = Color(0xFF757575)
                )
                Text(
                    text = "Placa: ${data.plate}",
                    fontSize = 13.sp,
                    color = Color(0xFF757575)
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = StepGreen.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VerifiedUser, null, tint = StepGreen, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("VÁLIDO", color = StepGreen, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

// ── Modelo de estado de cada paso ────────────────────────────────
private sealed class StepStatus {
    object Locked               : StepStatus()
    object Pending              : StepStatus()
    data class Loading(val msg: String) : StepStatus()
    object Done                 : StepStatus()
    data class Failed(val msg: String)  : StepStatus()
}

// ── Step card ─────────────────────────────────────────────────────
@Composable
private fun StepCard(
    number: Int,
    title: String,
    subtitle: String,
    stepColor: Color,
    isLocked: Boolean = false,
    status: StepStatus,
    content: @Composable ColumnScope.() -> Unit
) {
    val badgeColor = when (status) {
        StepStatus.Locked   -> StepGray
        StepStatus.Done     -> StepGreen
        is StepStatus.Failed -> StepRed
        else                -> stepColor
    }
    val cardAlpha = if (isLocked) 0.55f else 1f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard.copy(alpha = cardAlpha)),
        elevation = CardDefaults.cardElevation(if (isLocked) 0.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(verticalAlignment = Alignment.Top) {
                // Número / estado badge
                Box(
                    modifier = Modifier.size(36.dp).background(badgeColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    when (status) {
                        StepStatus.Done -> Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        is StepStatus.Failed -> Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        is StepStatus.Loading -> CircularProgressIndicator(
                            modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.5.dp
                        )
                        else -> Text("$number", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (isLocked) Color.Gray else Color.Black)
                    Text(subtitle, fontSize = 12.sp, color = Color(0xFF9E9E9E))
                }
            }

            // Contenido animado
            AnimatedVisibility(
                visible = !isLocked,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    content()
                }
            }

            // Mensaje de error
            if (status is StepStatus.Failed) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StepRed.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, null, tint = StepRed, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(status.msg, color = StepRed, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    }
                }
            }

            // Mensaje de loading
            if (status is StepStatus.Loading) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = stepColor.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = stepColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(status.msg, color = stepColor, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ── Conector entre pasos ──────────────────────────────────────────
@Composable
private fun StepConnector(done: Boolean) {
    val color by animateColorAsState(
        targetValue = if (done) StepGreen else StepGray.copy(alpha = 0.4f),
        animationSpec = tween(500),
        label = "connector"
    )
    Box(
        modifier = Modifier
            .padding(start = 37.dp)   // alineado al centro del badge (20dp padding + 36dp/2 - 2dp/2)
            .width(2.dp)
            .height(24.dp)
            .background(color)
    )
}

// ── Contenido Paso 1: ESP32 ───────────────────────────────────────
@Composable
private fun Esp32StepContent(uiState: ResultUiState, viewModel: ResultViewModel) {
    val status = uiState.espUploadStatus
    val isLoading = status is EspUploadStatus.Loading
    val isSuccess = status is EspUploadStatus.Success
    val isError   = status is EspUploadStatus.Error

    when {
        isSuccess -> StepDoneChip("Datos guardados en ESP32")
        else -> Button(
            onClick = { viewModel.uploadToEsp32() },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isError) StepRed else PrimaryBlue,
                disabledContainerColor = PrimaryBlue.copy(alpha = 0.5f)
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Enviando...", fontWeight = FontWeight.Bold)
            } else {
                Icon(
                    if (isError) Icons.Default.Refresh else Icons.Default.CloudUpload,
                    null, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(if (isError) "REINTENTAR" else "SUBIR AL ESP32", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Contenido Paso 2: UserSync ────────────────────────────────────
@Composable
private fun UserSyncStepContent(
    unlocked: Boolean,
    completed: Boolean,
    data: QrContent,
    navController: NavHostController
) {
    if (!unlocked) return

    if (completed) {
        StepDoneChip("Configuración enviada al dispositivo del usuario")
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Hint informativo
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = StepPurple.copy(alpha = 0.07f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, tint = StepPurple, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Asegúrate de que la App de Usuario esté en la pantalla de sincronización antes de continuar.",
                        color = StepPurple, fontSize = 12.sp
                    )
                }
            }
            Button(
                onClick = {
                    navController.navigate(
                        UserSync(userName = data.userName, cedula = data.cedula)
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StepPurple)
            ) {
                Icon(Icons.Default.Bluetooth, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("SINCRONIZAR CON USUARIO", fontWeight = FontWeight.Bold)
            }
            TextButton(
                onClick = {
                    // Permitir omitir el paso 2 directamente desde ResultScreen
                    // (no se hace nada, el paso 3 ya está desbloqueado)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Omitir este paso", color = Color(0xFF9E9E9E), fontSize = 12.sp)
            }
        }
    }
}

// ── Contenido Paso 3: Servidor ────────────────────────────────────
@Composable
private fun ServerStepContent(
    unlocked: Boolean,
    syncStatus: SyncStatus,
    userSyncDone: Boolean,
    viewModel: ResultViewModel,
    onScanAgain: () -> Unit
) {
    if (!unlocked) return

    val isLoading = syncStatus is SyncStatus.Loading
    val isSuccess = syncStatus is SyncStatus.Success
    val isError   = syncStatus is SyncStatus.Error

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Advertencia si no se hizo el paso 2
        if (!userSyncDone && syncStatus is SyncStatus.Idle) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFFFF8E1),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFF57F17), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "No sincronizaste la App de Usuario. Puedes continuar, pero el usuario no tendrá la config.",
                        color = Color(0xFFF57F17), fontSize = 12.sp
                    )
                }
            }
        }

        when {
            isSuccess -> {
                StepDoneChip("Registro guardado en el servidor")
                Spacer(Modifier.height(4.dp))
                // Nota de reconexión
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PrimaryBlue.copy(alpha = 0.07f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BluetoothConnected, null, tint = PrimaryBlue, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reconectando al ESP32 en segundo plano...", color = PrimaryBlue, fontSize = 12.sp)
                    }
                }
            }
            else -> {
                Button(
                    onClick = {
                        viewModel.registerEntry { onScanAgain() }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isError) StepRed else StepGreen,
                        disabledContainerColor = StepGreen.copy(alpha = 0.5f)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Sincronizando...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            if (isError) Icons.Default.Refresh else Icons.Default.CloudDone,
                            null, modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(if (isError) "REINTENTAR" else "AGREGAR ENTRADA", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Chip de paso completado ────────────────────────────────────────
@Composable
private fun StepDoneChip(message: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = StepGreen.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(28.dp).background(StepGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(message, color = StepGreen, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}
