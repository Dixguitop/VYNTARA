package com.vyntara.editor.editor

/**
 * Real editor document model. Persisted later with the project file.
 * No fake media: clips exist as logical entities until import is wired.
 */
enum class LayerType { VIDEO, AUDIO, IMAGE, TEXT, SOLID, ADJUSTMENT }

enum class SaveStatus { SAVED, DIRTY, SAVING, ERROR }

enum class Ease { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT }

data class Keyframe(
    val timeSec: Float,
    val value: Float,
    val ease: Ease = Ease.LINEAR,
    /** Normalized 0-1 bezier handle strengths for cubic segment leaving this key */
    val outStrength: Float = 0.4f,
    val inStrength: Float = 0.4f
)

data class EffectInstance(
    val id: String,
    val name: String,
    val category: String,
    val enabled: Boolean = true,
    val intensity: Float = 1f,
    val radius: Float = 8f
)

data class EditorClip(
    val id: String,
    val name: String,
    val type: LayerType,
    val trackIndex: Int,
    /** Start on timeline in seconds */
    val startSec: Float,
    /** Duration in seconds */
    val durationSec: Float,
    /** Content URI string when imported from device */
    val mediaUri: String? = null,
    val mediaWidth: Int? = null,
    val mediaHeight: Int? = null,
    /** Source media duration if known from metadata */
    val sourceDurationSec: Float? = null,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val muted: Boolean = false,
    /** 0..1 audio level */
    val volume: Float = 1f,
    /** Playback speed 0.25..4 (1 = normal). Affects duration perception / local time. */
    val speed: Float = 1f,
    val textContent: String = "Text",
    val textSize: Float = 1f,
    val textColorArgb: Int = 0xFFFFFFFF.toInt(),
    val fadeInSec: Float = 0f,
    val fadeOutSec: Float = 0f,
    // Transform
    val posX: Float = 0.5f,
    val posY: Float = 0.5f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    // Effects
    val effects: List<EffectInstance> = emptyList(),
    val smartBlur: Boolean = false,
    val blurRadius: Float = 0f,
    // Keyframes (property name -> keys)
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
) {
    val endSec: Float get() = startSec + durationSec
}

data class EditorDocument(
    val projectId: String,
    val title: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val backgroundArgb: Long,
    val durationSec: Float = 10f,
    val clips: List<EditorClip> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val playheadSec: Float = 0f,
    val isPlaying: Boolean = false,
    val loop: Boolean = false,
    val snap: Boolean = true,
    val showGrid: Boolean = false,
    val timelineZoom: Float = 1f,
    val saveStatus: SaveStatus = SaveStatus.SAVED,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
) {
    val selected: EditorClip? get() = clips.firstOrNull { it.id in selectedIds }
}

sealed class EditorAction {
    data class Select(val id: String?, val multi: Boolean = false) : EditorAction()
    data class SetPlayhead(val sec: Float) : EditorAction()
    data object TogglePlay : EditorAction()
    data object ToggleLoop : EditorAction()
    data object ToggleGrid : EditorAction()
    data object ToggleSnap : EditorAction()
    data class SetZoom(val zoom: Float) : EditorAction()
    data class UpdateClip(val clip: EditorClip) : EditorAction()
    data class AddClip(val clip: EditorClip) : EditorAction()
    data class DeleteSelected(val ids: Set<String>) : EditorAction()
    data class SplitAtPlayhead(val id: String) : EditorAction()
    data class AddEffect(val clipId: String, val effect: EffectInstance) : EditorAction()
    data class ToggleEffect(val clipId: String, val effectId: String) : EditorAction()
    data class SetTitle(val title: String) : EditorAction()
    data object MarkSaved : EditorAction()
    data object DuplicateSelected : EditorAction()
    data class AddMarker(val timeSec: Float) : EditorAction()
    data class ClearMarkers(val unused: Boolean = true) : EditorAction()
    data class SetSoloTrack(val track: Int?) : EditorAction()
    data object Undo : EditorAction()
    data object Redo : EditorAction()
}

val BUILTIN_EFFECTS = listOf(
    EffectInstance("smart_blur", "Smart Blur", "Blur", intensity = 0.7f, radius = 12f),
    EffectInstance("gaussian", "Gaussian Blur", "Blur", intensity = 0.5f, radius = 8f),
    EffectInstance("motion_blur", "Motion Blur", "Blur", intensity = 0.4f, radius = 6f),
    EffectInstance("glow", "Glow", "Light", intensity = 0.5f),
    EffectInstance("vignette", "Vignette", "Light", intensity = 0.35f),
    EffectInstance("curves", "Color Curves", "Color", intensity = 0.5f),
    EffectInstance("chroma", "Chromatic Aberration", "Distort", intensity = 0.3f),
    EffectInstance("sharpen", "Sharpen", "Detail", intensity = 0.4f),
    EffectInstance("noise", "Noise", "Texture", intensity = 0.25f)
)


data class ExportSettings(
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 30,
    val includeAudio: Boolean = true
)
