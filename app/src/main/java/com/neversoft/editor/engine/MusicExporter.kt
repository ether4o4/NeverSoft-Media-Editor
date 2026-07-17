package com.neversoft.editor.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.neversoft.editor.model.Song
import java.io.File

/**
 * Crops a [Song], applies volume/speed, and saves a fresh .m4a into the device
 * music library with new title/artist/album tags. Drive on the main thread.
 */
@UnstableApi
class MusicExporter(private val context: Context) {

    interface Callbacks {
        fun onProgress(percent: Int)
        fun onDone(savedUri: Uri?)
        fun onError(message: String)
    }

    private var transformer: Transformer? = null
    private val main = Handler(Looper.getMainLooper())
    private var polling: Runnable? = null

    fun start(song: Song, callbacks: Callbacks) {
        val mediaItem = MediaItem.Builder()
            .setUri(song.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(song.trimStartMs)
                    .setEndPositionMs(song.trimEndMs)
                    .build()
            )
            .build()

        val item = EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .setEffects(Effects(audioProcessors(song), emptyList()))
            .build()

        val composition = Composition.Builder(
            listOf(EditedMediaItemSequence.Builder(item).build())
        ).build()

        val output = File(context.cacheDir, "nsme_song_${System.currentTimeMillis()}.m4a")

        val t = Transformer.Builder(context)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    stopPolling()
                    val uri = publishToMusic(output, song)
                    callbacks.onProgress(100)
                    callbacks.onDone(uri)
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

    private fun audioProcessors(song: Song): List<AudioProcessor> {
        val list = mutableListOf<AudioProcessor>()
        if (song.speed != 1f) list.add(SonicAudioProcessor().apply { setSpeed(song.speed) })
        if (song.volume != 1f) {
            list.add(ChannelMixingAudioProcessor().apply {
                putChannelMixingMatrix(ChannelMixingMatrix.create(1, 1).scaleBy(song.volume))
                putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2).scaleBy(song.volume))
            })
        }
        return list
    }

    private fun beginPolling(callbacks: Callbacks) {
        val holder = ProgressHolder()
        polling = object : Runnable {
            override fun run() {
                val t = transformer ?: return
                if (t.getProgress(holder) != Transformer.PROGRESS_STATE_NOT_STARTED) {
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

    /** Save with the new tags. On API 29+ the tags land in the media library. */
    private fun publishToMusic(source: File, song: Song): Uri? {
        val safe = song.title.ifBlank { "NeverSoft_${System.currentTimeMillis()}" }
            .replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "NeverSoft" }
        val name = "$safe.m4a"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/NeverSoft")
                    put(MediaStore.Audio.Media.TITLE, song.title)
                    if (song.artist.isNotBlank()) put(MediaStore.Audio.Media.ARTIST, song.artist)
                    if (song.album.isNotBlank()) put(MediaStore.Audio.Media.ALBUM, song.album)
                    put(MediaStore.Audio.Media.IS_MUSIC, 1)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val collection =
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = resolver.insert(collection, values) ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val dir = File(
                    context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC),
                    "NeverSoft"
                ).apply { mkdirs() }
                val dest = File(dir, name)
                source.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
                Uri.fromFile(dest)
            }
        } catch (e: Exception) {
            null
        }
    }
}
