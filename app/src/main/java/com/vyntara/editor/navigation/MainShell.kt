package com.vyntara.editor.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vyntara.editor.capabilities.DeviceProfile
import com.vyntara.editor.home.HomeScreen
import com.vyntara.editor.home.StartScreen
import com.vyntara.editor.home.TemplatesScreen
import com.vyntara.editor.i18n.AppLanguage
import com.vyntara.editor.i18n.Strings
import com.vyntara.editor.project.CreateProjectSheet
import com.vyntara.editor.project.ProjectSettings
import com.vyntara.editor.project.ProjectSummary
import com.vyntara.editor.project.ProjectsScreen
import com.vyntara.editor.settings.AppSettings
import com.vyntara.editor.settings.SettingsScreen
import com.vyntara.editor.ui.theme.LocalAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    projects: List<ProjectSummary>,
    deviceProfile: DeviceProfile?,
    settings: AppSettings,
    storageLabel: String,
    onCreateProject: (ProjectSettings) -> ProjectSummary,
    onOpenProject: (ProjectSummary) -> Unit,
    onDeleteProject: (String) -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onClearCache: () -> Unit,
    onDeleteAllProjects: () -> Unit,
    onResetSettings: () -> Unit,
    onRefreshDiagnostics: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) } // 0 Home 1 Start 2 Projects 3 Templates 4 Settings
    var showCreate by remember { mutableStateOf(false) }
    val lang = AppLanguage.fromCode(settings.languageCode)
    fun t(key: String) = Strings.t(lang, key)
    val colors = LocalAppTheme.current.colors

    Scaffold(
        containerColor = colors.background,
        topBar = {
            // Gear always reachable from hub
            androidx.compose.material3.CenterAlignedTopAppBar(
                title = {
                    Text(
                        when (tab) {
                            0 -> t("tab_home")
                            1 -> t("tab_start")
                            2 -> t("tab_projects")
                            3 -> t("tab_templates")
                            else -> t("settings_title")
                        },
                        color = colors.text
                    )
                },
                actions = {
                    IconButton(onClick = { tab = 4 }) {
                        Icon(Icons.Outlined.Settings, contentDescription = t("tab_settings"), tint = colors.text)
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.surface
                )
            )
        },
        bottomBar = {
            if (tab != 4) {
                NavigationBar(containerColor = colors.surface) {
                    NavItem(tab == 0, { tab = 0 }, Icons.Outlined.Home, t("tab_home"), settings.showNavLabels, colors)
                    NavItem(tab == 1, { tab = 1 }, Icons.Outlined.School, t("tab_start"), settings.showNavLabels, colors)
                    NavigationBarItem(
                        selected = false,
                        onClick = { showCreate = true },
                        icon = {
                            FloatingActionButton(
                                onClick = { showCreate = true },
                                shape = CircleShape,
                                containerColor = colors.primary,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = t("create"), tint = Color.White)
                            }
                        },
                        label = { if (settings.showNavLabels) Text("") },
                        colors = navColors(colors)
                    )
                    NavItem(tab == 2, { tab = 2 }, Icons.Outlined.FolderOpen, t("tab_projects"), settings.showNavLabels, colors)
                    NavItem(tab == 3, { tab = 3 }, Icons.Outlined.AutoAwesome, t("tab_templates"), settings.showNavLabels, colors)
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> HomeScreen(
                    projectCount = projects.size,
                    storageLabel = storageLabel,
                    profile = deviceProfile,
                    onOpenSettings = { tab = 4 },
                    onOpenProjects = { tab = 2 }
                )
                1 -> StartScreen()
                2 -> ProjectsScreen(
                    projects = projects,
                    onOpen = onOpenProject,
                    onDelete = onDeleteProject
                )
                3 -> TemplatesScreen()
                4 -> SettingsScreen(
                    settings = settings,
                    profile = deviceProfile,
                    storageLabel = storageLabel,
                    onUpdate = onUpdateSettings,
                    onClearCache = onClearCache,
                    onDeleteAllProjects = onDeleteAllProjects,
                    onResetSettings = onResetSettings,
                    onRefreshDiagnostics = onRefreshDiagnostics
                )
            }
        }
    }

    if (showCreate) {
        CreateProjectSheet(
            profile = deviceProfile,
            settings = settings,
            onDismiss = { showCreate = false },
            onCreate = { s ->
                val created = onCreateProject(s)
                showCreate = false
                tab = 2
                onOpenProject(created)
            }
        )
    }
}

@Composable
private fun NavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    showLabel: Boolean,
    colors: com.vyntara.editor.ui.theme.VyntaraColors
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { if (showLabel) Text(label) },
        colors = navColors(colors)
    )
}

@Composable
private fun navColors(colors: com.vyntara.editor.ui.theme.VyntaraColors) =
    NavigationBarItemDefaults.colors(
        selectedIconColor = colors.primary,
        selectedTextColor = colors.primary,
        unselectedIconColor = colors.muted,
        unselectedTextColor = colors.muted,
        indicatorColor = colors.primary.copy(alpha = 0.15f)
    )
