package com.filmroll.camera.screens.camera

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Exposure
import androidx.compose.ui.graphics.vector.ImageVector
import com.filmroll.camera.resources.Res
import com.filmroll.camera.resources.tool_exposure
import com.filmroll.camera.screens.home.AdjustmentTool
import org.jetbrains.compose.resources.StringResource

/**
 * The viewfinder's rail — a strict subset of the editor's, plus one thing the
 * editor cannot offer.
 *
 * The subset exists because a live control has to hold 30 fps on a mid-range
 * phone: the five [AdjustmentTool]s reachable here are the ones both the GLES and
 * the Core Image path render for free alongside the LUT. Shadows, highlights and
 * fringing are absent on purpose rather than by omission — they belong to the
 * unhurried pass over a frame you already have.
 *
 * [EXPOSURE] is the odd one out and the reason this is an enum of its own. It
 * looks like the editor's exposure slider but it is not an adjustment at all: it
 * biases the *sensor*, so a brightened frame carries real highlight and shadow
 * information instead of a stretched copy of what a darker one recorded. That is
 * a thing only a camera can do, and it would be a waste to spend the control on a
 * post-hoc multiply.
 */
enum class ViewfinderTool(
    /** Null for [EXPOSURE], which drives the device rather than the image. */
    val adjustment: AdjustmentTool?,
) {
    EXPOSURE(null),
    STRENGTH(AdjustmentTool.STRENGTH),
    CONTRAST(AdjustmentTool.CONTRAST),
    TEMPERATURE(AdjustmentTool.TEMPERATURE),
    SATURATION(AdjustmentTool.SATURATION),
    GRAIN(AdjustmentTool.GRAIN);

    val labelRes: StringResource
        get() = adjustment?.labelRes ?: Res.string.tool_exposure

    val icon: ImageVector
        get() = adjustment?.icon ?: Icons.Rounded.Exposure

    /** Strength is meaningless with no film loaded, exactly as in the editor. */
    val requiresFilm: Boolean get() = adjustment?.requiresFilm == true
}
