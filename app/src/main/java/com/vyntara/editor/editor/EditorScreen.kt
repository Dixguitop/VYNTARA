package com.vyntara.editor.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vyntara.editor.home.LocalLanguage
import com.vyntara.editor.i18n.AppLanguage
import com.vyntara.editor.i18n.Strings
import com.vyntara.editor.project.ProjectSummary
import com.vyntara.editor.ui.theme.LocalAppTheme
import com.vyntara.editor.ui.theme.VyntaraColors
import kotlinx.coroutines.delay

private enum class BottomTab { TIMELINE, LAYERS, INSPECTOR, EFFECTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    project: ProjectSummary,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: EditorViewModel = viewModel()
) {
    val colors = LocalAppTheme.current.colors
    val lang = LocalLanguage.current
    fun t(key: String) = Strings.t(lang, key)

    LaunchedEffect(project.id) { vm.load(project) }
    val doc by vm.document.collectAsStateWithLifecycle()

    var bottomTab by remember { mutableStateOf(BottomTab.TIMELINE) }
    // Preview vs bottom weight (sliding split)
    var previewWeight by remember { mutableFloatStateOf(0.42f) }

    // Playback clock
    LaunchedEffect(doc.isPlaying) {
        while (doc.isPlaying) {
            delay(16)
            val next = doc.playheadSec + 0.016f
            if (next >= doc.durationSec) {
                if (doc.loop) vm.dispatch(EditorAction.SetPlayhead(0f))
                else {
                    vm.dispatch(EditorAction.SetPlayhead(doc.durationSec))
                    vm.dispatch(EditorAction.TogglePlay)
                }
            } else {
                vm.dispatch(EditorAction.SetPlayhead(next))
            }
        }
    }

    Column(modifier.fillMaxSize().background(colors.background)) {
        // ——— Top bar ———
        TopAppBar(
            title = {
                Column {
                    Text(
                        doc.title,
                        color = colors.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when (doc.saveStatus) {
                            SaveStatus.SAVED -> if (lang == AppLanguage.ENGLISH) "Saved" else "Guardado"
                            SaveStatus.DIRTY -> if (lang == AppLanguage.ENGLISH) "Unsaved changes" else "Cambios sin guardar"
                            SaveStatus.SAVING -> if (lang == AppLanguage.ENGLISH) "Saving…" else "Guardando…"
                            SaveStatus.ERROR -> if (lang == AppLanguage.ENGLISH) "Save error" else "Error al guardar"
                        },
                        color = when (doc.saveStatus) {
                            SaveStatus.DIRTY -> Color(0xFFFBBF24)
                            SaveStatus.ERROR -> colors.danger
                            else -> colors.muted
                        },
                        fontSize = 11.sp
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, t("editor_back"), tint = colors.text, modifier = Modifier.size(24.dp))
                }
            },
            actions = {
                IconButton(
                    onClick = { vm.dispatch(EditorAction.Undo) },
                    enabled = doc.canUndo,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, null, tint = if (doc.canUndo) colors.text else colors.muted.copy(alpha = 0.4f))
                }
                IconButton(
                    onClick = { vm.dispatch(EditorAction.Redo) },
                    enabled = doc.canRedo,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Redo, null, tint = if (doc.canRedo) colors.text else colors.muted.copy(alpha = 0.4f))
                }
                IconButton(onClick = { vm.dispatch(EditorAction.ToggleGrid) }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Outlined.GridOn, null, tint = if (doc.showGrid) colors.primary else colors.muted)
                }
                Text(
                    if (lang == AppLanguage.ENGLISH) "Export" else "Exportar",
                    color = colors.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable {
                            // Export shell — real encoder later
                            vm.dispatch(EditorAction.MarkSaved)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
        )

        // ——— Preview ———
        Box(
            Modifier
                .fillMaxWidth()
                .weight(previewWeight)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            val ratio = doc.width.toFloat() / doc.height.toFloat()
            Box(
                Modifier
                    .fillMaxWidth(0.92f)
                    .aspectRatio(ratio.coerceIn(0.45f, 2.4f))
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color((doc.backgroundArgb and 0xFFFFFFFFL).toULong()))
                    .border(1.dp, colors.muted.copy(alpha = 0.25f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (doc.showGrid) {
                    Canvas(Modifier.fillMaxSize()) {
                        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                        drawLine(colors.primary.copy(alpha = 0.35f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1f)
                        drawLine(colors.primary.copy(alpha = 0.35f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
                        drawLine(colors.muted.copy(alpha = 0.2f), Offset(size.width / 3, 0f), Offset(size.width / 3, size.height), 1f, pathEffect = dash)
                        drawLine(colors.muted.copy(alpha = 0.2f), Offset(size.width * 2 / 3, 0f), Offset(size.width * 2 / 3, size.height), 1f, pathEffect = dash)
                    }
                }
                val sel = doc.selected
                Text(
                    sel?.name ?: "${doc.width}×${doc.height}",
                    color = colors.muted.copy(alpha = 0.85f),
                    fontSize = 13.sp
                )
                if (sel?.smartBlur == true || sel?.effects?.any { it.enabled && it.name.contains("Blur") } == true) {
                    Text(
                        if (lang == AppLanguage.ENGLISH) "Blur active" else "Desenfoque activo",
                        color = colors.secondary,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                    )
                }
            }
        }

        // ——— Transport ———
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransportBtn(Icons.Outlined.SkipPrevious, colors) { vm.dispatch(EditorAction.SetPlayhead(0f)) }
            TransportBtn(if (doc.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, colors) {
                vm.dispatch(EditorAction.TogglePlay)
            }
            TransportBtn(Icons.Outlined.SkipNext, colors) { vm.dispatch(EditorAction.SetPlayhead(doc.durationSec)) }
            TransportBtn(Icons.Outlined.Loop, colors, active = doc.loop) { vm.dispatch(EditorAction.ToggleLoop) }
            Spacer(Modifier.width(12.dp))
            Text(
                formatTime(doc.playheadSec) + " / " + formatTime(doc.durationSec),
                color = colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(12.dp))
            TransportBtn(Icons.Outlined.ZoomOut, colors) {
                vm.dispatch(EditorAction.SetZoom(doc.timelineZoom - 0.25f))
            }
            TransportBtn(Icons.Outlined.ZoomIn, colors) {
                vm.dispatch(EditorAction.SetZoom(doc.timelineZoom + 0.25f))
            }
        }

        // Drag handle to resize preview/bottom
        Box(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
                .background(colors.surface)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, _ -> }
                }
                .pointerInput(previewWeight) {
                    detectTapGestures { }
                },
            contentAlignment = Alignment.Center
        ) {
            // vertical resize via drag on handle
            Box(
                Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.muted.copy(alpha = 0.45f))
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            // use vertical component via change
                        }
                    }
            )
        }
        // Better: simple buttons to expand/collapse bottom
        Row(
            Modifier.fillMaxWidth().background(colors.surface).padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                if (lang == AppLanguage.ENGLISH) "Preview +" else "Vista +",
                color = colors.muted,
                fontSize = 11.sp,
                modifier = Modifier
                    .clickable { previewWeight = (previewWeight + 0.08f).coerceAtMost(0.65f) }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Text(
                if (lang == AppLanguage.ENGLISH) "Panel +" else "Panel +",
                color = colors.muted,
                fontSize = 11.sp,
                modifier = Modifier
                    .clickable { previewWeight = (previewWeight - 0.08f).coerceAtLeast(0.28f) }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        // ——— Bottom tabs ———
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.card)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BottomTabBtn(Icons.Outlined.Movie, if (lang == AppLanguage.ENGLISH) "Timeline" else "Timeline", bottomTab == BottomTab.TIMELINE, colors) {
                bottomTab = BottomTab.TIMELINE
            }
            BottomTabBtn(Icons.Outlined.Layers, t("editor_layers"), bottomTab == BottomTab.LAYERS, colors) {
                bottomTab = BottomTab.LAYERS
            }
            BottomTabBtn(Icons.Outlined.Tune, t("editor_props"), bottomTab == BottomTab.INSPECTOR, colors) {
                bottomTab = BottomTab.INSPECTOR
            }
            BottomTabBtn(Icons.Outlined.BlurOn, if (lang == AppLanguage.ENGLISH) "Effects" else "Efectos", bottomTab == BottomTab.EFFECTS, colors) {
                bottomTab = BottomTab.EFFECTS
            }
            BottomTabBtn(Icons.Outlined.ContentCut, t("editor_cut"), false, colors) {
                doc.selectedIds.firstOrNull()?.let { vm.dispatch(EditorAction.SplitAtPlayhead(it)) }
            }
            BottomTabBtn(Icons.Outlined.FileUpload, t("editor_import"), false, colors) {
                // Import shell
            }
        }

        // ——— Bottom panel ———
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f - previewWeight)
                .background(colors.card)
        ) {
            when (bottomTab) {
                BottomTab.TIMELINE -> TimelinePanel(doc, colors, lang) { vm.dispatch(it) }
                BottomTab.LAYERS -> LayersPanel(doc, colors, lang) { vm.dispatch(it) }
                BottomTab.INSPECTOR -> InspectorPanel(doc, colors, lang) { vm.dispatch(it) }
                BottomTab.EFFECTS -> EffectsBrowser(doc, colors, lang) { vm.dispatch(it) }
            }
        }
    }
}

@Composable
private fun TransportBtn(icon: ImageVector, colors: VyntaraColors, active: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(icon, null, tint = if (active) colors.primary else colors.text, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun BottomTabBtn(icon: ImageVector, label: String, selected: Boolean, colors: VyntaraColors, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.primary.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .width(56.dp)
    ) {
        Icon(icon, label, tint = if (selected) colors.primary else colors.muted, modifier = Modifier.size(22.dp))
        Text(label, color = if (selected) colors.primary else colors.muted, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun TimelinePanel(doc: EditorDocument, colors: VyntaraColors, lang: AppLanguage, dispatch: (EditorAction) -> Unit) {
    val pxPerSec = 48f * doc.timelineZoom
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        // Ruler
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .horizontalScroll(scroll)
                .pointerInput(doc.durationSec, pxPerSec) {
                    detectTapGestures { offset ->
                        dispatch(EditorAction.SetPlayhead((offset.x / pxPerSec).coerceIn(0f, doc.durationSec)))
                    }
                }
        ) {
            Row(Modifier.width((doc.durationSec * pxPerSec).dp.coerceAtLeast(200.dp))) {
                val marks = (0..doc.durationSec.toInt()).toList()
                marks.forEach { s ->
                    Box(Modifier.width(pxPerSec.dp).height(28.dp)) {
                        Text("${s}s", color = colors.muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomStart))
                    }
                }
            }
            // playhead
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .padding(start = (doc.playheadSec * pxPerSec).dp)
                    .background(colors.primary)
            )
        }
        Spacer(Modifier.height(6.dp))
        // Tracks
        val tracks = doc.clips.groupBy { it.trackIndex }.toSortedMap()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tracks.forEach { (idx, clips) ->
                item {
                    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("T$idx", color = colors.muted, fontSize = 11.sp, modifier = Modifier.width(28.dp))
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.background)
                                .horizontalScroll(scroll)
                        ) {
                            Box(Modifier.width((doc.durationSec * pxPerSec).dp.coerceAtLeast(200.dp)).fillMaxHeight()) {
                                clips.forEach { clip ->
                                    val selected = clip.id in doc.selectedIds
                                    Box(
                                        Modifier
                                            .padding(start = (clip.startSec * pxPerSec).dp)
                                            .width((clip.durationSec * pxPerSec).dp.coerceAtLeast(24.dp))
                                            .fillMaxHeight()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(
                                                when (clip.type) {
                                                    LayerType.VIDEO -> colors.primary.copy(alpha = if (selected) 0.85f else 0.45f)
                                                    LayerType.AUDIO -> colors.secondary.copy(alpha = if (selected) 0.85f else 0.4f)
                                                    LayerType.SOLID -> colors.muted.copy(alpha = if (selected) 0.6f else 0.25f)
                                                    else -> colors.primary.copy(alpha = 0.35f)
                                                }
                                            )
                                            .border(
                                                if (selected) 2.dp else 0.dp,
                                                if (selected) Color.White else Color.Transparent,
                                                RoundedCornerShape(5.dp)
                                            )
                                            .clickable { dispatch(EditorAction.Select(clip.id)) },
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            clip.name,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 6.dp)
                                        )
                                    }
                                }
                                // playhead overlay
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .width(2.dp)
                                        .padding(start = (doc.playheadSec * pxPerSec).dp)
                                        .background(colors.primary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LayersPanel(doc: EditorDocument, colors: VyntaraColors, lang: AppLanguage, dispatch: (EditorAction) -> Unit) {
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (lang == AppLanguage.ENGLISH) "Layers" else "Capas", color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            IconButton(onClick = {
                val n = doc.clips.size + 1
                dispatch(
                    EditorAction.AddClip(
                        EditorClip(
                            id = "c$n",
                            name = "Layer $n",
                            type = LayerType.SOLID,
                            trackIndex = (doc.clips.maxOfOrNull { it.trackIndex } ?: 0) + 1,
                            startSec = 0f,
                            durationSec = 3f
                        )
                    )
                )
            }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Add, null, tint = colors.primary)
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(doc.clips.reversed(), key = { it.id }) { clip ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (clip.id in doc.selectedIds) colors.primary.copy(alpha = 0.2f) else colors.surface)
                        .clickable { dispatch(EditorAction.Select(clip.id)) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        when (clip.type) {
                            LayerType.VIDEO -> Icons.Outlined.Movie
                            else -> Icons.Outlined.Layers
                        },
                        null,
                        tint = colors.muted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(clip.name, color = colors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (clip.smartBlur || clip.effects.any { it.enabled }) {
                        Icon(Icons.Outlined.BlurOn, null, tint = colors.secondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    IconButton(
                        onClick = { dispatch(EditorAction.UpdateClip(clip.copy(visible = !clip.visible))) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (clip.visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            null,
                            tint = colors.muted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorPanel(doc: EditorDocument, colors: VyntaraColors, lang: AppLanguage, dispatch: (EditorAction) -> Unit) {
    val clip = doc.selected
    if (clip == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (lang == AppLanguage.ENGLISH) "Select a clip to edit properties" else "Selecciona un clip para editar propiedades",
                color = colors.muted,
                fontSize = 13.sp
            )
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text(clip.name, color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(
            "${clip.type} · ${"%.1f".format(clip.startSec)}s – ${"%.1f".format(clip.endSec)}s",
            color = colors.muted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(10.dp))
        PropSlider(if (lang == AppLanguage.ENGLISH) "Opacity" else "Opacidad", clip.opacity, 0f, 1f, colors) {
            dispatch(EditorAction.UpdateClip(clip.copy(opacity = it)))
        }
        PropSlider(if (lang == AppLanguage.ENGLISH) "Scale X" else "Escala X", clip.scaleX, 0.1f, 3f, colors) {
            dispatch(EditorAction.UpdateClip(clip.copy(scaleX = it)))
        }
        PropSlider(if (lang == AppLanguage.ENGLISH) "Scale Y" else "Escala Y", clip.scaleY, 0.1f, 3f, colors) {
            dispatch(EditorAction.UpdateClip(clip.copy(scaleY = it)))
        }
        PropSlider(if (lang == AppLanguage.ENGLISH) "Rotation" else "Rotacion", clip.rotation, -180f, 180f, colors) {
            dispatch(EditorAction.UpdateClip(clip.copy(rotation = it)))
        }
        PropSlider("X", clip.posX, 0f, 1f, colors) { dispatch(EditorAction.UpdateClip(clip.copy(posX = it))) }
        PropSlider("Y", clip.posY, 0f, 1f, colors) { dispatch(EditorAction.UpdateClip(clip.copy(posY = it))) }
        PropSlider(if (lang == AppLanguage.ENGLISH) "Blur radius" else "Radio desenfoque", clip.blurRadius, 0f, 40f, colors) {
            dispatch(EditorAction.UpdateClip(clip.copy(blurRadius = it, smartBlur = it > 0.5f)))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (lang == AppLanguage.ENGLISH) "Duration ${"%.2f".format(clip.durationSec)}s" else "Duracion ${"%.2f".format(clip.durationSec)}s",
            color = colors.muted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun PropSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    colors: VyntaraColors,
    onValue: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Text(label, color = colors.muted, fontSize = 11.sp, modifier = Modifier.width(88.dp))
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = onValue,
            valueRange = min..max,
            modifier = Modifier.weight(1f).height(32.dp),
            colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary)
        )
        Text("%.1f".format(value), color = colors.text, fontSize = 11.sp, modifier = Modifier.width(36.dp))
    }
}

@Composable
private fun EffectsBrowser(doc: EditorDocument, colors: VyntaraColors, lang: AppLanguage, dispatch: (EditorAction) -> Unit) {
    val clip = doc.selected
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Text(if (lang == AppLanguage.ENGLISH) "Effects library" else "Biblioteca de efectos", color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        if (clip == null) {
            Text(
                if (lang == AppLanguage.ENGLISH) "Select a clip first" else "Selecciona un clip primero",
                color = colors.muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(BUILTIN_EFFECTS) { effect ->
                val onClip = clip?.effects?.any { it.name == effect.name && it.enabled } == true
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (onClip) colors.primary.copy(alpha = 0.18f) else colors.surface)
                        .clickable(enabled = clip != null) {
                            if (clip != null) {
                                dispatch(EditorAction.AddEffect(clip.id, effect))
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.BlurOn, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(effect.name, color = colors.text, fontSize = 14.sp)
                        Text(effect.category, color = colors.muted, fontSize = 11.sp)
                    }
                    if (onClip) {
                        Text("ON", color = colors.secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun formatTime(sec: Float): String {
    val s = sec.toInt().coerceAtLeast(0)
    val m = s / 60
    val r = s % 60
    val ms = ((sec - s) * 10).toInt().coerceIn(0, 9)
    return "%d:%02d.%d".format(m, r, ms)
}
