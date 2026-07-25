package com.filmroll.camera.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.filmroll.camera.theme.eyebrowTextStyle
import com.filmroll.camera.theme.readoutTextStyle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The editor's one and only slider.
 *
 * Everything about it assumes a thumb is covering part of the screen: the value
 * readout sits *above* the track rather than under the thumb, the track grows
 * while you drag so it stays visible around your finger, and the fill is drawn
 * from [defaultValue] outward — so a glance tells you which way you pushed the
 * image and by how much, which a left-anchored fill can't.
 *
 * Resetting is deliberately available two ways: an explicit button that only
 * appears once the value is off-default (discoverable), and a snap-to-default
 * detent as you drag past it (fast). The detent also fires a haptic tick, which
 * is the only feedback that survives a finger sitting on top of the control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    defaultValue: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color,
    onCanvasColor: Color,
    onCanvasVariantColor: Color,
    valueLabel: (Float) -> String = { formatSigned(it, defaultValue) },
    resetContentDescription: String? = null,
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val dragging by interactionSource.collectIsDraggedAsState()
    val isModified = abs(value - defaultValue) > 0.001f

    // Tick once when the value lands exactly on the default. Dropping the first
    // emission stops it firing merely because the tool opened at its default.
    val latestValue = rememberUpdatedState(value)
    LaunchedEffect(Unit) {
        snapshotFlow { abs(latestValue.value - defaultValue) < 0.001f }
            .distinctUntilChanged()
            .drop(1)
            .collect { atDefault -> if (atDefault) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label.uppercase(),
                style = eyebrowTextStyle,
                color = onCanvasVariantColor,
                modifier = Modifier.weight(1f),
            )

            Text(
                text = valueLabel(value),
                style = readoutTextStyle,
                color = if (isModified) accentColor else onCanvasVariantColor,
            )

            AnimatedVisibility(
                visible = isModified,
                enter = fadeIn() + scaleIn(initialScale = 0.6f),
                exit = fadeOut() + scaleOut(targetScale = 0.6f),
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(onCanvasColor.copy(alpha = 0.10f))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onValueChange(defaultValue)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = resetContentDescription,
                        tint = onCanvasVariantColor,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }

        Slider(
            value = value,
            onValueChange = { raw ->
                val span = valueRange.endInclusive - valueRange.start
                // 1.5% of the range reads as "about there" under a fingertip.
                val detent = span * 0.015f
                onValueChange(if (abs(raw - defaultValue) < detent) defaultValue else raw)
            },
            valueRange = valueRange,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            thumb = {
                Box(
                    modifier = Modifier.size(width = 28.dp, height = 44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .scale(if (dragging) 1.25f else 1f)
                            .size(width = 5.dp, height = 26.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(accentColor),
                    )
                }
            },
            track = { sliderState ->
                val span = (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
                    .takeIf { it != 0f } ?: 1f
                val valueFraction = (value - sliderState.valueRange.start) / span
                val defaultFraction = (defaultValue - sliderState.valueRange.start) / span
                val thickness = if (dragging) 6.dp else 4.dp

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .drawBehind {
                            val h = thickness.toPx()
                            val y = (size.height - h) / 2f
                            val radius = CornerRadius(h / 2f, h / 2f)

                            drawRoundRect(
                                color = onCanvasVariantColor.copy(alpha = 0.28f),
                                topLeft = Offset(0f, y),
                                size = Size(size.width, h),
                                cornerRadius = radius,
                            )

                            // Fill spans default -> current, in whichever direction.
                            val from = minOf(valueFraction, defaultFraction) * size.width
                            val to = maxOf(valueFraction, defaultFraction) * size.width
                            if (to - from > 0.5f) {
                                drawRoundRect(
                                    color = accentColor,
                                    topLeft = Offset(from, y),
                                    size = Size(to - from, h),
                                    cornerRadius = radius,
                                )
                            }

                            // Home marker, only worth drawing when it isn't at an end.
                            if (defaultFraction > 0.02f && defaultFraction < 0.98f) {
                                val markerW = 2.dp.toPx()
                                val markerH = 12.dp.toPx()
                                drawRoundRect(
                                    color = onCanvasVariantColor.copy(alpha = 0.7f),
                                    topLeft = Offset(
                                        defaultFraction * size.width - markerW / 2f,
                                        (size.height - markerH) / 2f,
                                    ),
                                    size = Size(markerW, markerH),
                                    cornerRadius = CornerRadius(markerW, markerW),
                                )
                            }
                        },
                )
            },
        )
    }
}

/** "+12" / "-4" / "0" — the sign carries the meaning, so it is always shown. */
fun formatSigned(value: Float, defaultValue: Float): String {
    val rounded = value.roundToInt()
    val zero = defaultValue.roundToInt()
    return when {
        rounded > zero -> "+$rounded"
        else -> rounded.toString()
    }
}

/** A plain percentage, for values whose neutral point is 100. */
fun formatPercent(value: Float): String = "${value.roundToInt()}%"

/**
 * A slider for settings screens — same visual language, but laid out for a list row
 * rather than for a thumb hovering over a photo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueLabel: (Float) -> String = { formatPercent(it) },
    steps: Int = 0,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val dragging by interactionSource.collectIsDraggedAsState()
    val accent = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = valueLabel(value),
                style = readoutTextStyle,
                color = accent,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            thumb = {
                Box(
                    modifier = Modifier.size(width = 24.dp, height = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .scale(if (dragging) 1.2f else 1f)
                            .size(width = 5.dp, height = 24.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(accent),
                    )
                }
            },
            track = { sliderState ->
                val span = (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
                    .takeIf { it != 0f } ?: 1f
                val fraction = (value - sliderState.valueRange.start) / span
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .drawBehind {
                            val h = 4.dp.toPx()
                            val y = (size.height - h) / 2f
                            val radius = CornerRadius(h / 2f, h / 2f)
                            drawRoundRect(
                                color = onSurfaceVariant.copy(alpha = 0.28f),
                                topLeft = Offset(0f, y),
                                size = Size(size.width, h),
                                cornerRadius = radius,
                            )
                            drawRoundRect(
                                color = accent,
                                topLeft = Offset(0f, y),
                                size = Size(size.width * fraction, h),
                                cornerRadius = radius,
                            )
                        },
                )
            },
        )
    }
}
