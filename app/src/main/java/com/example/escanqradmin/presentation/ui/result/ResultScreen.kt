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
import androidx.compose.ui.draw.scale
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    navController: NavHostController,
    onScanAgain: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel()
) {
    val qrData  by viewModel.qrData.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

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
                UserInfoCard(data)
                Spacer(Modifier.height(24.dp))

                // PASO 1
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

                StepConnector(done = uiState.step1Done)

                // PASO 2
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
                    UserSyncStepContent(uiState.stepsUnlocked, uiState.userSyncCompleted, data, navController)
                }

                StepConnector(done = uiState.userSyncCompleted)

                // PASO 3
                StepCard(
                    number = 3,
                    title = "Registrar en servidor",
                    subtitle = "Guarda el registro en el backend de Alcaraván",
                    stepColor = StepGreen,
                    isLocked = !uiState.stepsUnlocked,
                    status = when (val s = uiState.syncStatus) {
                        is SyncStatus.Idle    -> if (uiState.stepsUnlocked) StepStatus.Pending else StepStatus.Locked
                        is SyncStatus.Loading -> StepStatus.Loading("Sincronizando...")
                        is SyncStatus.Success -> StepStatus.Done
                        is SyncStatus.Error   -> StepStatus.Failed(s.message)
                    }
                ) {
                    ServerStepContent(uiState.stepsUnlocked, uiState.syncStatus, uiState.userSyncCompleted, viewModel, onScanAgain)
                }

                Spacer(Modifier.height(24.dp))
                OutlinedButton(
                    onClick = onScanAgain,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.QrCodeScanner, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ESCANEAR OTRO QR", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ── Componentes de apoyo (UserInfoCard, StepCard, etc.) ─────────

@Composable
private fun UserInfoCard(data: QrContent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(52.dp).background(PrimaryBlue.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, null, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(data.userName, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Cédula: ${data.cedula}", fontSize = 13.sp, color = Color.Gray)
            }
            Surface(shape = RoundedCornerShape(8.dp), color = StepGreen.copy(alpha = 0.12f)) {
                Text("VÁLIDO", color = StepGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

private sealed class StepStatus {
    object Locked : StepStatus()
    object Pending : StepStatus()
    data class Loading(val msg: String) : StepStatus()
    object Done : StepStatus()
    data class Failed(val msg: String) : StepStatus()
}

@Composable
private fun StepCard(number: Int, title: String, subtitle: String, stepColor: Color, isLocked: Boolean = false, status: StepStatus, content: @Composable ColumnScope.() -> Unit) {
    val badgeColor = when (status) {
        StepStatus.Locked -> StepGray
        StepStatus.Done -> StepGreen
        is StepStatus.Failed -> StepRed
        else -> stepColor
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (isLocked) SurfaceCard.copy(alpha = 0.6f) else SurfaceCard),
        elevation = CardDefaults.cardElevation(if (isLocked) 0.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(36.dp).background(badgeColor, CircleShape), contentAlignment = Alignment.Center) {
                    if (status is StepStatus.Loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else if (status is StepStatus.Done) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    else Text("$number", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (isLocked) Color.Gray else Color.Black)
                    Text(subtitle, fontSize = 12.sp, color = Color.Gray)
                }
            }
            if (!isLocked) {
                Column(modifier = Modifier.padding(top = 16.dp)) { content() }
                if (status is StepStatus.Failed) {
                    Text(status.msg, color = StepRed, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun StepConnector(done: Boolean) {
    Box(modifier = Modifier.padding(start = 37.dp).width(2.dp).height(24.dp).background(if (done) StepGreen else StepGray.copy(alpha = 0.3f)))
}

@Composable
private fun Esp32StepContent(uiState: ResultUiState, viewModel: ResultViewModel) {
    if (uiState.step1Done) StepDoneChip("Datos guardados en ESP32")
    else Button(
        onClick = { viewModel.uploadToEsp32() },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        enabled = uiState.espUploadStatus !is EspUploadStatus.Loading
    ) {
        Text("SUBIR AL ESP32")
    }
}

@Composable
private fun UserSyncStepContent(unlocked: Boolean, completed: Boolean, data: QrContent, navController: NavHostController) {
    if (completed) StepDoneChip("Sincronización completada")
    else Button(
        onClick = { navController.navigate(UserSync(data.userName, data.cedula)) },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = StepPurple)
    ) {
        Icon(Icons.Default.Bluetooth, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("SINCRONIZAR CON USUARIO")
    }
}

@Composable
private fun ServerStepContent(unlocked: Boolean, syncStatus: SyncStatus, userSyncDone: Boolean, viewModel: ResultViewModel, onScanAgain: () -> Unit) {
    if (syncStatus is SyncStatus.Success) StepDoneChip("Registro en servidor exitoso")
    else Button(
        onClick = { viewModel.registerEntry { onScanAgain() } },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = StepGreen),
        enabled = syncStatus !is SyncStatus.Loading
    ) {
        Text("FINALIZAR Y REGISTRAR")
    }
}

@Composable
private fun StepDoneChip(msg: String) {
    Row(modifier = Modifier.fillMaxWidth().background(StepGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, null, tint = StepGreen, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(msg, color = StepGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
