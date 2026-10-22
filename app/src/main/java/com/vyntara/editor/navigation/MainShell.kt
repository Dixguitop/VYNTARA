package com.vyntara.editor.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.vyntara.editor.ui.theme.VyntaraColors

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
    var tab by remember { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    val lang = AppLanguage.fromCode(settings.languageCode)
    fun t(key: String) = Strings.t(lang, key)
    val themeColors = LocalAppTheme.current.colors

    Scaffold(
        containerColor = themeColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when (tab) {
                            0 -> t("tab_home")
                            1 -> t("tab_start")
                            2 -> t("tab_projects")
                            3 -> t("tab_templates")
                            else -> t("settings_title")
                        },
                        color = themeColors.text
                    )
                },
                actions = {
                    IconButton(onClick = { tab = 4 }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = t("tab_settings"),
                            tint = themeColors.text
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = themeColors.surface
                )
            )
        },
        bottomBar = {
            if (tab != 4) {
                NavigationBar(containerColor = themeColors.surface) {
                    HubNavItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = Icons.Outlined.Home,
                        label = t("tab_home"),
                        showLabel = settings.showNavLabels,
                        themeColors = themeColors
                    )
                    HubNavItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = Icons.Outlined.School,
                        label = t("tab_start"),
                        showLabel = settings.showNavLabels,
                        themeColors = themeColors
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { showCreate = true },
                        icon = {
                            FloatingActionButton(
                                onClick = { showCreate = true },
                                shape = CircleShape,
                                containerColor = themeColors.primary,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = t("create"),
                                    tint = Color.White
                                )
                            }
                        },
                        label = { if (settings.showNavLabels) Text("") },
                        colors = hubNavColors(themeColors)
                    )
                    HubNavItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = Icons.Outlined.FolderOpen,
                        label = t("tab_projects"),
                        showLabel = settings.showNavLabels,
                        themeColors = themeColors
                    )
                    HubNavItem(
                        selected = tab == 3,
                        onClick = { tab = 3 },
                        icon = Icons.Outlined.AutoAwesome,
                        label = t("tab_templates"),
                        showLabel = settings.showNavLabels,
                        themeColors = themeColors
                    )
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
            onCreate = { projectSettings ->
                val created = onCreateProject(projectSettings)
                showCreate = false
                tab = 2
                onOpenProject(created)
            }
        )
    }
}

/** Must be RowScope — NavigationBarItem is an extension on RowScope. */
@Composable
private fun RowScope.HubNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    showLabel: Boolean,
    themeColors: VyntaraColors
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(imageVector = icon, contentDescription = label) },
        label = { if (showLabel) Text(text = label) },
        colors = hubNavColors(themeColors)
    )
}

@Composable
private fun hubNavColors(themeColors: VyntaraColors) =
    NavigationBarItemDefaults.colors(
        selectedIconColor = themeColors.primary,
        selectedTextColor = themeColors.primary,
        unselectedIconColor = themeColors.muted,
        unselectedTextColor = themeColors.muted,
        indicatorColor = themeColors.primary.copy(alpha = 0.15f)
    )
