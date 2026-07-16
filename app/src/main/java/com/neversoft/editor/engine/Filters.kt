package com.neversoft.editor.engine

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import com.neversoft.editor.model.Filter

/**
 * Turns a [Filter] look into concrete Media3 GL effects. These are plain,
 * dependency-free colour math (contrast / saturation / channel scaling) so the
 * same look previews and exports identically with no external LUT files.
 */
@UnstableApi
object Filters {

    fun effectsFor(filter: Filter): List<Effect> = when (filter) {
        Filter.NONE -> emptyList()

        Filter.VIVID -> listOf(
            Contrast(0.18f),
            HslAdjustment.Builder().adjustSaturation(35f).build(),
        )

        Filter.WARM -> listOf(
            RgbAdjustment.Builder().setRedScale(1.12f).setBlueScale(0.90f).build(),
            HslAdjustment.Builder().adjustSaturation(12f).build(),
        )

        Filter.COOL -> listOf(
            RgbAdjustment.Builder().setRedScale(0.90f).setBlueScale(1.12f).build(),
            HslAdjustment.Builder().adjustSaturation(8f).build(),
        )

        Filter.MONO -> listOf(
            RgbFilter.createGrayscaleFilter(),
            Contrast(0.10f),
        )

        Filter.FADE -> listOf(
            Contrast(-0.14f),
            HslAdjustment.Builder().adjustSaturation(-22f).adjustLightness(6f).build(),
        )
    }
}
