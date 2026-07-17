package com.neversoft.editor.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.neversoft.editor.engine.MusicExporter
import com.neversoft.editor.engine.StemModelManager
import com.neversoft.editor.engine.StemSeparator
import com.neversoft.editor.media.MediaUtils
import com.neversoft.editor.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface MusicExportState {
    data object Idle : MusicExportState
    data class Running(val percent: Int) : MusicExportState
    data class Done(val uri: Uri?) : MusicExportState
    data class Error(val message: String) : MusicExportState
}

sealed interface StemState {
    data object Idle : StemState
    data class Downloading(val percent: Int) : StemState
    data class Working(val percent: Int, val label: String) : StemState
    data class Done(val vocals: Uri?, val instrumental: Uri?) : StemState
    data class Error(val message: String) : StemState
}

@UnstableApi
class MusicViewModel : ViewModel() {

    var song by mutableStateOf<Song?>(null); private set
    var importing by mutableStateOf(false); private set
    var exportState by mutableStateOf<MusicExportState>(MusicExportState.Idle); private set
    var stemState by mutableStateOf<StemState>(StemState.Idle); private set

    private var exporter: MusicExporter? = null

    fun pick(context: Context, uri: Uri) {
        importing = true
        val app = context.applicationContext
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { MediaUtils.probeSong(app, uri) }
            song = Song(
                uri = uri,
                title = info.title,
                artist = info.artist,
                album = info.album,
                sourceDurationMs = info.durationMs,
                trimEndMs = info.durationMs,
            )
            importing = false
        }
    }

    fun setTrim(startMs: Long, endMs: Long) = update {
        it.copy(
            trimStartMs = startMs.coerceIn(0, it.sourceDurationMs),
            trimEndMs = endMs.coerceIn(startMs + 500, it.sourceDurationMs),
        )
    }

    fun setVolume(v: Float) = update { it.copy(volume = v.coerceIn(0f, 2f)) }
    fun setSpeed(s: Float) = update { it.copy(speed = s.coerceIn(0.5f, 2f)) }
    fun setTitle(t: String) = update { it.copy(title = t) }
    fun setArtist(a: String) = update { it.copy(artist = a) }
    fun setAlbum(al: String) = update { it.copy(album = al) }

    fun export(context: Context) {
        val s = song ?: return
        if (exportState is MusicExportState.Running) return
        exportState = MusicExportState.Running(0)
        val ex = MusicExporter(context.applicationContext).also { exporter = it }
        ex.start(s, object : MusicExporter.Callbacks {
            override fun onProgress(percent: Int) { exportState = MusicExportState.Running(percent) }
            override fun onDone(savedUri: Uri?) { exportState = MusicExportState.Done(savedUri) }
            override fun onError(message: String) { exportState = MusicExportState.Error(message) }
        })
    }

    fun dismissExport() { exportState = MusicExportState.Idle }

    /** Split the current song into Vocals + Instrumental, fully on-device. */
    fun separateStems(context: Context) {
        val s = song ?: return
        if (stemState is StemState.Downloading || stemState is StemState.Working) return
        val app = context.applicationContext
        viewModelScope.launch {
            try {
                if (!StemModelManager.isReady(app)) {
                    stemState = StemState.Downloading(0)
                    val ok = withContext(Dispatchers.IO) {
                        StemModelManager.download(app) { pct -> stemState = StemState.Downloading(pct) }
                    }
                    if (!ok) {
                        stemState = StemState.Error("Model download failed — check your connection.")
                        return@launch
                    }
                }
                stemState = StemState.Working(0, "Starting")
                val out = withContext(Dispatchers.Default) {
                    StemSeparator.separate(
                        context = app,
                        uri = s.uri,
                        baseTitle = s.title.ifBlank { "Track" },
                        modelPath = StemModelManager.modelFile(app).absolutePath,
                    ) { pct, label -> stemState = StemState.Working(pct, label) }
                }
                stemState = StemState.Done(out.vocals, out.instrumental)
            } catch (e: Exception) {
                stemState = StemState.Error(e.message ?: "Separation failed")
            }
        }
    }

    fun dismissStems() { stemState = StemState.Idle }

    fun reset() {
        exporter?.cancel()
        song = null
        exportState = MusicExportState.Idle
    }

    private fun update(block: (Song) -> Song) {
        song = song?.let(block)
    }
}
