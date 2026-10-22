package com.vyntara.editor.capabilities

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.opengl.EGL14
import android.opengl.GLES20
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real multimedia capability inspection using Android APIs.
 * Runs off the main thread. Avoids aggressive benchmarks.
 */
class CapabilityChecker(private val context: Context) {

    companion object {
        private const val TAG = "Vyntara.Capabilities"

        private val VIDEO_MIME_TYPES = listOf(
            "video/avc",      // H.264
            "video/hevc",     // H.265
            "video/x-vnd.on2.vp9",
            "video/av01"      // AV1
        )

        private val AUDIO_MIME_TYPES = listOf(
            "audio/mp4a-latm", // AAC
            "audio/opus",
            "audio/vorbis"
        )
    }

    data class CheckProgress(
        val stage: String,
        val message: String
    )

    suspend fun evaluate(
        onProgress: (CheckProgress) -> Unit = {}
    ): DeviceProfile = withContext(Dispatchers.Default) {

        onProgress(CheckProgress("CODECS", "Comprobando capacidades multimedia…"))

        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val allInfos = codecList.codecInfos

        val videoDecoders = mutableListOf<DeviceProfile.CodecInfo>()
        val videoEncoders = mutableListOf<DeviceProfile.CodecInfo>()
        val audioDecoders = mutableListOf<DeviceProfile.CodecInfo>()
        val audioEncoders = mutableListOf<DeviceProfile.CodecInfo>()

        for (info in allInfos) {
            val isEncoder = info.isEncoder
            for (type in info.supportedTypes) {
                val lower = type.lowercase()
                val isVideo = VIDEO_MIME_TYPES.any { it.equals(lower, ignoreCase = true) }
                val isAudio = AUDIO_MIME_TYPES.any { it.equals(lower, ignoreCase = true) }
                if (!isVideo && !isAudio) continue

                val hw = isHardwareAccelerated(info)
                val (maxW, maxH) = if (isVideo) queryMaxSize(info, type) else 0 to 0
                val entry = DeviceProfile.CodecInfo(
                    name = info.name,
                    mime = type,
                    isHardware = hw,
                    maxWidth = maxW,
                    maxHeight = maxH
                )
                when {
                    isVideo && !isEncoder -> videoDecoders.add(entry)
                    isVideo && isEncoder -> videoEncoders.add(entry)
                    isAudio && !isEncoder -> audioDecoders.add(entry)
                    isAudio && isEncoder -> audioEncoders.add(entry)
                }
            }
        }

        onProgress(CheckProgress("GPU", "Evaluando motor gráfico…"))

        val glVersion = queryGlEsVersion()
        val hasVulkan = hasVulkanSupport()
        val maxTex = queryMaxTextureSize()

        onProgress(CheckProgress("MEMORY", "Perfil de rendimiento…"))

        val memMb = availableMemoryMb()

        val notes = mutableListOf<String>()
        val hasHevcDec = videoDecoders.any { it.mime.contains("hevc", true) }
        val hasAv1Dec = videoDecoders.any { it.mime.contains("av01", true) }
        val hasHwAvcDec = videoDecoders.any {
            it.mime.contains("avc", true) && it.isHardware
        }
        val hasHwAvcEnc = videoEncoders.any {
            it.mime.contains("avc", true) && it.isHardware
        }

        if (!hasHevcDec) notes.add("HEVC decode no disponible — se usará H.264 cuando sea posible")
        if (!hasAv1Dec) notes.add("AV1 decode no disponible en este dispositivo")
        if (!hasHwAvcDec) notes.add("Decodificación H.264 por software (posible impacto en rendimiento)")

        // Choose preview cap and effect quality from memory + codec strength
        val previewCap = when {
            memMb >= 6000 && hasHwAvcDec && maxTex >= 4096 -> 1920
            memMb >= 3000 && hasHwAvcDec -> 1280
            else -> 720
        }

        val effectQuality = when {
            memMb >= 5000 && maxTex >= 4096 -> DeviceProfile.EffectQuality.HIGH
            memMb >= 2500 -> DeviceProfile.EffectQuality.MEDIUM
            else -> DeviceProfile.EffectQuality.LOW
        }

        val profile = DeviceProfile(
            androidSdk = Build.VERSION.SDK_INT,
            supportedVideoDecoders = videoDecoders.distinctBy { it.mime + it.isHardware },
            supportedVideoEncoders = videoEncoders.distinctBy { it.mime + it.isHardware },
            supportedAudioDecoders = audioDecoders.distinctBy { it.mime },
            supportedAudioEncoders = audioEncoders.distinctBy { it.mime },
            glEsVersion = glVersion,
            hasVulkan = hasVulkan,
            maxTextureSize = maxTex,
            availableMemoryMb = memMb,
            previewResolutionCap = previewCap,
            preferHardwareDecode = hasHwAvcDec,
            preferHardwareEncode = hasHwAvcEnc,
            effectQuality = effectQuality,
            notes = notes
        )

        Log.i(TAG, "Profile ready: preview=${previewCap}p, fx=$effectQuality, " +
                "vDec=${videoDecoders.size}, vEnc=${videoEncoders.size}, mem=${memMb}MB")

        onProgress(CheckProgress("DONE", "Compatibilidad multimedia comprobada"))
        profile
    }

    private fun isHardwareAccelerated(info: MediaCodecInfo): Boolean {
        // Heuristic: software codecs usually contain "OMX.google" or "c2.android"
        val name = info.name.lowercase()
        if (name.contains("google") || name.contains("c2.android") || name.contains("sw")) {
            return false
        }
        // API 29+ has isHardwareAccelerated
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                info.isHardwareAccelerated
            } catch (_: Throwable) {
                !name.contains("google")
            }
        } else {
            !name.contains("google") && !name.contains("c2.android")
        }
    }

    private fun queryMaxSize(info: MediaCodecInfo, mime: String): Pair<Int, Int> {
        return try {
            val caps = info.getCapabilitiesForType(mime)
            val videoCaps = caps.videoCapabilities ?: return 0 to 0
            val w = videoCaps.supportedWidths?.upper ?: 0
            val h = videoCaps.supportedHeights?.upper ?: 0
            w to h
        } catch (_: Exception) {
            0 to 0
        }
    }

    private fun queryGlEsVersion(): String {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val configInfo = am.deviceConfigurationInfo
            configInfo.glEsVersion
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun hasVulkanSupport(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
            val pm = context.packageManager
            pm.hasSystemFeature("android.hardware.vulkan.level") ||
                pm.hasSystemFeature("android.hardware.vulkan.version")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Best-effort max texture size. Creates a temporary EGL context.
     * Safe and short-lived; does not leave resources hanging.
     */
    private fun queryMaxTextureSize(): Int {
        return try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return 2048
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return 2048

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfigs, 0)
            if (numConfigs[0] == 0) {
                EGL14.eglTerminate(display)
                return 2048
            }
            val ctxAttrib = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttrib, 0)
            val surfAttrib = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            val surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfAttrib, 0)
            EGL14.eglMakeCurrent(display, surface, surface, context)

            val size = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, size, 0)

            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)

            size[0].coerceAtLeast(2048)
        } catch (e: Exception) {
            Log.w(TAG, "GL texture query failed", e)
            2048
        }
    }

    private fun availableMemoryMb(): Long {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            info.availMem / (1024 * 1024)
        } catch (_: Exception) {
            0L
        }
    }
}
