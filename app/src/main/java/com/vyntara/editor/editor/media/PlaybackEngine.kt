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

/**
 * Fluid playback with audio/video sync via Media3 ExoPlayer.
 * One primary media URI at a time (active video/audio clip).
 * Timeline playhead is driven by player position when playing.
 */
class PlaybackEngine(context: Context) {

    private val appContext = context.applicationContext
    private var player: ExoPlayer? = null
    private var currentUri: String? = null

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _ready.value = playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING
        }
    }

    private fun ensurePlayer(): ExoPlayer {
        val existing = player
        if (existing != null) return existing
        val p = ExoPlayer.Builder(appContext).build().also {
            it.addListener(listener)
            it.repeatMode = Player.REPEAT_MODE_OFF
        }
        player = p
        return p
    }

    fun setMedia(uri: String?, autoPlay: Boolean = false) {
        if (uri == null) {
            player?.stop()
            player?.clearMediaItems()
            currentUri = null
            _ready.value = false
            return
        }
        if (uri == currentUri && player != null) {
            if (autoPlay) player?.play()
            return
        }
        currentUri = uri
        val p = ensurePlayer()
        p.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
        p.prepare()
        p.playWhenReady = autoPlay
    }

    fun attachSurface(surface: Surface?) {
        ensurePlayer().setVideoSurface(surface)
    }

    fun play() {
        ensurePlayer().play()
    }

    fun pause() {
        player?.pause()
    }

    fun toggle() {
        val p = ensurePlayer()
        if (p.isPlaying) p.pause() else p.play()
    }

    fun seekToMs(ms: Long) {
        player?.seekTo(ms.coerceAtLeast(0))
        _positionMs.value = ms.coerceAtLeast(0)
    }

    fun pollPosition() {
        val p = player ?: return
        _positionMs.value = p.currentPosition
        _isPlaying.value = p.isPlaying
    }

    fun release() {
        player?.removeListener(listener)
        player?.release()
        player = null
        currentUri = null
        _ready.value = false
        _isPlaying.value = false
    }
}
