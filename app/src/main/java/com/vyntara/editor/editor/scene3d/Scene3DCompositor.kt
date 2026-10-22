package com.vyntara.editor.editor.scene3d

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Professional software compositor for export + stills:
 * - Camera orbit
 * - Lambert-ish lighting from LIGHT objects
 * - Materials (roughness darkens, emissive lifts)
 * - Particle emitters (deterministic by time)
 * - Imported mesh wireframes
 * - Animated transforms via Scene3DAnim
 */
object Scene3DCompositor {

    fun draw(canvas: Canvas, scene: Scene3D, width: Int, height: Int, timeSec: Float = 0f) {
        if (!scene.previewEnabled) return
        val resolved = Scene3DAnim.resolveScene(scene, timeSec)
        val yaw = Math.toRadians(resolved.cameraYaw.toDouble())
        val pitch = Math.toRadians(resolved.cameraPitch.toDouble())
        val dist = resolved.cameraDistance.coerceAtLeast(1f)
        val eyeX = (dist * cos(pitch) * sin(yaw)).toFloat()
        val eyeY = (dist * sin(pitch)).toFloat()
        val eyeZ = (dist * cos(pitch) * cos(yaw)).toFloat()

        val lights = resolved.objects.filter { it.type == Object3DType.LIGHT && it.visible }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = width / 2f
        val cy = height / 2f
        val scale = minOf(width, height) / 4.5f
        val quality = resolved.viewportQuality.coerceIn(0, 2)

        fun project(x: Float, y: Float, z: Float): Pair<Float, Float>? {
            val vx = x - eyeX
            val vy = y - eyeY
            val vz = z - eyeZ
            val fl = 2.2f
            val depth = -vz + dist
            if (depth < 0.2f) return null
            val aspect = width / height.toFloat().coerceAtLeast(0.5f)
            val px = cx + (vx / depth) * scale * fl * aspect
            val py = cy - (vy / depth) * scale * fl
            return px to py
        }

        fun shade(obj: Object3D, nx: Float, ny: Float, nz: Float): Int {
            var l = 0.25f + obj.material.emissive * 0.5f
            for (light in lights) {
                val lx = light.position.x - obj.position.x
                val ly = light.position.y - obj.position.y
                val lz = light.position.z - obj.position.z
                val len = sqrt(lx * lx + ly * ly + lz * lz).coerceAtLeast(0.01f)
                val ndot = max(0f, (nx * lx + ny * ly + nz * lz) / len)
                l += ndot * light.intensity * (1f - obj.material.roughness * 0.4f)
            }
            l = l.coerceIn(0f, 1.4f)
            val r = (obj.colorR * l * 255).toInt().coerceIn(0, 255)
            val g = (obj.colorG * l * 255).toInt().coerceIn(0, 255)
            val b = (obj.colorB * l * 255).toInt().coerceIn(0, 255)
            return Color.argb(220, r, g, b)
        }

        // Grid density by quality
        paint.color = Color.argb(90, 80, 90, 110)
        paint.strokeWidth = 1.5f
        paint.style = Paint.Style.STROKE
        val gridN = when (quality) { 0 -> 3; 2 -> 8; else -> 5 }
        for (i in -gridN..gridN) {
            val a = project(-gridN.toFloat(), 0f, i.toFloat())
            val b = project(gridN.toFloat(), 0f, i.toFloat())
            if (a != null && b != null) canvas.drawLine(a.first, a.second, b.first, b.second, paint)
            val c = project(i.toFloat(), 0f, -gridN.toFloat())
            val d = project(i.toFloat(), 0f, gridN.toFloat())
            if (c != null && d != null) canvas.drawLine(c.first, c.second, d.first, d.second, paint)
        }

        for (mesh in resolved.importedMeshes) {
            paint.style = Paint.Style.STROKE
            paint.color = Color.argb(200, 140, 180, 255)
            paint.strokeWidth = 1.2f
            val pos = mesh.positions
            val idx = mesh.indices
            fun vtx(i: Int): Pair<Float, Float>? {
                val base = i * 3
                if (base + 2 >= pos.size) return null
                return project(
                    mesh.translation.x + pos[base] * mesh.scale.x,
                    mesh.translation.y + pos[base + 1] * mesh.scale.y,
                    mesh.translation.z + pos[base + 2] * mesh.scale.z
                )
            }
            if (idx != null) {
                var i = 0
                while (i + 2 < idx.size) {
                    val a = vtx(idx[i]); val b = vtx(idx[i + 1]); val c = vtx(idx[i + 2])
                    if (a != null && b != null && c != null) {
                        canvas.drawLine(a.first, a.second, b.first, b.second, paint)
                        canvas.drawLine(b.first, b.second, c.first, c.second, paint)
                        canvas.drawLine(c.first, c.second, a.first, a.second, paint)
                    }
                    i += 3
                }
            }
        }

        fun worldPos(o: Object3D): Vec3 {
            val parent = o.parentId?.let { pid -> resolved.objects.find { it.id == pid } }
            return if (parent != null) {
                val pp = worldPos(parent)
                Vec3(pp.x + o.position.x, pp.y + o.position.y, pp.z + o.position.z)
            } else o.position
        }
        for (obj0 in resolved.objects.filter { it.visible }) {
            val obj = obj0.copy(position = worldPos(obj0))
            when (obj.type) {
                Object3DType.PARTICLES -> drawParticles(canvas, obj, timeSec, ::project, quality)
                Object3DType.CUBE, Object3DType.SPHERE, Object3DType.CYLINDER ->
                    drawBox(canvas, obj, ::project, paint, ::shade)
                Object3DType.PLANE -> {
                    val s = obj.scale
                    val pts = listOf(
                        Triple(-0.5f * s.x, 0f, -0.5f * s.z),
                        Triple(0.5f * s.x, 0f, -0.5f * s.z),
                        Triple(0.5f * s.x, 0f, 0.5f * s.z),
                        Triple(-0.5f * s.x, 0f, 0.5f * s.z)
                    ).mapNotNull { (lx, ly, lz) ->
                        project(obj.position.x + lx, obj.position.y + ly, obj.position.z + lz)
                    }
                    if (pts.size == 4) {
                        val path = Path()
                        path.moveTo(pts[0].first, pts[0].second)
                        pts.drop(1).forEach { path.lineTo(it.first, it.second) }
                        path.close()
                        paint.style = Paint.Style.FILL
                        paint.color = shade(obj, 0f, 1f, 0f)
                        paint.alpha = 160
                        canvas.drawPath(path, paint)
                    }
                }
                Object3DType.LIGHT, Object3DType.CAMERA, Object3DType.NULL -> {
                    val p = project(obj.position.x, obj.position.y, obj.position.z) ?: continue
                    paint.style = Paint.Style.FILL
                    paint.color = when (obj.type) {
                        Object3DType.LIGHT -> Color.YELLOW
                        Object3DType.CAMERA -> Color.CYAN
                        else -> Color.GRAY
                    }
                    canvas.drawCircle(p.first, p.second, 8f, paint)
                }
            }
        }
    }

    private fun drawParticles(
        canvas: Canvas,
        obj: Object3D,
        timeSec: Float,
        project: (Float, Float, Float) -> Pair<Float, Float>?,
        quality: Int
    ) {
        val em = obj.particles ?: ParticleEmitter()
        val count = when (quality) {
            0 -> em.count / 3
            2 -> em.count
            else -> em.count * 2 / 3
        }.coerceAtLeast(8)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL
        for (i in 0 until count) {
            val seed = (i * 1103515245 + 12345)
            val life = em.life.coerceAtLeast(0.1f)
            val t = ((timeSec * em.speed) + (seed % 1000) / 1000f * life) % life
            val age = t / life
            val ang = (seed % 360) * Math.PI / 180.0
            val rad = em.spread * age
            val px = obj.position.x + (cos(ang) * rad).toFloat()
            val py = obj.position.y + age * em.speed * 1.5f
            val pz = obj.position.z + (sin(ang) * rad).toFloat()
            val screen = project(px, py, pz) ?: continue
            val alpha = ((1f - age) * 255).toInt().coerceIn(0, 255)
            paint.color = Color.argb(
                alpha,
                (em.colorR * 255).toInt(),
                (em.colorG * 255).toInt(),
                (em.colorB * 255).toInt()
            )
            val sz = em.size * (1f - age * 0.5f) * 40f
            canvas.drawCircle(screen.first, screen.second, sz, paint)
        }
    }

    private fun drawBox(
        canvas: Canvas,
        obj: Object3D,
        project: (Float, Float, Float) -> Pair<Float, Float>?,
        paint: Paint,
        shade: (Object3D, Float, Float, Float) -> Int
    ) {
        val hx = 0.5f * obj.scale.x
        val hy = 0.5f * obj.scale.y
        val hz = 0.5f * obj.scale.z
        val corners = arrayOf(
            floatArrayOf(-hx, -hy, -hz), floatArrayOf(hx, -hy, -hz),
            floatArrayOf(hx, hy, -hz), floatArrayOf(-hx, hy, -hz),
            floatArrayOf(-hx, -hy, hz), floatArrayOf(hx, -hy, hz),
            floatArrayOf(hx, hy, hz), floatArrayOf(-hx, hy, hz)
        )
        val proj = corners.mapNotNull {
            project(obj.position.x + it[0], obj.position.y + it[1], obj.position.z + it[2])
        }
        if (proj.size < 8) return
        val faces = arrayOf(
            intArrayOf(0, 1, 2, 3) to floatArrayOf(0f, 0f, -1f),
            intArrayOf(4, 5, 6, 7) to floatArrayOf(0f, 0f, 1f),
            intArrayOf(0, 1, 5, 4) to floatArrayOf(0f, -1f, 0f),
            intArrayOf(2, 3, 7, 6) to floatArrayOf(0f, 1f, 0f),
            intArrayOf(1, 2, 6, 5) to floatArrayOf(1f, 0f, 0f),
            intArrayOf(0, 3, 7, 4) to floatArrayOf(-1f, 0f, 0f)
        )
        for ((f, n) in faces) {
            val path = Path()
            path.moveTo(proj[f[0]].first, proj[f[0]].second)
            for (i in 1..3) path.lineTo(proj[f[i]].first, proj[f[i]].second)
            path.close()
            paint.style = Paint.Style.FILL
            paint.color = shade(obj, n[0], n[1], n[2])
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE
            paint.color = Color.argb(80, 255, 255, 255)
            paint.strokeWidth = 1.5f
            canvas.drawPath(path, paint)
        }
    }
}
