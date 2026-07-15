package com.neversoft.editor.model

import android.net.Uri

/** What kind of source a clip points at. */
enum class MediaType { VIDEO, IMAGE }

/** Where a text caption sits on the frame. */
enum class TextPosition { TOP, CENTER, BOTTOM }

/**
 * A single caption burned onto a clip for that clip's whole duration.
 * Kept per-clip so timing is trivial (it lives exactly as long as its clip).
 */
data class TextClip(
    val id: Long,
    val text: String,
    val position: TextPosition = TextPosition.BOTTOM,
    val sizeSp: Int = 28,
    val color: Long = 0xFFFFFFFF,
)

/**
 * A colour look. Values are plain adjustments so they map directly onto
 * Media3 effects (contrast / saturation / warmth) with no external LUTs.
 */
enum class Filter(val label: String) {
    NONE("Original"),
    VIVID("Vivid"),
    WARM("Warm"),
    COOL("Cool"),
    MONO("Mono"),
    FADE("Fade"),
}

/**
 * One segment on the timeline. Video clips carry a trim window; image clips
 * carry a chosen on-screen duration. Everything else (speed, filter, mute,
 * captions) is shared.
 */
data class Clip(
    val id: Long,
    val uri: Uri,
    val type: MediaType,
    val sourceDurationMs: Long,           // full length of the source (video only)
    val trimStartMs: Long = 0,            // video trim in
    val trimEndMs: Long = sourceDurationMs, // video trim out
    val imageDurationMs: Long = 3000,     // how long a photo stays on screen
    val speed: Float = 1f,                // video playback speed multiplier
    val filter: Filter = Filter.NONE,
    val muted: Boolean = false,
    val texts: List<TextClip> = emptyList(),
) {
    /** Length this clip occupies on the timeline, after trim + speed. */
    val timelineDurationMs: Long
        get() = when (type) {
            MediaType.IMAGE -> imageDurationMs
            MediaType.VIDEO -> {
                val trimmed = (trimEndMs - trimStartMs).coerceAtLeast(0)
                (trimmed / speed).toLong()
            }
        }
}

/** Background music laid under the whole timeline. */
data class AudioTrack(
    val uri: Uri,
    val title: String,
    val volume: Float = 1f,
)

/**
 * The whole edit. Immutable — every edit produces a new copy so undo/redo and
 * Compose recomposition stay trivial.
 */
data class Project(
    val clips: List<Clip> = emptyList(),
    val music: AudioTrack? = null,
    val outputWidth: Int = 720,
    val outputHeight: Int = 1280,
) {
    val totalDurationMs: Long get() = clips.sumOf { it.timelineDurationMs }
    val isEmpty: Boolean get() = clips.isEmpty()
}
