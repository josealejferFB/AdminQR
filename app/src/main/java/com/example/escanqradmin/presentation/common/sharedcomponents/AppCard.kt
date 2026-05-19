package com.example.escanqradmin.presentation.common.sharedcomponents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

object AppCardDefaults {
    val Shape: Shape
        @Composable
        get() = RoundedCornerShape(24.dp)

    val Elevation: CardElevation
        @Composable
        get() = CardDefaults.cardElevation(defaultElevation = 2.dp)

    @Composable
    fun border(color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)): BorderStroke {
        return BorderStroke(1.dp, color)
    }

    @Composable
    fun colors(containerColor: Color = MaterialTheme.colorScheme.surface): CardColors {
        return CardDefaults.cardColors(containerColor = containerColor)
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = AppCardDefaults.Shape,
    colors: CardColors = AppCardDefaults.colors(),
    elevation: CardElevation = AppCardDefaults.Elevation,
    border: BorderStroke? = AppCardDefaults.border(),
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        Card(
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            onClick = onClick,
            content = content
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            content = content
        )
    }
}
