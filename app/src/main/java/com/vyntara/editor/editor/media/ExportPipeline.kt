package com.vyntara.editor.editor.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.view.Surface
import com.vyntara.editor.editor.EditorClip
import com.vyntara.editor.editor.EditorDocument
import com.vyntara.editor.editor.KeyframeEval
import com.vyntara.editor.editor.LayerType
import com.vyntara.editor.editor.scene3d.Scene3D
import com.vyntara.editor.editor.scene3d.Scene3DCompositor
import com.vyntara.editor.editor.media.TextRenderer
import com.vyntara.editor.editor.media.SolidRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

data class ExportProgress(
    val frame: Int,
    val totalFrames: Int,
    val message: String
) {
    val fraction: Float get() = if (totalFrames <= 0) 0f else frame.toFloat() / totalFrames
}

/**
 * H.264 video + optional AAC audio from source clip via MediaExtractor copy.
 * Composition frames use KeyframeEval + EffectRenderer (same as preview path).
 */
object ExportPipeline {

    suspend fun export(
        context: Context,
        doc: EditorDocument,
        outputFile: File,
        onProgress: (ExportProgress) -> Unit,
        settingsWidth: Int? = null,
        settingsHeight: Int? = null,
        settingsFps: Int? = null,
        includeAudio: Boolean = true,
        scene3d: Scene3D? = null,
        isCancelled: () -> Boolean = { false }
    ): Result<File> = withContext(Dispatchers.Default) {
        runCatching {
            val width = (settingsWidth ?: doc.width).coerceIn(16, 3840).let { it - it % 2 }
            val height = (settingsHeight ?: doc.height).coerceIn(16, 2160).let { it - it % 2 }
            val fps = (settingsFps ?: doc.fps).coerceIn(12, 60)
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
            var videoTrack = -1
            var audioTrack = -1
            var muxerStarted = false
            val bufferInfo = MediaCodec.BufferInfo()

            val framePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val bgPaint = Paint().apply { color = (doc.backgroundArgb and 0xFFFFFFFFL).toInt() }

            // Prepare audio extractor if a unmuted audio/video source exists
            val audioSource = doc.clips.firstOrNull {
                it.visible && !it.muted && it.mediaUri != null &&
                    (it.type == LayerType.VIDEO || it.type == LayerType.AUDIO)
            }
            var audioExtractor: MediaExtractor? = null
            var audioFormat: MediaFormat? = null
            if (includeAudio && audioSource?.mediaUri != null) {
                try {
                    val ex = MediaExtractor()
                    ex.setDataSource(context, Uri.parse(audioSource.mediaUri), null)
                    for (i in 0 until ex.trackCount) {
                        val f = ex.getTrackFormat(i)
                        val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                        if (mime.startsWith("audio/")) {
                            ex.selectTrack(i)
                            audioFormat = f
                            audioExtractor = ex
                            break
                        }
                    }
                    if (audioExtractor == null) ex.release()
                } catch (_: Exception) {
                    audioExtractor = null
                    audioFormat = null
                }
            }

            try {
                for (frameIdx in 0 until totalFrames) {
                    if (!coroutineContext.isActive || isCancelled()) break
                    val timeSec = frameIdx.toFloat() / fps
                    onProgress(ExportProgress(frameIdx, totalFrames, "Frame ${frameIdx + 1}/$totalFrames"))

                    val canvas = inputSurface.lockCanvas(null)
                    try {
                        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
                        val clips = doc.clips
                            .filter { it.visible && it.type != LayerType.SOLID && it.type != LayerType.AUDIO }
                            .filter { timeSec >= it.startSec && timeSec < it.endSec }
                            .filter { doc.soloTrack == null || it.trackIndex == doc.soloTrack }
                            .sortedBy { it.trackIndex }
                        for (raw in clips) {
                            val clip = KeyframeEval.resolveClip(raw, timeSec)
                            if (clip.type == LayerType.TEXT) {
                                val tb = TextRenderer.render(clip, width, height)
                                canvas.drawBitmap(tb, 0f, 0f, framePaint)
                                tb.recycle()
                                continue
                            }
                            if (clip.type == LayerType.SOLID) {
                                val sb = SolidRenderer.render(clip, width, height)
                                canvas.drawBitmap(sb, 0f, 0f, framePaint)
                                sb.recycle()
                                continue
                            }
                            val uri = clip.mediaUri ?: continue
                            val speed = clip.speed.coerceIn(0.25f, 4f)
                            val localMs = (((timeSec - clip.startSec) * speed) * 1000f).toLong().coerceAtLeast(0)
                            var bmp = MediaProbe.decodePreviewFrame(
                                context, Uri.parse(uri), localMs, maxSide = maxOf(width, height)
                            ) ?: continue
                            bmp = EffectRenderer.apply(bmp, clip)
                            val dst = fitRect(bmp.width, bmp.height, width, height, clip)
                            canvas.drawBitmap(bmp, null, dst, framePaint)
                            if (!bmp.isRecycled) bmp.recycle()
                        }
                        // Real 3D primitive projection into export frame
                        scene3d?.let { Scene3DCompositor.draw(canvas, it, width, height, timeSec) }
                    } finally {
                        inputSurface.unlockCanvasAndPost(canvas)
                    }

                    var drained = false
                    while (!drained) {
                        val outIndex = codec.dequeueOutputBuffer(bufferInfo, 5_000)
                        when {
                            outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> drained = true
                            outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                if (!muxerStarted) {
                                    videoTrack = muxer.addTrack(codec.outputFormat)
                                    audioFormat?.let { audioTrack = muxer.addTrack(it) }
                                    muxer.start()
                                    muxerStarted = true
                                }
                            }
                            outIndex >= 0 -> {
                                val encoded = codec.getOutputBuffer(outIndex)
                                if (encoded != null && bufferInfo.size > 0 && muxerStarted) {
                                    bufferInfo.presentationTimeUs = frameIdx * 1_000_000L / fps
                                    muxer.writeSampleData(videoTrack, encoded, bufferInfo)
                                }
                                codec.releaseOutputBuffer(outIndex, false)
                                drained = true
                            }
                        }
                    }
                }

                codec.signalEndOfInputStream()
                var eos = false
                while (!eos) {
                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, 50_000)
                    when {
                        outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !muxerStarted -> {
                            videoTrack = muxer.addTrack(codec.outputFormat)
                            audioFormat?.let { audioTrack = muxer.addTrack(it) }
                            muxer.start()
                            muxerStarted = true
                        }
                        outIndex >= 0 -> {
                            val encoded = codec.getOutputBuffer(outIndex)
                            if (encoded != null && bufferInfo.size > 0 && muxerStarted) {
                                muxer.writeSampleData(videoTrack, encoded, bufferInfo)
                            }
                            eos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(outIndex, false)
                        }
                    }
                }

                // Copy audio samples (compressed) into muxer
                if (muxerStarted && audioExtractor != null && audioTrack >= 0) {
                    onProgress(ExportProgress(totalFrames, totalFrames, "Muxing audio…"))
                    val buf = ByteBuffer.allocate(256 * 1024)
                    val info = MediaCodec.BufferInfo()
                    audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    while (true) {
                        val size = audioExtractor.readSampleData(buf, 0)
                        if (size < 0) break
                        info.offset = 0
                        info.size = size
                        info.presentationTimeUs = audioExtractor.sampleTime
                        info.flags = audioExtractor.sampleFlags
                        // Stop if beyond composition duration
                        if (info.presentationTimeUs > durationSec * 1_000_000L) break
                        muxer.writeSampleData(audioTrack, buf, info)
                        audioExtractor.advance()
                    }
                }
            } finally {
                try { codec.stop() } catch (_: Exception) {}
                try { codec.release() } catch (_: Exception) {}
                try { inputSurface.release() } catch (_: Exception) {}
                try { audioExtractor?.release() } catch (_: Exception) {}
                if (muxerStarted) {
                    try { muxer.stop() } catch (_: Exception) {}
                }
                try { muxer.release() } catch (_: Exception) {}
            }

            onProgress(ExportProgress(totalFrames, totalFrames, "Done"))
            outputFile
        }
    }

    private fun fitRect(bw: Int, bh: Int, cw: Int, ch: Int, clip: EditorClip): Rect {
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
