package com.filmroll.camera.view

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/**
 * Blocking progress. Only two things in the app earn one of these — a full-
 * resolution export and a bulk LUT download — so it is allowed to take up room
 * and say plainly what it is doing.
 *
 * The determinate bar is animated toward its target rather than snapped: the
 * export reports progress in ~32 tile-sized jumps, and a bar that teleports
 * between them reads as broken.
 */
@Composable
fun ProgressDialog(
    loadingMessage: String,
    progress: Float? = null,
) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (progress != null) {
                val animated by animateFloatAsState(
                    targetValue = progress.coerceIn(0f, 1f),
                    animationSpec = tween(durationMillis = 160),
                    label = "exportProgress",
                )
                Text(
                    text = "${(animated * 100f).roundToInt()}%",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                DeterminateBar(fraction = animated)
            } else {
                IndeterminateTrack()
            }

            Text(
                text = loadingMessage,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Determinate bar shared by the export dialog and the LUT download dialog. */
@Composable
fun DeterminateBar(fraction: Float, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f)
    val fillColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .drawBehind {
                val radius = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(color = trackColor, cornerRadius = radius)
                drawRoundRect(
                    color = fillColor,
                    size = Size(size.width * fraction, size.height),
                    cornerRadius = radius,
                )
            },
    )
}

/**
 * A sliver of light travelling left to right — the same visual grammar as the
 * determinate bar, so a task that starts indeterminate and later gains a
 * percentage doesn't change shape halfway through.
 */
@Composable
private fun IndeterminateTrack(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "indeterminate")
    val head by transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "indeterminateHead",
    )
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f)
    val fillColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .drawBehind {
                val radius = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(color = trackColor, cornerRadius = radius)
                val start = (head * size.width).coerceAtLeast(0f)
                val end = ((head + 0.35f) * size.width).coerceAtMost(size.width)
                if (end > start) {
                    drawRoundRect(
                        color = fillColor,
                        topLeft = Offset(start, 0f),
                        size = Size(end - start, size.height),
                        cornerRadius = radius,
                    )
                }
            },
    )
}

/** Non-blocking readout used inline in the editor while a preview renders. */
@Composable
fun InlineBusyIndicator(color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "inlineBusy")
    val head by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "inlineBusyHead",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .drawBehind {
                val start = (head * size.width).coerceAtLeast(0f)
                val end = ((head + 0.4f) * size.width).coerceAtMost(size.width)
                if (end > start) {
                    drawRect(
                        color = color,
                        topLeft = Offset(start, 0f),
                        size = Size(end - start, size.height),
                    )
                }
            },
    )
}
