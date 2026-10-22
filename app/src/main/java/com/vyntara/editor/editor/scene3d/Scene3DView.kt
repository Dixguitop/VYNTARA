package com.vyntara.editor.editor.scene3d

import android.opengl.GLSurfaceView
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Compose wrapper for real GL viewport with orbit drag.
 */
@Composable
fun Scene3DViewport(
    scene: Scene3D,
    onSceneChange: (Scene3D) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val renderer = remember { GlSceneRenderer() }
    renderer.scene = scene

    DisposableEffect(Unit) {
        onDispose { }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            GLSurfaceView(context).apply {
                setEGLContextClientVersion(2)
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                var lastX = 0f
                var lastY = 0f
                setOnTouchListener { _, e ->
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            lastX = e.x; lastY = e.y
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = e.x - lastX
                            val dy = e.y - lastY
                            lastX = e.x; lastY = e.y
                            val s = renderer.scene
                            onSceneChange(
                                s.copy(
                                    cameraYaw = s.cameraYaw + dx * 0.4f,
                                    cameraPitch = (s.cameraPitch + dy * 0.3f).coerceIn(-80f, 80f)
                                )
                            )
                            true
                        }
                        else -> false
                    }
                }
            }
        },
        update = {
            renderer.scene = scene
            it.requestRender()
        }
    )
}
