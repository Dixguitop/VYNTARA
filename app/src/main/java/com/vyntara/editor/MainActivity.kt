package com.vyntara.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vyntara.editor.boot.BootViewModel
import com.vyntara.editor.boot.CinematicBootScreen
import com.vyntara.editor.editor.EditorScreen
import com.vyntara.editor.home.LocalLanguage
import com.vyntara.editor.i18n.AppLanguage
import com.vyntara.editor.i18n.Strings
import com.vyntara.editor.navigation.MainShell
import com.vyntara.editor.project.ProjectSummary
import com.vyntara.editor.project.ProjectsViewModel
import com.vyntara.editor.settings.SettingsViewModel
import com.vyntara.editor.ui.theme.LocalAppTheme
import com.vyntara.editor.ui.theme.ProvideAppTheme

class MainActivity : ComponentActivity() {

    private val bootViewModel: BootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsVm: SettingsViewModel = viewModel()
            val settings by settingsVm.settings.collectAsStateWithLifecycle()
            ProvideAppTheme(
                themeId = settings.themeId,
                density = settings.uiDensity,
                corners = settings.cardCorners,
                accentOverride = settings.accentOverrideArgb.takeIf { it != 0L }
            ) {
                val colors = LocalAppTheme.current.colors
                val lang = AppLanguage.fromCode(settings.languageCode)
                CompositionLocalProvider(LocalLanguage provides lang) {
                    Surface(Modifier.fillMaxSize(), color = colors.background) {
                        VyntaraRoot(bootViewModel, settingsVm)
                    }
                }
            }
        }
    }
}

private sealed interface AppDestination {
    data object Boot : AppDestination
    data object Hub : AppDestination
    data class Editor(val project: ProjectSummary) : AppDestination
}

@Composable
private fun VyntaraRoot(
    bootViewModel: BootViewModel,
    settingsVm: SettingsViewModel
) {
    val phase by bootViewModel.cinematicPhase.collectAsStateWithLifecycle()
    val status by bootViewModel.statusMessage.collectAsStateWithLifecycle()
    val canSkip by bootViewModel.canSkip.collectAsStateWithLifecycle()
    val navigate by bootViewModel.navigateToEditor.collectAsStateWithLifecycle()
    val profile by bootViewModel.deviceProfile.collectAsStateWithLifecycle()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()

    val projectsVm: ProjectsViewModel = viewModel()
    val projects by projectsVm.projects.collectAsStateWithLifecycle()

    var destination by remember { mutableStateOf<AppDestination>(AppDestination.Boot) }
    val storageLabel = remember(projects) {
        settingsVm.formatBytes(settingsVm.projectsStorageBytes())
    }
    val lang = AppLanguage.fromCode(settings.languageCode)

    LaunchedEffect(Unit) {
        bootViewModel.startBoot()
    }

    LaunchedEffect(navigate) {
        if (navigate && destination is AppDestination.Boot) {
            projectsVm.setDeviceProfile(profile)
            destination = AppDestination.Hub
            bootViewModel.onEditorEntered()
        }
    }

    LaunchedEffect(profile) {
        projectsVm.setDeviceProfile(profile)
    }

    when (val dest = destination) {
        is AppDestination.Boot -> {
            CinematicBootScreen(
                phase = phase,
                statusMessage = status.ifBlank {
                    Strings.t(lang, "boot_init")
                },
                canSkip = canSkip,
                onSkip = { bootViewModel.skipAnimation() },
                modifier = Modifier.fillMaxSize()
            )
        }
        is AppDestination.Hub -> {
            MainShell(
                projects = projects,
                deviceProfile = profile,
                settings = settings,
                storageLabel = storageLabel,
                onCreateProject = { projectsVm.createProject(it) },
                onOpenProject = { destination = AppDestination.Editor(it) },
                onDeleteProject = { projectsVm.deleteProject(it) },
                onUpdateSettings = { settingsVm.update(it) },
                onClearCache = { settingsVm.clearPreviewCache() },
                onDeleteAllProjects = {
                    settingsVm.deleteAllProjects()
                    projectsVm.refresh()
                },
                onResetSettings = { settingsVm.resetSettings() },
                onRefreshDiagnostics = {
                    // Re-run capability check would go here; profile already cached
                    projectsVm.setDeviceProfile(profile)
                }
            )
        }
        is AppDestination.Editor -> {
            EditorScreen(
                project = dest.project,
                onBack = {
                    projectsVm.refresh()
                    destination = AppDestination.Hub
                }
            )
        }
    }
}
