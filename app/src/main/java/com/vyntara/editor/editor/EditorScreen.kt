package com.vyntara.editor.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vyntara.editor.home.LocalLanguage
import com.vyntara.editor.i18n.Strings
import com.vyntara.editor.project.ProjectSummary
import com.vyntara.editor.ui.theme.LocalAppTheme

private enum class EditorPanel { NONE, LAYERS, PROPERTIES, EFFECTS, MEDIA }

private data class EditorLayer(
    val id: String,
    val name: String,
    val type: String,
    var visible: Boolean = true,
    var opacity: Float = 1f,
    var posX: Float = 0.5f,
    var posY: Float = 0.5f,
    var scale: Float = 1f,
    var rotation: Float = 0f,
    var blurRadius: Float = 0f,
    var smartBlur: Boolean = false
)

/**
 * Professional mobile editor shell inspired by AE + Alight Motion.
 * Status:
 *  DONE: multi-panel UI, large tools, layers list, properties, effects stubs, timeline tracks
 *  PARTIAL: smart blur toggles per layer (no GPU yet), playback transport visual
 *  TODO: MediaCodec import/preview, real effect shaders, keyframes graph
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    project: ProjectSummary,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppTheme.current.colors
    val lang = LocalLanguage.current
    fun t(key: String) = Strings.t(lang, key)

    var panel by remember { mutableStateOf(EditorPanel.LAYERS) }
    var layers by remember {
        mutableStateOf(
            listOf(
                EditorLayer("bg", if (lang.name == "ENGLISH") "Background" else "Fondo", "solid"),
                EditorLayer("v1", if (lang.name == "ENGLISH") "Video 1" else "Video 1", "video")
            )
        )
    }
    var selectedId by remember { mutableStateOf("v1") }
    var playhead by remember { mutableFloatStateOf(0f) }
    var status by remember {
        mutableStateOf(
            if (lang.name == "ENGLISH") "Select a layer or import media"
            else "Selecciona una capa o importa medios"
        )
    }
    var showGrid by remember { mutableStateOf(false) }

    val selected = layers.find { it.id == selectedId }

    Column(modifier.fillMaxSize().background(colors.background)) {
        TopAppBar(
            title = {
                Column {
                    Text(project.title, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${project.aspectLabel}  ${project.resolutionLabel}  ${project.fps}fps",
                        color = colors.muted,
                        fontSize = 12.sp
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, t("editor_back"), tint = colors.text, modifier = Modifier.size(26.dp))
                }
            },
            actions = {
                IconButton(onClick = { showGrid = !showGrid }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.GridOn, null, tint = if (showGrid) colors.primary else colors.muted, modifier = Modifier.size(24.dp))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
        )

        // Preview
        Box(
            Modifier
                .fillMaxWidth()
                .weight(0.42f)
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            val ratio = project.width.toFloat() / project.height.toFloat()
            Box(
                Modifier
                    .fillMaxWidth(0.94f)
                    .aspectRatio(ratio.coerceIn(0.45f, 2.4f))
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color((project.backgroundArgb and 0xFFFFFFFFL).toULong()))
                    .border(1.dp, colors.muted.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (showGrid) {
                    // simple center cross
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(1.dp).background(colors.primary.copy(alpha = 0.35f)))
                        Box(Modifier.align(Alignment.Center).fillMaxHeight().width(1.dp).background(colors.primary.copy(alpha = 0.35f)))
                    }
                }
                Text(
                    selected?.name ?: "${project.width}x${project.height}",
                    color = colors.muted.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
                if (selected?.smartBlur == true) {
                    Text(
                        if (lang.name == "ENGLISH") "Smart blur ON" else "Desenfoque inteligente ON",
                        color = colors.secondary,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                    )
                }
            }
        }

        // Tool rail — large touch targets
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BigTool(Icons.Outlined.PlayArrow, t("editor_play"), false, colors) {
                status = t("editor_hint_play")
            }
            BigTool(Icons.Outlined.Add, t("editor_import"), panel == EditorPanel.MEDIA, colors) {
                panel = EditorPanel.MEDIA
                status = t("editor_hint_import")
            }
            BigTool(Icons.Outlined.Layers, t("editor_layers"), panel == EditorPanel.LAYERS, colors) {
                panel = EditorPanel.LAYERS
            }
            BigTool(Icons.Outlined.Tune, t("editor_props"), panel == EditorPanel.PROPERTIES, colors) {
                panel = EditorPanel.PROPERTIES
            }
            BigTool(Icons.Outlined.BlurOn, t("editor_blur"), selected?.smartBlur == true, colors) {
                if (selectedId != null) {
                    layers = layers.map {
                        if (it.id == selectedId) it.copy(smartBlur = !it.smartBlur, blurRadius = if (!it.smartBlur) 12f else 0f)
                        else it
                    }
                    status = if (layers.find { it.id == selectedId }?.smartBlur == true)
                        t("editor_hint_blur_on") else t("editor_hint_blur_off")
                }
            }
            BigTool(Icons.Outlined.Movie, if (lang.name == "ENGLISH") "Effects" else "Efectos", panel == EditorPanel.EFFECTS, colors) {
                panel = EditorPanel.EFFECTS
            }
        }

        Text(
            status,
            color = colors.muted,
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )

        // Side panel content
        Box(
            Modifier
                .fillMaxWidth()
                .weight(0.28f)
                .background(colors.card)
        ) {
            when (panel) {
                EditorPanel.LAYERS -> LayersPanel(
                    layers = layers,
                    selectedId = selectedId,
                    onSelect = { selectedId = it },
                    onToggleVisible = { id ->
                        layers = layers.map { if (it.id == id) it.copy(visible = !it.visible) else it }
                    },
                    onAdd = {
                        val n = layers.size + 1
                        val layer = EditorLayer("l$n", "Layer $n", "solid")
                        layers = layers + layer
                        selectedId = layer.id
                    }
                )
                EditorPanel.PROPERTIES -> PropertiesPanel(
                    layer = selected,
                    onChange = { updated ->
                        layers = layers.map { if (it.id == updated.id) updated else it }
                    }
                )
                EditorPanel.EFFECTS -> EffectsPanel(
                    layer = selected,
                    onSmartBlur = {
                        if (selected != null) {
                            layers = layers.map {
                                if (it.id == selected.id) it.copy(smartBlur = true, blurRadius = 12f) else it
                            }
                            status = t("editor_hint_blur_on")
                        }
                    }
                )
                EditorPanel.MEDIA -> MediaPanel()
                EditorPanel.NONE -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(t("editor_hint_start"), color = colors.muted)
                }
            }
        }

        // Timeline
        Column(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(colors.surface)
                .padding(vertical = 8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(t("editor_timeline"), color = colors.muted, fontSize = 12.sp)
                Text("${(playhead * 10).toInt() / 10f}s", color = colors.muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.background)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, drag ->
                            playhead = (playhead + drag / size.width * 5f).coerceIn(0f, 10f)
                        }
                    }
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(playhead / 10f)
                        .background(colors.primary.copy(alpha = 0.25f))
                )
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .fillMaxWidth(0f)
                        .align(Alignment.CenterStart)
                        .padding(start = (playhead / 10f * 300).dp.coerceAtMost(300.dp))
                        .background(colors.primary)
                )
            }
            Spacer(Modifier.height(8.dp))
            TimelineTrack("V1", colors.primary.copy(alpha = 0.45f), colors)
            Spacer(Modifier.height(6.dp))
            TimelineTrack("A1", colors.secondary.copy(alpha = 0.35f), colors)
        }
    }
}

@Composable
private fun BigTool(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    colors: com.vyntara.editor.ui.theme.VyntaraColors,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.primary.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .width(56.dp)
    ) {
        Icon(icon, label, tint = if (selected) colors.primary else colors.text, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = colors.muted, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun LayersPanel(
    layers: List<EditorLayer>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onToggleVisible: (String) -> Unit,
    onAdd: () -> Unit
) {
    val colors = LocalAppTheme.current.colors
    val lang = LocalLanguage.current
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (lang.name == "ENGLISH") "Layers" else "Capas",
                color = colors.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            IconButton(onClick = onAdd, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Outlined.Add, null, tint = colors.primary, modifier = Modifier.size(26.dp))
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(layers.reversed(), key = { it.id }) { layer ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (layer.id == selectedId) colors.primary.copy(alpha = 0.2f) else colors.surface)
                        .clickable { onSelect(layer.id) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        when (layer.type) {
                            "video" -> Icons.Outlined.Movie
                            else -> Icons.Outlined.Layers
                        },
                        null,
                        tint = colors.muted,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(layer.name, color = colors.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (layer.smartBlur) {
                        Icon(Icons.Outlined.BlurOn, null, tint = colors.secondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = { onToggleVisible(layer.id) }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            if (layer.visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            null,
                            tint = colors.muted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PropertiesPanel(
    layer: EditorLayer?,
    onChange: (EditorLayer) -> Unit
) {
    val colors = LocalAppTheme.current.colors
    val lang = LocalLanguage.current
    if (layer == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (lang.name == "ENGLISH") "No layer selected" else "Ninguna capa seleccionada", color = colors.muted)
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Text(layer.name, color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(10.dp))
        PropSlider(if (lang.name == "ENGLISH") "Opacity" else "Opacidad", layer.opacity, 0f, 1f, colors) {
            onChange(layer.copy(opacity = it))
        }
        PropSlider(if (lang.name == "ENGLISH") "Scale" else "Escala", layer.scale, 0.1f, 3f, colors) {
            onChange(layer.copy(scale = it))
        }
        PropSlider(if (lang.name == "ENGLISH") "Rotation" else "Rotacion", layer.rotation, -180f, 180f, colors) {
            onChange(layer.copy(rotation = it))
        }
        PropSlider("X", layer.posX, 0f, 1f, colors) { onChange(layer.copy(posX = it)) }
        PropSlider("Y", layer.posY, 0f, 1f, colors) { onChange(layer.copy(posY = it)) }
        PropSlider(if (lang.name == "ENGLISH") "Blur" else "Desenfoque", layer.blurRadius, 0f, 40f, colors) {
            onChange(layer.copy(blurRadius = it, smartBlur = it > 0f))
        }
    }
}

@Composable
private fun PropSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    colors: com.vyntara.editor.ui.theme.VyntaraColors,
    onValue: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, color = colors.muted, fontSize = 12.sp, modifier = Modifier.width(78.dp))
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = onValue,
            valueRange = min..max,
            modifier = Modifier.weight(1f).height(36.dp),
            colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary)
        )
        Text("%.1f".format(value), color = colors.text, fontSize = 12.sp, modifier = Modifier.width(40.dp))
    }
}

@Composable
private fun EffectsPanel(layer: EditorLayer?, onSmartBlur: () -> Unit) {
    val colors = LocalAppTheme.current.colors
    val lang = LocalLanguage.current
    val effects = listOf(
        if (lang.name == "ENGLISH") "Smart Blur" else "Desenfoque inteligente",
        if (lang.name == "ENGLISH") "Gaussian Blur" else "Desenfoque gaussiano",
        if (lang.name == "ENGLISH") "Motion Blur" else "Desenfoque de movimiento",
        if (lang.name == "ENGLISH") "Glow" else "Resplandor",
        if (lang.name == "ENGLISH") "Vignette" else "Vineta",
        if (lang.name == "ENGLISH") "Color Curves" else "Curvas de color",
        if (lang.name == "ENGLISH") "Chromatic Aberration" else "Aberracion cromatica"
    )
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text(
            if (lang.name == "ENGLISH") "Effects" else "Efectos",
            color = colors.text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(effects) { name ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface)
                        .clickable {
                            if (name.contains("Smart", true) || name.contains("inteligente", true)) onSmartBlur()
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.BlurOn, null, tint = colors.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(name, color = colors.text, fontSize = 15.sp)
                    if (layer?.smartBlur == true && (name.contains("Smart") || name.contains("inteligente"))) {
                        Spacer(Modifier.weight(1f))
                        Text("ON", color = colors.secondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaPanel() {
    val colors = LocalAppTheme.current.colors
    val lang = LocalLanguage.current
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Movie, null, tint = colors.muted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            if (lang.name == "ENGLISH") "Import video, image or audio" else "Importar video, imagen o audio",
            color = colors.text,
            fontSize = 15.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (lang.name == "ENGLISH") "System picker coming in the next build" else "Selector del sistema en la siguiente build",
            color = colors.muted,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun TimelineTrack(label: String, trackColor: Color, colors: com.vyntara.editor.ui.theme.VyntaraColors) {
    Row(
        Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = colors.muted, fontSize = 12.sp, modifier = Modifier.width(28.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(colors.background)
                .horizontalScroll(rememberScrollState())
        ) {
            Box(
                Modifier
                    .width(200.dp)
                    .fillMaxHeight()
                    .padding(3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(trackColor)
            )
        }
    }
}
