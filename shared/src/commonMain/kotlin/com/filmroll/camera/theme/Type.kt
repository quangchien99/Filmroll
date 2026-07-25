package com.filmroll.camera.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * A full type scale, tuned rather than inherited.
 *
 * Two departures from the stock M3 scale: display and headline sizes get negative
 * tracking so large text sets tightly instead of drifting apart, and labels get
 * positive tracking because they are short, small and often all-caps-adjacent.
 * Line-height trimming keeps the first and last lines optically aligned with the
 * boxes they sit in — without it every card looks bottom-heavy.
 */

private val defaultFont = FontFamily.Default

private val trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Double,
) = TextStyle(
    fontFamily = defaultFont,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    lineHeightStyle = trim,
)

val filmrollTypography = Typography(
    displayLarge = style(56, 62, FontWeight.Bold, -1.2),
    displayMedium = style(44, 50, FontWeight.Bold, -1.0),
    displaySmall = style(35, 42, FontWeight.Bold, -0.6),

    headlineLarge = style(31, 38, FontWeight.Bold, -0.5),
    headlineMedium = style(27, 34, FontWeight.Bold, -0.4),
    headlineSmall = style(23, 30, FontWeight.SemiBold, -0.3),

    titleLarge = style(21, 28, FontWeight.SemiBold, -0.2),
    titleMedium = style(17, 24, FontWeight.SemiBold, 0.0),
    titleSmall = style(15, 20, FontWeight.SemiBold, 0.1),

    bodyLarge = style(16, 24, FontWeight.Normal, 0.1),
    bodyMedium = style(14, 21, FontWeight.Normal, 0.15),
    bodySmall = style(12, 17, FontWeight.Normal, 0.2),

    labelLarge = style(14, 18, FontWeight.SemiBold, 0.3),
    labelMedium = style(12, 16, FontWeight.SemiBold, 0.4),
    labelSmall = style(11, 14, FontWeight.Medium, 0.5),
)

/**
 * Numeric readouts in the editor — slider values, percentages, the export counter.
 * Bold and widely tracked so a value changing under your thumb stays legible, and
 * so the digits don't visually reflow as they swap between 1 and 8.
 */
val readoutTextStyle = TextStyle(
    fontFamily = defaultFont,
    fontSize = 15.sp,
    lineHeight = 18.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 0.8.sp,
)

/** The all-caps micro label used for section headers and tool names. */
val eyebrowTextStyle = TextStyle(
    fontFamily = defaultFont,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.4.sp,
)
