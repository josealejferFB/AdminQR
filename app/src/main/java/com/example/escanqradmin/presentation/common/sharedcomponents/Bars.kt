package com.example.escanqradmin.presentation.common.sharedcomponents

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.escanqradmin.presentation.navigation.Home
import com.example.escanqradmin.presentation.navigation.Scanner
import com.example.escanqradmin.presentation.theme.color.*

@Composable
fun CustomTopBar(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White,
    contentColor: Color = PrimaryBlue,
    applyPrivacyPadding: Boolean = false,
    isFloating: Boolean = false
) {
    Surface(
        color = containerColor,
        modifier = modifier
            .then(if (isFloating) Modifier.padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 8.dp) else Modifier)
            .fillMaxWidth(),
        shape = if (isFloating) RoundedCornerShape(24.dp) else RoundedCornerShape(0.dp),
        shadowElevation = if (isFloating) 8.dp else 4.dp
    ) {
        Row(
            modifier = Modifier
                .then(if (applyPrivacyPadding) Modifier.statusBarsPadding() else Modifier)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Logo",
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "EscanQR",
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(SecondaryOrange.copy(alpha = if (containerColor == Color.Transparent) 0.3f else 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = "Profile",
                    tint = if (containerColor == Color.Transparent) Color.White else SecondaryOrange,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun CustomBottomBar(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White,
    applyPrivacyPadding: Boolean = false,
    isFloating: Boolean = false
) {
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val destination = navBackStackEntry.value?.destination
    val haptic = LocalHapticFeedback.current

    Surface(
        color = containerColor,
        modifier = modifier
            .then(if (isFloating) Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 12.dp) else Modifier)
            .fillMaxWidth(),
        shadowElevation = if (isFloating) 12.dp else 8.dp,
        shape = when {
            isFloating -> RoundedCornerShape(32.dp)
            containerColor == Color.Transparent -> RoundedCornerShape(0.dp)
            else -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        }
    ) {
        Row(
            modifier = Modifier
                .then(if (applyPrivacyPadding) Modifier.navigationBarsPadding() else Modifier)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                icon = Icons.Default.Home, 
                label = "INICIO", 
                isSelected = destination?.hasRoute<Home>() == true,
                colorOnSelected = if (containerColor == Color.Transparent) Color.White else PrimaryBlue,
                colorOnUnselected = if (containerColor == Color.Transparent) Color.White.copy(alpha = 0.6f) else Color.Gray,
                onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    try {
                        navController.popBackStack(Home, inclusive = false)
                    } catch (e: Exception) {
                        navController.navigate(Home) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
            
            Spacer(modifier = Modifier.width(32.dp))
            
            // Central Scanner Button
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(PrimaryBlue, RoundedCornerShape(20.dp))
                    .clickable { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (destination?.hasRoute<Scanner>() != true) {
                            navController.navigate(Scanner) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scanner",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(32.dp))
            
            BottomNavItem(
                icon = Icons.Default.Person, 
                label = "Perfil", 
                isSelected = false, // TODO: Profile check
                colorOnSelected = if (containerColor == Color.Transparent) Color.White else PrimaryBlue,
                colorOnUnselected = if (containerColor == Color.Transparent) Color.White.copy(alpha = 0.6f) else Color.Gray,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    // Perfil navigation logic here
                }
            )
        }
    }
}

@Composable
fun BottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    label: String, 
    isSelected: Boolean,
    colorOnSelected: Color,
    colorOnUnselected: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) colorOnSelected else Color.Transparent,
        animationSpec = tween(300),
        label = "bgAnim"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else colorOnUnselected,
        animationSpec = tween(300),
        label = "contentAnim"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        label = "scaleAnim"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = icon, 
            contentDescription = label, 
            tint = contentColor, 
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label, 
            color = contentColor, 
            fontSize = 11.sp, 
            fontWeight = FontWeight.Bold
        )
    }
}
