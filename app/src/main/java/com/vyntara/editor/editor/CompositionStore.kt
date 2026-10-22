package com.vyntara.editor.editor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Versioned composition persistence per project.
 * Schema v1 — clips, transforms, effects, keyframes, media URIs.
 */
class CompositionStore(private val context: Context) {

    private fun dir(projectId: String) =
        File(context.filesDir, "projects/$projectId").also { if (!it.exists()) it.mkdirs() }

    private fun file(projectId: String) = File(dir(projectId), "composition.json")

    
    fun saveScene(projectId: String, sceneJson: String): Boolean {
        return try {
            File(dir(projectId), "scene3d.json").writeText(sceneJson)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun loadSceneJson(projectId: String): String? {
        val f = File(dir(projectId), "scene3d.json")
        return if (f.exists()) f.readText() else null
    }

    fun save(doc: EditorDocument): Boolean {
        return try {
            val root = JSONObject()
            root.put("schema", 1)
            root.put("projectId", doc.projectId)
            root.put("title", doc.title)
            root.put("width", doc.width)
            root.put("height", doc.height)
            root.put("fps", doc.fps)
            val marks = JSONArray()
            doc.markers.forEach { marks.put(it.toDouble()) }
            root.put("markers", marks)
            root.put("backgroundArgb", doc.backgroundArgb)
            root.put("durationSec", doc.durationSec.toDouble())
            root.put("playheadSec", doc.playheadSec.toDouble())
            val clips = JSONArray()
            doc.clips.forEach { c -> clips.put(clipToJson(c)) }
            root.put("clips", clips)
            file(doc.projectId).writeText(root.toString())
            true
        } catch (_: Exception) {
            false
        }
    }

    fun load(projectId: String, fallback: EditorDocument): EditorDocument {
        val f = file(projectId)
        if (!f.exists()) return fallback
        return try {
            val root = JSONObject(f.readText())
            val clipsArr = root.optJSONArray("clips") ?: JSONArray()
            val clips = (0 until clipsArr.length()).map { i ->
                jsonToClip(clipsArr.getJSONObject(i))
            }
            fallback.copy(
                title = root.optString("title", fallback.title),
                width = root.optInt("width", fallback.width),
                height = root.optInt("height", fallback.height),
                fps = root.optInt("fps", fallback.fps),
                backgroundArgb = root.optLong("backgroundArgb", fallback.backgroundArgb),
                durationSec = root.optDouble("durationSec", fallback.durationSec.toDouble()).toFloat(),
                playheadSec = root.optDouble("playheadSec", 0.0).toFloat(),
                clips = clips,
                selectedIds = emptySet(),
                saveStatus = SaveStatus.SAVED
            )
        } catch (_: Exception) {
            fallback
        }
    }

    private fun clipToJson(c: EditorClip): JSONObject {
        val o = JSONObject()
        o.put("id", c.id)
        o.put("name", c.name)
        o.put("type", c.type.name)
        o.put("trackIndex", c.trackIndex)
        o.put("startSec", c.startSec.toDouble())
        o.put("durationSec", c.durationSec.toDouble())
        o.put("mediaUri", c.mediaUri)
        o.put("mediaWidth", c.mediaWidth)
        o.put("mediaHeight", c.mediaHeight)
        o.put("sourceDurationSec", c.sourceDurationSec?.toDouble())
        o.put("visible", c.visible)
        o.put("locked", c.locked)
        o.put("muted", c.muted)
        o.put("volume", c.volume.toDouble())
        o.put("speed", c.speed.toDouble())
        o.put("textContent", c.textContent)
        o.put("textSize", c.textSize.toDouble())
        o.put("textColorArgb", c.textColorArgb)
        o.put("fadeInSec", c.fadeInSec.toDouble())
        o.put("fadeOutSec", c.fadeOutSec.toDouble())
        o.put("posX", c.posX.toDouble())
        o.put("posY", c.posY.toDouble())
        o.put("scaleX", c.scaleX.toDouble())
        o.put("scaleY", c.scaleY.toDouble())
        o.put("rotation", c.rotation.toDouble())
        o.put("opacity", c.opacity.toDouble())
        o.put("smartBlur", c.smartBlur)
        o.put("blurRadius", c.blurRadius.toDouble())
        val fx = JSONArray()
        c.effects.forEach { e ->
            fx.put(
                JSONObject()
                    .put("id", e.id)
                    .put("name", e.name)
                    .put("category", e.category)
                    .put("enabled", e.enabled)
                    .put("intensity", e.intensity.toDouble())
                    .put("radius", e.radius.toDouble())
            )
        }
        o.put("effects", fx)
        val kf = JSONObject()
        c.keyframes.forEach { (prop, keys) ->
            val arr = JSONArray()
            keys.forEach { k ->
                arr.put(JSONObject().put("t", k.timeSec.toDouble()).put("v", k.value.toDouble()).put("ease", k.ease.name))
            }
            kf.put(prop, arr)
        }
        o.put("keyframes", kf)
        return o
    }

    private fun jsonToClip(o: JSONObject): EditorClip {
        val type = try {
            LayerType.valueOf(o.optString("type", "SOLID"))
        } catch (_: Exception) {
            LayerType.SOLID
        }
        val fxArr = o.optJSONArray("effects") ?: JSONArray()
        val effects = (0 until fxArr.length()).map { i ->
            val e = fxArr.getJSONObject(i)
            EffectInstance(
                id = e.optString("id"),
                name = e.optString("name"),
                category = e.optString("category"),
                enabled = e.optBoolean("enabled", true),
                intensity = e.optDouble("intensity", 1.0).toFloat(),
                radius = e.optDouble("radius", 8.0).toFloat()
            )
        }
        val kfObj = o.optJSONObject("keyframes") ?: JSONObject()
        val keyframes = mutableMapOf<String, List<Keyframe>>()
        kfObj.keys().forEach { prop ->
            val arr = kfObj.getJSONArray(prop)
            keyframes[prop] = (0 until arr.length()).map { i ->
                val k = arr.getJSONObject(i)
                Keyframe(
                    k.optDouble("t", 0.0).toFloat(),
                    k.optDouble("v", 0.0).toFloat(),
                    try { Ease.valueOf(k.optString("ease", "LINEAR")) } catch (_: Exception) { Ease.LINEAR }
                )
            }
        }
        return EditorClip(
            id = o.optString("id"),
            name = o.optString("name"),
            type = type,
            trackIndex = o.optInt("trackIndex"),
            startSec = o.optDouble("startSec", 0.0).toFloat(),
            durationSec = o.optDouble("durationSec", 3.0).toFloat(),
            mediaUri = o.optString("mediaUri", null).takeIf { !it.isNullOrBlank() && it != "null" },
            mediaWidth = if (o.has("mediaWidth") && !o.isNull("mediaWidth")) o.optInt("mediaWidth") else null,
            mediaHeight = if (o.has("mediaHeight") && !o.isNull("mediaHeight")) o.optInt("mediaHeight") else null,
            sourceDurationSec = if (o.has("sourceDurationSec") && !o.isNull("sourceDurationSec"))
                o.optDouble("sourceDurationSec").toFloat() else null,
            visible = o.optBoolean("visible", true),
            locked = o.optBoolean("locked", false),
            muted = o.optBoolean("muted", false),
            volume = o.optDouble("volume", 1.0).toFloat(),
            speed = o.optDouble("speed", 1.0).toFloat(),
            textContent = o.optString("textContent", "Text"),
            textSize = o.optDouble("textSize", 1.0).toFloat(),
            textColorArgb = o.optInt("textColorArgb", 0xFFFFFFFF.toInt()),
            fadeInSec = o.optDouble("fadeInSec", 0.0).toFloat(),
            fadeOutSec = o.optDouble("fadeOutSec", 0.0).toFloat(),
            posX = o.optDouble("posX", 0.5).toFloat(),
            posY = o.optDouble("posY", 0.5).toFloat(),
            scaleX = o.optDouble("scaleX", 1.0).toFloat(),
            scaleY = o.optDouble("scaleY", 1.0).toFloat(),
            rotation = o.optDouble("rotation", 0.0).toFloat(),
            opacity = o.optDouble("opacity", 1.0).toFloat(),
            effects = effects,
            smartBlur = o.optBoolean("smartBlur", false),
            blurRadius = o.optDouble("blurRadius", 0.0).toFloat(),
            keyframes = keyframes
        )
    }
}
