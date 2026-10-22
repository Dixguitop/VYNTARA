package com.vyntara.editor.editor

import kotlin.math.pow

/**
 * Professional keyframe evaluation:
 * - LINEAR / EASE_* cubic ease
 * - BEZIER-like using outStrength/inStrength as cubic handle magnitudes
 * Same function used by preview and export.
 */
object KeyframeEval {

    fun value(keys: List<Keyframe>?, timeSec: Float, fallback: Float): Float {
        if (keys.isNullOrEmpty()) return fallback
        val sorted = keys.sortedBy { it.timeSec }
        if (timeSec <= sorted.first().timeSec) return sorted.first().value
        if (timeSec >= sorted.last().timeSec) return sorted.last().value
        for (i in 0 until sorted.lastIndex) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (timeSec >= a.timeSec && timeSec <= b.timeSec) {
                val span = (b.timeSec - a.timeSec).coerceAtLeast(1e-5f)
                val u = ((timeSec - a.timeSec) / span).coerceIn(0f, 1f)
                val t = when (a.ease) {
                    Ease.LINEAR -> u
                    Ease.EASE_IN -> u * u * u
                    Ease.EASE_OUT -> 1f - (1f - u).pow(3)
                    Ease.EASE_IN_OUT -> cubicBezierTime(u, a.outStrength, b.inStrength)
                }
                return a.value + (b.value - a.value) * t
            }
        }
        return fallback
    }

    /**
     * Approximate cubic ease between keys using handle strengths.
     * P0=0, P1=outStrength, P2=1-inStrength, P3=1 on the unit interval.
     */
    private fun cubicBezierTime(u: Float, outS: Float, inS: Float): Float {
        val p1 = outS.coerceIn(0f, 1f)
        val p2 = (1f - inS.coerceIn(0f, 1f))
        val inv = 1f - u
        return inv * inv * inv * 0f +
            3f * inv * inv * u * p1 +
            3f * inv * u * u * p2 +
            u * u * u * 1f
    }

    fun resolveClip(clip: EditorClip, timeSec: Float): EditorClip {
        if (clip.keyframes.isEmpty()) return clip
        val kf = clip.keyframes
        var opacity = value(kf["opacity"], timeSec, clip.opacity)
        // Fade in / out envelopes
        if (clip.fadeInSec > 0.001f) {
            val local = timeSec - clip.startSec
            if (local < clip.fadeInSec) opacity *= (local / clip.fadeInSec).coerceIn(0f, 1f)
        }
        if (clip.fadeOutSec > 0.001f) {
            val toEnd = clip.endSec - timeSec
            if (toEnd < clip.fadeOutSec) opacity *= (toEnd / clip.fadeOutSec).coerceIn(0f, 1f)
        }
        return clip.copy(
            posX = value(kf["posX"], timeSec, clip.posX),
            posY = value(kf["posY"], timeSec, clip.posY),
            scaleX = value(kf["scaleX"], timeSec, clip.scaleX),
            scaleY = value(kf["scaleY"], timeSec, clip.scaleY),
            rotation = value(kf["rotation"], timeSec, clip.rotation),
            opacity = opacity.coerceIn(0f, 1f),
            blurRadius = value(kf["blurRadius"], timeSec, clip.blurRadius),
            volume = value(kf["volume"], timeSec, clip.volume)
        )
    }
}
