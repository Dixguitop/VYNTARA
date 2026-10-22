package com.vyntara.editor.editor

import androidx.lifecycle.ViewModel
import com.vyntara.editor.project.ProjectSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class EditorViewModel : ViewModel() {

    private val undoStack = ArrayDeque<EditorDocument>(32)
    private val redoStack = ArrayDeque<EditorDocument>(32)

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

    fun load(project: ProjectSummary) {
        val bg = EditorClip(
            id = "bg",
            name = "Background",
            type = LayerType.SOLID,
            trackIndex = 0,
            startSec = 0f,
            durationSec = 10f,
            locked = true
        )
        val v1 = EditorClip(
            id = "v1",
            name = "Video 1",
            type = LayerType.VIDEO,
            trackIndex = 1,
            startSec = 0f,
            durationSec = 5f
        )
        _doc.value = EditorDocument(
            projectId = project.id,
            title = project.title,
            width = project.width,
            height = project.height,
            fps = project.fps,
            backgroundArgb = project.backgroundArgb,
            durationSec = 10f,
            clips = listOf(bg, v1),
            selectedIds = setOf("v1"),
            saveStatus = SaveStatus.SAVED
        )
        undoStack.clear()
        redoStack.clear()
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
                return
            }
            is EditorAction.TogglePlay -> {
                _doc.update { it.copy(isPlaying = !it.isPlaying) }
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
            else -> { /* structural edits below */ }
        }

        // Structural edits — push undo
        val before = _doc.value
        val after = applyStructural(before, action) ?: return
        if (after != before) {
            undoStack.addLast(before)
            if (undoStack.size > 30) undoStack.removeFirst()
            redoStack.clear()
            publish(after.copy(saveStatus = SaveStatus.DIRTY))
        }
    }

    private fun applyStructural(doc: EditorDocument, action: EditorAction): EditorDocument? {
        return when (action) {
            is EditorAction.UpdateClip -> doc.copy(
                clips = doc.clips.map { if (it.id == action.clip.id) action.clip else it }
            )
            is EditorAction.AddClip -> doc.copy(clips = doc.clips + action.clip, selectedIds = setOf(action.clip.id))
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
                    durationSec = rightDur
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
                        effects = c.effects + action.effect.copy(id = UUID.randomUUID().toString().take(8)),
                        smartBlur = c.smartBlur || action.effect.name.contains("Smart", true),
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

    private fun publish(doc: EditorDocument) {
        _doc.value = doc.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty())
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()
}
