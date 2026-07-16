package com.neversoft.editor.engine

import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.neversoft.editor.model.Clip
import com.neversoft.editor.model.MediaType
import com.neversoft.editor.model.Project
import com.neversoft.editor.model.TextClip
import com.neversoft.editor.model.TextPosition

/**
 * Builds a Media3 [Composition] from a [Project] for the final export via
 * [androidx.media3.transformer.Transformer]. This is where filters, captions,
 * speed and background music are rendered into the output file. (Live preview
 * uses a plain ExoPlayer on the raw clips for maximum playback reliability.)
 */
@UnstableApi
object CompositionFactory {

    fun build(project: Project): Composition {
        val videoItems = project.clips.map { editedItem(it, project) }
        val videoSequence = EditedMediaItemSequence.Builder(videoItems).build()

        val sequences = mutableListOf(videoSequence)
        project.music?.let { music ->
            val item = EditedMediaItem.Builder(MediaItem.fromUri(music.uri))
                .setRemoveVideo(true)
                .setEffects(
                    Effects(
                        listOf<AudioProcessor>(volumeProcessor(music.volume)),
                        emptyList()
                    )
                )
                .build()
            // Its own sequence so it plays under the whole timeline. The primary
            // (video) sequence defines total length; music is truncated to fit.
            sequences.add(EditedMediaItemSequence.Builder(item).build())
        }

        return Composition.Builder(sequences)
            // Images and muted clips have no audio track — force a silent one so
            // clips concatenate cleanly instead of failing on a track mismatch.
            .experimentalSetForceAudioTrack(true)
            .build()
    }

    private fun editedItem(clip: Clip, project: Project): EditedMediaItem {
        val mediaItemBuilder = MediaItem.Builder().setUri(clip.uri)
        if (clip.type == MediaType.VIDEO) {
            mediaItemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.trimStartMs)
                    .setEndPositionMs(clip.trimEndMs)
                    .build()
            )
        }

        val builder = EditedMediaItem.Builder(mediaItemBuilder.build())

        if (clip.type == MediaType.IMAGE) {
            builder.setDurationUs(clip.imageDurationMs * 1000)
            builder.setFrameRate(30)
        }
        if (clip.muted || clip.type == MediaType.IMAGE) {
            builder.setRemoveAudio(true)
        }

        builder.setEffects(Effects(audioProcessors(clip), videoEffects(clip, project)))
        return builder.build()
    }

    private fun audioProcessors(clip: Clip): List<AudioProcessor> {
        if (clip.type == MediaType.IMAGE || clip.muted) return emptyList()
        if (clip.speed != 1f) {
            return listOf(SonicAudioProcessor().apply { setSpeed(clip.speed) })
        }
        return emptyList()
    }

    private fun videoEffects(clip: Clip, project: Project): List<Effect> {
        val effects = mutableListOf<Effect>()

        if (clip.type == MediaType.VIDEO && clip.speed != 1f) {
            effects.add(SpeedChangeEffect(clip.speed))
        }

        effects.addAll(Filters.effectsFor(clip.filter))

        // Normalise every clip to the project canvas so mixed orientations sit
        // on one consistent frame (letterboxed rather than stretched).
        effects.add(
            Presentation.createForWidthAndHeight(
                project.outputWidth,
                project.outputHeight,
                Presentation.LAYOUT_SCALE_TO_FIT
            )
        )

        if (clip.texts.isNotEmpty()) {
            val overlays: List<TextureOverlay> = clip.texts.map { textOverlay(it) }
            effects.add(OverlayEffect(overlays))
        }
        return effects
    }

    private fun textOverlay(text: TextClip): TextOverlay {
        val span = SpannableString(text.text)
        span.setSpan(
            ForegroundColorSpan(text.color.toInt()),
            0, span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        // A soft dark plate keeps captions readable over any footage.
        span.setSpan(
            BackgroundColorSpan(0x80000000.toInt()),
            0, span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        span.setSpan(
            AbsoluteSizeSpan(text.sizeSp, true),
            0, span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val y = when (text.position) {
            TextPosition.TOP -> 0.8f
            TextPosition.CENTER -> 0f
            TextPosition.BOTTOM -> -0.8f
        }
        val settings = StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(0f, y)
            .build()
        return TextOverlay.createStaticTextOverlay(span, settings)
    }

    private fun volumeProcessor(volume: Float): AudioProcessor {
        // Scale amplitude by re-mixing each channel layout onto itself.
        return ChannelMixingAudioProcessor().apply {
            putChannelMixingMatrix(ChannelMixingMatrix.create(1, 1).scaleBy(volume))
            putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2).scaleBy(volume))
        }
    }

    /** Convenience: total output duration in microseconds. */
    fun durationUs(project: Project): Long =
        if (project.totalDurationMs <= 0) C.TIME_UNSET else project.totalDurationMs * 1000
}
