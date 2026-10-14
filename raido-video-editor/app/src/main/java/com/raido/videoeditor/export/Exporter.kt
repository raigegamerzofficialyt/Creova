@file:OptIn(UnstableApi::class)

package com.raido.videoeditor.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.raido.videoeditor.data.AspectRatio
import com.raido.videoeditor.data.Clip
import java.io.File

enum class ExportQuality(val label: String, val shortSide: Int, val approxBytesPerSecond: Long) {
    HD_720("720p", 720, 1_000_000L),
    FHD_1080("1080p", 1080, 2_000_000L),
}

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Float) : ExportState
    data class Done(val savedTo: String) : ExportState
    data class Failed(val message: String) : ExportState
}

/** Thin wrapper around Media3 Transformer. Must be created and used on the main thread. */
class Exporter(private val context: Context) {

    private var transformer: Transformer? = null
    private var outputFile: File? = null

    /** Returns an error message if the phone doesn't have enough free space, otherwise null. */
    fun checkStorage(clip: Clip, quality: ExportQuality): String? {
        val seconds = (clip.trimmedMs / 1000L).coerceAtLeast(1L)
        // Need room for the temporary export AND the copy saved to the gallery.
        val needed = seconds * quality.approxBytesPerSecond * 2L + 50_000_000L
        val free = StatFs(context.cacheDir.absolutePath).availableBytes
        return if (free < needed) {
            "Not enough storage. Need about ${needed / 1_000_000} MB free, only ${free / 1_000_000} MB available."
        } else {
            null
        }
    }

    fun start(
        clip: Clip,
        aspect: AspectRatio,
        quality: ExportQuality,
        onDone: (File) -> Unit,
        onError: (String) -> Unit,
    ) {
        val exportDir = File(context.cacheDir, "export").apply { mkdirs() }
        val out = File(exportDir, "raido_${System.currentTimeMillis()}.mp4")
        outputFile = out

        val (w, h) = outputSize(aspect, quality.shortSide)
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(File(clip.path)))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.trimStartMs)
                    .setEndPositionMs(clip.trimEndMs)
                    .build(),
            )
            .build()
        val edited = EditedMediaItem.Builder(mediaItem)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)),
                ),
            )
            .build()

        val t = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    onDone(out)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    out.delete()
                    onError("Export failed (${exportException.errorCodeName}). Try 720p or a shorter clip.")
                }
            })
            .build()
        transformer = t
        try {
            t.start(edited, out.absolutePath)
        } catch (e: Exception) {
            out.delete()
            onError("Could not start export: ${e.message}")
        }
    }

    /** Progress 0..1, or null if unknown. */
    fun progress(): Float? {
        val t = transformer ?: return null
        val holder = ProgressHolder()
        return if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) holder.progress / 100f else null
    }

    fun cancel() {
        transformer?.cancel()
        outputFile?.delete()
    }

    companion object {
        /** Output size with an even width/height, based on the project's aspect ratio. */
        fun outputSize(aspect: AspectRatio, shortSide: Int): Pair<Int, Int> {
            val r = aspect.ratio
            val (w, h) = if (r >= 1f) (shortSide * r).toInt() to shortSide else shortSide to (shortSide / r).toInt()
            return (w and 1.inv()) to (h and 1.inv())
        }

        /** Copies the finished MP4 into the phone's gallery (Movies/Raido). Blocking. Returns a display path. */
        fun saveToGallery(context: Context, file: File, displayName: String): String {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "$displayName.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Raido")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create a gallery entry.")
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: error("Could not write to the gallery.")
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            } finally {
                file.delete()
            }
            return "Movies/Raido/$displayName.mp4"
        }
    }
}
