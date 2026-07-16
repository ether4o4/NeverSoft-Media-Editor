package com.neversoft.editor.ui.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.neversoft.editor.ui.EditorViewModel
import com.neversoft.editor.ui.theme.Magenta
import com.neversoft.editor.ui.theme.OnDim
import com.neversoft.editor.ui.theme.Surface1
import com.neversoft.editor.ui.theme.Violet

@UnstableApi
@Composable
fun HomeScreen(vm: EditorViewModel) {
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        vm.importInitial(context, uris)
    }

    fun pick() = picker.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
    )

    val brand = Brush.linearGradient(listOf(Violet, Magenta))

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "NeverSoft",
            fontSize = 44.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
        )
        Text(
            text = "MEDIA EDITOR",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 6.sp,
            color = Magenta,
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = "Cut, caption and score your videos in seconds.\nNo account. No watermark. No learning curve.",
            color = OnDim,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )

        Spacer(Modifier.height(40.dp))

        // The one thing to do on this screen.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(brand)
                .clickable { pick() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Text(
                "Import photos & videos",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "Pick as many as you like — they drop straight onto the timeline.",
            color = OnDim,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(44.dp))

        Feature(
            Icons.Filled.Bolt,
            "Instant import",
            "Files are opened in place — never copied — so a 4-minute 4K clip loads as fast as a photo.",
        )
        Feature(
            Icons.Filled.CloudUpload,
            "Any size, any length",
            "There's no upload and no file-size cap. Everything stays on your phone.",
        )
        Feature(
            Icons.Filled.Lock,
            "Private by design",
            "No sign-in, no cloud. Your footage never leaves the device.",
        )
    }

        if (vm.importing) {
            Box(
                Modifier.fillMaxSize().background(Color(0xE60B0B10)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Magenta)
                    Spacer(Modifier.height(14.dp))
                    Text("Adding your media…", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Violet,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(body, color = OnDim, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}
