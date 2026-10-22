package com.vyntara.editor.project

import com.vyntara.editor.capabilities.DeviceProfile

/**
 * Derives which resolutions / FPS the UI should allow based on the
 * real device profile from CapabilityChecker.
 */
object CapabilityLimits {

    fun maxLongSide(profile: DeviceProfile?): Int {
        if (profile == null) return 1080
        // Prefer codec-reported max, then texture, then preview cap
        val codecMax = profile.supportedVideoEncoders
            .maxOfOrNull { maxOf(it.maxWidth, it.maxHeight) } ?: 0
        val tex = profile.maxTextureSize
        val preview = profile.previewResolutionCap
        val candidates = listOf(codecMax, tex, preview * 2, 1920).filter { it > 0 }
        return candidates.maxOrNull()?.coerceAtMost(2160) ?: 1080
    }

    fun isResolutionAllowed(longSide: Int, profile: DeviceProfile?): Boolean {
        return longSide <= maxLongSide(profile)
    }

    fun isFpsAllowed(fps: Int, profile: DeviceProfile?): Boolean {
        // Without reliable FPS caps from MediaCodec on all devices, use conservative
        // heuristics from memory + hardware decode preference.
        if (profile == null) return fps <= 30
        val mem = profile.availableMemoryMb
        val hw = profile.preferHardwareEncode
        return when {
            fps <= 30 -> true
            fps <= 50 -> hw && mem >= 2000
            else -> hw && mem >= 3500 && profile.maxTextureSize >= 2048
        }
    }

    fun resolutionBlockReason(longSide: Int, profile: DeviceProfile?): String? {
        if (isResolutionAllowed(longSide, profile)) return null
        val max = maxLongSide(profile)
        return "Este dispositivo admite hasta aproximadamente ${max}p"
    }

    fun fpsBlockReason(fps: Int, profile: DeviceProfile?): String? {
        if (isFpsAllowed(fps, profile)) return null
        return when {
            profile == null -> "No se pudo verificar el rendimiento; usa 30 fps o menos"
            !profile.preferHardwareEncode -> "Sin codificación por hardware fiable para $fps fps"
            profile.availableMemoryMb < 3500 -> "Memoria insuficiente para $fps fps de forma estable"
            else -> "Este dispositivo puede no mantener $fps fps de forma estable"
        }
    }
}
