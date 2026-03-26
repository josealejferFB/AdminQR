package com.example.escanqradmin.presentation.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.escanqradmin.presentation.ui.home.ActiveUser
import com.example.escanqradmin.presentation.theme.color.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.runtime.*

@Composable
fun ActiveUserCard(
    user: ActiveUser,
    onDelete: () -> Unit,
    onUpdate: (ActiveUser) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    // Editable states
    var name by remember(user) { mutableStateOf(user.name) }
    var plate by remember(user) { mutableStateOf(user.plate) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = SurfaceGrey.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (user.status == "VALIDADO") PrimaryBlue else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        if (isExpanded) {
                            Text(
                                text = "EDITANDO USUARIO",
                                color = PrimaryBlue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = user.name,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                fontSize = 15.sp
                            )
                        }
                        
                        if (!isExpanded) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = user.document,
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                
                if (!isExpanded) {
                    Text(
                        text = user.status,
                        color = if (user.status == "VALIDADO") PrimaryBlue else SecondaryOrange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp
                    )
                } else {
                    IconButton(
                        onClick = { isExpanded = false },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.Gray)
                    }
                }
            }
            
            AnimatedVisibility(visible = !isExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "PLACA",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = user.plate,
                                color = PrimaryBlue,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Borrar registro",
                                tint = Color.Red.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Cédula NO EDITABLE
                    OutlinedTextField(
                        value = user.document,
                        onValueChange = { },
                        label = { Text("Cédula (No editable)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = Color.LightGray,
                            disabledTextColor = Color.Gray,
                            disabledLabelColor = Color.Gray
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = plate,
                        onValueChange = { plate = it },
                        label = { Text("Placa") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onUpdate(user.copy(name = name, plate = plate))
                                isExpanded = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Guardar")
                        }
                        
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Borrar")
                        }
                    }
                }
            }
        }
    }
}
