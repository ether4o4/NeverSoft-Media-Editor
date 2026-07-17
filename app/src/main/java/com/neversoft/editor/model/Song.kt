package com.neversoft.editor.model

import android.net.Uri

/**
 * A single track being edited in the Music Studio. Cropping is a trim window;
 * title/artist/album are the new tags written when it's saved. Volume and speed
 * are simple, reliable transforms applied on export.
 */
data class Song(
    val uri: Uri,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val sourceDurationMs: Long,
    val trimStartMs: Long = 0,
    val trimEndMs: Long = sourceDurationMs,
    val volume: Float = 1f,
    val speed: Float = 1f,
) {
    /** Length of the saved file after crop + speed. */
    val outDurationMs: Long
        get() = ((trimEndMs - trimStartMs).coerceAtLeast(0) / speed).toLong()
}
