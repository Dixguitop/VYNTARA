package com.vyntara.editor.project

import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * Local project index. Stores summaries in app files dir.
 * Full composition data will live in per-project folders later.
 */
class ProjectRepository(private val context: Context) {

    private val indexFile: File
        get() = File(context.filesDir, "projects_index.json")

    private val projectsDir: File
        get() = File(context.filesDir, "projects").also { if (!it.exists()) it.mkdirs() }

    fun listProjects(): List<ProjectSummary> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).mapNotNull { i ->
                ProjectSummary.fromJson(arr.getString(i))
            }.sortedByDescending { it.updatedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getProject(id: String): ProjectSummary? = listProjects().find { it.id == id }

    fun createProject(settings: ProjectSettings): ProjectSummary {
        val summary = ProjectSummary(
            title = settings.title.ifBlank { "Nuevo proyecto" },
            width = settings.width,
            height = settings.height,
            fps = settings.fps,
            durationMs = 0L,
            fileSizeBytes = 0L,
            backgroundArgb = when (settings.background) {
                BackgroundPreset.CUSTOM -> settings.customBackgroundArgb
                BackgroundPreset.TRANSPARENT -> 0x00000000L
                BackgroundPreset.BLACK -> 0xFF000000L
                BackgroundPreset.WHITE -> 0xFFFFFFFFL
                BackgroundPreset.LIGHT_GRAY -> 0xFFD1D5DBL
                BackgroundPreset.GREEN -> 0xFF22C55EL
                BackgroundPreset.BLUE -> 0xFF3B82F6L
            }
        )
        // Project folder scaffold
        File(projectsDir, summary.id).mkdirs()
        val all = listProjects().toMutableList()
        all.add(0, summary)
        saveIndex(all)
        return summary
    }

    fun updateProject(summary: ProjectSummary) {
        val all = listProjects().toMutableList()
        val idx = all.indexOfFirst { it.id == summary.id }
        if (idx >= 0) {
            all[idx] = summary.copy(updatedAt = System.currentTimeMillis())
            saveIndex(all)
        }
    }

    fun deleteProject(id: String) {
        val all = listProjects().filterNot { it.id == id }
        saveIndex(all)
        File(projectsDir, id).deleteRecursively()
    }

    private fun saveIndex(projects: List<ProjectSummary>) {
        val arr = JSONArray()
        projects.forEach { arr.put(it.toJson()) }
        indexFile.writeText(arr.toString())
    }
}
