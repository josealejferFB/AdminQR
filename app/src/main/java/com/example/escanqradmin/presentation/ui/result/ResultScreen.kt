package com.example.escanqradmin.presentation.ui.result

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.escanqradmin.domain.model.QrContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    onScanAgain: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel()
) {
    val qrData by viewModel.qrData.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resultados del QR") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                DataCard("Android ID", data.androidId)
                DataCard("Nombre de Usuario", data.userName)
                DataCard("Cédula", data.cedula)
                DataCard("Teléfono", data.phone)
                DataCard("Placa", data.plate)
                
                Spacer(modifier = Modifier.weight(1f))
                
                Button(
                    onClick = onScanAgain,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Escanear Otro", fontSize = 16.sp)
                }
            } ?: run {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay datos disponibles")
                }
            }
        }
    }
}

@Composable
fun DataCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
