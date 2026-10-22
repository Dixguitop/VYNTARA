package com.vyntara.editor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val VyntaraBlack = Color(0xFF050508)
val VyntaraSurface = Color(0xFF0E0E14)
val VyntaraCard = Color(0xFF16161F)
val VyntaraViolet = Color(0xFF7B5CFF)
val VyntaraCyan = Color(0xFF00E5FF)
val VyntaraText = Color(0xFFE8E8F0)
val VyntaraMuted = Color(0xFF8B8BA0)
val VyntaraGreen = Color(0xFF22C55E)
val VyntaraBlue = Color(0xFF3B82F6)

private val DarkScheme = darkColorScheme(
    primary = VyntaraViolet,
    secondary = VyntaraCyan,
    background = VyntaraBlack,
    surface = VyntaraSurface,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = VyntaraText,
    onSurface = VyntaraText,
    surfaceVariant = VyntaraCard,
    onSurfaceVariant = VyntaraMuted
)

@Composable
fun VyntaraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
