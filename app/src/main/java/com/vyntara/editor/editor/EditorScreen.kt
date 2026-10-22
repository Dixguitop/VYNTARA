package com.vyntara.editor.editor

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vyntara.editor.editor.effects.EffectCatalog
import com.vyntara.editor.editor.effects.EffectStatus
import com.vyntara.editor.editor.media.ImportedMedia
import com.vyntara.editor.home.LocalLanguage
import com.vyntara.editor.i18n.AppLanguage
import com.vyntara.editor.project.ProjectSummary
import com.vyntara.editor.ui.theme.LocalAppTheme
import com.vyntara.editor.ui.theme.VyntaraColors
import kotlinx.coroutines.delay

private enum class ToolPanel { TIMELINE, LAYERS, INSPECTOR, EFFECTS, MEDIA, TEXT, SCENE3D }

/**
 * Professional adaptive editor.
 * Portrait: stacked monitor + timeline + tools.
 * Landscape: left rail | monitor+timeline | inspector.
 *
 * Real: model actions, undo, import URI grant, effect registry with READY/PLANNED.
 * Not real yet: MediaCodec decode of URI into monitor, GPU shaders, export encoder.
 */
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
    val ctx = LocalContext.current

    LaunchedEffect(project.id) { vm.load(project) }
    val doc by vm.document.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()

    var panel by remember { mutableStateOf(ToolPanel.TIMELINE) }
    var showExitDialog by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) { /* some providers disallow persist */ }
        val mime = ctx.contentResolver.getType(uri) ?: ""
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "media"
        vm.importMedia(
            ImportedMedia(
                uri = uri.toString(),
                displayName = name,
                mimeType = mime,
                isVideo = mime.startsWith("video"),
                isAudio = mime.startsWith("audio"),
                isImage = mime.startsWith("image")
            )
        )
        panel = ToolPanel.TIMELINE
    }

    fun requestExit() {
        if (doc.saveStatus == SaveStatus.DIRTY) showExitDialog = true
        else onBack()
    }

    BackHandler {
        if (panel != ToolPanel.TIMELINE) panel = ToolPanel.TIMELINE
        else requestExit()
    }

    LaunchedEffect(doc.isPlaying) {
        while (doc.isPlaying) {
            delay(33)
            val next = doc.playheadSec + 0.033f
            if (next >= doc.durationSec) {
                if (doc.loop) vm.dispatch(EditorAction.SetPlayhead(0f))
                else {
                    vm.dispatch(EditorAction.SetPlayhead(doc.durationSec))
                    vm.dispatch(EditorAction.TogglePlay)
                }
            } else vm.dispatch(EditorAction.SetPlayhead(next))
        }
    }

    Box(modifier.fillMaxSize().background(colors.background)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight && maxWidth > 600.dp
            if (landscape) {
                LandscapeEditor(
                    doc = doc,
                    panel = panel,
                    colors = colors,
                    lang = lang,
                    onPanel = { panel = it },
                    onDispatch = vm::dispatch,
                    onApplyEffect = vm::applyEffect,
                    onImport = { picker.launch(arrayOf("video/*", "image/*", "audio/*")) },
                    onBack = ::requestExit
                )
            } else {
                PortraitEditor(
                    doc = doc,
                    panel = panel,
                    colors = colors,
                    lang = lang,
                    onPanel = { panel = it },
                    onDispatch = vm::dispatch,
                    onApplyEffect = vm::applyEffect,
                    onImport = { picker.launch(arrayOf("video/*", "image/*", "audio/*")) },
                    onBack = ::requestExit
                )
            }
        }

        toast?.let { msg ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = {
                    TextButton(onClick = { vm.clearToast() }) {
                        Text("OK", color = colors.primary)
                    }
                }
            ) { Text(msg) }
            LaunchedEffect(msg) {
                delay(2500)
                vm.clearToast()
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = {
                Text(if (lang == AppLanguage.ENGLISH) "Unsaved changes" else "Cambios sin guardar")
            },
            text = {
                Text(
                    if (lang == AppLanguage.ENGLISH) "Save before leaving?"
                    else "Guardar antes de salir?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.dispatch(EditorAction.MarkSaved)
                    showExitDialog = false
                    onBack()
                }) {
                    Text(if (lang == AppLanguage.ENGLISH) "Save" else "Guardar", color = colors.primary)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text(if (lang == AppLanguage.ENGLISH) "Cancel" else "Cancelar")
                    }
                    TextButton(onClick = {
                        showExitDialog = false
                        onBack()
                    }) {
                        Text(if (lang == AppLanguage.ENGLISH) "Discard" else "Descartar", color = colors.danger)
                    }
                }
            },
            containerColor = colors.card
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortraitEditor(
    doc: EditorDocument,
    panel: ToolPanel,
    colors: VyntaraColors,
    lang: AppLanguage,
    onPanel: (ToolPanel) -> Unit,
    onDispatch: (EditorAction) -> Unit,
    onApplyEffect: (String) -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        EditorTopBar(doc, colors, lang, onDispatch, onBack)
        Monitor(doc, colors, lang, Modifier.fillMaxWidth().weight(0.36f))
        TransportBar(doc, colors, onDispatch)
        TimelineStrip(doc, colors, Modifier.fillMaxWidth().height(120.dp), onDispatch)
        ToolStrip(panel, colors, lang, onPanel, onImport, onDispatch, doc)
        AnimatedContent(
            targetState = panel,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.weight(0.32f).fillMaxWidth(),
            label = "panel"
        ) { p ->
            PanelBody(p, doc, colors, lang, onDispatch, onApplyEffect, onImport)
        }
    }
}

@Composable
private fun LandscapeEditor(
    doc: EditorDocument,
    panel: ToolPanel,
    colors: VyntaraColors,
    lang: AppLanguage,
    onPanel: (ToolPanel) -> Unit,
    onDispatch: (EditorAction) -> Unit,
    onApplyEffect: (String) -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        EditorTopBar(doc, colors, lang, onDispatch, onBack)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            // Left tool rail
            Column(
                Modifier
                    .width(72.dp)
                    .fillMaxHeight()
                    .background(colors.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RailBtn(Icons.Outlined.Movie, panel == ToolPanel.TIMELINE, colors) { onPanel(ToolPanel.TIMELINE) }
                RailBtn(Icons.Outlined.Layers, panel == ToolPanel.LAYERS, colors) { onPanel(ToolPanel.LAYERS) }
                RailBtn(Icons.Outlined.Tune, panel == ToolPanel.INSPECTOR, colors) { onPanel(ToolPanel.INSPECTOR) }
                RailBtn(Icons.Outlined.BlurOn, panel == ToolPanel.EFFECTS, colors) { onPanel(ToolPanel.EFFECTS) }
                RailBtn(Icons.Outlined.FileUpload, panel == ToolPanel.MEDIA, colors) { onImport() }
                RailBtn(Icons.Outlined.TextFields, panel == ToolPanel.TEXT, colors) { onPanel(ToolPanel.TEXT) }
                RailBtn(Icons.Outlined.ViewInAr, panel == ToolPanel.SCENE3D, colors) { onPanel(ToolPanel.SCENE3D) }
                RailBtn(Icons.Outlined.ContentCut, false, colors) {
                    doc.selectedIds.firstOrNull()?.let { onDispatch(EditorAction.SplitAtPlayhead(it)) }
                }
            }
            // Center: monitor + timeline
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Monitor(doc, colors, lang, Modifier.fillMaxWidth().weight(0.55f))
                TransportBar(doc, colors, onDispatch)
                TimelineStrip(doc, colors, Modifier.fillMaxWidth().weight(0.45f), onDispatch)
            }
            // Right inspector / active panel
            Box(
                Modifier
                    .widthIn(min = 260.dp, max = 320.dp)
                    .fillMaxHeight()
                    .background(colors.card)
            ) {
                PanelBody(panel, doc, colors, lang, onDispatch, onApplyEffect, onImport)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    doc: EditorDocument,
    colors: VyntaraColors,
    lang: AppLanguage,
    onDispatch: (EditorAction) -> Unit,
    onBack: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    doc.title.ifBlank { if (lang == AppLanguage.ENGLISH) "Untitled" else "Sin titulo" },
                    color = colors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    when (doc.saveStatus) {
                        SaveStatus.SAVED -> if (lang == AppLanguage.ENGLISH) "Saved" else "Guardado"
                        SaveStatus.DIRTY -> if (lang == AppLanguage.ENGLISH) "Unsaved" else "Sin guardar"
                        SaveStatus.SAVING -> if (lang == AppLanguage.ENGLISH) "Saving…" else "Guardando…"
                        SaveStatus.ERROR -> if (lang == AppLanguage.ENGLISH) "Error" else "Error"
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
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = colors.text)
            }
        },
        actions = {
            IconButton(onClick = { onDispatch(EditorAction.Undo) }, enabled = doc.canUndo) {
                Icon(Icons.AutoMirrored.Outlined.Undo, null, tint = if (doc.canUndo) colors.text else colors.muted.copy(alpha = 0.35f))
            }
            IconButton(onClick = { onDispatch(EditorAction.Redo) }, enabled = doc.canRedo) {
                Icon(Icons.AutoMirrored.Outlined.Redo, null, tint = if (doc.canRedo) colors.text else colors.muted.copy(alpha = 0.35f))
            }
            IconButton(onClick = { onDispatch(EditorAction.ToggleGrid) }) {
                Icon(Icons.Outlined.GridOn, null, tint = if (doc.showGrid) colors.primary else colors.muted)
            }
            Text(
                if (lang == AppLanguage.ENGLISH) "Export" else "Exportar",
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable {
                        // Encoder not implemented — honest mark
                        onDispatch(EditorAction.MarkSaved)
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
    )
}

@Composable
private fun Monitor(
    doc: EditorDocument,
    colors: VyntaraColors,
    lang: AppLanguage,
    modifier: Modifier
) {
    Box(modifier.padding(8.dp), contentAlignment = Alignment.Center) {
        val ratio = doc.width.toFloat() / doc.height.toFloat()
        Box(
            Modifier
                .fillMaxWidth(0.96f)
                .aspectRatio(ratio.coerceIn(0.45f, 2.4f))
                .clip(RoundedCornerShape(8.dp))
                .background(Color((doc.backgroundArgb and 0xFFFFFFFFL).toULong()))
                .border(1.dp, colors.muted.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (doc.showGrid) {
                Canvas(Modifier.fillMaxSize()) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    drawLine(colors.primary.copy(alpha = 0.4f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.5f)
                    drawLine(colors.primary.copy(alpha = 0.4f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.5f)
                    drawLine(colors.muted.copy(alpha = 0.2f), Offset(size.width / 3, 0f), Offset(size.width / 3, size.height), 1f, pathEffect = dash)
                    drawLine(colors.muted.copy(alpha = 0.2f), Offset(size.width * 2 / 3, 0f), Offset(size.width * 2 / 3, size.height), 1f, pathEffect = dash)
                }
            }
            val mediaClips = doc.clips.filter { it.type != LayerType.SOLID && it.visible }
            if (mediaClips.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.FileUpload, null, tint = colors.muted, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (lang == AppLanguage.ENGLISH) "Import media to start"
                        else "Importa medios para empezar",
                        color = colors.muted,
                        fontSize = 13.sp
                    )
                    Text(
                        "${doc.width}×${doc.height} · ${doc.fps}fps",
                        color = colors.muted.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            } else {
                // Honest: no pixel decode yet — show clip identity at playhead
                val active = mediaClips.filter {
                    doc.playheadSec >= it.startSec && doc.playheadSec < it.endSec
                }
                Text(
                    active.joinToString { it.name }.ifBlank { mediaClips.first().name },
                    color = colors.text.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                if (active.any { it.smartBlur || it.effects.any { e -> e.enabled } }) {
                    Text(
                        if (lang == AppLanguage.ENGLISH) "Effects on (GPU pending)"
                        else "Efectos activos (GPU pendiente)",
                        color = colors.secondary,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TransportBar(doc: EditorDocument, colors: VyntaraColors, onDispatch: (EditorAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onDispatch(EditorAction.SetPlayhead(0f)) }) {
            Icon(Icons.Outlined.SkipPrevious, null, tint = colors.text)
        }
        IconButton(onClick = { onDispatch(EditorAction.TogglePlay) }) {
            Icon(if (doc.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null, tint = colors.primary, modifier = Modifier.size(32.dp))
        }
        IconButton(onClick = { onDispatch(EditorAction.SetPlayhead(doc.durationSec)) }) {
            Icon(Icons.Outlined.SkipNext, null, tint = colors.text)
        }
        IconButton(onClick = { onDispatch(EditorAction.ToggleLoop) }) {
            Icon(Icons.Outlined.Loop, null, tint = if (doc.loop) colors.primary else colors.muted)
        }
        Spacer(Modifier.width(8.dp))
        Text(formatTime(doc.playheadSec) + " / " + formatTime(doc.durationSec), color = colors.text, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { onDispatch(EditorAction.SetZoom(doc.timelineZoom - 0.25f)) }) {
            Icon(Icons.Outlined.ZoomOut, null, tint = colors.muted)
        }
        IconButton(onClick = { onDispatch(EditorAction.SetZoom(doc.timelineZoom + 0.25f)) }) {
            Icon(Icons.Outlined.ZoomIn, null, tint = colors.muted)
        }
    }
}

@Composable
private fun TimelineStrip(
    doc: EditorDocument,
    colors: VyntaraColors,
    modifier: Modifier,
    onDispatch: (EditorAction) -> Unit
) {
    val pxPerSec = 40f * doc.timelineZoom
    val scroll = rememberScrollState()
    Column(modifier.background(colors.card).padding(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .horizontalScroll(scroll)
                .pointerInput(doc.durationSec, pxPerSec) {
                    detectTapGestures { offset ->
                        onDispatch(EditorAction.SetPlayhead((offset.x / pxPerSec).coerceIn(0f, doc.durationSec)))
                    }
                }
        ) {
            Row(Modifier.width((doc.durationSec * pxPerSec).dp.coerceAtLeast(160.dp))) {
                (0..doc.durationSec.toInt()).forEach { s ->
                    Box(Modifier.width(pxPerSec.dp)) {
                        Text("${s}s", color = colors.muted, fontSize = 9.sp)
                    }
                }
            }
            Box(Modifier.fillMaxHeight().width(2.dp).padding(start = (doc.playheadSec * pxPerSec).dp).background(colors.primary))
        }
        val tracks = doc.clips.groupBy { it.trackIndex }.toSortedMap()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxSize()) {
            tracks.forEach { (idx, clips) ->
                item(key = "t$idx") {
                    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("T$idx", color = colors.muted, fontSize = 10.sp, modifier = Modifier.width(24.dp))
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(5.dp))
                                .background(colors.background)
                                .horizontalScroll(scroll)
                        ) {
                            Box(Modifier.width((doc.durationSec * pxPerSec).dp.coerceAtLeast(160.dp)).fillMaxHeight()) {
                                clips.forEach { clip ->
                                    val sel = clip.id in doc.selectedIds
                                    Box(
                                        Modifier
                                            .padding(start = (clip.startSec * pxPerSec).dp)
                                            .width((clip.durationSec * pxPerSec).dp.coerceAtLeast(20.dp))
                                            .fillMaxHeight()
                                            .padding(vertical = 3.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                when (clip.type) {
                                                    LayerType.VIDEO -> colors.primary.copy(alpha = if (sel) 0.9f else 0.5f)
                                                    LayerType.AUDIO -> colors.secondary.copy(alpha = if (sel) 0.9f else 0.45f)
                                                    LayerType.IMAGE -> Color(0xFF10B981).copy(alpha = if (sel) 0.9f else 0.5f)
                                                    else -> colors.muted.copy(alpha = if (sel) 0.55f else 0.25f)
                                                }
                                            )
                                            .border(if (sel) 1.5.dp else 0.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                            .clickable { onDispatch(EditorAction.Select(clip.id)) },
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(clip.name, color = Color.White, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp))
                                    }
                                }
                                Box(Modifier.fillMaxHeight().width(2.dp).padding(start = (doc.playheadSec * pxPerSec).dp).background(colors.primary))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolStrip(
    panel: ToolPanel,
    colors: VyntaraColors,
    lang: AppLanguage,
    onPanel: (ToolPanel) -> Unit,
    onImport: () -> Unit,
    onDispatch: (EditorAction) -> Unit,
    doc: EditorDocument
) {
    Row(
        Modifier.fillMaxWidth().background(colors.surface).padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ToolChip(Icons.Outlined.Movie, "TL", panel == ToolPanel.TIMELINE, colors) { onPanel(ToolPanel.TIMELINE) }
        ToolChip(Icons.Outlined.Layers, if (lang == AppLanguage.ENGLISH) "Layers" else "Capas", panel == ToolPanel.LAYERS, colors) { onPanel(ToolPanel.LAYERS) }
        ToolChip(Icons.Outlined.Tune, if (lang == AppLanguage.ENGLISH) "Props" else "Props", panel == ToolPanel.INSPECTOR, colors) { onPanel(ToolPanel.INSPECTOR) }
        ToolChip(Icons.Outlined.BlurOn, if (lang == AppLanguage.ENGLISH) "FX" else "FX", panel == ToolPanel.EFFECTS, colors) { onPanel(ToolPanel.EFFECTS) }
        ToolChip(Icons.Outlined.FileUpload, if (lang == AppLanguage.ENGLISH) "Import" else "Import", false, colors, onImport)
        ToolChip(Icons.Outlined.ContentCut, if (lang == AppLanguage.ENGLISH) "Cut" else "Cortar", false, colors) {
            doc.selectedIds.firstOrNull()?.let { onDispatch(EditorAction.SplitAtPlayhead(it)) }
        }
    }
}

@Composable
private fun ToolChip(icon: ImageVector, label: String, selected: Boolean, colors: VyntaraColors, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.primary.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .width(52.dp)
    ) {
        Icon(icon, label, tint = if (selected) colors.primary else colors.muted, modifier = Modifier.size(22.dp))
        Text(label, color = if (selected) colors.primary else colors.muted, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun RailBtn(icon: ImageVector, selected: Boolean, colors: VyntaraColors, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.primary.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (selected) colors.primary else colors.muted, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun PanelBody(
    panel: ToolPanel,
    doc: EditorDocument,
    colors: VyntaraColors,
    lang: AppLanguage,
    onDispatch: (EditorAction) -> Unit,
    onApplyEffect: (String) -> Unit,
    onImport: () -> Unit
) {
    when (panel) {
        ToolPanel.TIMELINE -> Box(Modifier.fillMaxSize().padding(12.dp)) {
            Text(
                if (lang == AppLanguage.ENGLISH)
                    "Timeline above. Select clips, cut at playhead, zoom with +/−."
                else
                    "Timeline arriba. Selecciona clips, corta en el cabezal, zoom con +/−.",
                color = colors.muted,
                fontSize = 13.sp
            )
        }
        ToolPanel.LAYERS -> LayersBody(doc, colors, lang, onDispatch)
        ToolPanel.INSPECTOR -> InspectorBody(doc, colors, lang, onDispatch)
        ToolPanel.EFFECTS -> EffectsBody(doc, colors, lang, onApplyEffect)
        ToolPanel.MEDIA -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.FileUpload, null, tint = colors.primary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    if (lang == AppLanguage.ENGLISH) "Import video, image or audio"
                    else "Importar video, imagen o audio",
                    color = colors.text,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (lang == AppLanguage.ENGLISH)
                        "System picker grants a real URI. Decode/preview via MediaCodec is next."
                    else
                        "El selector otorga un URI real. Decodificacion MediaCodec es el siguiente paso.",
                    color = colors.muted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    if (lang == AppLanguage.ENGLISH) "Open picker" else "Abrir selector",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.primary)
                        .clickable(onClick = onImport)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
        ToolPanel.TEXT, ToolPanel.SCENE3D -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                if (lang == AppLanguage.ENGLISH)
                    "${panel.name} — planned after media pipeline"
                else
                    "${panel.name} — planificado tras el pipeline multimedia",
                color = colors.muted,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun LayersBody(doc: EditorDocument, colors: VyntaraColors, lang: AppLanguage, onDispatch: (EditorAction) -> Unit) {
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (lang == AppLanguage.ENGLISH) "Layers" else "Capas", color = colors.text, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = {
                onDispatch(
                    EditorAction.AddClip(
                        EditorClip(
                            id = "c${doc.clips.size + 1}",
                            name = "Layer ${doc.clips.size}",
                            type = LayerType.SOLID,
                            trackIndex = (doc.clips.maxOfOrNull { it.trackIndex } ?: 0) + 1,
                            startSec = 0f,
                            durationSec = 3f
                        )
                    )
                )
            }) { Icon(Icons.Outlined.Add, null, tint = colors.primary) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(doc.clips.reversed(), key = { it.id }) { clip ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (clip.id in doc.selectedIds) colors.primary.copy(alpha = 0.2f) else colors.surface)
                        .clickable { onDispatch(EditorAction.Select(clip.id)) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (clip.type == LayerType.VIDEO) Icons.Outlined.Movie else Icons.Outlined.Layers,
                        null, tint = colors.muted, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(clip.name, color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onDispatch(EditorAction.UpdateClip(clip.copy(visible = !clip.visible))) }, modifier = Modifier.size(32.dp)) {
                        Icon(if (clip.visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, null, tint = colors.muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorBody(doc: EditorDocument, colors: VyntaraColors, lang: AppLanguage, onDispatch: (EditorAction) -> Unit) {
    val clip = doc.selected
    if (clip == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (lang == AppLanguage.ENGLISH) "Select a clip" else "Selecciona un clip", color = colors.muted)
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text(clip.name, color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text("${clip.type} · ${"%.1f".format(clip.durationSec)}s", color = colors.muted, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        listOf(
            (if (lang == AppLanguage.ENGLISH) "Opacity" else "Opacidad") to (clip.opacity to { v: Float -> onDispatch(EditorAction.UpdateClip(clip.copy(opacity = v))) }),
            (if (lang == AppLanguage.ENGLISH) "Scale X" else "Escala X") to (clip.scaleX to { v: Float -> onDispatch(EditorAction.UpdateClip(clip.copy(scaleX = v))) }),
            (if (lang == AppLanguage.ENGLISH) "Scale Y" else "Escala Y") to (clip.scaleY to { v: Float -> onDispatch(EditorAction.UpdateClip(clip.copy(scaleY = v))) }),
            (if (lang == AppLanguage.ENGLISH) "Rotation" else "Rotacion") to (clip.rotation to { v: Float -> onDispatch(EditorAction.UpdateClip(clip.copy(rotation = v))) }),
            "X" to (clip.posX to { v: Float -> onDispatch(EditorAction.UpdateClip(clip.copy(posX = v))) }),
            "Y" to (clip.posY to { v: Float -> onDispatch(EditorAction.UpdateClip(clip.copy(posY = v))) }),
            "Blur" to (clip.blurRadius to { v: Float -> onDispatch(EditorAction.UpdateClip(clip.copy(blurRadius = v, smartBlur = v > 0.5f))) })
        ).forEach { (label, pair) ->
            val (value, onV) = pair
            val range = when (label) {
                "Blur" -> 0f to 40f
                (if (lang == AppLanguage.ENGLISH) "Rotation" else "Rotacion") -> -180f to 180f
                (if (lang == AppLanguage.ENGLISH) "Scale X" else "Escala X"),
                (if (lang == AppLanguage.ENGLISH) "Scale Y" else "Escala Y") -> 0.1f to 3f
                else -> 0f to 1f
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = colors.muted, fontSize = 11.sp, modifier = Modifier.width(72.dp))
                Slider(
                    value = value.coerceIn(range.first, range.second),
                    onValueChange = onV,
                    valueRange = range.first..range.second,
                    modifier = Modifier.weight(1f).height(28.dp),
                    colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary)
                )
                Text("%.1f".format(value), color = colors.text, fontSize = 11.sp, modifier = Modifier.width(32.dp))
            }
        }
    }
}

@Composable
private fun EffectsBody(doc: EditorDocument, colors: VyntaraColors, lang: AppLanguage, onApply: (String) -> Unit) {
    var cat by remember { mutableStateOf(if (lang == AppLanguage.ENGLISH) "All" else "Todos") }
    val cats = listOf(if (lang == AppLanguage.ENGLISH) "All" else "Todos") + EffectCatalog.categories()
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Text(
            if (lang == AppLanguage.ENGLISH) "Effects" else "Efectos",
            color = colors.text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Text(
            if (lang == AppLanguage.ENGLISH)
                "${EffectCatalog.ready().size} ready · ${EffectCatalog.all.size} listed · GPU shaders next"
            else
                "${EffectCatalog.ready().size} listos · ${EffectCatalog.all.size} listados · shaders GPU despues",
            color = colors.muted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cats.forEach { c ->
                Text(
                    c,
                    color = if (c == cat) Color.White else colors.text,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (c == cat) colors.primary else colors.surface)
                        .clickable { cat = c }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(EffectCatalog.byCategory(cat), key = { it.id }) { e ->
                val ready = e.status == EffectStatus.READY
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .clickable(enabled = ready) { onApply(e.id) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.BlurOn, null, tint = if (ready) colors.primary else colors.muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (lang == AppLanguage.ENGLISH) e.nameEn else e.name, color = colors.text, fontSize = 13.sp)
                        Text(e.category + " · " + e.cost, color = colors.muted, fontSize = 11.sp)
                    }
                    Text(
                        if (ready) "OK" else if (lang == AppLanguage.ENGLISH) "Soon" else "Pronto",
                        color = if (ready) colors.success else colors.muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
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
