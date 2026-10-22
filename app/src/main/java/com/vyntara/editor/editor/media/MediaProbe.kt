package com.vyntara.editor.editor.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real media probing via MediaMetadataRetriever / ContentResolver.
 * No invented durations: if metadata is missing, durationSec stays null.
 */
data class MediaProbeResult(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val isAudio: Boolean,
    val isImage: Boolean,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val rotation: Int?,
    val hasAudio: Boolean?
)

object MediaProbe {

    suspend fun probe(context: Context, uri: Uri): MediaProbeResult = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri) ?: ""
        val name = queryDisplayName(context, uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "media"
        val isVideo = mime.startsWith("video")
        val isAudio = mime.startsWith("audio")
        val isImage = mime.startsWith("image")

        var durationMs: Long? = null
        var width: Int? = null
        var height: Int? = null
        var rotation: Int? = null
        var hasAudio: Boolean? = null

        if (isVideo || isAudio) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull()
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()
                rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull()
                val hasAudioStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                hasAudio = hasAudioStr == "yes"
            } catch (_: Exception) {
                // leave nulls — caller must not invent values
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }

        if (isImage) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, opts)
                    width = opts.outWidth.takeIf { it > 0 }
                    height = opts.outHeight.takeIf { it > 0 }
                }
            } catch (_: Exception) {}
        }

        MediaProbeResult(
            uri = uri.toString(),
            displayName = name,
            mimeType = mime,
            isVideo = isVideo,
            isAudio = isAudio,
            isImage = isImage,
            durationMs = durationMs,
            width = width,
            height = height,
            rotation = rotation,
            hasAudio = hasAudio
        )
    }

    /**
     * Decode a downscaled still for image clips or a video keyframe near timeMs.
     * Runs on IO; result may be null if decode fails.
     */
    suspend fun decodePreviewFrame(
        context: Context,
        uri: Uri,
        timeMs: Long,
        maxSide: Int = 720
    ): Bitmap? = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri) ?: return@withContext null
        try {
            when {
                mime.startsWith("image") -> decodeImageScaled(context, uri, maxSide)
                mime.startsWith("video") -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        val frame = retriever.getFrameAtTime(
                            timeMs * 1000L,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                        frame?.let { scaleBitmap(it, maxSide) }
                    } finally {
                        try { retriever.release() } catch (_: Exception) {}
                    }
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeImageScaled(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        val w = bounds.outWidth
        val h = bounds.outHeight
        while (w / sample > maxSide * 2 || h / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        return scaleBitmap(bmp, maxSide)
    }

    private fun scaleBitmap(src: Bitmap, maxSide: Int): Bitmap {
        val w = src.width
        val h = src.height
        val scale = maxSide.toFloat() / maxOf(w, h).toFloat()
        if (scale >= 1f) return src
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
        } catch (_: Exception) {
            null
        }
    }
}
