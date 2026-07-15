package com.neversoft.editor.ui.editor

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.ui.PlayerView
import com.neversoft.editor.engine.CompositionFactory
import com.neversoft.editor.ui.EditorViewModel
import com.neversoft.editor.ui.ExportState
import com.neversoft.editor.ui.theme.Bg
import com.neversoft.editor.ui.theme.Magenta
import com.neversoft.editor.ui.theme.OnDim
import com.neversoft.editor.ui.theme.Stroke
import com.neversoft.editor.ui.theme.Success
import com.neversoft.editor.ui.theme.Surface1
import com.neversoft.editor.ui.theme.Violet
import kotlinx.coroutines.delay

@UnstableApi
@Composable
fun EditorScreen(vm: EditorViewModel) {
    val context = LocalContext.current

    val playerView = remember {
        PlayerView(context).apply {
            useController = false
            setBackgroundColor(android.graphics.Color.BLACK)
        }
    }
    var player by remember { mutableStateOf<CompositionPlayer?>(null) }
    var playheadMs by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableStateOf(0L) }

    val addMore = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> vm.addMore(context, uris) }

    // Rebuild the preview composition after edits settle (debounced so dragging
    // a slider doesn't thrash the player).
    LaunchedEffect(vm.previewVersion) {
        delay(260)
        player?.release()
        isPlaying = false
        playheadMs = 0
        durationMs = vm.project.totalDurationMs
        if (vm.project.isEmpty) { player = null; return@LaunchedEffect }
        val p = CompositionPlayer.Builder(context).build()
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) isPlaying = false
            }
        })
        p.setComposition(CompositionFactory.build(vm.project))
        p.prepare()
        playerView.player = p
        player = p
    }

    // Advance the playhead readout while playing.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            player?.let { playheadMs = it.currentPosition }
            delay(60)
        }
    }

    DisposableEffect(Unit) {
        onDispose { player?.release() }
    }

    fun togglePlay() {
        val p = player ?: return
        if (isPlaying) {
            p.pause(); isPlaying = false
        } else {
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
            p.play(); isPlaying = true
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            TopBar(
                canUndo = vm.canUndo,
                onClose = { vm.reset() },
                onUndo = { vm.undo() },
                onExport = {
                    player?.pause(); isPlaying = false
                    vm.export(context)
                },
            )

            // Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    factory = { playerView },
                    modifier = Modifier.fillMaxSize(),
                )
                // Big central play/pause affordance.
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x66000000))
                        .clickable { togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play / pause",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }

            // Scrubber
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatMs(playheadMs), color = OnDim, fontSize = 12.sp)
                Slider(
                    value = playheadMs.toFloat(),
                    onValueChange = {
                        playheadMs = it.toLong()
                        player?.seekTo(it.toLong())
                    },
                    valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Magenta,
                        activeTrackColor = Magenta,
                        inactiveTrackColor = Stroke,
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(formatMs(durationMs), color = OnDim, fontSize = 12.sp)
            }

            Timeline(
                project = vm.project,
                selectedId = vm.selectedClipId,
                onSelect = { vm.select(it) },
                onAddMore = {
                    addMore.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
            )

            EditorTools(vm = vm, playheadMs = playheadMs)
        }

        // Export overlay
        when (val s = vm.exportState) {
            is ExportState.Running -> ExportOverlay(percent = s.percent, onCancel = { vm.cancelExport() })
            is ExportState.Done -> ExportDone(onDismiss = { vm.dismissExport() })
            is ExportState.Error -> ExportError(s.message, onDismiss = { vm.dismissExport() })
            ExportState.Idle -> {}
        }
    }
}

@Composable
private fun TopBar(
    canUndo: Boolean,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            Icons.Filled.Close,
            contentDescription = "Start over",
            tint = Color.White,
            modifier = Modifier.size(26.dp).clickable(onClick = onClose),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Undo,
                contentDescription = "Undo",
                tint = if (canUndo) Color.White else Stroke,
                modifier = Modifier
                    .size(26.dp)
                    .clickable(enabled = canUndo, onClick = onUndo),
            )
            Spacer(Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Violet, Magenta)))
                    .clickable(onClick = onExport)
                    .padding(horizontal = 22.dp, vertical = 10.dp),
            ) {
                Text("Export", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ExportOverlay(percent: Int, onCancel: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xE60B0B10)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Surface1)
                .padding(32.dp),
        ) {
            CircularProgressIndicator(color = Magenta)
            Spacer(Modifier.height(18.dp))
            Text("Exporting… $percent%", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Rendering your video in full quality.", color = OnDim, fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            Text(
                "Cancel",
                color = OnDim,
                modifier = Modifier.clickable(onClick = onCancel),
            )
        }
    }
}

@Composable
private fun ExportDone(onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xE60B0B10)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Surface1)
                .padding(32.dp),
        ) {
            Text("✓", color = Success, fontSize = 44.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            Text("Saved to your gallery", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Find it in Movies › NeverSoft.", color = OnDim, fontSize = 12.sp)
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Violet, Magenta)))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 28.dp, vertical = 12.dp),
            ) {
                Text("Keep editing", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ExportError(message: String, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xE60B0B10)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Surface1)
                .padding(32.dp)
                .width(280.dp),
        ) {
            Text("Export failed", color = Color(0xFFFF5A5A), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = OnDim, fontSize = 12.sp)
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface1)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 24.dp, vertical = 10.dp),
            ) {
                Text("Close", color = Color.White)
            }
        }
    }
}
