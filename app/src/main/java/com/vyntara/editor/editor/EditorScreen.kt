package com.vyntara.editor.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vyntara.editor.home.LocalLanguage
import com.vyntara.editor.i18n.Strings
import com.vyntara.editor.project.ProjectSummary
import com.vyntara.editor.ui.theme.LocalAppTheme

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

    var selectedTool by remember { mutableStateOf("select") }
    var blurArmed by remember { mutableStateOf(false) }
    var statusHint by remember { mutableStateOf(t("editor_hint_start")) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(project.title, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "${project.aspectLabel} · ${project.resolutionLabel} · ${project.fps}fps",
                        color = colors.muted,
                        fontSize = 11.sp
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = t("editor_back"),
                        tint = colors.text
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val ratio = project.width.toFloat() / project.height.toFloat()
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .aspectRatio(ratio.coerceIn(0.4f, 2.5f))
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color((project.backgroundArgb and 0xFFFFFFFFL).toULong()))
                    .border(1.dp, colors.muted.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${project.width}×${project.height}",
                    color = colors.muted.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolBtn(Icons.Outlined.PlayArrow, t("editor_play"), selectedTool == "play") {
                selectedTool = "play"
                statusHint = t("editor_hint_play")
            }
            ToolBtn(Icons.Outlined.Add, t("editor_import"), selectedTool == "import") {
                selectedTool = "import"
                statusHint = t("editor_hint_import")
            }
            ToolBtn(Icons.Outlined.ContentCut, t("editor_cut"), selectedTool == "cut") {
                selectedTool = "cut"
                statusHint = t("editor_hint_cut")
            }
            ToolBtn(Icons.Outlined.BlurOn, t("editor_blur"), blurArmed) {
                blurArmed = !blurArmed
                statusHint = if (blurArmed) t("editor_hint_blur_on") else t("editor_hint_blur_off")
            }
            ToolBtn(Icons.Outlined.Layers, t("editor_layers"), selectedTool == "layers") {
                selectedTool = "layers"
                statusHint = t("editor_hint_layers")
            }
            ToolBtn(Icons.Outlined.Tune, t("editor_props"), selectedTool == "props") {
                selectedTool = "props"
                statusHint = t("editor_hint_props")
            }
        }

        Text(
            statusHint,
            color = if (blurArmed) colors.secondary else colors.muted,
            fontSize = 11.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 14.dp, vertical = 4.dp)
        )

        Column(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(colors.card)
                .padding(vertical = 8.dp)
        ) {
            Text(
                t("editor_timeline"),
                color = colors.muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(Modifier.height(6.dp))
            TimelineTrack(label = "V1", trackColor = colors.primary.copy(alpha = 0.5f))
            Spacer(Modifier.height(6.dp))
            TimelineTrack(label = "A1", trackColor = colors.secondary.copy(alpha = 0.35f))
        }
    }
}

@Composable
private fun ToolBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalAppTheme.current.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.primary.copy(alpha = 0.25f) else Color.Transparent)
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) colors.secondary else colors.text,
            modifier = Modifier.size(22.dp)
        )
        Text(label, color = colors.muted, fontSize = 9.sp)
    }
}

@Composable
private fun TimelineTrack(label: String, trackColor: Color) {
    val colors = LocalAppTheme.current.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = colors.muted, fontSize = 11.sp, modifier = Modifier.width(28.dp))
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
                    .width(280.dp)
                    .fillMaxHeight()
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(trackColor.copy(alpha = 0.15f))
            )
        }
    }
}
