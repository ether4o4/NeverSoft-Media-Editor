package com.neversoft.editor.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.neversoft.editor.model.Clip
import com.neversoft.editor.model.MediaType

/**
 * Everything the editor needs to know about a picked item — without copying a
 * single byte. We hand the content Uri straight to Media3, so importing a 4 GB
 * video is as instant as importing a thumbnail: only metadata is read here.
 */
object MediaUtils {

    private var idSeed = 1L
    fun nextId(): Long = idSeed++

    data class Probe(
        val type: MediaType,
        val durationMs: Long,
        val width: Int,
        val height: Int,
    )

    /** Cheap metadata probe. Falls back to sane defaults if a field is missing. */
    fun probe(context: Context, uri: Uri): Probe {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val isImage = mime.startsWith("image")
        if (isImage) {
            // Images have no duration; dimensions come from the retriever too.
            val (w, h) = imageSize(context, uri)
            return Probe(MediaType.IMAGE, 0L, w, h)
        }
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            Probe(MediaType.VIDEO, dur, w, h)
        } catch (e: Exception) {
            Probe(MediaType.VIDEO, 0L, 0, 0)
        } finally {
            runCatching { r.release() }
        }
    }

    private fun imageSize(context: Context, uri: Uri): Pair<Int, Int> {
        return try {
            val opts = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, opts)
            }
            (opts.outWidth.takeIf { it > 0 } ?: 1080) to
                (opts.outHeight.takeIf { it > 0 } ?: 1080)
        } catch (e: Exception) {
            1080 to 1080
        }
    }

    /** Build a fresh Clip from a picked Uri, reading only its metadata. */
    fun clipFrom(context: Context, uri: Uri): Clip {
        val p = probe(context, uri)
        return Clip(
            id = nextId(),
            uri = uri,
            type = p.type,
            sourceDurationMs = p.durationMs,
            sourceWidth = p.width,
            sourceHeight = p.height,
            trimStartMs = 0,
            trimEndMs = p.durationMs,
        )
    }

    /** Display name for a Uri (used to label a chosen music track). */
    fun displayName(context: Context, uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull() ?: "Audio"
    }

    /** Pick a good output canvas from the first video/photo's orientation. */
    fun canvasFor(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 720 to 1280
        return if (width >= height) 1280 to 720 else 720 to 1280
    }
}
