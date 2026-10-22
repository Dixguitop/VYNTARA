package com.vyntara.editor.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vyntara.editor.ui.theme.colors.card
import com.vyntara.editor.ui.theme.colors.muted
import com.vyntara.editor.ui.theme.colors.text
import com.vyntara.editor.home.LocalLanguage
import com.vyntara.editor.i18n.Strings
import com.vyntara.editor.ui.theme.LocalAppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectsScreen(
    projects: List<ProjectSummary>,
    onOpen: (ProjectSummary) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppTheme.current.colors
    val lang = LocalLanguage.current
    fun t(key: String) = Strings.t(lang, key)
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(t("projects_title"), color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                t("projects_sort"),
                color = colors.muted,
                fontSize = 13.sp
            )
        }

        if (projects.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t("projects_empty"), color = colors.text, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(t("projects_empty_hint"), color = colors.muted, fontSize = 13.sp)
                }
            }
        } else {
            val grouped = projects.groupBy { dayKey(it.updatedAt) }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                grouped.forEach { (day, list) ->
                    item {
                        Text(
                            day,
                            color = colors.muted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(list, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            onOpen = { onOpen(project) },
                            onDelete = { onDelete(project.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(
    project: ProjectSummary,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalAppTheme.current.colors
    val lang = LocalLanguage.current
    fun t(key: String) = Strings.t(lang, key)
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color((project.backgroundArgb and 0xFFFFFFFFL).toULong())),
            contentAlignment = Alignment.BottomStart
        ) {
            Text(
                project.durationLabel,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 10.sp,
                modifier = Modifier
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                project.title,
                color = colors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetaIcon(Icons.Outlined.AspectRatio, project.aspectLabel)
                MetaIcon(Icons.Outlined.HighQuality, project.resolutionLabel)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetaIcon(Icons.Outlined.Folder, project.sizeLabel)
                MetaIcon(Icons.Outlined.Speed, "${project.fps}fps")
            }
        }

        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = t("more"), tint = colors.muted)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(t("projects_open")) },
                    onClick = { menu = false; onOpen() }
                )
                DropdownMenuItem(
                    text = { Text(t("projects_delete"), color = colors.danger) },
                    onClick = { menu = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun MetaIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val colors = LocalAppTheme.current.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = colors.muted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, color = colors.muted, fontSize = 12.sp)
    }
}

private fun dayKey(epochMs: Long): String {
    val fmt = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es", "ES"))
    return fmt.format(Date(epochMs))
}
