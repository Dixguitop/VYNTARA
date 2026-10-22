package com.vyntara.editor.editor.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.vyntara.editor.editor.EditorClip

object SolidRenderer {
    fun render(clip: EditorClip, width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(16), height.coerceAtLeast(16), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val alpha = (clip.opacity * 255).toInt().coerceIn(0, 255)
        // Use textColorArgb as solid color carrier
        val c = clip.textColorArgb
        paint.color = (alpha shl 24) or (c and 0x00FFFFFF)
        val w = width * clip.scaleX.coerceIn(0.05f, 2f)
        val h = height * clip.scaleY.coerceIn(0.05f, 2f)
        val left = width * clip.posX - w / 2f
        val top = height * clip.posY - h / 2f
        canvas.save()
        canvas.rotate(clip.rotation, width * clip.posX, height * clip.posY)
        canvas.drawRoundRect(RectF(left, top, left + w, top + h), 8f, 8f, paint)
        canvas.restore()
        return bmp
    }
}
