package com.vyntara.editor.editor.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.view.Surface
import com.vyntara.editor.editor.EditorClip
import com.vyntara.editor.editor.EditorDocument
import com.vyntara.editor.editor.LayerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

data class ExportProgress(
    val frame: Int,
    val totalFrames: Int,
    val message: String
) {
    val fraction: Float get() = if (totalFrames <= 0) 0f else frame.toFloat() / totalFrames
}

/**
 * Real H.264 export via MediaCodec + MediaMuxer.
 * Each frame: composite bitmaps from media (retriever/image) + EffectRenderer, draw to codec input surface.
 * Audio: not muxed in v1 of this pipeline (video-only); documented limitation.
 */
object ExportPipeline {

    suspend fun export(
        context: Context,
        doc: EditorDocument,
        outputFile: File,
        onProgress: (ExportProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.Default) {
        runCatching {
            val width = doc.width.coerceIn(16, 3840).let { it - it % 2 }
            val height = doc.height.coerceIn(16, 2160).let { it - it % 2 }
            val fps = doc.fps.coerceIn(12, 60)
            val durationSec = doc.durationSec.coerceAtLeast(0.5f)
            val totalFrames = (durationSec * fps).toInt().coerceAtLeast(1)
            val bitRate = (width * height * fps * 0.15).toInt().coerceIn(1_000_000, 12_000_000)

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface: Surface = codec.createInputSurface()
            codec.start()

            if (outputFile.exists()) outputFile.delete()
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false
            val bufferInfo = MediaCodec.BufferInfo()

            val framePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val bgPaint = Paint().apply { color = (doc.backgroundArgb and 0xFFFFFFFFL).toInt() }

            try {
                for (frameIdx in 0 until totalFrames) {
                    if (!coroutineContext.isActive) break
                    val timeSec = frameIdx.toFloat() / fps

                    onProgress(
                        ExportProgress(
                            frameIdx,
                            totalFrames,
                            "Frame ${frameIdx + 1}/$totalFrames"
                        )
                    )

                    // Draw composition onto encoder surface
                    val canvas = inputSurface.lockCanvas(null)
                    try {
                        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
                        val clips = doc.clips
                            .filter { it.visible && it.type != LayerType.SOLID }
                            .filter { timeSec >= it.startSec && timeSec < it.endSec }
                            .sortedBy { it.trackIndex }
                        for (clip in clips) {
                            val uri = clip.mediaUri ?: continue
                            val localMs = ((timeSec - clip.startSec) * 1000f).toLong().coerceAtLeast(0)
                            var bmp = MediaProbe.decodePreviewFrame(
                                context,
                                Uri.parse(uri),
                                localMs,
                                maxSide = maxOf(width, height)
                            ) ?: continue
                            bmp = EffectRenderer.apply(bmp, clip)
                            val dst = fitRect(bmp.width, bmp.height, width, height, clip)
                            canvas.drawBitmap(bmp, null, dst, framePaint)
                            if (!bmp.isRecycled) bmp.recycle()
                        }
                    } finally {
                        inputSurface.unlockCanvasAndPost(canvas)
                    }

                    // Drain encoder
                    var outputDone = false
                    while (!outputDone) {
                        val outIndex = codec.dequeueOutputBuffer(bufferInfo, 5_000)
                        when {
                            outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> outputDone = true
                            outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                if (muxerStarted) error("format changed twice")
                                trackIndex = muxer.addTrack(codec.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                            outIndex >= 0 -> {
                                val encoded = codec.getOutputBuffer(outIndex)
                                    ?: error("null output buffer")
                                if (bufferInfo.size > 0 && muxerStarted) {
                                    bufferInfo.presentationTimeUs =
                                        (frameIdx * 1_000_000L / fps)
                                    muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                                }
                                codec.releaseOutputBuffer(outIndex, false)
                                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                    outputDone = true
                                } else {
                                    outputDone = true // one packet per frame typically
                                }
                            }
                        }
                    }
                }

                codec.signalEndOfInputStream()
                // Final drain
                var eos = false
                while (!eos) {
                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, 50_000)
                    if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !muxerStarted) {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                        continue
                    }
                    if (outIndex >= 0) {
                        val encoded = codec.getOutputBuffer(outIndex)
                        if (encoded != null && bufferInfo.size > 0 && muxerStarted) {
                            muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                        }
                        eos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outIndex, false)
                    }
                }
            } finally {
                try { codec.stop() } catch (_: Exception) {}
                try { codec.release() } catch (_: Exception) {}
                try { inputSurface.release() } catch (_: Exception) {}
                if (muxerStarted) {
                    try { muxer.stop() } catch (_: Exception) {}
                }
                try { muxer.release() } catch (_: Exception) {}
            }

            onProgress(ExportProgress(totalFrames, totalFrames, "Done"))
            outputFile
        }
    }

    private fun fitRect(
        bw: Int,
        bh: Int,
        cw: Int,
        ch: Int,
        clip: EditorClip
    ): Rect {
        val scale = minOf(cw.toFloat() / bw, ch.toFloat() / bh) * clip.scaleX.coerceIn(0.1f, 3f)
        val dw = (bw * scale).toInt().coerceAtLeast(1)
        val dh = (bh * scale).toInt().coerceAtLeast(1)
        val cx = (clip.posX * cw).toInt()
        val cy = (clip.posY * ch).toInt()
        val left = cx - dw / 2
        val top = cy - dh / 2
        return Rect(left, top, left + dw, top + dh)
    }
}
