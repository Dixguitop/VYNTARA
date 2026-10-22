package com.vyntara.editor.boot

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Paleta del campo espacial del boot (compartida solo con este módulo visual). */
private val ElectricViolet = Color(0xFF7B5CFF)
private val ColdBlue = Color(0xFF3B82F6)
private val LuminousCyan = Color(0xFF00E5FF)
private val SoftWhite = Color(0xFFE8E8F0)

/**
 * Capa de geometría / partículas del arranque.
 * Separada de la orquestación de fases y del logo.
 */
@Composable
fun BootSpatialField(
    phase: CinematicPhase,
    phaseProgress: Float,
    ambientPulse: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val p = phaseProgress
        val t = ambientPulse

        // Atmósfera radial
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ElectricViolet.copy(alpha = 0.07f * p * t),
                    ColdBlue.copy(alpha = 0.04f * p),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = minOf(w, h) * 0.55f
            ),
            radius = minOf(w, h) * 0.55f,
            center = Offset(cx, cy)
        )

        // Partículas de profundidad (semilla fija = estable entre frames)
        val particleCount = 48
        val rng = Random(42)
        for (i in 0 until particleCount) {
            val depth = 0.15f + rng.nextFloat() * 0.85f
            val angle = rng.nextFloat() * (2 * PI).toFloat()
            val baseR = minOf(w, h) * (0.08f + depth * 0.42f)
            val converge = (1f - p * 0.85f).coerceIn(0.12f, 1f)
            val r = baseR * converge
            val x = cx + cos(angle) * r
            val y = cy + sin(angle) * r
            val alpha = (0.15f + depth * 0.55f) * p.coerceIn(0f, 1f) * (0.5f + 0.5f * t)
            val radius = (0.8f + depth * 2.2f) * (if (p > 0.7f) 1.1f else 1f)
            drawCircle(
                color = when {
                    depth > 0.7f -> LuminousCyan.copy(alpha = alpha * 0.9f)
                    depth > 0.4f -> ElectricViolet.copy(alpha = alpha)
                    else -> SoftWhite.copy(alpha = alpha * 0.5f)
                },
                radius = radius,
                center = Offset(x, y)
            )
        }

        // Líneas geométricas
        val lineCount = 12
        for (i in 0 until lineCount) {
            val a = (i / lineCount.toFloat()) * (2 * PI).toFloat() + p * 0.4f
            val inner = minOf(w, h) * (0.06f + (1f - p) * 0.12f)
            val outer = minOf(w, h) * (0.22f + p * 0.18f)
            val start = Offset(cx + cos(a) * inner, cy + sin(a) * inner)
            val end = Offset(cx + cos(a) * outer, cy + sin(a) * outer)
            val lineAlpha = (0.25f + 0.45f * p) * (0.6f + 0.4f * t)
            drawLine(
                color = ElectricViolet.copy(alpha = lineAlpha * 0.7f),
                start = start,
                end = end,
                strokeWidth = 1.2f,
                cap = StrokeCap.Round
            )
        }

        // Anillos orbitales
        if (p > 0.12f) {
            val ringProgress = ((p - 0.12f) / 0.88f).coerceIn(0f, 1f)
            listOf(0.18f, 0.28f, 0.38f).forEachIndexed { idx, frac ->
                val radius = minOf(w, h) * frac * (0.7f + ringProgress * 0.35f)
                val ringAlpha = (0.15f + idx * 0.05f) * ringProgress * t
                drawCircle(
                    color = when (idx) {
                        0 -> LuminousCyan.copy(alpha = ringAlpha)
                        1 -> ElectricViolet.copy(alpha = ringAlpha)
                        else -> ColdBlue.copy(alpha = ringAlpha * 0.8f)
                    },
                    radius = radius,
                    center = Offset(cx, cy),
                    style = Stroke(
                        width = 1.1f,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(8f, 12f),
                            phase = t * 40f + idx * 10f
                        )
                    )
                )
            }
        }

        // Núcleo en diamante (fase B+)
        if (p > 0.3f) {
            val coreP = ((p - 0.3f) / 0.4f).coerceIn(0f, 1f)
            val s = minOf(w, h) * 0.11f * coreP
            val pts = listOf(
                Offset(cx, cy - s),
                Offset(cx + s, cy),
                Offset(cx, cy + s),
                Offset(cx - s, cy)
            )
            for (i in pts.indices) {
                drawLine(
                    color = LuminousCyan.copy(alpha = 0.55f * coreP),
                    start = pts[i],
                    end = pts[(i + 1) % pts.size],
                    strokeWidth = 1.6f,
                    cap = StrokeCap.Round
                )
            }
        }

        // Onda de pulso del motor (fase D)
        if (phase == CinematicPhase.ENGINE_PULSE || phase == CinematicPhase.STATUS_HOLD) {
            val waveR = minOf(w, h) * (0.15f + t * 0.35f)
            drawCircle(
                color = LuminousCyan.copy(alpha = (1f - t) * 0.25f),
                radius = waveR,
                center = Offset(cx, cy),
                style = Stroke(width = 2.5f)
            )
        }
    }
}
