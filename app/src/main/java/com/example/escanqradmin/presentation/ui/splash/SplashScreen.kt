package com.example.escanqradmin.presentation.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Animatable values for a state-of-the-art fluid transition
    val scale = remember { Animatable(0f) }
    val rotation = remember { Animatable(-90f) }
    val alpha = remember { Animatable(0f) }
    val yOffset = remember { Animatable(40f) }
    val haloScale = remember { Animatable(0.5f) }
    val haloAlpha = remember { Animatable(0f) }

    LaunchedEffect(key1 = true) {
        // Halo expands and fades in
        launch {
            haloScale.animateTo(
                targetValue = 1.2f,
                animationSpec = tween(1200, easing = EaseOutQuart)
            )
        }
        launch {
            haloAlpha.animateTo(
                targetValue = 0.15f,
                animationSpec = tween(800)
            )
        }

        // Logo spring bounce and rotate
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            rotation.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }

        // Text fades in and slides up
        launch {
            delay(400)
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(800, easing = EaseOutCubic)
            )
        }
        launch {
            delay(400)
            yOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    // Navigate when completed
    LaunchedEffect(uiState) {
        if (uiState is SplashUiState.Completed) {
            onNavigateToHome()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Radial background glow / premium halo
        Box(
            modifier = Modifier
                .size(300.dp)
                .scale(haloScale.value)
                .alpha(haloAlpha.value)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo Icon
            Box(
                modifier = Modifier
                    .scale(scale.value)
                    .graphicsLayer {
                        rotationZ = rotation.value
                    }
                    .size(110.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.example.escanqradmin.R.drawable.ic_app_logo),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Brand text and subtitle container
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .alpha(alpha.value)
                    .offset(y = yOffset.value.dp)
            ) {
                Text(
                    text = "EscanQR",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Panel de Control",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }
}
