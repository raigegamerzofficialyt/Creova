@file:OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)

package com.raido.videoeditor.ui

import android.app.Application
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.raido.videoeditor.data.Clip
import com.raido.videoeditor.export.ExportQuality
import com.raido.videoeditor.export.ExportState
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

private const val MIN_TRIM_MS = 500L

@Composable
fun EditorScreen(projectId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: EditorViewModel = viewModel(
        key = projectId,
        factory = viewModelFactory {
            initializer {
                EditorViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application, projectId)
            }
        },
    )
    val project by vm.project.collectAsState()
    val importing by vm.importing.collectAsState()
    val message by vm.message.collectAsState()
    val exportState by vm.export.collectAsState()
    var showExportOptions by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) vm.importVideo(uri)
    }
    val pickVideo = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }

    val p = project
    if (p == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Text("This project could not be found.", color = MaterialTheme.colorScheme.onBackground)
        }
        BackHandler(onBack = onBack)
        return
    }
    val clip = p.clips.firstOrNull()
    val exporting = exportState is ExportState.Running

    BackHandler(enabled = !exporting, onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = !exporting) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Column(Modifier.weight(1f)) {
                Text(p.name, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(p.aspect.label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Button(
                onClick = { showExportOptions = true },
                enabled = clip != null && !exporting && !importing,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export")
            }
            Spacer(Modifier.width(8.dp))
        }

        if (clip == null) {
            EmptyEditor(importing = importing, onImport = pickVideo, modifier = Modifier.weight(1f))
        } else {
            PreviewAndTrim(
                clip = clip,
                aspectRatio = p.aspect.ratio,
                onTrim = vm::setTrim,
                modifier = Modifier.weight(1f),
            )
            // Bottom toolbar: only tools that actually work are shown.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ToolButton(Icons.Filled.VideoLibrary, if (importing) "Importing…" else "Replace video") {
                    if (!importing) pickVideo()
                }
            }
        }
    }

    message?.let {
        AlertDialog(
            onDismissRequest = vm::dismissMessage,
            title = { Text("Something went wrong") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = vm::dismissMessage) { Text("OK") } },
        )
    }

    if (showExportOptions && clip != null) {
        ExportOptionsDialog(
            clip = clip,
            onDismiss = { showExportOptions = false },
            onStart = { quality ->
                showExportOptions = false
                vm.startExport(quality)
            },
        )
    }

    when (val s = exportState) {
        is ExportState.Running -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Exporting…") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${(s.progress * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = vm::cancelExport) { Text("Cancel") } },
        )
        is ExportState.Done -> AlertDialog(
            onDismissRequest = vm::dismissExport,
            title = { Text("Export complete") },
            text = { Text("Saved to your gallery in ${s.savedTo}.") },
            confirmButton = { TextButton(onClick = vm::dismissExport) { Text("OK") } },
        )
        is ExportState.Failed -> AlertDialog(
            onDismissRequest = vm::dismissExport,
            title = { Text("Export failed") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = vm::dismissExport) { Text("OK") } },
        )
        ExportState.Idle -> Unit
    }
}

@Composable
private fun EmptyEditor(importing: Boolean, onImport: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (importing) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Importing video…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Add a video to start editing", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onImport, shape = RoundedCornerShape(14.dp)) { Text("Import video") }
            }
        }
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun PreviewAndTrim(
    clip: Clip,
    aspectRatio: Float,
    onTrim: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(player) { onDispose { player.release() } }

    // Load the clip (re-load only if the file itself changes).
    LaunchedEffect(clip.path) {
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(clip.path))))
        player.prepare()
        player.seekTo(clip.trimStartMs)
    }

    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(clip.trimStartMs) }

    // Local slider state so dragging is smooth; committed to the project when the finger lifts.
    var startMs by remember(clip.id) { mutableFloatStateOf(clip.trimStartMs.toFloat()) }
    var endMs by remember(clip.id) { mutableFloatStateOf(clip.trimEndMs.toFloat()) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Keep playback inside the trimmed range and update the playhead.
    LaunchedEffect(player, clip.id) {
        while (true) {
            val pos = player.currentPosition
            positionMs = pos
            if (player.isPlaying && pos >= endMs.toLong()) {
                player.pause()
                player.seekTo(startMs.toLong())
            }
            delay(40)
        }
    }

    Column(modifier.padding(horizontal = 16.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            this.player = player
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    if (player.currentPosition >= endMs.toLong() - 50 || player.currentPosition < startMs.toLong()) {
                        player.seekTo(startMs.toLong())
                    }
                    player.play()
                }
            }) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                "${formatTime(positionMs - startMs.toLong())} / ${formatTime((endMs - startMs).toLong())}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }

        Text("Trim", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        RangeSlider(
            value = startMs..endMs,
            onValueChange = { range ->
                val total = clip.durationMs.toFloat()
                var s = range.start.coerceIn(0f, total)
                var e = range.endInclusive.coerceIn(0f, total)
                if (e - s < MIN_TRIM_MS) {
                    // Keep a minimum length, pushing whichever handle wasn't moved.
                    if (s != startMs) s = (e - MIN_TRIM_MS).coerceAtLeast(0f) else e = (s + MIN_TRIM_MS).coerceAtMost(total)
                }
                val movedStart = s != startMs
                startMs = s
                endMs = e
                player.pause()
                player.seekTo(if (movedStart) s.toLong() else (e - 200f).coerceAtLeast(s).toLong())
            },
            onValueChangeFinished = { onTrim(startMs.toLong(), endMs.toLong()) },
            valueRange = 0f..clip.durationMs.toFloat(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(startMs.toLong()), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(formatTime(endMs.toLong()), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ExportOptionsDialog(clip: Clip, onDismiss: () -> Unit, onStart: (ExportQuality) -> Unit) {
    var quality by remember { mutableStateOf(ExportQuality.FHD_1080) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export MP4") },
        text = {
            Column {
                Text("Length: ${formatTime(clip.trimmedMs)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportQuality.values().forEach { q ->
                        val selected = q == quality
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { quality = q }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) { Text(q.label, color = MaterialTheme.colorScheme.onSurface) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Frame rate follows the source video. 4K and 60 FPS export are not available yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onStart(quality) }) { Text("Export") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L)) / 1000.0
    val minutes = (total / 60).toInt()
    val seconds = total - minutes * 60
    return String.format(Locale.US, "%d:%04.1f", minutes, seconds)
}
