package com.vyntara.editor.editor.scene3d

import com.vyntara.editor.editor.Ease
import com.vyntara.editor.editor.Keyframe
import java.util.UUID

enum class Object3DType { CUBE, SPHERE, PLANE, CYLINDER, LIGHT, CAMERA, NULL, PARTICLES }

data class Vec3(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f)

data class Material3D(
    val roughness: Float = 0.5f,
    val metalness: Float = 0.1f,
    val emissive: Float = 0f
)

data class ParticleEmitter(
    val count: Int = 80,
    val size: Float = 0.08f,
    val speed: Float = 0.6f,
    val spread: Float = 1.2f,
    val life: Float = 2f,
    val colorR: Float = 0.9f,
    val colorG: Float = 0.6f,
    val colorB: Float = 0.2f
)

data class Object3D(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    val type: Object3DType,
    val position: Vec3 = Vec3(),
    val rotation: Vec3 = Vec3(),
    val scale: Vec3 = Vec3(1f, 1f, 1f),
    val visible: Boolean = true,
    val locked: Boolean = false,
    val colorR: Float = 0.55f,
    val colorG: Float = 0.45f,
    val colorB: Float = 0.95f,
    val intensity: Float = 1f,
    val parentId: String? = null,
    val material: Material3D = Material3D(),
    val particles: ParticleEmitter? = null,
    /** Animated properties: "posX","posY","posZ","rotY","scale" */
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
)

data class Scene3D(
    val objects: List<Object3D> = emptyList(),
    val selectedId: String? = null,
    val previewEnabled: Boolean = true,
    val cameraYaw: Float = 35f,
    val cameraPitch: Float = 25f,
    val cameraDistance: Float = 6f,
    val importedMeshes: List<MeshData> = emptyList(),
    /** 0=low 1=med 2=high viewport density */
    val viewportQuality: Int = 1,
    /** Scene camera animation: yaw, pitch, distance */
    val cameraKeyframes: Map<String, List<com.vyntara.editor.editor.Keyframe>> = emptyMap()
) {
    val selected: Object3D? get() = objects.find { it.id == selectedId }

    companion object {
        fun demo(): Scene3D {
            val cube = Object3D(
                name = "Cube",
                type = Object3DType.CUBE,
                position = Vec3(0f, 0.5f, 0f),
                material = Material3D(roughness = 0.35f, metalness = 0.2f)
            )
            val plane = Object3D(
                name = "Ground",
                type = Object3DType.PLANE,
                scale = Vec3(4f, 1f, 4f),
                colorR = 0.25f, colorG = 0.27f, colorB = 0.32f
            )
            val light = Object3D(
                name = "Key Light",
                type = Object3DType.LIGHT,
                position = Vec3(2f, 4f, 2f),
                intensity = 1.2f
            )
            val cam = Object3D(name = "Camera", type = Object3DType.CAMERA, position = Vec3(0f, 1.5f, 5f))
            return Scene3D(objects = listOf(plane, cube, light, cam), selectedId = cube.id)
        }
    }
}

/** Evaluate object transform at composition time. */
object Scene3DAnim {
    fun resolve(obj: Object3D, timeSec: Float): Object3D {
        if (obj.keyframes.isEmpty()) return obj
        fun v(key: String, fallback: Float) =
            com.vyntara.editor.editor.KeyframeEval.value(obj.keyframes[key], timeSec, fallback)
        return obj.copy(
            position = Vec3(
                v("posX", obj.position.x),
                v("posY", obj.position.y),
                v("posZ", obj.position.z)
            ),
            rotation = Vec3(
                v("rotX", obj.rotation.x),
                v("rotY", obj.rotation.y),
                v("rotZ", obj.rotation.z)
            ),
            scale = Vec3(
                v("scaleX", obj.scale.x),
                v("scaleY", obj.scale.y),
                v("scaleZ", obj.scale.z)
            )
        )
    }

    fun resolveScene(scene: Scene3D, timeSec: Float): Scene3D {
        val objs = scene.objects.map { resolve(it, timeSec) }
        if (scene.cameraKeyframes.isEmpty()) return scene.copy(objects = objs)
        fun cv(key: String, fallback: Float) =
            com.vyntara.editor.editor.KeyframeEval.value(scene.cameraKeyframes[key], timeSec, fallback)
        return scene.copy(
            objects = objs,
            cameraYaw = cv("yaw", scene.cameraYaw),
            cameraPitch = cv("pitch", scene.cameraPitch),
            cameraDistance = cv("distance", scene.cameraDistance)
        )
    }
}
