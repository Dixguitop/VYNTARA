package com.vyntara.editor.project

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vyntara.editor.home.LocalLanguage
import com.vyntara.editor.i18n.Strings
import com.vyntara.editor.ui.theme.LocalAppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Projects hub — grid layout aligned with reference mock (cards + search).
 */
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
    var query by remember { mutableStateOf("") }
    val filtered = remember(projects, query) {
        if (query.isBlank()) projects
        else projects.filter { it.title.contains(query, ignoreCase = true) }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            t("projects_title"),
            color = colors.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp, bottom = 10.dp)
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text(
                    if (lang.name == "ENGLISH") "Search projects…" else "Buscar proyectos…",
                    color = colors.muted
                )
            },
            leadingIcon = {
                Icon(Icons.Outlined.Search, null, tint = colors.muted)
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.muted.copy(alpha = 0.35f),
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                cursorColor = colors.primary,
                focusedTextColor = colors.text,
                unfocusedTextColor = colors.text
            )
        )
        Spacer(Modifier.height(14.dp))

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.FolderOpen, null, tint = colors.muted, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(t("projects_empty"), color = colors.text, fontSize = 16.sp)
                    Text(t("projects_empty_hint"), color = colors.muted, fontSize = 13.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered, key = { it.id }) { project ->
                    ProjectGridCard(
                        project = project,
                        onOpen = { onOpen(project) },
                        onDelete = { onDelete(project.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectGridCard(
    project: ProjectSummary,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalAppTheme.current.colors
    val accent = colors.primary
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, colors.muted.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.35f),
                            Color(0xFF0A0A12),
                            colors.secondary.copy(alpha = 0.25f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Movie, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(36.dp))
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Delete, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                }
            }
            Text(
                project.resolutionLabel,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
        Column(Modifier.padding(10.dp)) {
            Text(
                project.title,
                color = colors.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${project.aspectLabel} · ${project.fps}fps · ${project.sizeLabel}",
                color = colors.muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                relativeDay(project.updatedAt),
                color = colors.muted.copy(alpha = 0.8f),
                fontSize = 10.sp
            )
        }
    }
}

private fun relativeDay(epochMs: Long): String {
    val fmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    return fmt.format(Date(epochMs))
}
