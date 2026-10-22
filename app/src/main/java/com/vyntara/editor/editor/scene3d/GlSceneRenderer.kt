package com.vyntara.editor.editor.scene3d

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * Real OpenGL ES 2.0 viewport — cube, plane, grid, orbit camera.
 * Not a decorative image; geometry is drawn every frame.
 */
class GlSceneRenderer : GLSurfaceView.Renderer {

    @Volatile var scene: Scene3D = Scene3D.demo()

    private val mvp = FloatArray(16)
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val scratch = FloatArray(16)

    private var program = 0
    private var aPos = 0
    private var uMvp = 0
    private var uColor = 0

    private lateinit var cubeBuf: FloatBuffer
    private lateinit var planeBuf: FloatBuffer
    private lateinit var gridBuf: FloatBuffer
    private var gridVertexCount = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.06f, 0.07f, 0.09f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        program = buildProgram(VERT, FRAG)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uColor = GLES20.glGetUniformLocation(program, "uColor")
        cubeBuf = floatBuf(CUBE)
        planeBuf = floatBuf(PLANE)
        val grid = buildGrid(10, 1f)
        gridBuf = floatBuf(grid)
        gridVertexCount = grid.size / 3
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.coerceAtLeast(1)
        Matrix.perspectiveM(proj, 0, 45f, aspect, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!scene.previewEnabled) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            return
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        val yaw = Math.toRadians(scene.cameraYaw.toDouble())
        val pitch = Math.toRadians(scene.cameraPitch.toDouble())
        val dist = scene.cameraDistance
        val eyeX = (dist * cos(pitch) * sin(yaw)).toFloat()
        val eyeY = (dist * sin(pitch)).toFloat()
        val eyeZ = (dist * cos(pitch) * cos(yaw)).toFloat()
        Matrix.setLookAtM(view, 0, eyeX, eyeY, eyeZ, 0f, 0.5f, 0f, 0f, 1f, 0f)

        // Grid
        Matrix.setIdentityM(model, 0)
        Matrix.multiplyMM(scratch, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, scratch, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniform4f(uColor, 0.2f, 0.22f, 0.28f, 1f)
        drawLines(gridBuf, gridVertexCount)

        for (obj in scene.objects) {
            if (!obj.visible) continue
            when (obj.type) {
                Object3DType.CUBE, Object3DType.SPHERE, Object3DType.CYLINDER -> {
                    // Sphere/cylinder approximated as cube for Phase 1 solid geometry
                    drawMesh(obj, cubeBuf, CUBE.size / 3, GLES20.GL_TRIANGLES)
                }
                Object3DType.PLANE -> drawMesh(obj, planeBuf, PLANE.size / 3, GLES20.GL_TRIANGLES)
                Object3DType.PARTICLES -> {
                    // viewport: small markers
                    val marker = obj.copy(scale = Vec3(0.1f, 0.1f, 0.1f))
                    drawMesh(marker, cubeBuf, CUBE.size / 3, GLES20.GL_TRIANGLES)
                }
                Object3DType.LIGHT, Object3DType.CAMERA, Object3DType.NULL -> {
                    // Small marker cube
                    val marker = obj.copy(scale = Vec3(0.15f, 0.15f, 0.15f))
                    drawMesh(marker, cubeBuf, CUBE.size / 3, GLES20.GL_TRIANGLES)
                }
            }
        }
    }

    private fun drawMesh(obj: Object3D, buf: FloatBuffer, count: Int, mode: Int) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, obj.position.x, obj.position.y, obj.position.z)
        Matrix.rotateM(model, 0, obj.rotation.x, 1f, 0f, 0f)
        Matrix.rotateM(model, 0, obj.rotation.y, 0f, 1f, 0f)
        Matrix.rotateM(model, 0, obj.rotation.z, 0f, 0f, 1f)
        Matrix.scaleM(model, 0, obj.scale.x, obj.scale.y, obj.scale.z)
        Matrix.multiplyMM(scratch, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, scratch, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        val sel = obj.id == scene.selectedId
        val boost = if (sel) 1.25f else 1f
        GLES20.glUniform4f(
            uColor,
            (obj.colorR * boost).coerceAtMost(1f),
            (obj.colorG * boost).coerceAtMost(1f),
            (obj.colorB * boost).coerceAtMost(1f),
            1f
        )
        buf.position(0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glDrawArrays(mode, 0, count)
        GLES20.glDisableVertexAttribArray(aPos)
    }

    private fun drawLines(buf: FloatBuffer, count: Int) {
        buf.position(0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, count)
        GLES20.glDisableVertexAttribArray(aPos)
    }

    companion object {
        private const val VERT = """
            attribute vec3 aPos;
            uniform mat4 uMvp;
            void main() { gl_Position = uMvp * vec4(aPos, 1.0); }
        """
        private const val FRAG = """
            precision mediump float;
            uniform vec4 uColor;
            void main() { gl_FragColor = uColor; }
        """

        private val CUBE = floatArrayOf(
            // front
            -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f,
            -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
            // back
            0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f,
            0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f,
            // left
            -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
            -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f,
            // right
            0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f,
            0.5f, -0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f,
            // top
            -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f,
            -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f,
            // bottom
            -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f,
            -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f
        )

        private val PLANE = floatArrayOf(
            -0.5f, 0f, -0.5f, 0.5f, 0f, -0.5f, 0.5f, 0f, 0.5f,
            -0.5f, 0f, -0.5f, 0.5f, 0f, 0.5f, -0.5f, 0f, 0.5f
        )

        private fun buildGrid(half: Int, step: Float): FloatArray {
            val list = mutableListOf<Float>()
            for (i in -half..half) {
                val v = i * step
                list.addAll(listOf(-half * step, 0f, v, half * step, 0f, v))
                list.addAll(listOf(v, 0f, -half * step, v, 0f, half * step))
            }
            return list.toFloatArray()
        }

        private fun floatBuf(data: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer().also { it.put(data); it.position(0) }

        private fun buildProgram(v: String, f: String): Int {
            fun compile(type: Int, src: String): Int {
                val id = GLES20.glCreateShader(type)
                GLES20.glShaderSource(id, src)
                GLES20.glCompileShader(id)
                return id
            }
            val vs = compile(GLES20.GL_VERTEX_SHADER, v)
            val fs = compile(GLES20.GL_FRAGMENT_SHADER, f)
            val prog = GLES20.glCreateProgram()
            GLES20.glAttachShader(prog, vs)
            GLES20.glAttachShader(prog, fs)
            GLES20.glLinkProgram(prog)
            return prog
        }
    }
}
