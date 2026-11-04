package com.zero.minidrama.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ZeroColors = darkColorScheme(
    primary = Color(0xFFFF3B5C),
    onPrimary = Color.White,
    secondary = Color(0xFFFF6B81),
    background = Color(0xFF0A0A0C),
    surface = Color(0xFF141418),
    onBackground = Color(0xFFF4F4F6),
    onSurface = Color(0xFFF4F4F6),
    surfaceVariant = Color(0xFF1C1C22),
    outline = Color(0x33FFFFFF)
)

@Composable
fun ZeroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ZeroColors,
        content = content
    )
}
