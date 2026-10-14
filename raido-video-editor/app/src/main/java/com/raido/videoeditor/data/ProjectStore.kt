package com.raido.videoeditor.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

/** Saves each project as a small JSON file in the app's private storage. */
class ProjectStore(private val context: Context) {

    private val dir = File(context.filesDir, "projects").apply { mkdirs() }
    private val mediaDir = File(context.filesDir, "media").apply { mkdirs() }

    fun list(): List<Project> =
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { Project.fromJson(it.readText()) }
            .sortedByDescending { it.modifiedAt }

    fun get(id: String): Project? =
        File(dir, "$id.json").takeIf { it.exists() }?.let { Project.fromJson(it.readText()) }

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
        // Write to a temp file first so a crash can never leave a half-written project.
        val tmp = File(dir, "${project.id}.json.tmp")
        tmp.writeText(project.toJson())
        tmp.renameTo(File(dir, "${project.id}.json"))
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
        File(mediaDir, id).deleteRecursively()
    }

    /**
     * Copies a picked video into the app's private storage (so the project keeps working even if
     * the original is moved or deleted) and reads its duration. Blocking: call from a background thread.
     */
    fun importVideo(projectId: String, uri: Uri): Clip {
        val folder = File(mediaDir, projectId).apply { mkdirs() }
        val clipId = UUID.randomUUID().toString()
        val target = File(folder, "$clipId.mp4")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { out -> input.copyTo(out) }
        } ?: error("Could not open the selected video.")

        val retriever = MediaMetadataRetriever()
        val duration = try {
            retriever.setDataSource(target.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
        if (duration == null || duration <= 0L) {
            target.delete()
            error("This file doesn't look like a playable video.")
        }
        return Clip(
            id = clipId,
            path = target.absolutePath,
            name = displayName(uri) ?: "Video",
            durationMs = duration,
            trimStartMs = 0L,
            trimEndMs = duration,
        )
    }

    private fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }
}
