package com.example.escanqradmin.presentation.ui.result.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.escanqradmin.presentation.ui.result.SyncStatus

@Composable
fun ResultSnackbar(
    status  : SyncStatus,
    modifier: Modifier = Modifier
) {
    val visible = status !is SyncStatus.Idle

    AnimatedVisibility(
        visible  = visible,
        enter    = slideInVertically { -it } + fadeIn(),
        exit     = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        val (containerColor, icon, message) = when (status) {
            is SyncStatus.Loading -> Triple(
                MaterialTheme.colorScheme.primaryContainer,
                Icons.Default.Sync,
                "Sincronizando con el servidor..."
            )
            is SyncStatus.Success -> Triple(
                MaterialTheme.colorScheme.secondaryContainer,
                Icons.Default.CheckCircle,
                "✓ Registro completado correctamente"
            )
            is SyncStatus.Error -> Triple(
                MaterialTheme.colorScheme.errorContainer,
                Icons.Default.Error,
                "✗ ${status.message}"
            )
            SyncStatus.Idle -> return@AnimatedVisibility
        }

        val contentColor = when (status) {
            is SyncStatus.Loading -> MaterialTheme.colorScheme.onPrimaryContainer
            is SyncStatus.Success -> MaterialTheme.colorScheme.onSecondaryContainer
            is SyncStatus.Error -> MaterialTheme.colorScheme.onErrorContainer
            SyncStatus.Idle -> Color.Transparent
        }

        Card(
            modifier  = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            shape     = RoundedCornerShape(14.dp),
            colors    = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Row(
                modifier          = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (status is SyncStatus.Loading) {
                    CircularProgressIndicator(
                        color       = contentColor,
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text       = message,
                    color      = contentColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
