package com.example.escanqradmin.presentation.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.escanqradmin.presentation.common.sharedcomponents.CustomBottomBar
import com.example.escanqradmin.presentation.common.sharedcomponents.CustomTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToScanner: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val totalScans by viewModel.totalScans.collectAsState()
    val totalUsers by viewModel.totalUsers.collectAsState()
    val activeUsers by viewModel.activeUsers.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = { CustomTopBar() },
        bottomBar = {
            CustomBottomBar(
                currentRoute = "home",
                onNavigateToHome = { },
                onNavigateToScanner = onNavigateToScanner
            )
        },
        containerColor = Color(0xFFFAFAFA)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            item {
                Text(
                    text = "ESTÁS EN LÍNEA",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Registros Activos",
                    color = Color(0xFF0D1B54),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value = totalScans.toString(),
                        label = "ESCANEOS TOTALES",
                        valueColor = Color(0xFF0D1B54)
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value = totalUsers.toString(),
                        label = "USUARIOS TOTALES",
                        valueColor = Color(0xFFFF6B6B)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar usuario por Cédula, Número de Teléfono o Placa...", fontSize = 12.sp, color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFEEEEEE),
                        unfocusedContainerColor = Color(0xFFEEEEEE),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Usuarios Activos",
                        color = Color.Black,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFE8EAF6), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${activeUsers.size} En línea",
                            color = Color(0xFF3F51B5),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            items(activeUsers) { user ->
                ActiveUserCard(user)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, value: String, label: String, valueColor: Color) {
    Box(
        modifier = modifier
            .background(Color(0xFFF5F5F5), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = value,
                color = valueColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = Color.Gray,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ActiveUserCard(user: ActiveUser) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = if (user.status == "VALIDADO") Color(0xFFE8EAF6) else Color(0xFFF0F0F0),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (user.status == "VALIDADO") Color(0xFF0D1B54) else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = user.name,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = user.document,
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
                
                Text(
                    text = user.status,
                    color = if (user.status == "VALIDADO") Color(0xFF0D1B54) else Color(0xFFFF6B6B),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "CONTACTO",
                color = Color.Gray,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = user.contact,
                color = Color.Black,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "PLACA",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = user.plate,
                        color = Color(0xFF0D1B54),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                
                Text(
                    text = "DETALLES",
                    color = Color(0xFF3F51B5),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp
                )
            }
        }
    }
}
