package com.vyntara.editor.editor.media

import android.content.Context
import android.net.Uri
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioLane(
    val clipId: String,
    val uri: String,
    val volume: Float
)

/**
 * Primary video player + parallel audio lanes (ExoPlayer per unmuted audio/video-with-audio clip).
 * Timeline clock drives seeks; volumes applied per lane.
 */
class PlaybackEngine(context: Context) {

    private val appContext = context.applicationContext
    private var videoPlayer: ExoPlayer? = null
    private val audioPlayers = mutableMapOf<String, ExoPlayer>()
    private var currentVideoUri: String? = null

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
        }
    }

    private fun ensureVideo(): ExoPlayer {
        videoPlayer?.let { return it }
        return ExoPlayer.Builder(appContext).build().also {
            it.addListener(listener)
            it.repeatMode = Player.REPEAT_MODE_OFF
            videoPlayer = it
        }
    }

    fun setVideo(uri: String?, autoPlay: Boolean) {
        if (uri == null) {
            videoPlayer?.stop()
            videoPlayer?.clearMediaItems()
            currentVideoUri = null
            return
        }
        if (uri == currentVideoUri && videoPlayer != null) {
            if (autoPlay) videoPlayer?.play() else videoPlayer?.pause()
            return
        }
        currentVideoUri = uri
        val p = ensureVideo()
        p.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
        p.prepare()
        p.playWhenReady = autoPlay
    }

    fun syncAudioLanes(lanes: List<AudioLane>, localTimeMsByClip: Map<String, Long>, playing: Boolean) {
        val wanted = lanes.map { it.clipId }.toSet()
        // Release obsolete
        audioPlayers.keys.filter { it !in wanted }.toList().forEach { id ->
            audioPlayers.remove(id)?.let {
                it.stop()
                it.release()
            }
        }
        for (lane in lanes) {
            val p = audioPlayers.getOrPut(lane.clipId) {
                ExoPlayer.Builder(appContext).build().also { player ->
                    player.setMediaItem(MediaItem.fromUri(Uri.parse(lane.uri)))
                    player.prepare()
                }
            }
            p.volume = lane.volume.coerceIn(0f, 1f)
            val seek = localTimeMsByClip[lane.clipId] ?: 0L
            if (kotlin.math.abs(p.currentPosition - seek) > 120) {
                p.seekTo(seek.coerceAtLeast(0))
            }
            if (playing && !p.isPlaying) p.play()
            if (!playing && p.isPlaying) p.pause()
        }
    }

    fun attachSurface(surface: Surface?) {
        ensureVideo().setVideoSurface(surface)
    }

    fun play() {
        videoPlayer?.play()
        audioPlayers.values.forEach { it.play() }
    }

    fun pause() {
        videoPlayer?.pause()
        audioPlayers.values.forEach { it.pause() }
    }

    fun seekVideoMs(ms: Long) {
        videoPlayer?.seekTo(ms.coerceAtLeast(0))
        _positionMs.value = ms.coerceAtLeast(0)
    }

    fun pollPosition() {
        val p = videoPlayer ?: return
        _positionMs.value = p.currentPosition
        _isPlaying.value = p.isPlaying
    }

    fun release() {
        videoPlayer?.removeListener(listener)
        videoPlayer?.release()
        videoPlayer = null
        audioPlayers.values.forEach { it.release() }
        audioPlayers.clear()
        currentVideoUri = null
        _isPlaying.value = false
    }
}
