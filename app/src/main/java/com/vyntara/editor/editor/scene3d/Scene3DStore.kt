package com.vyntara.editor.editor.scene3d

import org.json.JSONArray
import org.json.JSONObject

object Scene3DStore {
    fun toJson(scene: Scene3D): JSONObject {
        val root = JSONObject()
        root.put("previewEnabled", scene.previewEnabled)
        root.put("cameraYaw", scene.cameraYaw.toDouble())
        root.put("cameraPitch", scene.cameraPitch.toDouble())
        root.put("cameraDistance", scene.cameraDistance.toDouble())
        root.put("selectedId", scene.selectedId)
        val arr = JSONArray()
        scene.objects.forEach { o ->
            arr.put(
                JSONObject()
                    .put("id", o.id)
                    .put("name", o.name)
                    .put("type", o.type.name)
                    .put("px", o.position.x.toDouble())
                    .put("py", o.position.y.toDouble())
                    .put("pz", o.position.z.toDouble())
                    .put("rx", o.rotation.x.toDouble())
                    .put("ry", o.rotation.y.toDouble())
                    .put("rz", o.rotation.z.toDouble())
                    .put("sx", o.scale.x.toDouble())
                    .put("sy", o.scale.y.toDouble())
                    .put("sz", o.scale.z.toDouble())
                    .put("visible", o.visible)
                    .put("locked", o.locked)
                    .put("cr", o.colorR.toDouble())
                    .put("cg", o.colorG.toDouble())
                    .put("cb", o.colorB.toDouble())
                    .put("intensity", o.intensity.toDouble())
            )
        }
        root.put("objects", arr)
        return root
    }

    fun fromJson(root: JSONObject?): Scene3D {
        if (root == null) return Scene3D.demo()
        return try {
            val arr = root.optJSONArray("objects") ?: JSONArray()
            val objects = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val type = try {
                    Object3DType.valueOf(o.optString("type", "CUBE"))
                } catch (_: Exception) {
                    Object3DType.CUBE
                }
                Object3D(
                    id = o.optString("id"),
                    name = o.optString("name", type.name),
                    type = type,
                    position = Vec3(
                        o.optDouble("px", 0.0).toFloat(),
                        o.optDouble("py", 0.0).toFloat(),
                        o.optDouble("pz", 0.0).toFloat()
                    ),
                    rotation = Vec3(
                        o.optDouble("rx", 0.0).toFloat(),
                        o.optDouble("ry", 0.0).toFloat(),
                        o.optDouble("rz", 0.0).toFloat()
                    ),
                    scale = Vec3(
                        o.optDouble("sx", 1.0).toFloat(),
                        o.optDouble("sy", 1.0).toFloat(),
                        o.optDouble("sz", 1.0).toFloat()
                    ),
                    visible = o.optBoolean("visible", true),
                    locked = o.optBoolean("locked", false),
                    colorR = o.optDouble("cr", 0.55).toFloat(),
                    colorG = o.optDouble("cg", 0.45).toFloat(),
                    colorB = o.optDouble("cb", 0.95).toFloat(),
                    intensity = o.optDouble("intensity", 1.0).toFloat()
                )
            }
            Scene3D(
                objects = objects.ifEmpty { Scene3D.demo().objects },
                selectedId = root.optString("selectedId", null).takeIf { !it.isNullOrBlank() && it != "null" },
                previewEnabled = root.optBoolean("previewEnabled", true),
                cameraYaw = root.optDouble("cameraYaw", 35.0).toFloat(),
                cameraPitch = root.optDouble("cameraPitch", 25.0).toFloat(),
                cameraDistance = root.optDouble("cameraDistance", 6.0).toFloat()
            )
        } catch (_: Exception) {
            Scene3D.demo()
        }
    }
}
