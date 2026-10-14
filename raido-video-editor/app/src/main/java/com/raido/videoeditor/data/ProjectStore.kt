package com.raido.videoeditor.data

import android.content.Context
import java.io.File
import java.util.UUID

/** Saves each project as a small JSON file in the app's private storage. */
class ProjectStore(context: Context) {

    private val dir = File(context.filesDir, "projects").apply { mkdirs() }

    fun list(): List<Project> =
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { Project.fromJson(it.readText()) }
            .sortedByDescending { it.modifiedAt }

    fun create(name: String, aspect: AspectRatio): Project {
        val now = System.currentTimeMillis()
        val project = Project(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Untitled project" },
            aspect = aspect,
            createdAt = now,
            modifiedAt = now,
        )
        save(project)
        return project
    }

    fun save(project: Project) {
        File(dir, "${project.id}.json").writeText(project.toJson())
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }
}
