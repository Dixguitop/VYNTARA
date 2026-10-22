package com.vyntara.editor.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vyntara.editor.editor.effects.EffectCatalog
import com.vyntara.editor.editor.effects.EffectStatus
import com.vyntara.editor.editor.media.EffectRenderer
import com.vyntara.editor.editor.media.ExportPipeline
import com.vyntara.editor.editor.media.ExportProgress
import com.vyntara.editor.editor.media.MediaProbe
import com.vyntara.editor.editor.media.PlaybackEngine
import com.vyntara.editor.project.ProjectSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val undoStack = ArrayDeque<EditorDocument>(32)
    private val redoStack = ArrayDeque<EditorDocument>(32)
    private val playback = PlaybackEngine(app)

    private val _doc = MutableStateFlow(
        EditorDocument(
            projectId = "",
            title = "",
            width = 1920,
            height = 1080,
            fps = 30,
            backgroundArgb = 0xFF000000L
        )
    )
    val document: StateFlow<EditorDocument> = _doc.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _exportProgress = MutableStateFlow<ExportProgress?>(null)
    val exportProgress: StateFlow<ExportProgress?> = _exportProgress.asStateFlow()

    private val _lastExportPath = MutableStateFlow<String?>(null)
    val lastExportPath: StateFlow<String?> = _lastExportPath.asStateFlow()

    private var previewJob: Job? = null
    private var playJob: Job? = null

    fun clearToast() { _toast.value = null }

    fun load(project: ProjectSummary) {
        stopPlayback()
        val bg = EditorClip(
            id = "bg",
            name = "Background",
            type = LayerType.SOLID,
            trackIndex = 0,
            startSec = 0f,
            durationSec = 10f,
            locked = true
        )
        _doc.value = EditorDocument(
            projectId = project.id,
            title = project.title,
            width = project.width,
            height = project.height,
            fps = project.fps,
            backgroundArgb = project.backgroundArgb,
            durationSec = 10f,
            clips = listOf(bg),
            selectedIds = emptySet(),
            saveStatus = SaveStatus.SAVED
        )
        undoStack.clear()
        redoStack.clear()
        _previewBitmap.value = null
        _exportProgress.value = null
        _lastExportPath.value = null
    }

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            _importing.value = true
            try {
                val probe = MediaProbe.probe(getApplication(), uri)
                val type = when {
                    probe.isVideo -> LayerType.VIDEO
                    probe.isAudio -> LayerType.AUDIO
                    probe.isImage -> LayerType.IMAGE
                    else -> LayerType.VIDEO
                }
                val durationSec = when {
                    probe.durationMs != null && probe.durationMs > 0 ->
                        (probe.durationMs / 1000f).coerceAtLeast(0.1f)
                    probe.isImage -> 3f
                    else -> 5f
                }
                val track = when (type) {
                    LayerType.AUDIO ->
                        (document.value.clips.filter { it.type == LayerType.AUDIO }
                            .maxOfOrNull { it.trackIndex } ?: 9) + 1
                    else ->
                        (document.value.clips.filter { it.type != LayerType.AUDIO }
                            .maxOfOrNull { it.trackIndex } ?: 0) + 1
                }
                val clip = EditorClip(
                    id = UUID.randomUUID().toString().take(8),
                    name = probe.displayName.take(28),
                    type = type,
                    trackIndex = track,
                    startSec = document.value.playheadSec,
                    durationSec = durationSec,
                    mediaUri = probe.uri,
                    mediaWidth = probe.width,
                    mediaHeight = probe.height,
                    sourceDurationSec = probe.durationMs?.let { it / 1000f }
                )
                dispatch(EditorAction.AddClip(clip))
                val meta = buildString {
                    append(probe.displayName)
                    probe.width?.let { w -> probe.height?.let { h -> append(" · ${w}x$h") } }
                    probe.durationMs?.let { append(" · ${"%.1f".format(it / 1000f)}s") }
                }
                _toast.value = "Imported: $meta"
                refreshPreview()
            } catch (e: Exception) {
                _toast.value = "Import failed: ${e.message ?: "error"}"
            } finally {
                _importing.value = false
            }
        }
    }

    /** Active A/V clip under playhead for ExoPlayer. */
    private fun primaryMediaClip(doc: EditorDocument = document.value): EditorClip? {
        return doc.clips
            .filter { it.visible && it.mediaUri != null && (it.type == LayerType.VIDEO || it.type == LayerType.AUDIO) }
            .filter { doc.playheadSec >= it.startSec && doc.playheadSec < it.endSec }
            .maxByOrNull { it.trackIndex }
    }

    fun refreshPreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val doc = document.value
            // While ExoPlayer is playing video, still update effect overlay stills less often
            val active = doc.clips
                .filter { it.visible && it.type != LayerType.SOLID && it.mediaUri != null }
                .filter { doc.playheadSec >= it.startSec && doc.playheadSec < it.endSec }
                .maxByOrNull { it.trackIndex }
            if (active?.mediaUri == null) {
                _previewBitmap.value = null
                return@launch
            }
            val localTimeMs = ((doc.playheadSec - active.startSec) * 1000f).toLong().coerceAtLeast(0)
            var bmp = MediaProbe.decodePreviewFrame(
                getApplication(),
                Uri.parse(active.mediaUri),
                localTimeMs,
                maxSide = 720
            ) ?: return@launch
            bmp = EffectRenderer.apply(bmp, active)
            _previewBitmap.value = bmp
        }
    }

    fun applyEffect(effectId: String) {
        val def = EffectCatalog.all.find { it.id == effectId } ?: return
        if (def.status != EffectStatus.READY) {
            _toast.value = "Not ready: ${def.name}"
            return
        }
        val clipId = document.value.selectedIds.firstOrNull() ?: run {
            _toast.value = "Select a clip first"
            return
        }
        dispatch(
            EditorAction.AddEffect(
                clipId,
                EffectInstance(
                    id = UUID.randomUUID().toString().take(8),
                    name = def.nameEn,
                    category = def.category,
                    intensity = def.defaultIntensity,
                    radius = def.defaultRadius
                )
            )
        )
        _toast.value = "Applied: ${def.name}"
        refreshPreview()
    }

    fun stepFrame(delta: Int) {
        stopPlayback()
        val fps = document.value.fps.coerceAtLeast(1)
        val step = 1f / fps
        dispatch(EditorAction.SetPlayhead(document.value.playheadSec + delta * step))
        refreshPreview()
    }

    fun togglePlay() {
        val doc = document.value
        if (doc.isPlaying) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        val doc = document.value
        val clip = primaryMediaClip(doc)
        playJob?.cancel()
        _doc.update { it.copy(isPlaying = true) }

        if (clip?.mediaUri != null && (clip.type == LayerType.VIDEO || clip.type == LayerType.AUDIO)) {
            // Fluid player path with A/V sync
            val localMs = ((doc.playheadSec - clip.startSec) * 1000f).toLong().coerceAtLeast(0)
            playback.setMedia(clip.mediaUri, autoPlay = true)
            playback.seekToMs(localMs)
            playJob = viewModelScope.launch {
                while (isActive && document.value.isPlaying) {
                    playback.pollPosition()
                    val pos = playback.positionMs.value / 1000f
                    val timelinePos = (clip.startSec + pos).coerceIn(0f, document.value.durationSec)
                    _doc.update { it.copy(playheadSec = timelinePos) }
                    // Still frame with effects ~5 fps while playing (player shows smooth video separately if surface used)
                    if (System.currentTimeMillis() % 200 < 50) refreshPreview()
                    if (timelinePos >= clip.endSec - 0.04f) {
                        if (document.value.loop) {
                            playback.seekToMs(0)
                            _doc.update { it.copy(playheadSec = clip.startSec) }
                        } else {
                            stopPlayback()
                            break
                        }
                    }
                    delay(16)
                }
            }
        } else {
            // Image / no media: clock only + frame refresh
            playJob = viewModelScope.launch {
                while (isActive && document.value.isPlaying) {
                    delay(33)
                    val next = document.value.playheadSec + 0.033f
                    if (next >= document.value.durationSec) {
                        if (document.value.loop) {
                            _doc.update { it.copy(playheadSec = 0f) }
                        } else {
                            _doc.update { it.copy(playheadSec = document.value.durationSec, isPlaying = false) }
                            break
                        }
                    } else {
                        _doc.update { it.copy(playheadSec = next) }
                    }
                    refreshPreview()
                }
            }
        }
    }

    private fun stopPlayback() {
        playJob?.cancel()
        playJob = null
        playback.pause()
        _doc.update { it.copy(isPlaying = false) }
    }

    fun export() {
        if (_exportProgress.value != null &&
            (_exportProgress.value?.frame ?: 0) < (_exportProgress.value?.totalFrames ?: 1)
        ) {
            _toast.value = "Export already running"
            return
        }
        viewModelScope.launch {
            stopPlayback()
            val doc = document.value
            val movies = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: getApplication<Application>().filesDir
            if (!movies.exists()) movies.mkdirs()
            val out = File(movies, "vyntara_${doc.projectId}_${System.currentTimeMillis()}.mp4")
            _exportProgress.value = ExportProgress(0, 1, "Starting…")
            val result = ExportPipeline.export(getApplication(), doc, out) { p ->
                _exportProgress.value = p
            }
            result.onSuccess { file ->
                _lastExportPath.value = file.absolutePath
                _exportProgress.value = ExportProgress(1, 1, "Saved")
                _toast.value = "Exported: ${file.name}"
                dispatch(EditorAction.MarkSaved)
            }.onFailure { e ->
                _exportProgress.value = null
                _toast.value = "Export failed: ${e.message ?: "error"}"
            }
        }
    }

    fun dispatch(action: EditorAction) {
        when (action) {
            is EditorAction.Undo -> {
                if (undoStack.isNotEmpty()) {
                    redoStack.addLast(_doc.value)
                    publish(undoStack.removeLast())
                }
                return
            }
            is EditorAction.Redo -> {
                if (redoStack.isNotEmpty()) {
                    undoStack.addLast(_doc.value)
                    publish(redoStack.removeLast())
                }
                return
            }
            is EditorAction.SetPlayhead -> {
                _doc.update { it.copy(playheadSec = action.sec.coerceIn(0f, it.durationSec)) }
                val clip = primaryMediaClip()
                if (clip?.mediaUri != null && !document.value.isPlaying) {
                    val localMs = ((action.sec - clip.startSec) * 1000f).toLong().coerceAtLeast(0)
                    playback.setMedia(clip.mediaUri, autoPlay = false)
                    playback.seekToMs(localMs)
                }
                return
            }
            is EditorAction.TogglePlay -> {
                togglePlay()
                return
            }
            is EditorAction.ToggleLoop -> {
                _doc.update { it.copy(loop = !it.loop) }
                return
            }
            is EditorAction.ToggleGrid -> {
                _doc.update { it.copy(showGrid = !it.showGrid) }
                return
            }
            is EditorAction.ToggleSnap -> {
                _doc.update { it.copy(snap = !it.snap) }
                return
            }
            is EditorAction.SetZoom -> {
                _doc.update { it.copy(timelineZoom = action.zoom.coerceIn(0.5f, 4f)) }
                return
            }
            is EditorAction.Select -> {
                _doc.update { doc ->
                    val ids = when {
                        action.id == null -> emptySet()
                        action.multi -> doc.selectedIds + action.id
                        else -> setOf(action.id)
                    }
                    doc.copy(selectedIds = ids)
                }
                return
            }
            is EditorAction.MarkSaved -> {
                _doc.update { it.copy(saveStatus = SaveStatus.SAVED) }
                return
            }
            else -> {}
        }

        val before = _doc.value
        val after = applyStructural(before, action) ?: return
        if (after != before) {
            undoStack.addLast(before)
            if (undoStack.size > 30) undoStack.removeFirst()
            redoStack.clear()
            publish(after.copy(saveStatus = SaveStatus.DIRTY))
        }
    }

    private fun publish(doc: EditorDocument) {
        _doc.value = doc.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty())
    }

    private fun applyStructural(doc: EditorDocument, action: EditorAction): EditorDocument? {
        return when (action) {
            is EditorAction.UpdateClip -> doc.copy(
                clips = doc.clips.map { if (it.id == action.clip.id) action.clip else it }
            )
            is EditorAction.AddClip -> doc.copy(
                clips = doc.clips + action.clip,
                selectedIds = setOf(action.clip.id),
                durationSec = maxOf(doc.durationSec, action.clip.endSec + 1f)
            )
            is EditorAction.DeleteSelected -> {
                val ids = action.ids.filter { id -> doc.clips.any { it.id == id && !it.locked } }.toSet()
                doc.copy(clips = doc.clips.filterNot { it.id in ids }, selectedIds = emptySet())
            }
            is EditorAction.SplitAtPlayhead -> {
                val clip = doc.clips.find { it.id == action.id } ?: return null
                if (doc.playheadSec <= clip.startSec + 0.05f || doc.playheadSec >= clip.endSec - 0.05f) return null
                val leftDur = doc.playheadSec - clip.startSec
                val rightDur = clip.endSec - doc.playheadSec
                val left = clip.copy(durationSec = leftDur)
                val right = clip.copy(
                    id = UUID.randomUUID().toString().take(8),
                    name = clip.name + " B",
                    startSec = doc.playheadSec,
                    durationSec = rightDur,
                    mediaUri = clip.mediaUri,
                    mediaWidth = clip.mediaWidth,
                    mediaHeight = clip.mediaHeight,
                    sourceDurationSec = clip.sourceDurationSec
                )
                doc.copy(
                    clips = doc.clips.flatMap { if (it.id == clip.id) listOf(left, right) else listOf(it) },
                    selectedIds = setOf(right.id)
                )
            }
            is EditorAction.AddEffect -> doc.copy(
                clips = doc.clips.map { c ->
                    if (c.id != action.clipId) c
                    else c.copy(
                        effects = c.effects + action.effect,
                        smartBlur = c.smartBlur || action.effect.name.contains("Blur", true),
                        blurRadius = if (action.effect.name.contains("Blur", true)) action.effect.radius else c.blurRadius
                    )
                }
            )
            is EditorAction.ToggleEffect -> doc.copy(
                clips = doc.clips.map { c ->
                    if (c.id != action.clipId) c
                    else c.copy(effects = c.effects.map {
                        if (it.id == action.effectId) it.copy(enabled = !it.enabled) else it
                    })
                }
            )
            is EditorAction.SetTitle -> doc.copy(title = action.title)
            else -> null
        }
    }

    override fun onCleared() {
        stopPlayback()
        playback.release()
        super.onCleared()
    }
}
