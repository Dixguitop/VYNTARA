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
import kotlin.math.max
import kotlin.math.min

/**
 * Bitmap effects used by preview AND export (parity).
 * Each READY catalog effect maps to a real pixel operation here.
 */
object EffectRenderer {

    fun apply(source: Bitmap, clip: EditorClip): Bitmap {
        if (source.isRecycled) return source
        var bmp = source.copy(Bitmap.Config.ARGB_8888, true) ?: return source

        if (clip.opacity < 0.999f) {
            bmp = applyOpacity(bmp, clip.opacity.coerceIn(0f, 1f))
        }

        val effects = clip.effects.filter { it.enabled }

        val blurRadius = when {
            clip.blurRadius > 0.5f -> clip.blurRadius
            clip.smartBlur -> 10f
            else -> effects.filter { it.name.contains("Blur", true) }
                .maxOfOrNull { it.radius } ?: 0f
        }
        if (blurRadius > 0.5f) {
            bmp = boxBlur(bmp, blurRadius.coerceAtMost(16f).toInt().coerceAtLeast(1))
        }

        for (e in effects) {
            val n = e.name.lowercase()
            when {
                "vignette" in n -> bmp = applyVignette(bmp, e.intensity)
                "glow" in n -> bmp = applyGlow(bmp, e.intensity)
                "contrast" in n -> bmp = applyContrast(bmp, e.intensity)
                "bright" in n || "exposure" in n -> bmp = applyBrightness(bmp, e.intensity)
                "satur" in n -> bmp = applySaturation(bmp, e.intensity)
                "negative" in n || "invert" in n -> bmp = applyInvert(bmp)
                "pixel" in n -> bmp = applyPixelate(bmp, (4 + e.intensity * 20).toInt())
                "sharpen" in n || "nitidez" in n -> bmp = applySharpen(bmp, e.intensity)
                "gray" in n || "mono" in n -> bmp = applySaturation(bmp, 0f)
                "sepia" in n -> bmp = applySepia(bmp, e.intensity)
                "poster" in n -> bmp = applyPosterize(bmp, (2 + e.intensity * 6).toInt())
                "grain" in n || "film" in n -> bmp = applyGrain(bmp, e.intensity)
                "temperature" in n || "warm" in n -> bmp = applyTemperature(bmp, e.intensity)
                "duotone" in n -> bmp = applyDuotone(bmp, e.intensity)
                "edge" in n || "sobel" in n -> bmp = applyEdges(bmp, e.intensity)
                "hue" in n -> bmp = applyHue(bmp, e.intensity)
                "tint" in n -> bmp = applyTint(bmp, e.intensity)
                "fade" in n || "vintage" in n -> bmp = applyVintage(bmp, e.intensity)
                "chroma" in n || "aberration" in n -> bmp = applyChromatic(bmp, e.intensity)
                "mirror" in n -> bmp = applyMirror(bmp, e.intensity)
                "chroma key" in n || "chromakey" in n || "key" in n && "chroma" in n -> bmp = applyChromaKey(bmp, e.intensity)
                "green" in n && "screen" in n -> bmp = applyChromaKey(bmp, e.intensity)
            }
        }
        return bmp
    }

    private fun applySepia(src: Bitmap, intensity: Float): Bitmap {
        val i = intensity.coerceIn(0f, 1f)
        val m = ColorMatrix(
            floatArrayOf(
                0.393f + 0.607f * (1 - i), 0.769f * i, 0.189f * i, 0f, 0f,
                0.349f * i, 0.686f + 0.314f * (1 - i), 0.168f * i, 0f, 0f,
                0.272f * i, 0.534f * i, 0.131f + 0.869f * (1 - i), 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return filterMatrix(src, m)
    }

    private fun applyPosterize(src: Bitmap, levels: Int): Bitmap {
        val lv = levels.coerceIn(2, 8)
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val step = 255 / (lv - 1)
        for (i in px.indices) {
            val c = px[i]
            fun q(v: Int) = ((v / step) * step).coerceIn(0, 255)
            px[i] = (c and 0xFF000000.toInt()) or
                (q((c shr 16) and 0xFF) shl 16) or
                (q((c shr 8) and 0xFF) shl 8) or
                q(c and 0xFF)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        if (src != out) src.recycle()
        return out
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

    private fun applyContrast(src: Bitmap, intensity: Float): Bitmap {
        // intensity 0.5 = neutral, 0 = flat, 1 = strong
        val c = 0.5f + intensity * 1.5f
        val t = (1f - c) / 2f * 255f
        val m = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return filterMatrix(src, m)
    }

    private fun applyBrightness(src: Bitmap, intensity: Float): Bitmap {
        val b = (intensity - 0.5f) * 100f
        val m = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, b,
                0f, 1f, 0f, 0f, b,
                0f, 0f, 1f, 0f, b,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return filterMatrix(src, m)
    }

    private fun applySaturation(src: Bitmap, intensity: Float): Bitmap {
        val m = ColorMatrix()
        m.setSaturation(intensity.coerceIn(0f, 2f))
        return filterMatrix(src, m)
    }

    private fun applyInvert(src: Bitmap): Bitmap {
        val m = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return filterMatrix(src, m)
    }

    private fun filterMatrix(src: Bitmap, matrix: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(matrix)
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

    private fun applyPixelate(src: Bitmap, block: Int): Bitmap {
        val b = block.coerceIn(2, 48)
        val sw = max(1, src.width / b)
        val sh = max(1, src.height / b)
        val small = Bitmap.createScaledBitmap(src, sw, sh, false)
        val out = Bitmap.createScaledBitmap(small, src.width, src.height, false)
        small.recycle()
        if (src != out) src.recycle()
        return out
    }

    private fun applySharpen(src: Bitmap, intensity: Float): Bitmap {
        // Unsharp-like: original + amount * (original - blur)
        val blurred = boxBlur(src.copy(Bitmap.Config.ARGB_8888, true) ?: return src, 2)
        val w = src.width
        val h = src.height
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        src.getPixels(a, 0, w, 0, 0, w, h)
        blurred.getPixels(b, 0, w, 0, 0, w, h)
        val amount = intensity.coerceIn(0f, 1f) * 1.5f
        val outPx = IntArray(w * h)
        for (i in a.indices) {
            val ca = a[i]
            val cb = b[i]
            fun ch(shift: Int): Int {
                val va = (ca shr shift) and 0xFF
                val vb = (cb shr shift) and 0xFF
                return (va + amount * (va - vb)).toInt().coerceIn(0, 255)
            }
            outPx[i] = (ca and 0xFF000000.toInt()) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPx, 0, w, 0, 0, w, h)
        if (!blurred.isRecycled) blurred.recycle()
        if (src != out) src.recycle()
        return out
    }

    private fun applyGrain(src: Bitmap, intensity: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val amount = (intensity * 40).toInt().coerceIn(0, 60)
        var seed = 1234567
        for (i in px.indices) {
            seed = seed * 1103515245 + 12345
            val n = ((seed shr 16) and 0xFF) - 128
            val noise = n * amount / 128
            val c = px[i]
            fun ch(shift: Int) = ((c shr shift) and 0xFF + noise).coerceIn(0, 255)
            px[i] = (c and 0xFF000000.toInt()) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        if (src != out) src.recycle()
        return out
    }

    private fun applyChromaKey(src: Bitmap, intensity: Float): Bitmap {
        // Remove green-ish pixels; intensity = tolerance
        val tol = (40 + intensity * 80).toInt()
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            if (g > r + tol / 3 && g > b + tol / 3 && g > 60) {
                val excess = (g - maxOf(r, b)).coerceAtLeast(0)
                val a = (255 - excess * 255 / tol).coerceIn(0, 255)
                px[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        if (src != out) src.recycle()
        return out
    }

    private fun applyChromatic(src: Bitmap, intensity: Float): Bitmap {
        val shift = (intensity * 12).toInt().coerceIn(1, 24)
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val r = (px[y * w + (x + shift).coerceIn(0, w - 1)] shr 16) and 0xFF
                val g = (px[i] shr 8) and 0xFF
                val b = px[y * w + (x - shift).coerceIn(0, w - 1)] and 0xFF
                val a = (px[i] ushr 24) and 0xFF
                out[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        if (src != bmp) src.recycle()
        return bmp
    }

    private fun applyMirror(src: Bitmap, intensity: Float): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        val mid = w / 2
        for (y in 0 until h) {
            for (x in 0 until w) {
                val sx = if (intensity > 0.5f) {
                    if (x < mid) x else (mid - (x - mid + 1)).coerceIn(0, w - 1)
                } else {
                    if (x >= mid) x else (mid + (mid - x)).coerceIn(0, w - 1)
                }
                out[y * w + x] = px[y * w + sx]
            }
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        if (src != bmp) src.recycle()
        return bmp
    }

    private fun applyDuotone(src: Bitmap, intensity: Float): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val shadow = intArrayOf(20, 30, 80)
        val highlight = intArrayOf(255, 200, 120)
        val mix = intensity.coerceIn(0f, 1f)
        for (i in px.indices) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val l = (0.3f * r + 0.59f * g + 0.11f * b) / 255f
            val nr = (shadow[0] + (highlight[0] - shadow[0]) * l).toInt()
            val ng = (shadow[1] + (highlight[1] - shadow[1]) * l).toInt()
            val nb = (shadow[2] + (highlight[2] - shadow[2]) * l).toInt()
            val or = (r * (1 - mix) + nr * mix).toInt().coerceIn(0, 255)
            val og = (g * (1 - mix) + ng * mix).toInt().coerceIn(0, 255)
            val ob = (b * (1 - mix) + nb * mix).toInt().coerceIn(0, 255)
            px[i] = (c and 0xFF000000.toInt()) or (or shl 16) or (og shl 8) or ob
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        if (src != out) src.recycle()
        return out
    }

    private fun applyEdges(src: Bitmap, intensity: Float): Bitmap {
        val blurred = boxBlur(src.copy(Bitmap.Config.ARGB_8888, true) ?: src, 1)
        val w = src.width; val h = src.height
        val a = IntArray(w * h); val b = IntArray(w * h)
        src.getPixels(a, 0, w, 0, 0, w, h)
        blurred.getPixels(b, 0, w, 0, 0, w, h)
        val outPx = IntArray(w * h)
        val amount = intensity.coerceIn(0f, 1f)
        for (i in a.indices) {
            val ca = a[i]; val cb = b[i]
            fun d(shift: Int) = kotlin.math.abs(((ca shr shift) and 0xFF) - ((cb shr shift) and 0xFF))
            val edge = ((d(16) + d(8) + d(0)) / 3f * amount * 2f).toInt().coerceIn(0, 255)
            outPx[i] = (0xFF shl 24) or (edge shl 16) or (edge shl 8) or edge
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPx, 0, w, 0, 0, w, h)
        if (!blurred.isRecycled) blurred.recycle()
        if (src != out) src.recycle()
        return out
    }

    private fun applyHue(src: Bitmap, intensity: Float): Bitmap {
        val m = ColorMatrix()
        val degrees = (intensity - 0.5f) * 180f
        m.setRotate(0, degrees)
        m.setRotate(1, degrees)
        m.setRotate(2, degrees)
        // ColorMatrix has setRotate for axis - use combined
        val cm = ColorMatrix()
        cm.setRotate(0, degrees * 0.3f)
        return filterMatrix(src, cm)
    }

    private fun applyTint(src: Bitmap, intensity: Float): Bitmap {
        val t = intensity.coerceIn(0f, 1f)
        val m = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, t * 40f,
            0f, 1f, 0f, 0f, t * 10f,
            0f, 0f, 1f, 0f, t * 30f,
            0f, 0f, 0f, 1f, 0f
        ))
        return filterMatrix(src, m)
    }

    private fun applyVintage(src: Bitmap, intensity: Float): Bitmap {
        var bmp = applySepia(src, intensity * 0.7f)
        bmp = applyVignette(bmp, intensity * 0.5f)
        return applyContrast(bmp, 0.45f + intensity * 0.2f)
    }

    private fun applyTemperature(src: Bitmap, intensity: Float): Bitmap {
        // <0.5 cool, >0.5 warm
        val warm = (intensity - 0.5f) * 2f
        val m = ColorMatrix(
            floatArrayOf(
                1f + warm * 0.15f, 0f, 0f, 0f, warm * 10f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f - warm * 0.15f, 0f, -warm * 10f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return filterMatrix(src, m)
    }

    fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return src
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val temp = IntArray(w * h)
        val div = radius * 2 + 1

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
