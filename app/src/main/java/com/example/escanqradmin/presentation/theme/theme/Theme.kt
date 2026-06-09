package com.example.escanqradmin.presentation.theme.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.escanqradmin.presentation.theme.type.Typography

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1E293B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2E8F0),
    onPrimaryContainer = Color(0xFF0F172A),
    secondary = Color(0xFF0D9488),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF042F2E),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF1E0A3C),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF450A0A)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF94A3B8),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E293B),
    onPrimaryContainer = Color(0xFFF1F5F9),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF134E4A),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFFA78BFA),
    onTertiary = Color(0xFF1E0A3C),
    tertiaryContainer = Color(0xFF3B1F6E),
    onTertiaryContainer = Color(0xFFEDE9FE),
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFFAFAFA),
    surface = Color(0xFF18181B),
    onSurface = Color(0xFFE4E4E7),
    surfaceVariant = Color(0xFF27272A),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF3F3F46),
    outlineVariant = Color(0xFF52525B),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

object EspColorScheme {
    val surfaceLight = Color(0xFFF8FAFC)
    val surfaceDark = Color(0xFF0D1117)
    val surfaceVariantLight = Color(0xFFE2E8F0)
    val surfaceVariantDark = Color(0xFF161B22)
    val outlineLight = Color(0xFFCBD5E1)
    val outlineDark = Color(0xFF30363D)
    val onSurfaceLight = Color(0xFF0F172A)
    val onSurfaceDark = Color(0xFFE6EDF3)
    val primary = Color(0xFF1F6FEB)
    val secondary = Color(0xFF238636)
    val tertiary = Color(0xFF8957E5)
    val onSurfaceVariantLight = Color(0xFF64748B)
    val onSurfaceVariantDark = Color(0xFF8B949E)
}

@Composable
fun EspColorScheme(): EspColorSchemeColors {
    val dark = isSystemInDarkTheme()
    return EspColorSchemeColors(
        surface = if (dark) EspColorScheme.surfaceDark else EspColorScheme.surfaceLight,
        surfaceVariant = if (dark) EspColorScheme.surfaceVariantDark else EspColorScheme.surfaceVariantLight,
        outline = if (dark) EspColorScheme.outlineDark else EspColorScheme.outlineLight,
        onSurface = if (dark) EspColorScheme.onSurfaceDark else EspColorScheme.onSurfaceLight,
        onSurfaceVariant = if (dark) EspColorScheme.onSurfaceVariantDark else EspColorScheme.onSurfaceVariantLight,
        primary = EspColorScheme.primary,
        secondary = EspColorScheme.secondary,
        tertiary = EspColorScheme.tertiary
    )
}

data class EspColorSchemeColors(
    val surface: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
)

@Composable
fun EscanQRAdminTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
