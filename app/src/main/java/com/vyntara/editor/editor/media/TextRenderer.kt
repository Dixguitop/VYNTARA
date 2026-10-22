package com.vyntara.editor.editor.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.vyntara.editor.editor.EditorClip

object TextRenderer {
    fun render(clip: EditorClip, width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(64), height.coerceAtLeast(64), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = clip.textColorArgb
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val baseSize = (minOf(width, height) * 0.08f * clip.scaleX * clip.textSize).coerceIn(18f, 200f)
        paint.textSize = baseSize
        paint.alpha = (clip.opacity * 255).toInt().coerceIn(0, 255)
        val text = clip.textContent.ifBlank { clip.name.ifBlank { "Text" } }
        val x = width * clip.posX
        val y = height * clip.posY
        canvas.save()
        canvas.rotate(clip.rotation, x, y)
        paint.setShadowLayer(10f, 2f, 3f, Color.argb(180, 0, 0, 0))
        // Multi-line
        val lines = text.split('\n')
        val lineH = baseSize * 1.25f
        val startY = y - (lines.size - 1) * lineH / 2f
        lines.forEachIndexed { i, line ->
            canvas.drawText(line, x, startY + i * lineH, paint)
        }
        canvas.restore()
        return bmp
    }
}
