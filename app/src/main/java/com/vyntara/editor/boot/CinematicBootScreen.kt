import com.vyntara.editor.ui.theme.LocalAppTheme
import com.vyntara.editor.home.LocalLanguage
import com.vyntara.editor.i18n.Strings
package com.vyntara.editor.boot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Orquestador visual del arranque.
 * Delega el campo espacial a [BootSpatialField] y el logo a [BootLogo].
 * No contiene la lógica de comprobaciones (eso vive en BootViewModel).
 */
@Composable
fun CinematicBootScreen(
    phase: CinematicPhase,
    statusMessage: String,
    canSkip: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "bootPulse")
    val ambientPulse by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient"
    )

    val phaseProgress = remember { Animatable(0f) }

    LaunchedEffect(phase) {
        val target = when (phase) {
            CinematicPhase.BLACK -> 0f
            CinematicPhase.INIT_SPACE -> 0.18f
            CinematicPhase.CORE_FORMATION -> 0.42f
            CinematicPhase.LOGO_REVEAL -> 0.68f
            CinematicPhase.ENGINE_PULSE -> 0.82f
            CinematicPhase.STATUS_HOLD -> 0.92f
            CinematicPhase.TRANSITION_OUT -> 1f
            CinematicPhase.FINISHED -> 1f
        }
        phaseProgress.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = if (phase == CinematicPhase.TRANSITION_OUT) 550 else 900,
                easing = LinearEasing
            )
        )
    }

    val logoAlpha = when (phase) {
        CinematicPhase.LOGO_REVEAL,
        CinematicPhase.ENGINE_PULSE,
        CinematicPhase.STATUS_HOLD -> 1f
        CinematicPhase.TRANSITION_OUT -> 0.35f
        else -> 0f
    }
    val logoScale = when (phase) {
        CinematicPhase.LOGO_REVEAL -> 1f
        CinematicPhase.ENGINE_PULSE,
        CinematicPhase.STATUS_HOLD -> 1.02f
        CinematicPhase.TRANSITION_OUT -> 0.55f
        else -> 0.6f
    }

    val showLogo = phase == CinematicPhase.LOGO_REVEAL ||
        phase == CinematicPhase.ENGINE_PULSE ||
        phase == CinematicPhase.STATUS_HOLD ||
        phase == CinematicPhase.TRANSITION_OUT

    val showStatus = phase == CinematicPhase.STATUS_HOLD ||
        phase == CinematicPhase.ENGINE_PULSE ||
        phase == CinematicPhase.TRANSITION_OUT

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.colors.background)
    ) {
        BootSpatialField(
            phase = phase,
            phaseProgress = phaseProgress.value,
            ambientPulse = ambientPulse,
            modifier = Modifier.fillMaxSize()
        )

        if (showLogo) {
            BootLogo(
                alpha = logoAlpha,
                scale = logoScale,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (showStatus && statusMessage.isNotBlank()) {
            Text(
                text = statusMessage,
                color = LocalAppTheme.current.colors.muted.copy(alpha = 0.85f),
                fontSize = 12.sp,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp)
                    .padding(horizontal = 32.dp)
            )
        }

        if (canSkip &&
            phase != CinematicPhase.TRANSITION_OUT &&
            phase != CinematicPhase.FINISHED
        ) {
            Text(
                text = Strings.t(LocalLanguage.current, "skip"),
                color = LocalAppTheme.current.colors.text.copy(alpha = 0.45f),
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onSkip() }
                    .padding(8.dp)
            )
        }
    }
}
