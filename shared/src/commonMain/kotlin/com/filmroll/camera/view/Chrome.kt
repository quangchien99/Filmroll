package com.filmroll.camera.view

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.filmroll.camera.theme.eyebrowTextStyle
import com.filmroll.camera.theme.emphatic
import com.filmroll.camera.theme.standard

/**
 * Chrome that floats over the photo.
 *
 * There is no real blur here on purpose — Compose's blur is Android-12-and-up and
 * doesn't exist on iOS, so a "glass" panel would look like two different products.
 * A high-opacity dark fill with a hairline top edge reads as the same material on
 * both platforms and stays legible over a white sky, which is what actually matters.
 */
@Composable
fun ChromePanel(
    modifier: Modifier = Modifier,
    chromeColor: Color,
    shape: RoundedCornerShape = RoundedCornerShape(0.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(chromeColor)
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                shape,
            ),
    ) {
        content()
    }
}

/**
 * Round icon button sized for a thumb (44dp target) but drawn small, so a row of
 * them over a photo stays quiet until you reach for one.
 */
@Composable
fun ChromeIconButton(
    onClick: () -> Unit,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    imageVector: ImageVector? = null,
    painter: Painter? = null,
    active: Boolean = false,
    activeTint: Color = tint,
    enabled: Boolean = true,
    backgroundAlpha: Float = 0.10f,
) {
    val resolvedTint by animateColorAsState(
        targetValue = when {
            !enabled -> tint.copy(alpha = 0.35f)
            active -> activeTint
            else -> tint
        },
        animationSpec = standard(),
        label = "chromeIconTint",
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (active) activeTint.copy(alpha = 0.16f)
                else tint.copy(alpha = backgroundAlpha)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            imageVector != null -> Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = resolvedTint,
                modifier = Modifier.size(21.dp),
            )

            painter != null -> Icon(
                painter = painter,
                contentDescription = contentDescription,
                tint = resolvedTint,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

/**
 * Two-or-three-way segmented control.
 *
 * The selected pill is a single moving surface rather than a per-segment
 * background, so switching modes reads as one object sliding rather than two
 * things blinking.
 */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color,
    thumbColor: Color,
    selectedTextColor: Color,
    unselectedTextColor: Color,
) {
    BoxWithConstraints(
        modifier = modifier
            .height(38.dp)
            .clip(CircleShape)
            .background(trackColor),
    ) {
        val count = options.size.coerceAtLeast(1)
        val segmentWidth = maxWidth / count
        val thumbOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = emphatic(),
            label = "segmentOffset",
        )

        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(segmentWidth)
                .height(38.dp)
                .padding(3.dp)
                .clip(CircleShape)
                .background(thumbColor),
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (selected) selectedTextColor else unselectedTextColor,
                    animationSpec = standard(),
                    label = "segmentText",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(CircleShape)
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label.uppercase(),
                        style = eyebrowTextStyle,
                        color = textColor,
                    )
                }
            }
        }
    }
}

/** Small filter chip used for LUT categories. */
@Composable
fun FilmrollChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    selectedColor: Color,
    unselectedColor: Color,
    selectedTextColor: Color,
    unselectedTextColor: Color,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (selected) selectedColor else unselectedColor,
        animationSpec = standard(),
        label = "chipBackground",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) selectedTextColor else unselectedTextColor,
        animationSpec = standard(),
        label = "chipText",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.97f,
        animationSpec = emphatic(),
        label = "chipScale",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .height(32.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = eyebrowTextStyle, color = textColor)
    }
}

/** The small dot that marks a tool as "you've changed this". */
@Composable
fun ModifiedDot(visible: Boolean, color: Color, modifier: Modifier = Modifier, size: Dp = 5.dp) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = standard(),
        label = "modifiedDot",
    )
    Box(
        modifier = modifier
            .alpha(alpha)
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}
