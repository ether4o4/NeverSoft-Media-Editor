package com.neversoft.editor.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.media3.common.util.UnstableApi
import com.neversoft.editor.engine.EditorExporter
import com.neversoft.editor.media.MediaUtils
import com.neversoft.editor.model.Clip
import com.neversoft.editor.model.Filter
import com.neversoft.editor.model.MediaType
import com.neversoft.editor.model.Project
import com.neversoft.editor.model.TextClip
import com.neversoft.editor.model.TextPosition
import java.io.File

/** Where an export currently stands, for the UI to reflect. */
sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val percent: Int) : ExportState
    data class Done(val uri: Uri?, val file: File) : ExportState
    data class Error(val message: String) : ExportState
}

@UnstableApi
class EditorViewModel : ViewModel() {

    var project by mutableStateOf(Project()); private set
    var selectedClipId by mutableStateOf<Long?>(null); private set

    /** Bumped on every edit so the preview knows to rebuild its composition. */
    var previewVersion by mutableStateOf(0); private set

    var exportState by mutableStateOf<ExportState>(ExportState.Idle); private set

    private val undoStack = ArrayDeque<Project>()
    val canUndo: Boolean get() = undoStack.isNotEmpty()

    private var exporter: EditorExporter? = null

    val selectedClip: Clip?
        get() = project.clips.firstOrNull { it.id == selectedClipId }

    // ---- import -----------------------------------------------------------

    fun importInitial(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val clips = uris.map { MediaUtils.clipFrom(context, it) }
        val first = MediaUtils.probe(context, uris.first())
        val (w, h) = MediaUtils.canvasFor(first.width, first.height)
        mutate {
            it.copy(clips = clips, outputWidth = w, outputHeight = h)
        }
        selectedClipId = clips.firstOrNull()?.id
    }

    fun addMore(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val clips = uris.map { MediaUtils.clipFrom(context, it) }
        mutate { it.copy(clips = it.clips + clips) }
    }

    // ---- selection & arrangement -----------------------------------------

    fun select(id: Long?) { selectedClipId = id }

    fun delete(id: Long) {
        mutate { it.copy(clips = it.clips.filterNot { c -> c.id == id }) }
        if (selectedClipId == id) selectedClipId = project.clips.firstOrNull()?.id
    }

    fun duplicate(id: Long) {
        mutate { p ->
            val idx = p.clips.indexOfFirst { it.id == id }
            if (idx < 0) return@mutate p
            val copy = p.clips[idx].copy(id = MediaUtils.nextId())
            p.copy(clips = p.clips.toMutableList().apply { add(idx + 1, copy) })
        }
    }

    fun moveLeft(id: Long) = swap(id, -1)
    fun moveRight(id: Long) = swap(id, +1)

    private fun swap(id: Long, dir: Int) {
        mutate { p ->
            val i = p.clips.indexOfFirst { it.id == id }
            val j = i + dir
            if (i < 0 || j !in p.clips.indices) return@mutate p
            val list = p.clips.toMutableList()
            val tmp = list[i]; list[i] = list[j]; list[j] = tmp
            p.copy(clips = list)
        }
    }

    // ---- per-clip edits ---------------------------------------------------

    fun setTrim(id: Long, startMs: Long, endMs: Long) = editClip(id) {
        it.copy(
            trimStartMs = startMs.coerceIn(0, it.sourceDurationMs),
            trimEndMs = endMs.coerceIn(startMs + 200, it.sourceDurationMs),
        )
    }

    fun setImageDuration(id: Long, ms: Long) = editClip(id) {
        it.copy(imageDurationMs = ms.coerceIn(500, 30_000))
    }

    fun setSpeed(id: Long, speed: Float) = editClip(id) {
        it.copy(speed = speed.coerceIn(0.25f, 4f))
    }

    fun setFilter(id: Long, filter: Filter) = editClip(id) { it.copy(filter = filter) }

    fun toggleMute(id: Long) = editClip(id) { it.copy(muted = !it.muted) }

    fun addText(id: Long, text: String, position: TextPosition) = editClip(id) {
        if (text.isBlank()) return@editClip it
        it.copy(texts = it.texts + TextClip(MediaUtils.nextId(), text.trim(), position))
    }

    fun removeText(clipId: Long, textId: Long) = editClip(clipId) {
        it.copy(texts = it.texts.filterNot { t -> t.id == textId })
    }

    // ---- music ------------------------------------------------------------

    fun setMusic(context: Context, uri: Uri) {
        val title = MediaUtils.displayName(context, uri)
        mutate { it.copy(music = com.neversoft.editor.model.AudioTrack(uri, title)) }
    }

    fun clearMusic() = mutate { it.copy(music = null) }

    fun setMusicVolume(volume: Float) = mutate {
        val m = it.music ?: return@mutate it
        it.copy(music = m.copy(volume = volume.coerceIn(0f, 1f)))
    }

    // ---- split ------------------------------------------------------------

    /** Split the selected clip at the global timeline position (ms). */
    fun splitAtPlayhead(globalMs: Long) {
        val id = selectedClipId ?: project.clips.firstOrNull()?.id ?: return
        mutate { p ->
            var acc = 0L
            val out = mutableListOf<Clip>()
            var newSelection: Long? = selectedClipId
            for (clip in p.clips) {
                val dur = clip.timelineDurationMs
                val within = globalMs - acc
                if (clip.id == id && within in 1 until dur) {
                    val (a, b) = clip.splitAt(within)
                    out.add(a); out.add(b)
                    newSelection = b.id
                } else {
                    out.add(clip)
                }
                acc += dur
            }
            selectedClipId = newSelection
            p.copy(clips = out)
        }
    }

    private fun Clip.splitAt(withinTimelineMs: Long): Pair<Clip, Clip> {
        return when (type) {
            MediaType.IMAGE -> {
                val a = copy(imageDurationMs = withinTimelineMs)
                val b = copy(
                    id = MediaUtils.nextId(),
                    imageDurationMs = imageDurationMs - withinTimelineMs,
                    texts = emptyList(),
                )
                a to b
            }
            MediaType.VIDEO -> {
                val sourceSplit = (trimStartMs + withinTimelineMs * speed).toLong()
                    .coerceIn(trimStartMs + 1, trimEndMs - 1)
                val a = copy(trimEndMs = sourceSplit)
                val b = copy(
                    id = MediaUtils.nextId(),
                    trimStartMs = sourceSplit,
                    texts = emptyList(),
                )
                a to b
            }
        }
    }

    // ---- undo -------------------------------------------------------------

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        project = prev
        previewVersion++
        if (project.clips.none { it.id == selectedClipId }) {
            selectedClipId = project.clips.firstOrNull()?.id
        }
    }

    // ---- export -----------------------------------------------------------

    fun export(context: Context) {
        if (project.isEmpty) return
        if (exportState is ExportState.Running) return
        exportState = ExportState.Running(0)
        val ex = EditorExporter(context.applicationContext).also { exporter = it }
        ex.start(project, object : EditorExporter.Callbacks {
            override fun onProgress(percent: Int) {
                exportState = ExportState.Running(percent)
            }
            override fun onDone(savedUri: Uri?, file: File) {
                exportState = ExportState.Done(savedUri, file)
            }
            override fun onError(message: String) {
                exportState = ExportState.Error(message)
            }
        })
    }

    fun dismissExport() {
        exportState = ExportState.Idle
    }

    /** Clear everything and return to the start screen. */
    fun reset() {
        exporter?.cancel()
        undoStack.clear()
        project = Project()
        selectedClipId = null
        exportState = ExportState.Idle
        previewVersion++
    }

    fun cancelExport() {
        exporter?.cancel()
        exportState = ExportState.Idle
    }

    // ---- helpers ----------------------------------------------------------

    private fun editClip(id: Long, block: (Clip) -> Clip) {
        mutate { p ->
            p.copy(clips = p.clips.map { if (it.id == id) block(it) else it })
        }
    }

    private fun mutate(block: (Project) -> Project) {
        undoStack.addLast(project)
        if (undoStack.size > 60) undoStack.removeFirst()
        project = block(project)
        previewVersion++
    }
}
