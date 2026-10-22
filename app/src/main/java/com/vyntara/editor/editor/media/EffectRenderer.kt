package com.vyntara.editor.editor.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.vyntara.editor.editor.EditorClip
import com.vyntara.editor.editor.EffectInstance
import kotlin.math.max
import kotlin.math.min

/**
 * Bitmap-space effects used by BOTH monitor stills and export frames.
 * Not GPU shaders yet — CPU filters that guarantee preview/export parity.
 *
 * Implemented:
 * - Opacity
 * - Vignette
 * - Glow (soft bright pass approximation)
 * - Box blur (small radius, for Smart/Gaussian/Motion blur model flags)
 */
object EffectRenderer {

    fun apply(source: Bitmap, clip: EditorClip): Bitmap {
        if (source.isRecycled) return source
        var bmp = source.copy(Bitmap.Config.ARGB_8888, true) ?: return source

        // Opacity via color matrix
        if (clip.opacity < 0.999f) {
            bmp = applyOpacity(bmp, clip.opacity.coerceIn(0f, 1f))
        }

        val effects = clip.effects.filter { it.enabled }
        val blurRadius = when {
            clip.blurRadius > 0.5f -> clip.blurRadius
            clip.smartBlur -> 10f
            else -> effects.filter {
                it.name.contains("Blur", true)
            }.maxOfOrNull { it.radius } ?: 0f
        }
        if (blurRadius > 0.5f) {
            bmp = boxBlur(bmp, blurRadius.coerceAtMost(16f).toInt().coerceAtLeast(1))
        }

        if (effects.any { it.name.contains("Vignette", true) } ||
            effects.any { it.category == "Light" && it.name.contains("Vignette", true) }
        ) {
            val intensity = effects.find { it.name.contains("Vignette", true) }?.intensity ?: 0.4f
            bmp = applyVignette(bmp, intensity)
        }

        if (effects.any { it.name.contains("Glow", true) }) {
            val intensity = effects.find { it.name.contains("Glow", true) }?.intensity ?: 0.5f
            bmp = applyGlow(bmp, intensity)
        }

        return bmp
    }

    private fun applyOpacity(src: Bitmap, opacity: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val m = ColorMatrix()
        m.setScale(1f, 1f, 1f, opacity)
        paint.colorFilter = ColorMatrixColorFilter(m)
        canvas.drawBitmap(src, 0f, 0f, paint)
        if (src != out) src.recycle()
        return out
    }

    private fun applyVignette(src: Bitmap, intensity: Float): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true) ?: return src
        val canvas = Canvas(out)
        val cx = out.width / 2f
        val cy = out.height / 2f
        val radius = max(cx, cy) * 1.1f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(Color.TRANSPARENT, Color.argb((intensity * 180).toInt().coerceIn(0, 200), 0, 0, 0)),
            floatArrayOf(0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
        if (src != out) src.recycle()
        return out
    }

    private fun applyGlow(src: Bitmap, intensity: Float): Bitmap {
        // Soften and screen-blend approximation
        val blurred = boxBlur(src.copy(Bitmap.Config.ARGB_8888, true) ?: src, 6)
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(src, 0f, 0f, null)
        val paint = Paint().apply { alpha = (intensity * 120).toInt().coerceIn(0, 180) }
        canvas.drawBitmap(blurred, 0f, 0f, paint)
        if (blurred != src && !blurred.isRecycled) blurred.recycle()
        if (src != out) src.recycle()
        return out
    }

    /** Separable box blur — real pixel change, exportable. */
    fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return src
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val temp = IntArray(w * h)
        val div = radius * 2 + 1

        // Horizontal
        for (y in 0 until h) {
            var r = 0; var g = 0; var b = 0; var a = 0
            for (i in -radius..radius) {
                val x = min(w - 1, max(0, i))
                val c = pixels[y * w + x]
                a += c ushr 24 and 0xFF
                r += c shr 16 and 0xFF
                g += c shr 8 and 0xFF
                b += c and 0xFF
            }
            for (x in 0 until w) {
                temp[y * w + x] =
                    ((a / div) shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
                val remove = pixels[y * w + max(0, x - radius)]
                val add = pixels[y * w + min(w - 1, x + radius + 1)]
                a += (add ushr 24 and 0xFF) - (remove ushr 24 and 0xFF)
                r += (add shr 16 and 0xFF) - (remove shr 16 and 0xFF)
                g += (add shr 8 and 0xFF) - (remove shr 8 and 0xFF)
                b += (add and 0xFF) - (remove and 0xFF)
            }
        }
        // Vertical
        val outPx = IntArray(w * h)
        for (x in 0 until w) {
            var r = 0; var g = 0; var b = 0; var a = 0
            for (i in -radius..radius) {
                val y = min(h - 1, max(0, i))
                val c = temp[y * w + x]
                a += c ushr 24 and 0xFF
                r += c shr 16 and 0xFF
                g += c shr 8 and 0xFF
                b += c and 0xFF
            }
            for (y in 0 until h) {
                outPx[y * w + x] =
                    ((a / div) shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
                val remove = temp[max(0, y - radius) * w + x]
                val add = temp[min(h - 1, y + radius + 1) * w + x]
                a += (add ushr 24 and 0xFF) - (remove ushr 24 and 0xFF)
                r += (add shr 16 and 0xFF) - (remove shr 16 and 0xFF)
                g += (add shr 8 and 0xFF) - (remove shr 8 and 0xFF)
                b += (add and 0xFF) - (remove and 0xFF)
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPx, 0, w, 0, 0, w, h)
        if (src != out) src.recycle()
        return out
    }
}
