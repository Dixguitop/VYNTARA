package com.vyntara.editor.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vyntara.editor.VyntaraApp
import com.vyntara.editor.data.PreferencesRepository
import com.vyntara.editor.i18n.AppLanguage
import com.vyntara.editor.project.ProjectRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: PreferencesRepository =
        (application as VyntaraApp).preferencesRepository
    private val projects = ProjectRepository(application)

    val settings: StateFlow<AppSettings> = prefs.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppSettings()
    )

    fun update(block: (AppSettings) -> AppSettings) {
        viewModelScope.launch { prefs.update(block) }
    }

    fun setLanguage(lang: AppLanguage) = update { it.copy(languageCode = lang.code) }
    fun setTheme(id: AppThemeId) = update { it.copy(themeId = id) }

    fun projectsStorageBytes(): Long {
        val dir = File(getApplication<Application>().filesDir, "projects")
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    fun clearPreviewCache(): Boolean {
        val cache = File(getApplication<Application>().cacheDir, "preview")
        return if (cache.exists()) cache.deleteRecursively() else true
    }

    fun deleteAllProjects() {
        projects.listProjects().forEach { projects.deleteProject(it.id) }
    }

    fun resetSettings() {
        viewModelScope.launch { prefs.resetAll() }
    }

    fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
