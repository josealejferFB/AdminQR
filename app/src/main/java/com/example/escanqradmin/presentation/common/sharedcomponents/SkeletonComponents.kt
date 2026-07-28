package com.example.escanqradmin.presentation.common.sharedcomponents

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.escanqradmin.presentation.theme.shape.AppShapes

@Composable
fun shimmerBrush(showShimmer: Boolean = true): Brush {
    if (!showShimmer) {
        return Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent),
            start = Offset.Zero,
            end = Offset.Zero
        )
    }

    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerAnimation"
    )

    val color1 = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val color2 = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

    return Brush.linearGradient(
        colors = listOf(color1, color2, color1),
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )
}

@Composable
fun SkeletonUserCard() {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .height(16.dp)
                        .width(150.dp)
                        .background(shimmerBrush(), AppShapes.Chip)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .height(12.dp)
                        .width(100.dp)
                        .background(shimmerBrush(), AppShapes.Chip)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row {
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .width(60.dp)
                            .background(shimmerBrush(), AppShapes.Chip)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .width(80.dp)
                            .background(shimmerBrush(), AppShapes.Chip)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(shimmerBrush(), CircleShape)
            )
        }
    }
}
