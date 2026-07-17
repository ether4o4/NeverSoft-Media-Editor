package com.neversoft.editor.ui.music

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.neversoft.editor.ui.MusicExportState
import com.neversoft.editor.ui.MusicViewModel
import com.neversoft.editor.ui.theme.Bg
import com.neversoft.editor.ui.theme.Magenta
import com.neversoft.editor.ui.theme.OnDim
import com.neversoft.editor.ui.theme.Stroke
import com.neversoft.editor.ui.theme.Success
import com.neversoft.editor.ui.theme.Surface1
import com.neversoft.editor.ui.theme.Surface2
import com.neversoft.editor.ui.theme.Violet
import kotlinx.coroutines.delay
import kotlin.math.min

@UnstableApi
@Composable
fun MusicStudioScreen(vm: MusicViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val song = vm.song

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { vm.pick(context, it) } }

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }

    // Preview the cropped audio; rebuild when the crop/speed changes.
    LaunchedEffect(song?.uri, song?.trimStartMs, song?.trimEndMs, song?.speed, song?.volume) {
        if (song == null) return@LaunchedEffect
        delay(160)
        val p = player ?: ExoPlayer.Builder(context).build().also {
            it.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) playing = false
                }
            })
            player = it
        }
        p.setMediaItem(
            MediaItem.Builder()
                .setUri(song.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(song.trimStartMs)
                        .setEndPositionMs(song.trimEndMs)
                        .build()
                )
                .build()
        )
        p.playbackParameters = PlaybackParameters(song.speed)
        p.volume = min(song.volume, 1f)
        p.prepare()
        playing = false
    }

    DisposableEffect(Unit) { onDispose { player?.release() } }

    fun toggle() {
        val p = player ?: return
        if (playing) {
            p.pause(); playing = false
        } else {
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
            p.play(); playing = true
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp).clickable {
                        player?.pause(); playing = false; onBack()
                    },
                )
                Text("Music Studio", color = Color.White, fontWeight = FontWeight.Bold)
                if (song != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(Violet, Magenta)))
                            .clickable { player?.pause(); playing = false; vm.export(context) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) { Text("Save", color = Color.White, fontWeight = FontWeight.Bold) }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
            }

            if (song == null) {
                EmptyState { picker.launch("audio/*") }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    // Play + title
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Brush.linearGradient(listOf(Violet, Magenta)))
                                .clickable { toggle() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play / pause",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                song.title.ifBlank { "Untitled" },
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Saves ${fmt(song.outDurationMs)} of ${fmt(song.sourceDurationMs)}",
                                color = OnDim,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Label("Crop")
                    val range = song.trimStartMs.toFloat()..song.trimEndMs.toFloat()
                    RangeSlider(
                        value = range,
                        onValueChange = { vm.setTrim(it.start.toLong(), it.endInclusive.toLong()) },
                        valueRange = 0f..song.sourceDurationMs.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Violet,
                            activeTrackColor = Violet,
                            inactiveTrackColor = Stroke,
                        ),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Start ${fmt(song.trimStartMs)}", color = OnDim, fontSize = 12.sp)
                        Text("End ${fmt(song.trimEndMs)}", color = OnDim, fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(16.dp))
                    Label("Volume ${(song.volume * 100).toInt()}%")
                    Slider(
                        value = song.volume,
                        onValueChange = { vm.setVolume(it) },
                        valueRange = 0f..2f,
                        colors = accent(),
                    )

                    Spacer(Modifier.height(8.dp))
                    Label("Speed")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                            FilterChip(
                                selected = song.speed == s,
                                onClick = { vm.setSpeed(s) },
                                label = { Text(chip(s)) },
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Label("Tags")
                    Field("Title", song.title) { vm.setTitle(it) }
                    Spacer(Modifier.height(8.dp))
                    Field("Artist", song.artist) { vm.setArtist(it) }
                    Spacer(Modifier.height(8.dp))
                    Field("Album", song.album) { vm.setAlbum(it) }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Saving writes a new .m4a into Music › NeverSoft with these tags.",
                        color = OnDim,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }
        }

        when (val s = vm.exportState) {
            is MusicExportState.Running -> Overlay { Progress(s.percent) }
            is MusicExportState.Done -> Overlay { DoneCard { vm.dismissExport() } }
            is MusicExportState.Error -> Overlay { ErrorCard(s.message) { vm.dismissExport() } }
            MusicExportState.Idle -> {}
        }

        if (vm.importing) {
            Overlay {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Magenta)
                    Spacer(Modifier.height(12.dp))
                    Text("Loading track…", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onPick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Violet, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("Crop, tweak & re-tag a song", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Trim the length, set the volume and speed, and save it with a new title, artist and album.",
            color = OnDim,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Violet, Magenta)))
                .clickable(onClick = onPick)
                .padding(horizontal = 26.dp, vertical = 14.dp),
        ) { Text("Choose a song", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun accent() = SliderDefaults.colors(
    thumbColor = Violet,
    activeTrackColor = Violet,
    inactiveTrackColor = Stroke,
)

@Composable
private fun Overlay(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xE60B0B10)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Surface1).padding(30.dp),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
private fun Progress(percent: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Magenta)
        Spacer(Modifier.height(16.dp))
        Text("Saving… $percent%", color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DoneCard(onDismiss: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("✓", color = Success, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text("Saved to your music", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Music › NeverSoft", color = OnDim, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Violet, Magenta)))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 24.dp, vertical = 10.dp),
        ) { Text("Done", color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(260.dp)) {
        Text("Couldn't save", color = Color(0xFFFF5A5A), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = OnDim, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Surface2)
                .clickable(onClick = onDismiss)
                .padding(horizontal = 22.dp, vertical = 9.dp),
        ) { Text("Close", color = Color.White) }
    }
}

private fun fmt(ms: Long): String {
    val total = (ms / 1000.0).let { Math.round(it).toInt() }
    return "%d:%02d".format(total / 60, total % 60)
}

private fun chip(v: Float): String =
    if (v == v.toLong().toFloat()) "${v.toLong()}x" else "${v}x"
