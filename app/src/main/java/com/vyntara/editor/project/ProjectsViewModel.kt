package com.vyntara.editor.project

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vyntara.editor.capabilities.DeviceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProjectsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ProjectRepository(application)

    private val _projects = MutableStateFlow<List<ProjectSummary>>(emptyList())
    val projects: StateFlow<List<ProjectSummary>> = _projects.asStateFlow()

    private val _deviceProfile = MutableStateFlow<DeviceProfile?>(null)
    val deviceProfile: StateFlow<DeviceProfile?> = _deviceProfile.asStateFlow()

    init {
        refresh()
    }

    fun setDeviceProfile(profile: DeviceProfile?) {
        _deviceProfile.value = profile
    }

    fun refresh() {
        viewModelScope.launch {
            _projects.value = repo.listProjects()
        }
    }

    fun createProject(settings: ProjectSettings): ProjectSummary {
        val created = repo.createProject(settings)
        refresh()
        return created
    }

    fun deleteProject(id: String) {
        repo.deleteProject(id)
        refresh()
    }

    fun getProject(id: String): ProjectSummary? = repo.getProject(id)
}
