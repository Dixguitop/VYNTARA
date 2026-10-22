package com.vyntara.editor.project

import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import java.util.UUID

/** Aspect ratios available when creating a project. */
enum class AspectPreset(val label: String, val widthRatio: Int, val heightRatio: Int) {
    RATIO_16_9("16:9", 16, 9),
    RATIO_9_16("9:16", 9, 16),
    RATIO_4_5("4:5", 4, 5),
    RATIO_1_1("1:1", 1, 1),
    RATIO_4_3("4:3", 4, 3),
    CUSTOM("Personalizado", 0, 0);

    fun computeSize(longSide: Int): Pair<Int, Int> {
        if (this == CUSTOM) return longSide to longSide
        val w = widthRatio
        val h = heightRatio
        return if (w >= h) {
            longSide to ((longSide.toLong() * h) / w).toInt().coerceAtLeast(2)
        } else {
            ((longSide.toLong() * w) / h).toInt().coerceAtLeast(2) to longSide
        }
    }
}

/** Named resolution steps (long side). */
enum class ResolutionPreset(val label: String, val longSide: Int) {
    P180("180p", 180),
    P240("240p", 240),
    P360("360p", 360),
    P480("480p", 480),
    P720("720p", 720),
    P1080("1080p", 1080),
    P1440("1440p", 1440),
    P2160("2160p", 2160)
}

val FPS_OPTIONS = listOf(12, 15, 24, 25, 30, 48, 50, 60)

enum class BackgroundPreset(val label: String, val color: Color?) {
    BLACK("Negro", Color(0xFF000000)),
    WHITE("Blanco", Color(0xFFFFFFFF)),
    LIGHT_GRAY("Gris claro", Color(0xFFD1D5DB)),
    GREEN("Verde", Color(0xFF22C55E)),
    BLUE("Azul", Color(0xFF3B82F6)),
    TRANSPARENT("Transparente", Color.Transparent),
    CUSTOM("Personalizado", null)
}

data class ProjectSettings(
    val title: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val aspect: AspectPreset,
    val background: BackgroundPreset,
    val customBackgroundArgb: Long = 0xFF000000
) {
    val resolutionLabel: String
        get() {
            val longSide = maxOf(width, height)
            return ResolutionPreset.entries.find { it.longSide == longSide }?.label
                ?: "${width}×${height}"
        }

    val aspectLabel: String
        get() = if (aspect == AspectPreset.CUSTOM) "${width}:${height}" else aspect.label
}

/**
 * Lightweight project summary for the projects list (Alight Motion style).
 */
data class ProjectSummary(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val backgroundArgb: Long = 0xFF000000,
    val thumbnailPath: String? = null
) {
    val aspectLabel: String
        get() {
            val g = gcd(width, height)
            return if (g > 0) "${width / g}:${height / g}" else "—"
        }

    val resolutionLabel: String
        get() {
            val longSide = maxOf(width, height)
            return ResolutionPreset.entries.find { it.longSide == longSide }?.label
                ?: "${height}p"
        }

    val durationLabel: String
        get() {
            val totalSec = (durationMs / 1000).toInt()
            val m = totalSec / 60
            val s = totalSec % 60
            return "%02d:%02d:%02d".format(m / 60, m % 60, s)
        }

    val sizeLabel: String
        get() = when {
            fileSizeBytes < 1024 -> "${fileSizeBytes}b"
            fileSizeBytes < 1024 * 1024 -> "%.1fKB".format(fileSizeBytes / 1024.0)
            else -> "%.1fMB".format(fileSizeBytes / (1024.0 * 1024.0))
        }

    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("width", width)
        put("height", height)
        put("fps", fps)
        put("durationMs", durationMs)
        put("fileSizeBytes", fileSizeBytes)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("backgroundArgb", backgroundArgb)
        put("thumbnailPath", thumbnailPath ?: "")
    }.toString()

    companion object {
        fun fromJson(json: String): ProjectSummary? = try {
            val o = JSONObject(json)
            ProjectSummary(
                id = o.getString("id"),
                title = o.getString("title"),
                width = o.getInt("width"),
                height = o.getInt("height"),
                fps = o.getInt("fps"),
                durationMs = o.optLong("durationMs"),
                fileSizeBytes = o.optLong("fileSizeBytes"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                backgroundArgb = o.optLong("backgroundArgb", 0xFF000000),
                thumbnailPath = o.optString("thumbnailPath").ifBlank { null }
            )
        } catch (_: Exception) {
            null
        }

        private fun gcd(a: Int, b: Int): Int {
            var x = kotlin.math.abs(a)
            var y = kotlin.math.abs(b)
            while (y != 0) {
                val t = y
                y = x % y
                x = t
            }
            return x
        }
    }
}
