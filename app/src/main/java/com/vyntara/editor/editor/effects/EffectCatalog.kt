package com.vyntara.editor.editor.effects

/**
 * Scalable effect registry.
 *
 * Status meanings:
 * - READY: parameters apply to composition model and survive save (GPU shader pending = still affects model flags)
 * - PLANNED: listed for roadmap, not applyable
 *
 * Honesty rule: catalog size is not "1000 ready effects". Ready set is small and real.
 * Architecture supports growing toward large catalog without rewriting UI.
 */
enum class EffectStatus { READY, PLANNED }

data class EffectDef(
    val id: String,
    val name: String,
    val nameEn: String,
    val category: String,
    val description: String,
    val descriptionEn: String,
    val cost: String, // low | medium | high
    val status: EffectStatus,
    val defaultIntensity: Float = 0.5f,
    val defaultRadius: Float = 8f
)

object EffectCatalog {
    val all: List<EffectDef> = listOf(
        // READY — connected to EditorDocument / layer flags
        EffectDef("smart_blur", "Desenfoque inteligente", "Smart Blur", "Blur",
            "Suaviza el clip con radio controlable. Modelo listo; shader GPU pendiente.",
            "Softens the clip with controllable radius. Model ready; GPU shader pending.",
            "medium", EffectStatus.READY, 0.7f, 12f),
        EffectDef("gaussian_blur", "Desenfoque gaussiano", "Gaussian Blur", "Blur",
            "Desenfoque gaussiano clásico por radio.", "Classic Gaussian blur by radius.",
            "medium", EffectStatus.READY, 0.5f, 8f),
        EffectDef("motion_blur", "Desenfoque de movimiento", "Motion Blur", "Blur",
            "Estela direccional según movimiento.", "Directional streak based on motion.",
            "high", EffectStatus.READY, 0.4f, 6f),
        EffectDef("opacity_boost", "Opacidad", "Opacity", "Color",
            "Ajuste de opacidad de capa.", "Layer opacity adjustment.",
            "low", EffectStatus.READY, 1f, 0f),
        EffectDef("glow", "Resplandor", "Glow", "Light",
            "Halo luminoso alrededor de bordes claros.", "Luminous halo around bright edges.",
            "medium", EffectStatus.READY, 0.5f, 10f),
        EffectDef("vignette", "Vineta", "Vignette", "Light",
            "Oscurece bordes del fotograma.", "Darkens frame edges.",
            "low", EffectStatus.READY, 0.35f, 0f),
        // PLANNED — visible as coming soon, not fake-apply
        EffectDef("curves", "Curvas de color", "Color Curves", "Color",
            "Curvas RGB/Luma.", "RGB/Luma curves.", "medium", EffectStatus.PLANNED),
        EffectDef("chroma", "Aberracion cromatica", "Chromatic Aberration", "Distort",
            "Separacion de canales RGB.", "RGB channel split.", "medium", EffectStatus.PLANNED),
        EffectDef("sharpen", "Nitidez", "Sharpen", "Detail",
            "Acentua bordes.", "Edge emphasis.", "low", EffectStatus.PLANNED),
        EffectDef("noise", "Ruido", "Noise", "Texture",
            "Grano procedural.", "Procedural grain.", "low", EffectStatus.PLANNED),
        EffectDef("glitch", "Glitch", "Glitch", "Stylize",
            "Desplazamiento de bloques y RGB.", "Block shift and RGB.", "medium", EffectStatus.PLANNED),
        EffectDef("vhs", "VHS", "VHS", "Stylize",
            "Estetica cinta analogica.", "Analog tape look.", "medium", EffectStatus.PLANNED),
        EffectDef("particles", "Particulas", "Particles", "Particles",
            "Emisor basico 2D.", "Basic 2D emitter.", "high", EffectStatus.PLANNED),
        EffectDef("chroma_key", "Chroma key", "Chroma Key", "Key",
            "Eliminacion de fondo por color.", "Color keying.", "high", EffectStatus.PLANNED)
    )

    fun categories(): List<String> = all.map { it.category }.distinct()
    fun byCategory(cat: String): List<EffectDef> =
        if (cat == "All" || cat == "Todos") all else all.filter { it.category == cat }
    fun ready(): List<EffectDef> = all.filter { it.status == EffectStatus.READY }
}
