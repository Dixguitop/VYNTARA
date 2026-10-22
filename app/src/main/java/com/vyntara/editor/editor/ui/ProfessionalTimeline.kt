package com.vyntara.editor.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.graphics.Bitmap
import com.vyntara.editor.editor.media.ThumbnailCache
import com.vyntara.editor.editor.EditorAction
import com.vyntara.editor.editor.EditorClip
import com.vyntara.editor.editor.EditorDocument
import com.vyntara.editor.editor.LayerType
import com.vyntara.editor.ui.theme.VyntaraColors
import kotlin.math.max

/**
 * Professional multi-track timeline inspired by reference:
 * track headers, clip blocks, audio-style waveform decoration,
 * playhead, ruler, zoom, split/delete actions.
 */
@Composable
fun ProfessionalTimeline(
    doc: EditorDocument,
    colors: VyntaraColors,
    modifier: Modifier = Modifier,
    onDispatch: (EditorAction) -> Unit,
    compact: Boolean = false
) {
    val pxPerSec = 48f * doc.timelineZoom
    val scroll = rememberScrollState()
    val trackH = if (compact) 36.dp else 44.dp
    val headerW = if (compact) 56.dp else 72.dp
    val duration = max(doc.durationSec, 5f)

    Column(modifier.background(Color(0xFF12141A))) {
        // Toolbar
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF191B23))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TimelineAction("Split", colors) {
                doc.selectedIds.firstOrNull()?.let { onDispatch(EditorAction.SplitAtPlayhead(it)) }
            }
            TimelineAction("Copy", colors) { onDispatch(EditorAction.DuplicateSelected) }
            TimelineAction("Mark", colors) { onDispatch(EditorAction.AddMarker(doc.playheadSec)) }
            TimelineAction("Del", colors) {
                if (doc.selectedIds.isNotEmpty()) onDispatch(EditorAction.DeleteSelected(doc.selectedIds))
            }
            Spacer(Modifier.weight(1f))
            Text("Zoom", color = colors.muted, fontSize = 10.sp)
            Text("−", color = colors.text, fontSize = 14.sp, modifier = Modifier
                .clickable { onDispatch(EditorAction.SetZoom(doc.timelineZoom - 0.25f)) }
                .padding(6.dp))
            Text("%.1fx".format(doc.timelineZoom), color = colors.muted, fontSize = 11.sp)
            Text("+", color = colors.text, fontSize = 14.sp, modifier = Modifier
                .clickable { onDispatch(EditorAction.SetZoom(doc.timelineZoom + 0.25f)) }
                .padding(6.dp))
        }

        // Ruler + tracks
        Row(Modifier.fillMaxSize()) {
            // Fixed headers column
            Column(Modifier.width(headerW).fillMaxHeight()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .background(Color(0xFF191B23)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Tracks", color = colors.muted, fontSize = 9.sp)
                }
                val tracks = doc.clips.groupBy { it.trackIndex }.toSortedMap()
                LazyColumn(Modifier.weight(1f)) {
                    tracks.forEach { (idx, clips) ->
                        item(key = "h$idx") {
                            TrackHeader(
                                index = idx,
                                clips = clips,
                                colors = colors,
                                height = trackH,
                                onToggleVis = { clip ->
                                    onDispatch(EditorAction.UpdateClip(clip.copy(visible = !clip.visible)))
                                }
                            )
                        }
                    }
                }
            }

            // Scrollable content
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(scroll)
            ) {
                val contentW = (duration * pxPerSec).dp.coerceAtLeast(200.dp)

                // Time ruler
                Box(
                    Modifier
                        .width(contentW)
                        .height(22.dp)
                        .background(Color(0xFF191B23))
                        .pointerInput(duration, pxPerSec) {
                            detectTapGestures { offset ->
                                onDispatch(
                                    EditorAction.SetPlayhead(
                                        (offset.x / pxPerSec).coerceIn(0f, duration)
                                    )
                                )
                            }
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val step = when {
                            pxPerSec > 80f -> 0.5f
                            pxPerSec > 40f -> 1f
                            else -> 2f
                        }
                        var t = 0f
                        while (t <= duration) {
                            val x = t * pxPerSec
                            drawLine(
                                Color(0xFF3A3F4D),
                                Offset(x, size.height * 0.4f),
                                Offset(x, size.height),
                                strokeWidth = 1f
                            )
                            t += step
                        }
                        // playhead
                        val px = doc.playheadSec * pxPerSec
                        drawLine(
                            Color(0xFF8C82FF),
                            Offset(px, 0f),
                            Offset(px, size.height),
                            strokeWidth = 2f
                        )
                    }
                    Row(Modifier.width(contentW)) {
                        var t = 0
                        while (t <= duration.toInt()) {
                            Box(Modifier.width(pxPerSec.dp)) {
                                Text("${t}s", color = colors.muted, fontSize = 9.sp)
                            }
                            t++
                        }
                    }
                }

                val tracks = doc.clips.groupBy { it.trackIndex }.toSortedMap()
                LazyColumn(Modifier.weight(1f)) {
                    tracks.forEach { (idx, clips) ->
                        item(key = "t$idx") {
                            Box(
                                Modifier
                                    .width(contentW)
                                    .height(trackH)
                                    .background(
                                        if (idx % 2 == 0) Color(0xFF15171E) else Color(0xFF12141A)
                                    )
                                    .pointerInput(duration, pxPerSec) {
                                        detectTapGestures { offset ->
                                            onDispatch(
                                                EditorAction.SetPlayhead(
                                                    (offset.x / pxPerSec).coerceIn(0f, duration)
                                                )
                                            )
                                        }
                                    }
                            ) {
                                clips.forEach { clip ->
                                    ClipBlock(
                                        clip = clip,
                                        selected = clip.id in doc.selectedIds,
                                        pxPerSec = pxPerSec,
                                        colors = colors,
                                        height = trackH,
                                        onSelect = { onDispatch(EditorAction.Select(clip.id)) },
                                        onMove = { newStart ->
                                            if (!clip.locked) {
                                                onDispatch(
                                                    EditorAction.UpdateClip(
                                                        clip.copy(startSec = newStart.coerceAtLeast(0f))
                                                    )
                                                )
                                            }
                                        },
                                        onTrim = { start, dur ->
                                            if (!clip.locked) {
                                                onDispatch(
                                                    EditorAction.UpdateClip(
                                                        clip.copy(startSec = start, durationSec = dur)
                                                    )
                                                )
                                            }
                                        }
                                    )
                                }
                                // playhead line
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .width(2.dp)
                                        .padding(start = (doc.playheadSec * pxPerSec).dp)
                                        .background(Color(0xFF8C82FF))
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
private fun TrackHeader(
    index: Int,
    clips: List<EditorClip>,
    colors: VyntaraColors,
    height: androidx.compose.ui.unit.Dp,
    onToggleVis: (EditorClip) -> Unit
) {
    val sample = clips.firstOrNull()
    val label = when (sample?.type) {
        LayerType.AUDIO -> "A$index"
        LayerType.VIDEO -> "V$index"
        LayerType.IMAGE -> "I$index"
        LayerType.TEXT -> "T$index"
        else -> "L$index"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(Color(0xFF191B23))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = colors.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (sample != null) {
            Icon(
                if (sample.visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                null,
                tint = colors.muted,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onToggleVis(sample) }
            )
        }
    }
}

@Composable
private fun ClipBlock(
    clip: EditorClip,
    selected: Boolean,
    pxPerSec: Float,
    colors: VyntaraColors,
    height: androidx.compose.ui.unit.Dp,
    onSelect: () -> Unit,
    onMove: (Float) -> Unit,
    onTrim: (start: Float, duration: Float) -> Unit = { _, _ -> }
) {
    val base = when (clip.type) {
        LayerType.VIDEO -> Color(0xFF5B6CFF)
        LayerType.AUDIO -> Color(0xFF2DD4BF)
        LayerType.IMAGE -> Color(0xFF34D399)
        LayerType.TEXT -> Color(0xFFF472B6)
        else -> Color(0xFF64748B)
    }
    var dragAcc by remember(clip.id) { mutableFloatStateOf(0f) }
    var thumb by remember(clip.mediaUri) { mutableStateOf<Bitmap?>(null) }
    val ctx = LocalContext.current
    LaunchedEffect(clip.mediaUri) {
        val uri = clip.mediaUri ?: return@LaunchedEffect
        if (clip.type == LayerType.VIDEO || clip.type == LayerType.IMAGE) {
            thumb = ThumbnailCache.get(ctx, uri, 0L)
        }
    }

    Box(
        Modifier
            .padding(start = (clip.startSec * pxPerSec).dp, top = 4.dp, bottom = 4.dp)
            .width((clip.durationSec * pxPerSec).dp.coerceAtLeast(28.dp))
            .height(height - 8.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(base.copy(alpha = if (selected) 0.95f else 0.7f))
            .border(
                if (selected) 1.5.dp else 0.dp,
                Color.White.copy(alpha = 0.85f),
                RoundedCornerShape(5.dp)
            )
            .pointerInput(clip.id, pxPerSec) {
                detectDragGestures(
                    onDragStart = { onSelect() },
                    onDrag = { change, amount ->
                        change.consume()
                        dragAcc += amount.x
                        if (kotlin.math.abs(dragAcc) > 4f) {
                            val deltaSec = dragAcc / pxPerSec
                            dragAcc = 0f
                            onMove(clip.startSec + deltaSec)
                        }
                    }
                )
            }
            .clickable(onClick = onSelect)
    ) {
        thumb?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.45f
            )
        }
        if (clip.type == LayerType.AUDIO) {
            Canvas(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 6.dp)) {
                val mid = size.height / 2
                var x = 0f
                while (x < size.width) {
                    val h = (8 + (x.toInt() * 13) % 14).toFloat()
                    drawLine(
                        Color.White.copy(alpha = 0.45f),
                        Offset(x, mid - h / 2),
                        Offset(x, mid + h / 2),
                        strokeWidth = 2f
                    )
                    x += 5f
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp).align(Alignment.CenterStart)
        ) {
            if (clip.locked) {
                Icon(Icons.Outlined.Lock, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
            }
            Text(
                clip.name,
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
        }
        // Trim handles when selected
        if (selected && !clip.locked) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(10.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.85f))
                    .pointerInput(clip.id, pxPerSec) {
                        detectDragGestures { change, amount ->
                            change.consume()
                            val d = amount.x / pxPerSec
                            val newStart = (clip.startSec + d).coerceAtLeast(0f)
                            val newDur = (clip.durationSec - d).coerceAtLeast(0.1f)
                            onTrim(newStart, newDur)
                        }
                    }
            )
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(10.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.85f))
                    .pointerInput(clip.id, pxPerSec) {
                        detectDragGestures { change, amount ->
                            change.consume()
                            val d = amount.x / pxPerSec
                            val newDur = (clip.durationSec + d).coerceAtLeast(0.1f)
                            onTrim(clip.startSec, newDur)
                        }
                    }
            )
        }
        if (clip.keyframes.isNotEmpty()) {
            val keys = clip.keyframes.values.flatten()
            keys.forEach { kf ->
                val local = kf.timeSec - clip.startSec
                if (local >= 0 && local <= clip.durationSec) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = (local * pxPerSec).dp, bottom = 2.dp)
                            .size(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFFBBF24))
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineAction(label: String, colors: VyntaraColors, onClick: () -> Unit) {
    Text(
        label,
        color = colors.text,
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF22242D))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}
