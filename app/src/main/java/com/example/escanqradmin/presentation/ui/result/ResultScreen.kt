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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.escanqradmin.domain.model.QrContent
import com.example.escanqradmin.presentation.theme.color.PrimaryBlue
import com.example.escanqradmin.presentation.common.sharedcomponents.AppCard
import com.example.escanqradmin.presentation.common.sharedcomponents.AppCardDefaults
import com.example.escanqradmin.presentation.common.sharedcomponents.QrCodeBox
import com.example.escanqradmin.presentation.common.util.buildProvisioningJson

// ── Design tokens ─────────────────────────────────────────────────
private val StepGreen  = Color(0xFF2E7D32)
private val StepRed    = Color(0xFFC62828)
private val StepGray   = Color(0xFFBDBDBD)
private val StepPurple = Color(0xFF7B1FA2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    navController: NavHostController,
    onScanAgain: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel()
) {
    val qrData  by viewModel.qrData.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor      = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding().displayCutoutPadding(),
                title = { Text("Activación de Usuario", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onScanAgain) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            qrData?.let { data ->
                UserInfoCard(data)
                Spacer(Modifier.height(24.dp))

                // PASO 1: SERVIDOR
                StepCard(
                    number    = 1,
                    title     = "Registrar en servidor",
                    subtitle  = "Guarda el registro en el backend de Alcaraván",
                    stepColor = StepGreen,
                    status    = when (val s = uiState.syncStatus) {
                        is SyncStatus.Idle    -> StepStatus.Pending
                        is SyncStatus.Loading -> StepStatus.Loading("Sincronizando...")
                        is SyncStatus.Success -> StepStatus.Done
                        is SyncStatus.Error   -> StepStatus.Failed(s.message)
                    }
                ) {
                    ServerStepContent(uiState.syncStatus, viewModel)
                }

                StepConnector(done = uiState.syncDone)

                // PASO 2: MOSTRAR QR
                StepCard(
                    number   = 2,
                    title    = "Vincular App de Usuario",
                    subtitle = "Muestra el código QR para que el usuario lo escanee",
                    stepColor = StepPurple,
                    isLocked  = !uiState.qrUnlocked,
                    status    = if (uiState.qrUnlocked && uiState.showQrCode) StepStatus.Done
                                else if (uiState.qrUnlocked) StepStatus.Pending
                                else StepStatus.Locked
                ) {
                    QrStepContent(
                        unlocked    = uiState.qrUnlocked,
                        showQr      = uiState.showQrCode,
                        onToggleQr  = { viewModel.toggleQr() },
                        qrPayload   = buildProvisioningJson()
                    )
                }

                Spacer(Modifier.height(24.dp))
                OutlinedButton(
                    onClick  = onScanAgain,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(16.dp),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.QrCodeScanner, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ESCANEAR OTRO QR", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ── Componentes de apoyo ──────────────────────────────────────────

@Composable
private fun UserInfoCard(data: QrContent) {
    AppCard(
        modifier  = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier        = Modifier.size(52.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(data.userName, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Cédula: ${data.cedula}", fontSize = 13.sp, color = Color.Gray)
            }
            Surface(shape = RoundedCornerShape(8.dp), color = StepGreen.copy(alpha = 0.12f)) {
                Text(
                    "VÁLIDO", color = StepGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private sealed class StepStatus {
    object Locked  : StepStatus()
    object Pending : StepStatus()
    data class Loading(val msg: String) : StepStatus()
    object Done    : StepStatus()
    data class Failed(val msg: String) : StepStatus()
}

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
        StepStatus.Locked    -> StepGray
        StepStatus.Done      -> StepGreen
        is StepStatus.Failed -> StepRed
        else                 -> stepColor
    }
    AppCard(
        modifier  = Modifier.fillMaxWidth(),
        colors    = AppCardDefaults.colors(containerColor = if (isLocked) MaterialTheme.colorScheme.surface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface),
        elevation = if (isLocked) CardDefaults.cardElevation(defaultElevation = 0.dp) else AppCardDefaults.Elevation
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier        = Modifier.size(36.dp).background(badgeColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (status is StepStatus.Loading)
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else if (status is StepStatus.Done)
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    else
                        Text("$number", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (isLocked) Color.Gray else MaterialTheme.colorScheme.onSurface)
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
    Box(
        modifier = Modifier
            .padding(start = 37.dp)
            .width(2.dp)
            .height(24.dp)
            .background(if (done) StepGreen else StepGray.copy(alpha = 0.3f))
    )
}

@Composable
private fun ServerStepContent(syncStatus: SyncStatus, viewModel: ResultViewModel) {
    if (syncStatus is SyncStatus.Success) {
        StepDoneChip("Registro en servidor exitoso")
    } else {
        Button(
            onClick  = { viewModel.registerEntry() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = StepGreen),
            enabled  = syncStatus !is SyncStatus.Loading
        ) {
            Text("REGISTRAR EN SERVIDOR")
        }
    }
}

@Composable
private fun QrStepContent(unlocked: Boolean, showQr: Boolean, onToggleQr: () -> Unit, qrPayload: String) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick  = onToggleQr,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = StepPurple)
        ) {
            Icon(Icons.Default.QrCode, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (showQr) "OCULTAR QR" else "MOSTRAR QR")
        }

        AnimatedVisibility(
            visible = showQr,
            enter   = expandVertically(animationSpec = tween(400)) + fadeIn(animationSpec = tween(400)),
            exit    = shrinkVertically(animationSpec = tween(400)) + fadeOut(animationSpec = tween(400))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 16.dp)) {
                QrCodeBox(content = qrPayload, size = 200.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Pide al usuario que escanee este código desde su aplicación para recibir la configuración.",
                    fontSize    = 12.sp,
                    color       = Color.Gray,
                    textAlign   = TextAlign.Center,
                    modifier    = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun StepDoneChip(msg: String) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .background(StepGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.CheckCircle, null, tint = StepGreen, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(msg, color = StepGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
