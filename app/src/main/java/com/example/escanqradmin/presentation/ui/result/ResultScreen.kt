package com.example.escanqradmin.presentation.ui.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.escanqradmin.domain.model.QrContent
import com.example.escanqradmin.presentation.theme.color.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    onScanAgain: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel()
) {
    val qrData by viewModel.qrData.collectAsState()

    Scaffold(
        containerColor = BackgroundLight,
        topBar = {
            TopAppBar(
                title = { Text("Resultados del Escaneo", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            qrData?.let { data ->
                DataCard("ESTADO DE VERIFICACIÓN", "VALIDADO", isStatus = true)
                DataCard("NOMBRE DE USUARIO", data.userName)
                DataCard("CÉDULA", data.cedula)
                DataCard("PLACAS REGISTRADAS", data.plate)
                DataCard("ANDROID ID (Descifrado)", data.androidId)
                
                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = { 
                        viewModel.registerScan()
                        onScanAgain() 
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("REGISTRAR ENTRADA", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                
                OutlinedButton(
                    onClick = onScanAgain,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    border = BorderStroke(1.dp, PrimaryBlue),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("ESCANEAR OTRO QR", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                }
            } ?: run {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay datos disponibles", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun DataCard(label: String, value: String, isStatus: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label, 
                fontWeight = FontWeight.Bold, 
                fontSize = 10.sp, 
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value, 
                fontSize = if (isStatus) 18.sp else 16.sp, 
                fontWeight = if (isStatus) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (isStatus) PrimaryBlue else Color.Black
            )
        }
    }
}
