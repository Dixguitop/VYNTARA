package com.vyntara.editor.editor.effects

enum class EffectStatus { READY, PLANNED }

data class EffectDef(
    val id: String,
    val name: String,
    val nameEn: String,
    val category: String,
    val description: String,
    val descriptionEn: String,
    val cost: String,
    val status: EffectStatus,
    val defaultIntensity: Float = 0.5f,
    val defaultRadius: Float = 8f
)

object EffectCatalog {
    val all: List<EffectDef> = listOf(
        EffectDef("smart_blur", "Desenfoque inteligente", "Smart Blur", "Blur",
            "Suavizado por radio.", "Softens by radius.", "medium", EffectStatus.READY, 0.7f, 12f),
        EffectDef("gaussian_blur", "Desenfoque gaussiano", "Gaussian Blur", "Blur",
            "Box blur aproximado.", "Approx box blur.", "medium", EffectStatus.READY, 0.5f, 8f),
        EffectDef("motion_blur", "Desenfoque de movimiento", "Motion Blur", "Blur",
            "Desenfoque direccional simple.", "Simple directional blur.", "high", EffectStatus.READY, 0.4f, 6f),
        EffectDef("opacity_boost", "Opacidad", "Opacity", "Color",
            "Opacidad de capa.", "Layer opacity.", "low", EffectStatus.READY, 1f, 0f),
        EffectDef("glow", "Resplandor", "Glow", "Light",
            "Halo luminoso.", "Luminous halo.", "medium", EffectStatus.READY, 0.5f, 10f),
        EffectDef("vignette", "Vineta", "Vignette", "Light",
            "Oscurece bordes.", "Darkens edges.", "low", EffectStatus.READY, 0.35f, 0f),
        EffectDef("contrast", "Contraste", "Contrast", "Color",
            "Ajuste de contraste real.", "Real contrast.", "low", EffectStatus.READY, 0.6f, 0f),
        EffectDef("brightness", "Brillo", "Brightness", "Color",
            "Exposicion/brillo.", "Exposure/brightness.", "low", EffectStatus.READY, 0.5f, 0f),
        EffectDef("saturation", "Saturacion", "Saturation", "Color",
            "Saturacion de color.", "Color saturation.", "low", EffectStatus.READY, 1f, 0f),
        EffectDef("invert", "Negativo", "Negative", "Color",
            "Invierte colores.", "Inverts colors.", "low", EffectStatus.READY, 1f, 0f),
        EffectDef("pixelate", "Pixelado", "Pixelate", "Stylize",
            "Bloques de pixel.", "Pixel blocks.", "low", EffectStatus.READY, 0.5f, 0f),
        EffectDef("sharpen", "Nitidez", "Sharpen", "Detail",
            "Unsharp mask simple.", "Simple unsharp.", "medium", EffectStatus.READY, 0.5f, 0f),
        EffectDef("grayscale", "Escala de grises", "Grayscale", "Color",
            "Desatura por completo.", "Full desaturate.", "low", EffectStatus.READY, 0f, 0f),
        EffectDef("sepia", "Sepia", "Sepia", "Color",
            "Tono sepia clasico.", "Classic sepia tone.", "low", EffectStatus.READY, 0.8f, 0f),
        EffectDef("film_grain", "Grano de pelicula", "Film Grain", "Texture",
            "Grano procedural.", "Procedural grain.", "low", EffectStatus.READY, 0.4f, 0f),
        EffectDef("temperature", "Temperatura", "Temperature", "Color",
            "Calido/frio.", "Warm/cool balance.", "low", EffectStatus.READY, 0.5f, 0f),
        EffectDef("posterize", "Posterizar", "Posterize", "Stylize",
            "Reduce niveles de color.", "Reduce color levels.", "low", EffectStatus.READY, 0.5f, 0f),
        EffectDef("duotone", "Duotono", "Duotone", "Color",
            "Mapeo de dos tonos.", "Two-tone mapping.", "low", EffectStatus.READY, 0.7f, 0f),
        EffectDef("edges", "Bordes", "Edges", "Stylize",
            "Detector de bordes.", "Edge detect.", "medium", EffectStatus.READY, 0.6f, 0f),
        EffectDef("hue_shift", "Tono", "Hue Shift", "Color",
            "Desplazamiento de matiz.", "Hue rotation.", "low", EffectStatus.READY, 0.5f, 0f),
        EffectDef("tint", "Tinte", "Tint", "Color",
            "Tinte de color.", "Color tint.", "low", EffectStatus.READY, 0.4f, 0f),
        EffectDef("vintage", "Vintage", "Vintage", "Stylize",
            "Sepia + vineta + contraste.", "Sepia+vignette+contrast.", "medium", EffectStatus.READY, 0.6f, 0f),
        // Planned
        EffectDef("curves", "Curvas de color", "Color Curves", "Color",
            "Curvas RGB.", "RGB curves.", "medium", EffectStatus.PLANNED),
        EffectDef("chroma", "Aberracion cromatica", "Chromatic Aberration", "Distort",
            "Split RGB.", "RGB split.", "medium", EffectStatus.PLANNED),
        EffectDef("glitch", "Glitch", "Glitch", "Stylize",
            "Block shift.", "Block shift.", "medium", EffectStatus.PLANNED),
        EffectDef("particles", "Particulas", "Particles", "Particles",
            "Emisor 2D.", "2D emitter.", "high", EffectStatus.PLANNED),
        EffectDef("chroma_key", "Chroma key", "Chroma Key", "Key",
            "Key verde basico.", "Basic green key.", "medium", EffectStatus.READY, 0.5f, 0f)
    )

    fun categories(): List<String> = all.map { it.category }.distinct()
    fun byCategory(cat: String): List<EffectDef> =
        if (cat == "All" || cat == "Todos") all else all.filter { it.category == cat }
    fun ready(): List<EffectDef> = all.filter { it.status == EffectStatus.READY }
}
