package com.neversoft.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.neversoft.editor.model.Clip
import com.neversoft.editor.model.MediaType
import com.neversoft.editor.model.Project
import com.neversoft.editor.ui.theme.Magenta
import com.neversoft.editor.ui.theme.OnDim
import com.neversoft.editor.ui.theme.Stroke
import com.neversoft.editor.ui.theme.Surface2
import com.neversoft.editor.ui.theme.Violet

/**
 * The horizontal filmstrip. Every clip is a real thumbnail (a decoded video
 * frame or the photo itself), sized by its duration. Tap to select; tap the
 * trailing "+" tile to add more media.
 */
@Composable
fun Timeline(
    project: Project,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onAddMore: () -> Unit,
) {
    val scroll = rememberScrollState()
    Column {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Timeline",
                color = OnDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                formatMs(project.totalDurationMs),
                color = OnDim,
                fontSize = 12.sp,
            )
        }
        Row(
            modifier = Modifier
                .height(96.dp)
                .horizontalScroll(scroll)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            project.clips.forEach { clip ->
                ClipTile(
                    clip = clip,
                    selected = clip.id == selectedId,
                    onClick = { onSelect(clip.id) },
                )
                Spacer(Modifier.width(6.dp))
            }
            AddTile(onAddMore)
        }
    }
}

@Composable
private fun ClipTile(clip: Clip, selected: Boolean, onClick: () -> Unit) {
    val w = (clip.timelineDurationMs / 1000f * 44f).dp
        .coerceIn(64.dp, 190.dp)
    Box(
        modifier = Modifier
            .width(w)
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) Violet else Stroke,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = clip.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp)),
        )

        // Little status badges so the strip reads at a glance.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (clip.type == MediaType.VIDEO && clip.muted) {
                Badge { Icon(Icons.Filled.VolumeOff, null, tint = Color.White, modifier = Modifier.size(11.dp)) }
            }
            if (clip.texts.isNotEmpty()) {
                Badge { Icon(Icons.Filled.TextFields, null, tint = Color.White, modifier = Modifier.size(11.dp)) }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xAA000000))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text(
                text = speedLabel(clip) + formatMs(clip.timelineDurationMs),
                color = Color.White,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun Badge(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xAA000000)),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun AddTile(onAddMore: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp, 80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .border(1.dp, Magenta, RoundedCornerShape(12.dp))
            .clickable(onClick = onAddMore),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Add, contentDescription = "Add media", tint = Magenta)
            Text("Add", color = Magenta, fontSize = 11.sp)
        }
    }
}

private fun speedLabel(clip: Clip): String =
    if (clip.type == MediaType.VIDEO && clip.speed != 1f) "${trimZeros(clip.speed)}x · " else ""

private fun trimZeros(v: Float): String =
    if (v == v.toLong().toFloat()) v.toLong().toString() else v.toString()

fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000.0).let { Math.round(it).toInt() }
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
