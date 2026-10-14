package com.raido.videoeditor.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raido.videoeditor.data.Project
import com.raido.videoeditor.data.ProjectStore
import com.raido.videoeditor.export.ExportQuality
import com.raido.videoeditor.export.ExportState
import com.raido.videoeditor.export.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(app: Application, private val projectId: String) : AndroidViewModel(app) {

    private val store = ProjectStore(app)
    private val exporter = Exporter(app)

    private val _project = MutableStateFlow(store.get(projectId))
    val project: StateFlow<Project?> = _project

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _export = MutableStateFlow<ExportState>(ExportState.Idle)
    val export: StateFlow<ExportState> = _export

    private var progressJob: Job? = null

    fun dismissMessage() { _message.value = null }
    fun dismissExport() { _export.value = ExportState.Idle }

    /** Imports a picked video as the project's clip (replaces the current one for now). */
    fun importVideo(uri: Uri) {
        val current = _project.value ?: return
        viewModelScope.launch {
            _importing.value = true
            try {
                val clip = withContext(Dispatchers.IO) { store.importVideo(current.id, uri) }
                update(current.copy(clips = listOf(clip)))
            } catch (e: Exception) {
                _message.value = e.message ?: "Could not import that video."
            } finally {
                _importing.value = false
            }
        }
    }

    fun setTrim(startMs: Long, endMs: Long) {
        val current = _project.value ?: return
        val clip = current.clips.firstOrNull() ?: return
        update(current.copy(clips = listOf(clip.copy(trimStartMs = startMs, trimEndMs = endMs))))
    }

    private fun update(p: Project) {
        val saved = p.copy(modifiedAt = System.currentTimeMillis())
        _project.value = saved
        store.save(saved)
    }

    fun startExport(quality: ExportQuality) {
        val p = _project.value ?: return
        val clip = p.clips.firstOrNull() ?: return
        exporter.checkStorage(clip, quality)?.let {
            _export.value = ExportState.Failed(it)
            return
        }
        _export.value = ExportState.Running(0f)
        exporter.start(
            clip = clip,
            aspect = p.aspect,
            quality = quality,
            onDone = { file ->
                progressJob?.cancel()
                _export.value = ExportState.Running(1f)
                viewModelScope.launch {
                    try {
                        val name = "Raido_${p.name.replace(Regex("[^A-Za-z0-9]+"), "_")}_${System.currentTimeMillis() / 1000}"
                        val where = withContext(Dispatchers.IO) { Exporter.saveToGallery(getApplication(), file, name) }
                        _export.value = ExportState.Done(where)
                    } catch (e: Exception) {
                        _export.value = ExportState.Failed("Export finished but saving to the gallery failed: ${e.message}")
                    }
                }
            },
            onError = { msg ->
                progressJob?.cancel()
                _export.value = ExportState.Failed(msg)
            },
        )
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                exporter.progress()?.let { if (_export.value is ExportState.Running) _export.value = ExportState.Running(it) }
                delay(200)
            }
        }
    }

    fun cancelExport() {
        progressJob?.cancel()
        exporter.cancel()
        _export.value = ExportState.Idle
    }

    override fun onCleared() {
        progressJob?.cancel()
        if (_export.value is ExportState.Running) exporter.cancel()
    }
}
