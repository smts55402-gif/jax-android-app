package com.jax.automation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JaxColorScheme = darkColorScheme(
    primary = Color(0xFF00E5A0),
    onPrimary = Color(0xFF06231A),
    primaryContainer = Color(0xFF0E3D2C),
    onPrimaryContainer = Color(0xFFB8F5DC),
    secondary = Color(0xFF4DA3FF),
    onSecondary = Color(0xFF0A1B33),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE8ECF3),
    surface = Color(0xFF11151D),
    onSurface = Color(0xFFE8ECF3),
    surfaceVariant = Color(0xFF1A2130),
    onSurfaceVariant = Color(0xFFAEB8CC),
    error = Color(0xFFFF5A5A),
    onError = Color(0xFF2B0B0B)
)

@Composable
fun JaxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JaxColorScheme,
        typography = Typography(),
        content = content
    )
}
