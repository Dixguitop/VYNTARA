package com.vyntara.editor.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vyntara.editor.editor.effects.EffectCatalog
import com.vyntara.editor.editor.scene3d.Object3D
import com.vyntara.editor.editor.scene3d.Object3DType
import com.vyntara.editor.editor.scene3d.Scene3D
import com.vyntara.editor.editor.scene3d.Scene3DStore
import com.vyntara.editor.editor.scene3d.GltfLoader
import com.vyntara.editor.editor.scene3d.Vec3
import com.vyntara.editor.editor.effects.EffectStatus
import com.vyntara.editor.editor.media.EffectRenderer
import com.vyntara.editor.editor.media.TextRenderer
import com.vyntara.editor.editor.media.SolidRenderer
import com.vyntara.editor.editor.media.ExportPipeline
import com.vyntara.editor.editor.media.ExportProgress
import com.vyntara.editor.editor.media.MediaProbe
import com.vyntara.editor.editor.media.PlaybackEngine
import com.vyntara.editor.editor.media.AudioLane
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
    private val compositionStore = CompositionStore(app)

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
        // Restore saved composition if present
        val restored = compositionStore.load(project.id, _doc.value)
        _doc.value = restored.copy(canUndo = false, canRedo = false)
        val sceneJson = compositionStore.loadSceneJson(project.id)
        _scene3d.value = if (sceneJson != null) {
            try {
                Scene3DStore.fromJson(org.json.JSONObject(sceneJson))
            } catch (_: Exception) {
                Scene3D.demo()
            }
        } else Scene3D.demo()
        _exportSettings.value = ExportSettings(
            width = restored.width,
            height = restored.height,
            fps = restored.fps,
            includeAudio = true
        )
        refreshPreview()
    }

    fun saveComposition() {
        val doc = document.value
        _doc.update { it.copy(saveStatus = SaveStatus.SAVING) }
        val ok = compositionStore.save(doc.copy(saveStatus = SaveStatus.SAVED))
        val sceneOk = compositionStore.saveScene(
            doc.projectId,
            Scene3DStore.toJson(_scene3d.value).toString()
        )
        _doc.update {
            it.copy(saveStatus = if (ok && sceneOk) SaveStatus.SAVED else SaveStatus.ERROR)
        }
        _toast.value = if (ok && sceneOk) "Project saved" else "Save failed"
    }

    fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(1500)
            if (document.value.saveStatus == SaveStatus.DIRTY) {
                saveComposition()
            }
        }
    }

    fun updateExportSettings(settings: ExportSettings) {
        _exportSettings.value = settings
    }

    fun duplicateSelected() {
        val clip = document.value.selected ?: return
        if (clip.locked) {
            _toast.value = "Locked clip"
            return
        }
        val copy = clip.copy(
            id = java.util.UUID.randomUUID().toString().take(8),
            name = clip.name + " copy",
            startSec = clip.endSec,
            trackIndex = clip.trackIndex
        )
        dispatch(EditorAction.AddClip(copy))
    }

    fun updateScene3d(scene: Scene3D) {
        _scene3d.value = scene
        _doc.update { it.copy(saveStatus = SaveStatus.DIRTY) }
        scheduleAutosave()
    }

    fun importGltf(uri: android.net.Uri) {
        viewModelScope.launch {
            val result = GltfLoader.loadJson(getApplication(), uri)
            if (result.objects.isEmpty()) {
                _toast.value = result.warnings.firstOrNull() ?: "No mesh in glTF"
                return@launch
            }
            val s = _scene3d.value
            _scene3d.value = s.copy(
                objects = s.objects + result.objects,
                importedMeshes = s.importedMeshes + result.meshes,
                selectedId = result.objects.firstOrNull()?.id
            )
            _doc.update { it.copy(saveStatus = SaveStatus.DIRTY) }
            scheduleAutosave()
            val w = if (result.warnings.isNotEmpty()) " (${result.warnings.size} warnings)" else ""
            _toast.value = "glTF: ${result.objects.size} objects$w"
        }
    }

    fun parentSelectedToNull() {
        val s = _scene3d.value
        val sel = s.selected ?: return
        val nullObj = Object3D(name = "Null_${sel.name}", type = Object3DType.NULL, position = sel.position)
        val child = sel.copy(parentId = nullObj.id)
        _scene3d.value = s.copy(
            objects = s.objects.map { if (it.id == sel.id) child else it } + nullObj,
            selectedId = nullObj.id
        )
        _doc.update { it.copy(saveStatus = SaveStatus.DIRTY) }
        scheduleAutosave()
        _toast.value = "Parent null created"
    }

    fun addParticleEmitter() {
        val em = Object3D(
            name = "Particles",
            type = Object3DType.PARTICLES,
            position = Vec3(0f, 0.2f, 0f),
            particles = com.vyntara.editor.editor.scene3d.ParticleEmitter()
        )
        val s = _scene3d.value
        _scene3d.value = s.copy(objects = s.objects + em, selectedId = em.id)
        _doc.update { it.copy(saveStatus = SaveStatus.DIRTY) }
        scheduleAutosave()
    }

    fun addKeyframe3d(property: String) {
        val s = _scene3d.value
        val obj = s.selected ?: run {
            _toast.value = "Select a 3D object"
            return
        }
        val t = document.value.playheadSec
        val base = when (property) {
            "posX" -> obj.position.x
            "posY" -> obj.position.y
            "posZ" -> obj.position.z
            "rotY" -> obj.rotation.y
            "scaleX" -> obj.scale.x
            else -> 0f
        }
        val existing = obj.keyframes[property].orEmpty().filter { kotlin.math.abs(it.timeSec - t) > 0.01f }
        val keys = (existing + Keyframe(t, base)).sortedBy { it.timeSec }
        val updated = obj.copy(keyframes = obj.keyframes + (property to keys))
        _scene3d.value = s.copy(objects = s.objects.map { if (it.id == obj.id) updated else it })
        _doc.update { it.copy(saveStatus = SaveStatus.DIRTY) }
        scheduleAutosave()
        _toast.value = "3D KF $property @ ${"%.2f".format(t)}s"
    }

    fun setViewportQuality(q: Int) {
        _scene3d.value = _scene3d.value.copy(viewportQuality = q.coerceIn(0, 2))
    }

    fun addObject3d(type: Object3DType) {
        val name = type.name.lowercase().replaceFirstChar { it.uppercase() }
        val obj = Object3D(
            name = name,
            type = type,
            position = Vec3(0f, if (type == Object3DType.PLANE) 0f else 0.5f, 0f)
        )
        val s = _scene3d.value
        _scene3d.value = s.copy(objects = s.objects + obj, selectedId = obj.id)
        _doc.update { it.copy(saveStatus = SaveStatus.DIRTY) }
    }

    fun addSolidClip(colorArgb: Int = 0xFFE11D48.toInt()) {
        val track = (document.value.clips.maxOfOrNull { it.trackIndex } ?: 0) + 1
        val clip = EditorClip(
            id = java.util.UUID.randomUUID().toString().take(8),
            name = "Solid",
            type = LayerType.SOLID,
            trackIndex = track,
            startSec = document.value.playheadSec,
            durationSec = 3f,
            textColorArgb = colorArgb,
            scaleX = 0.4f,
            scaleY = 0.25f,
            posX = 0.5f,
            posY = 0.5f
        )
        dispatch(EditorAction.AddClip(clip))
        _toast.value = "Solid layer"
    }

    fun addCameraKeyframe(property: String) {
        val s = _scene3d.value
        val tSec = document.value.playheadSec
        val base = when (property) {
            "yaw" -> s.cameraYaw
            "pitch" -> s.cameraPitch
            "distance" -> s.cameraDistance
            else -> 0f
        }
        val existing = s.cameraKeyframes[property].orEmpty().filter { kotlin.math.abs(it.timeSec - tSec) > 0.01f }
        val keys = (existing + Keyframe(tSec, base)).sortedBy { it.timeSec }
        _scene3d.value = s.copy(cameraKeyframes = s.cameraKeyframes + (property to keys))
        _doc.update { it.copy(saveStatus = SaveStatus.DIRTY) }
        scheduleAutosave()
        _toast.value = "Cam KF $property @ ${"%.2f".format(tSec)}s"
    }

    fun addTextClip() {
        val track = (document.value.clips.maxOfOrNull { it.trackIndex } ?: 0) + 1
        val clip = EditorClip(
            id = java.util.UUID.randomUUID().toString().take(8),
            name = "Text",
            type = LayerType.TEXT,
            trackIndex = track,
            startSec = document.value.playheadSec,
            durationSec = 3f
        )
        dispatch(EditorAction.AddClip(clip))
        _toast.value = "Text layer added"
    }

    fun addKeyframe(property: String) {
        val clip = document.value.selected ?: run {
            _toast.value = "Select a clip first"
            return
        }
        val t = document.value.playheadSec
        val base = when (property) {
            "opacity" -> clip.opacity
            "posX" -> clip.posX
            "posY" -> clip.posY
            "scaleX" -> clip.scaleX
            "scaleY" -> clip.scaleY
            "rotation" -> clip.rotation
            else -> 0f
        }
        val existing = clip.keyframes[property].orEmpty().filter { kotlin.math.abs(it.timeSec - t) > 0.01f }
        val keys = (existing + Keyframe(t, base, Ease.LINEAR)).sortedBy { it.timeSec }
        dispatch(EditorAction.UpdateClip(clip.copy(keyframes = clip.keyframes + (property to keys))))
        _toast.value = "Keyframe $property @ ${"%.2f".format(t)}s"
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
                val overlays = doc.clips.filter {
                    it.visible && (it.type == LayerType.TEXT || it.type == LayerType.SOLID) &&
                        doc.playheadSec >= it.startSec && doc.playheadSec < it.endSec
                }.sortedBy { it.trackIndex }
                if (overlays.isEmpty()) {
                    _previewBitmap.value = null
                    return@launch
                }
                val w = 720
                val h = (720f * doc.height / doc.width).toInt().coerceAtLeast(64)
                val base = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(base)
                canvas.drawColor((doc.backgroundArgb and 0xFFFFFFFFL).toInt())
                overlays.forEach { raw ->
                    val c = KeyframeEval.resolveClip(raw, doc.playheadSec)
                    val lb = if (c.type == LayerType.SOLID)
                        SolidRenderer.render(c, w, h)
                    else
                        TextRenderer.render(c, w, h)
                    canvas.drawBitmap(lb, 0f, 0f, null)
                    lb.recycle()
                }
                _previewBitmap.value = base
                return@launch
            }
            val spd = active.speed.coerceIn(0.25f, 4f)
            val localTimeMs = (((doc.playheadSec - active.startSec) * spd) * 1000f).toLong().coerceAtLeast(0)
            val resolved = KeyframeEval.resolveClip(active, doc.playheadSec)
            var bmp = MediaProbe.decodePreviewFrame(
                getApplication(),
                Uri.parse(resolved.mediaUri ?: active.mediaUri!!),
                localTimeMs,
                maxSide = 720
            ) ?: return@launch
            bmp = EffectRenderer.apply(bmp, resolved)
            // Overlay solid + text layers (higher track on top handled by list order)
            val overlays = doc.clips.filter {
                it.visible && (it.type == LayerType.TEXT || it.type == LayerType.SOLID) &&
                    doc.playheadSec >= it.startSec && doc.playheadSec < it.endSec
            }.sortedBy { it.trackIndex }
            if (overlays.isNotEmpty()) {
                val mutable = bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                val canvas = android.graphics.Canvas(mutable)
                overlays.forEach { raw ->
                    val c = KeyframeEval.resolveClip(raw, doc.playheadSec)
                    val layerBmp = if (c.type == LayerType.SOLID)
                        SolidRenderer.render(c, mutable.width, mutable.height)
                    else
                        TextRenderer.render(c, mutable.width, mutable.height)
                    canvas.drawBitmap(layerBmp, 0f, 0f, null)
                    layerBmp.recycle()
                }
                if (bmp != mutable) bmp.recycle()
                bmp = mutable
            }
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
        playJob?.cancel()
        _doc.update { it.copy(isPlaying = true) }

        val videoClip = doc.clips
            .filter { it.visible && it.mediaUri != null && it.type == LayerType.VIDEO }
            .filter { doc.playheadSec >= it.startSec && doc.playheadSec < it.endSec }
            .maxByOrNull { it.trackIndex }

        if (videoClip?.mediaUri != null) {
            val localMs = ((doc.playheadSec - videoClip.startSec) * 1000f).toLong().coerceAtLeast(0)
            playback.setVideo(videoClip.mediaUri, autoPlay = true)
            playback.seekVideoMs(localMs)
        } else {
            playback.setVideo(null, false)
        }

        playJob = viewModelScope.launch {
            while (isActive && document.value.isPlaying) {
                val d = document.value
                val tSec = d.playheadSec
                val lanes = d.clips.filter {
                    it.visible && !it.muted && it.mediaUri != null &&
                        (it.type == LayerType.AUDIO || it.type == LayerType.VIDEO) &&
                        tSec >= it.startSec && tSec < it.endSec &&
                        (d.soloTrack == null || it.trackIndex == d.soloTrack)
                }.map {
                    AudioLane(it.id, it.mediaUri!!, it.volume.coerceIn(0f, 1f))
                }
                val seeks = lanes.associate { lane ->
                    val clip = d.clips.first { it.id == lane.clipId }
                    lane.clipId to ((tSec - clip.startSec) * 1000f).toLong().coerceAtLeast(0)
                }
                playback.syncAudioLanes(lanes, seeks, playing = true)

                if (videoClip?.mediaUri != null) {
                    playback.pollPosition()
                    val pos = playback.positionMs.value / 1000f
                    val timelinePos = (videoClip.startSec + pos).coerceIn(0f, d.durationSec)
                    _doc.update { it.copy(playheadSec = timelinePos) }
                    if (timelinePos >= videoClip.endSec - 0.04f) {
                        if (d.loop) {
                            playback.seekVideoMs(0)
                            _doc.update { it.copy(playheadSec = videoClip.startSec) }
                        } else {
                            stopPlayback()
                            break
                        }
                    }
                } else {
                    val next = tSec + 0.033f
                    if (next >= d.durationSec) {
                        if (d.loop) _doc.update { it.copy(playheadSec = 0f) }
                        else {
                            _doc.update { it.copy(playheadSec = d.durationSec, isPlaying = false) }
                            break
                        }
                    } else _doc.update { it.copy(playheadSec = next) }
                }
                if (System.currentTimeMillis() % 200 < 40) refreshPreview()
                delay(16)
            }
            playback.syncAudioLanes(emptyList(), emptyMap(), playing = false)
        }
    }

    private fun stopPlayback() {
        playJob?.cancel()
        playJob = null
        playback.pause()
        playback.syncAudioLanes(emptyList(), emptyMap(), playing = false)
        _doc.update { it.copy(isPlaying = false) }
    }

    fun cancelExport() {
        exportCancelled = true
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
            val es = _exportSettings.value
            exportCancelled = false
            val result = ExportPipeline.export(
                getApplication(),
                doc,
                out,
                onProgress = { p -> _exportProgress.value = p },
                settingsWidth = es.width,
                settingsHeight = es.height,
                settingsFps = es.fps,
                includeAudio = es.includeAudio,
                scene3d = _scene3d.value,
                isCancelled = { exportCancelled }
            )
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
                    playback.setVideo(clip.mediaUri, autoPlay = false)
                    playback.seekVideoMs(localMs)
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
                saveComposition()
                return
            }
            is EditorAction.DuplicateSelected -> {
                duplicateSelected()
                return
            }
            is EditorAction.AddMarker -> {
                val t = action.timeSec.coerceIn(0f, document.value.durationSec)
                _doc.update {
                    it.copy(
                        markers = (it.markers + t).distinct().sorted(),
                        saveStatus = SaveStatus.DIRTY
                    )
                }
                scheduleAutosave()
                return
            }
            is EditorAction.ClearMarkers -> {
                _doc.update { it.copy(markers = emptyList(), saveStatus = SaveStatus.DIRTY) }
                scheduleAutosave()
                return
            }
            is EditorAction.SetSoloTrack -> {
                _doc.update { it.copy(soloTrack = action.track, saveStatus = SaveStatus.DIRTY) }
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
            scheduleAutosave()
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
