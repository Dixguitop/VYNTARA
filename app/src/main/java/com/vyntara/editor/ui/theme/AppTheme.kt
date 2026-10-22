package com.vyntara.editor.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vyntara.editor.settings.AppThemeId
import com.vyntara.editor.settings.CardCorners
import com.vyntara.editor.settings.UiDensity

data class VyntaraColors(
    val background: Color,
    val surface: Color,
    val card: Color,
    val primary: Color,
    val secondary: Color,
    val text: Color,
    val muted: Color,
    val danger: Color,
    val success: Color
)

data class VyntaraMetrics(
    val cardRadius: Dp,
    val contentPadding: Dp,
    val sectionGap: Dp
)

data class AppThemeState(
    val colors: VyntaraColors,
    val metrics: VyntaraMetrics,
    val themeId: AppThemeId
)

val LocalAppTheme = staticCompositionLocalOf {
    AppThemeState(
        colors = themeColors(AppThemeId.CRIMSON, null),
        metrics = metricsFor(UiDensity.COMFORTABLE, CardCorners.ROUNDED),
        themeId = AppThemeId.CRIMSON
    )
}

fun themeColors(id: AppThemeId, accentOverride: Long?): VyntaraColors {
    val base = when (id) {
        AppThemeId.CRIMSON -> VyntaraColors(
            background = Color(0xFF080808),
            surface = Color(0xFF121212),
            card = Color(0xFF1A1A1A),
            primary = Color(0xFFE11D2E),
            secondary = Color(0xFFFF4D5A),
            text = Color(0xFFF5F5F5),
            muted = Color(0xFF9A9A9A),
            danger = Color(0xFFFF6B6B),
            success = Color(0xFF22C55E)
        )
        AppThemeId.VIOLET -> VyntaraColors(
            background = Color(0xFF050508),
            surface = Color(0xFF0E0E14),
            card = Color(0xFF16161F),
            primary = Color(0xFF7B5CFF),
            secondary = Color(0xFF00E5FF),
            text = Color(0xFFE8E8F0),
            muted = Color(0xFF8B8BA0),
            danger = Color(0xFFF87171),
            success = Color(0xFF22C55E)
        )
        AppThemeId.OCEAN -> VyntaraColors(
            background = Color(0xFF061018),
            surface = Color(0xFF0C1A24),
            card = Color(0xFF132838),
            primary = Color(0xFF0EA5E9),
            secondary = Color(0xFF38BDF8),
            text = Color(0xFFE8F4FC),
            muted = Color(0xFF7A9BB0),
            danger = Color(0xFFF87171),
            success = Color(0xFF34D399)
        )
        AppThemeId.EMERALD -> VyntaraColors(
            background = Color(0xFF06140C),
            surface = Color(0xFF0C1C12),
            card = Color(0xFF132A1A),
            primary = Color(0xFF10B981),
            secondary = Color(0xFF34D399),
            text = Color(0xFFE8F8F0),
            muted = Color(0xFF7A9E8A),
            danger = Color(0xFFF87171),
            success = Color(0xFF22C55E)
        )
        AppThemeId.AMBER -> VyntaraColors(
            background = Color(0xFF120E06),
            surface = Color(0xFF1A140A),
            card = Color(0xFF241C10),
            primary = Color(0xFFF59E0B),
            secondary = Color(0xFFFBBF24),
            text = Color(0xFFFFF8EB),
            muted = Color(0xFFA8946E),
            danger = Color(0xFFF87171),
            success = Color(0xFF22C55E)
        )
    }
    if (accentOverride == null || accentOverride == 0L) return base
    val accent = Color((accentOverride and 0xFFFFFFFFL).toULong())
    return base.copy(primary = accent, secondary = accent.copy(alpha = 0.85f))
}

fun metricsFor(density: UiDensity, corners: CardCorners): VyntaraMetrics {
    val radius = when (corners) {
        CardCorners.SHARP -> 6.dp
        CardCorners.ROUNDED -> 16.dp
        CardCorners.PILL -> 24.dp
    }
    return when (density) {
        UiDensity.COMPACT -> VyntaraMetrics(radius, 12.dp, 8.dp)
        UiDensity.COMFORTABLE -> VyntaraMetrics(radius, 16.dp, 12.dp)
        UiDensity.SPACIOUS -> VyntaraMetrics(radius, 20.dp, 16.dp)
    }
}

@Composable
fun ProvideAppTheme(
    themeId: AppThemeId,
    density: UiDensity,
    corners: CardCorners,
    accentOverride: Long?,
    content: @Composable () -> Unit
) {
    val state = AppThemeState(
        colors = themeColors(themeId, accentOverride),
        metrics = metricsFor(density, corners),
        themeId = themeId
    )
    CompositionLocalProvider(LocalAppTheme provides state) {
        content()
    }
}
