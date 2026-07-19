package com.neversoft.editor.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.neversoft.editor.model.Project
import java.io.File

/**
 * Renders a [Project] to an MP4 with Media3 [Transformer], then publishes it to
 * the device gallery. Must be created and driven on the main thread (Transformer
 * posts callbacks to the Looper of the thread that started it).
 */
@UnstableApi
class EditorExporter(private val context: Context) {

    interface Callbacks {
        fun onProgress(percent: Int)
        fun onDone(savedUri: Uri?, file: File)
        fun onError(message: String)
    }

    private var transformer: Transformer? = null
    private val main = Handler(Looper.getMainLooper())
    private var polling: Runnable? = null

    fun start(project: Project, callbacks: Callbacks) {
        // Any synchronous failure here (composition build, decoder/encoder init)
        // must surface as an error, never crash the app and lose the user's edits.
        try {
            startInternal(project, callbacks)
        } catch (t: Throwable) {
            stopPolling()
            runCatching { transformer?.cancel() }
            transformer = null
            callbacks.onError(t.message ?: "Export couldn't start on this device")
        }
    }

    private fun startInternal(project: Project, callbacks: Callbacks) {
        val composition: Composition = CompositionFactory.build(project)
        val output = File(context.cacheDir, "nsme_export_${System.currentTimeMillis()}.mp4")

        val t = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    stopPolling()
                    val uri = publishToGallery(output)
                    callbacks.onProgress(100)
                    callbacks.onDone(uri, output)
                }

                override fun onError(
                    composition: Composition,
                    result: ExportResult,
                    exception: ExportException,
                ) {
                    stopPolling()
                    callbacks.onError(exception.message ?: "Export failed")
                }
            })
            .build()
        transformer = t
        t.start(composition, output.absolutePath)
        beginPolling(callbacks)
    }

    fun cancel() {
        stopPolling()
        runCatching { transformer?.cancel() }
        transformer = null
    }

    private fun beginPolling(callbacks: Callbacks) {
        val holder = ProgressHolder()
        polling = object : Runnable {
            override fun run() {
                val t = transformer ?: return
                val state = t.getProgress(holder)
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    callbacks.onProgress(holder.progress.coerceIn(0, 99))
                }
                main.postDelayed(this, 150)
            }
        }
        main.post(polling!!)
    }

    private fun stopPolling() {
        polling?.let { main.removeCallbacks(it) }
        polling = null
    }

    /**
     * Copy the rendered file into the shared gallery. On API 29+ this uses
     * scoped MediaStore (no permission). On 26–28 we fall back to the app's
     * external Movies dir, which also needs no runtime permission.
     */
    private fun publishToGallery(source: File): Uri? {
        val name = "NeverSoft_${System.currentTimeMillis()}.mp4"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/NeverSoft")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val collection =
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = resolver.insert(collection, values) ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val dir = File(
                    context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES),
                    "NeverSoft"
                ).apply { mkdirs() }
                val dest = File(dir, name)
                source.inputStream().use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
                Uri.fromFile(dest)
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        /** A best-effort estimate so the UI can show a size hint before export. */
        fun estimateMb(project: Project): Int {
            // ~8 Mbps H.264 + ~128 kbps audio.
            val seconds = project.totalDurationMs / 1000.0
            return ((seconds * (8_000_000 + 128_000)) / 8 / 1_000_000).toInt().coerceAtLeast(1)
        }
    }
}
