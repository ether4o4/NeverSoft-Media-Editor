package com.neversoft.editor.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.neversoft.editor.model.Clip
import com.neversoft.editor.model.Filter
import com.neversoft.editor.model.MediaType
import com.neversoft.editor.model.TextPosition
import com.neversoft.editor.ui.EditorViewModel
import com.neversoft.editor.ui.theme.Magenta
import com.neversoft.editor.ui.theme.OnDim
import com.neversoft.editor.ui.theme.Stroke
import com.neversoft.editor.ui.theme.Surface1
import com.neversoft.editor.ui.theme.Surface2
import com.neversoft.editor.ui.theme.Violet

private enum class Tool { TRIM, SPEED, FILTER, TEXT, MUSIC, AUTOCLIP, ASPECT }

@UnstableApi
@Composable
fun EditorTools(vm: EditorViewModel, playheadMs: Long) {
    val context = LocalContext.current
    var active by remember { mutableStateOf<Tool?>(null) }
    val clip = vm.selectedClip

    val musicPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { vm.setMusic(context, it) } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface1),
    ) {
        // The open panel (if any) sits directly above the toolbar.
        if (active != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                when (active) {
                    Tool.TRIM -> TrimPanel(vm, clip)
                    Tool.SPEED -> SpeedPanel(vm, clip)
                    Tool.FILTER -> FilterPanel(vm, clip)
                    Tool.TEXT -> TextPanel(vm, clip)
                    Tool.MUSIC -> MusicPanel(vm) { musicPicker.launch("audio/*") }
                    Tool.AUTOCLIP -> AutoClipPanel(vm, clip)
                    Tool.ASPECT -> AspectPanel(vm)
                    null -> {}
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val id = clip?.id
            ToolButton(Icons.Filled.Crop, "Trim", active == Tool.TRIM, id != null) {
                active = if (active == Tool.TRIM) null else Tool.TRIM
            }
            ToolButton(Icons.Filled.ContentCut, "Split", false, id != null) {
                active = null
                vm.splitAtPlayhead(playheadMs)
            }
            ToolButton(Icons.Filled.Speed, "Speed", active == Tool.SPEED, id != null) {
                active = if (active == Tool.SPEED) null else Tool.SPEED
            }
            ToolButton(Icons.Filled.Tune, "Filter", active == Tool.FILTER, id != null) {
                active = if (active == Tool.FILTER) null else Tool.FILTER
            }
            ToolButton(Icons.Filled.AutoFixHigh, "Enhance", clip?.autoEnhance == true, id != null) {
                id?.let { vm.toggleEnhance(it) }
            }
            ToolButton(Icons.Filled.Bolt, "Auto-clip", active == Tool.AUTOCLIP, id != null) {
                active = if (active == Tool.AUTOCLIP) null else Tool.AUTOCLIP
            }
            ToolButton(Icons.Filled.Rotate90DegreesCw, "Rotate", false, id != null) {
                id?.let { vm.rotate(it) }
            }
            ToolButton(Icons.Filled.AspectRatio, "Aspect", active == Tool.ASPECT, true) {
                active = if (active == Tool.ASPECT) null else Tool.ASPECT
            }
            ToolButton(Icons.Filled.TextFields, "Text", active == Tool.TEXT, id != null) {
                active = if (active == Tool.TEXT) null else Tool.TEXT
            }
            ToolButton(Icons.Filled.MusicNote, "Music", active == Tool.MUSIC, true) {
                active = if (active == Tool.MUSIC) null else Tool.MUSIC
            }
            val muteIcon = if (clip?.muted == true) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp
            ToolButton(muteIcon, "Mute", false, id != null && clip.type == MediaType.VIDEO) {
                id?.let { vm.toggleMute(it) }
            }
            ToolButton(Icons.Filled.ContentCopy, "Copy", false, id != null) {
                id?.let { vm.duplicate(it) }
            }
            ToolButton(Icons.Filled.ChevronLeft, "Move ◀", false, id != null) {
                id?.let { vm.moveLeft(it) }
            }
            ToolButton(Icons.Filled.ChevronRight, "Move ▶", false, id != null) {
                id?.let { vm.moveRight(it) }
            }
            ToolButton(Icons.Filled.Delete, "Delete", false, id != null) {
                active = null
                id?.let { vm.delete(it) }
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    activeState: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> Stroke
        activeState -> Magenta
        else -> Color.White
    }
    Column(
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ---- panels ---------------------------------------------------------------

@Composable
private fun PanelTitle(text: String) {
    Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun TrimPanel(vm: EditorViewModel, clip: Clip?) {
    if (clip == null) return
    Column {
        if (clip.type == MediaType.IMAGE) {
            PanelTitle("Photo duration — ${formatSeconds(clip.imageDurationMs)}")
            Slider(
                value = clip.imageDurationMs.toFloat(),
                onValueChange = { vm.setImageDuration(clip.id, it.toLong()) },
                valueRange = 500f..15000f,
                colors = sliderColors(),
            )
            Text("How long this photo stays on screen.", color = OnDim, fontSize = 12.sp)
        } else {
            PanelTitle("Trim — keep ${formatSeconds(clip.trimEndMs - clip.trimStartMs)} of ${formatSeconds(clip.sourceDurationMs)}")
            val range = clip.trimStartMs.toFloat()..clip.trimEndMs.toFloat()
            RangeSlider(
                value = range,
                onValueChange = {
                    vm.setTrim(clip.id, it.start.toLong(), it.endInclusive.toLong())
                },
                valueRange = 0f..clip.sourceDurationMs.toFloat().coerceAtLeast(1f),
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = Violet,
                    activeTrackColor = Violet,
                    inactiveTrackColor = Stroke,
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Start ${formatSeconds(clip.trimStartMs)}", color = OnDim, fontSize = 12.sp)
                Text("End ${formatSeconds(clip.trimEndMs)}", color = OnDim, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SpeedPanel(vm: EditorViewModel, clip: Clip?) {
    if (clip == null) return
    if (clip.type == MediaType.IMAGE) {
        // Speed is meaningless for a still — offer duration instead.
        TrimPanel(vm, clip)
        return
    }
    Column {
        PanelTitle("Speed — ${trimZerosF(clip.speed)}x")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.5f, 1f, 1.5f, 2f, 3f).forEach { s ->
                FilterChip(
                    selected = clip.speed == s,
                    onClick = { vm.setSpeed(clip.id, s) },
                    label = { Text("${trimZerosF(s)}x") },
                )
            }
        }
        Slider(
            value = clip.speed,
            onValueChange = { vm.setSpeed(clip.id, (Math.round(it * 20f) / 20f)) },
            valueRange = 0.25f..4f,
            colors = sliderColors(),
        )
        Text("Slow motion below 1x, fast-forward above.", color = OnDim, fontSize = 12.sp)
    }
}

@Composable
private fun FilterPanel(vm: EditorViewModel, clip: Clip?) {
    if (clip == null) return
    Column {
        PanelTitle("Filter")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Filter.values().forEach { f ->
                FilterChip(
                    selected = clip.filter == f,
                    onClick = { vm.setFilter(clip.id, f) },
                    label = { Text(f.label) },
                )
            }
        }
    }
}

@Composable
private fun TextPanel(vm: EditorViewModel, clip: Clip?) {
    if (clip == null) return
    var draft by remember { mutableStateOf("") }
    var pos by remember { mutableStateOf(TextPosition.BOTTOM) }
    Column {
        PanelTitle("Caption")
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text("Type a caption…") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextPosition.values().forEach { p ->
                FilterChip(
                    selected = pos == p,
                    onClick = { pos = p },
                    label = { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Magenta)
                    .clickable {
                        vm.addText(clip.id, draft, pos)
                        draft = ""
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text("Add", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        if (clip.texts.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            clip.texts.forEach { t ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface2)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("“${t.text}”", color = Color.White, fontSize = 13.sp)
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove caption",
                        tint = OnDim,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { vm.removeText(clip.id, t.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicPanel(vm: EditorViewModel, onPick: () -> Unit) {
    val music = vm.project.music
    Column {
        PanelTitle("Background music")
        if (music == null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Violet)
                    .clickable(onClick = onPick)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text("Choose a track", color = Color.White, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(music.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("Volume ${(music.volume * 100).toInt()}%", color = OnDim, fontSize = 12.sp)
            Slider(
                value = music.volume,
                onValueChange = { vm.setMusicVolume(it) },
                valueRange = 0f..1f,
                colors = sliderColors(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface2)
                        .clickable(onClick = onPick)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) { Text("Replace", color = Color.White) }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface2)
                        .clickable { vm.clearMusic() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) { Text("Remove", color = Magenta) }
            }
        }
    }
}

@Composable
private fun AutoClipPanel(vm: EditorViewModel, clip: Clip?) {
    if (clip == null) return
    Column {
        PanelTitle("Auto-clip — keep the best bit")
        if (clip.type != MediaType.VIDEO) {
            Text("Auto-clip works on video clips.", color = OnDim, fontSize = 12.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 15, 30, 60).forEach { sec ->
                    FilterChip(
                        selected = false,
                        onClick = { vm.autoClip(clip.id, sec * 1000L) },
                        label = { Text("${sec}s") },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Trims a long clip to a centred window of that length — the middle is usually the good part.",
                color = OnDim,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun AspectPanel(vm: EditorViewModel) {
    val w = vm.project.outputWidth
    val h = vm.project.outputHeight
    Column {
        PanelTitle("Aspect ratio")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = w == 720 && h == 1280,
                onClick = { vm.setAspect(720, 1280) },
                label = { Text("9:16") },
            )
            FilterChip(
                selected = w == h,
                onClick = { vm.setAspect(1080, 1080) },
                label = { Text("1:1") },
            )
            FilterChip(
                selected = w == 1280 && h == 720,
                onClick = { vm.setAspect(1280, 720) },
                label = { Text("16:9") },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("Clips fit inside the frame (letterboxed), never stretched.", color = OnDim, fontSize = 12.sp)
    }
}

@Composable
private fun sliderColors() = androidx.compose.material3.SliderDefaults.colors(
    thumbColor = Violet,
    activeTrackColor = Violet,
    inactiveTrackColor = Stroke,
)

private fun formatSeconds(ms: Long): String {
    val s = ms / 1000.0
    return if (s >= 10) "%.0fs".format(s) else "%.1fs".format(s)
}

private fun trimZerosF(v: Float): String =
    if (v == v.toLong().toFloat()) v.toLong().toString() else v.toString()
