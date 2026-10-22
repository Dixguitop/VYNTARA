package com.vyntara.editor.capabilities

import org.json.JSONArray
import org.json.JSONObject

/**
 * Snapshot of device multimedia and graphics capabilities used to pick
 * initial preview / export settings. Serializable for DataStore caching.
 */
data class DeviceProfile(
    val androidSdk: Int,
    val supportedVideoDecoders: List<CodecInfo>,
    val supportedVideoEncoders: List<CodecInfo>,
    val supportedAudioDecoders: List<CodecInfo>,
    val supportedAudioEncoders: List<CodecInfo>,
    val glEsVersion: String,
    val hasVulkan: Boolean,
    val maxTextureSize: Int,
    val availableMemoryMb: Long,
    val previewResolutionCap: Int,          // max side length for preview
    val preferHardwareDecode: Boolean,
    val preferHardwareEncode: Boolean,
    val effectQuality: EffectQuality,
    val notes: List<String> = emptyList()
) {
    enum class EffectQuality { HIGH, MEDIUM, LOW }

    data class CodecInfo(
        val name: String,
        val mime: String,
        val isHardware: Boolean,
        val maxWidth: Int = 0,
        val maxHeight: Int = 0
    )

    fun toJson(): String {
        fun codecArr(list: List<CodecInfo>) = JSONArray().apply {
            list.forEach { c ->
                put(JSONObject().apply {
                    put("name", c.name)
                    put("mime", c.mime)
                    put("hw", c.isHardware)
                    put("mw", c.maxWidth)
                    put("mh", c.maxHeight)
                })
            }
        }
        return JSONObject().apply {
            put("sdk", androidSdk)
            put("vDec", codecArr(supportedVideoDecoders))
            put("vEnc", codecArr(supportedVideoEncoders))
            put("aDec", codecArr(supportedAudioDecoders))
            put("aEnc", codecArr(supportedAudioEncoders))
            put("gl", glEsVersion)
            put("vulkan", hasVulkan)
            put("tex", maxTextureSize)
            put("mem", availableMemoryMb)
            put("prevCap", previewResolutionCap)
            put("hwDec", preferHardwareDecode)
            put("hwEnc", preferHardwareEncode)
            put("fx", effectQuality.name)
            put("notes", JSONArray(notes))
        }.toString()
    }

    companion object {
        fun fromJson(json: String): DeviceProfile? = try {
            val o = JSONObject(json)
            fun parseCodecs(key: String): List<CodecInfo> {
                val arr = o.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).map { i ->
                    val c = arr.getJSONObject(i)
                    CodecInfo(
                        name = c.optString("name"),
                        mime = c.optString("mime"),
                        isHardware = c.optBoolean("hw"),
                        maxWidth = c.optInt("mw"),
                        maxHeight = c.optInt("mh")
                    )
                }
            }
            val notesArr = o.optJSONArray("notes")
            val notes = if (notesArr != null) {
                (0 until notesArr.length()).map { notesArr.getString(it) }
            } else emptyList()

            DeviceProfile(
                androidSdk = o.optInt("sdk"),
                supportedVideoDecoders = parseCodecs("vDec"),
                supportedVideoEncoders = parseCodecs("vEnc"),
                supportedAudioDecoders = parseCodecs("aDec"),
                supportedAudioEncoders = parseCodecs("aEnc"),
                glEsVersion = o.optString("gl", "unknown"),
                hasVulkan = o.optBoolean("vulkan"),
                maxTextureSize = o.optInt("tex", 2048),
                availableMemoryMb = o.optLong("mem", 0),
                previewResolutionCap = o.optInt("prevCap", 1080),
                preferHardwareDecode = o.optBoolean("hwDec", true),
                preferHardwareEncode = o.optBoolean("hwEnc", true),
                effectQuality = runCatching {
                    EffectQuality.valueOf(o.optString("fx", "MEDIUM"))
                }.getOrDefault(EffectQuality.MEDIUM),
                notes = notes
            )
        } catch (_: Exception) {
            null
        }
    }
}
