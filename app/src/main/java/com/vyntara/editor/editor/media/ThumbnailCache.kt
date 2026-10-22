package com.vyntara.editor.editor.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LRU thumbnails for timeline clips. Generated off main thread.
 */
object ThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>(24) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun get(context: Context, uri: String, timeMs: Long = 0L): Bitmap? {
        val key = "$uri@$timeMs"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bmp = MediaProbe.decodePreviewFrame(context, Uri.parse(uri), timeMs, maxSide = 96)
            if (bmp != null) cache.put(key, bmp)
            bmp
        }
    }

    fun clear() = cache.evictAll()
}
