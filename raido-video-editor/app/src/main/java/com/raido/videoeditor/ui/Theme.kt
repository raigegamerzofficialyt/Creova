package com.raido.videoeditor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RaidoColors = darkColorScheme(
    primary = Color(0xFF7C5CFF),
    onPrimary = Color.White,
    secondary = Color(0xFF2EE6A6),
    background = Color(0xFF0E0F13),
    onBackground = Color(0xFFECEDF2),
    surface = Color(0xFF171923),
    onSurface = Color(0xFFECEDF2),
    surfaceVariant = Color(0xFF222534),
    onSurfaceVariant = Color(0xFF9EA3B5),
    error = Color(0xFFFF5C6C),
)

@Composable
fun RaidoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RaidoColors, content = content)
}
