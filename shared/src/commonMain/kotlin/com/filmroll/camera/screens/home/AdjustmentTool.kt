package com.filmroll.camera.screens.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Exposure
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tonality
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.filmroll.camera.image.ImageAdjustments
import com.filmroll.camera.resources.Res
import com.filmroll.camera.resources.tool_contrast
import com.filmroll.camera.resources.tool_exposure
import com.filmroll.camera.resources.tool_fringing
import com.filmroll.camera.resources.tool_grain
import com.filmroll.camera.resources.tool_highlights
import com.filmroll.camera.resources.tool_saturation
import com.filmroll.camera.resources.tool_shadows
import com.filmroll.camera.resources.tool_strength
import com.filmroll.camera.resources.tool_temperature
import org.jetbrains.compose.resources.StringResource
import kotlin.math.abs

/**
 * The adjust rail, described once.
 *
 * Range, neutral point, icon, label and both accessors live together so the UI can
 * render any tool generically — the editor shows one slider and swaps which entry
 * drives it. Adding a tenth adjustment means adding a line here and nothing else.
 *
 * [STRENGTH] is listed first and behaves differently from the rest: it is the LUT
 * mix, so it is only meaningful once a film is chosen, and its neutral value is
 * 100 rather than 0.
 */
enum class AdjustmentTool(
    val labelRes: StringResource,
    val icon: ImageVector,
    val range: ClosedFloatingPointRange<Float>,
    val neutral: Float,
    val requiresFilm: Boolean = false,
) {
    STRENGTH(Res.string.tool_strength, Icons.Rounded.Opacity, 0f..200f, 100f, requiresFilm = true),
    EXPOSURE(Res.string.tool_exposure, Icons.Rounded.Exposure, -20f..20f, 0f),
    CONTRAST(Res.string.tool_contrast, Icons.Rounded.Contrast, -20f..20f, 0f),
    TEMPERATURE(Res.string.tool_temperature, Icons.Rounded.Thermostat, -20f..20f, 0f),
    SATURATION(Res.string.tool_saturation, Icons.Rounded.Palette, -20f..20f, 0f),
    SHADOWS(Res.string.tool_shadows, Icons.Rounded.Tonality, -20f..20f, 0f),
    HIGHLIGHTS(Res.string.tool_highlights, Icons.Rounded.WbSunny, -20f..20f, 0f),
    GRAIN(Res.string.tool_grain, Icons.Rounded.Grain, 0f..10f, 0f),
    FRINGING(Res.string.tool_fringing, Icons.Rounded.BlurOn, 0f..10f, 0f);

    fun read(adjustments: ImageAdjustments): Float = when (this) {
        STRENGTH -> adjustments.lutIntensity
        EXPOSURE -> adjustments.exposure
        CONTRAST -> adjustments.contrast
        TEMPERATURE -> adjustments.temperature
        SATURATION -> adjustments.saturation
        SHADOWS -> adjustments.shadows
        HIGHLIGHTS -> adjustments.highlights
        GRAIN -> adjustments.grain
        FRINGING -> adjustments.chromaticAberration
    }

    fun write(adjustments: ImageAdjustments, value: Float): ImageAdjustments = when (this) {
        STRENGTH -> adjustments.copy(lutIntensity = value)
        EXPOSURE -> adjustments.copy(exposure = value)
        CONTRAST -> adjustments.copy(contrast = value)
        TEMPERATURE -> adjustments.copy(temperature = value)
        SATURATION -> adjustments.copy(saturation = value)
        SHADOWS -> adjustments.copy(shadows = value)
        HIGHLIGHTS -> adjustments.copy(highlights = value)
        GRAIN -> adjustments.copy(grain = value)
        FRINGING -> adjustments.copy(chromaticAberration = value)
    }

    /** Drives the dot on the rail that marks a tool as touched. */
    fun isModified(adjustments: ImageAdjustments): Boolean =
        abs(read(adjustments) - neutral) > 0.001f

    /** Percentages read as percentages; everything else as a signed offset. */
    fun format(value: Float): String = when (this) {
        STRENGTH -> "${value.toInt()}%"
        else -> {
            val rounded = value.toInt()
            if (rounded > 0) "+$rounded" else rounded.toString()
        }
    }
}
