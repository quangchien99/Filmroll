package com.filmroll.camera.view

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.filmroll.camera.FilmLut
import com.filmroll.camera.data.source.network.GITHUB_BASE_URL
import com.filmroll.camera.theme.emphatic
import com.filmroll.camera.theme.eyebrowTextStyle
import com.filmroll.camera.theme.standard
import com.filmroll.camera.util.THUMBNAILS_DIR
import com.filmroll.camera.util.systemTemporaryPath

private val TILE = 74.dp

/**
 * The film strip: a horizontally scrolling row of live previews, one per stock.
 *
 * This is the single biggest change to how the app is used. Choosing a look used
 * to mean opening a sheet, drilling into a category, and picking from a grid —
 * three taps and a full-screen context switch away from the photo you were
 * judging. Here the candidates sit under the photo and swapping between them is
 * one tap with the image still in view, which is the only way to actually compare
 * two film stocks.
 *
 * Each tile renders the user's *own* photo through that LUT (see
 * `HomeScreenModel.generateThumbnailsForCategory`), so the strip answers "what
 * would this do to my picture" rather than "what does this look like on someone
 * else's sample shot".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilmStrip(
    films: List<FilmLut>,
    thumbnails: Map<String, String>,
    selectedFilm: FilmLut?,
    favoriteNames: Set<String>,
    onSelect: (FilmLut?) -> Unit,
    onToggleFavorite: (FilmLut) -> Unit,
    onBrowseAll: () -> Unit,
    originalLabel: String,
    browseLabel: String,
    accentColor: Color,
    onCanvasColor: Color,
    onCanvasVariantColor: Color,
    favoriteColor: Color,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Keep the current pick on screen when it changes from somewhere else (the
    // browse sheet, or a reset). Index 0 is the "no film" tile.
    LaunchedEffect(selectedFilm, films) {
        val index = films.indexOfFirst { it.name == selectedFilm?.name }
        if (index >= 0) listState.animateScrollToItem(index + 1)
    }

    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "__none__") {
            StripTile(
                label = originalLabel,
                selected = selectedFilm == null,
                accentColor = accentColor,
                onCanvasColor = onCanvasColor,
                onCanvasVariantColor = onCanvasVariantColor,
                onClick = { onSelect(null) },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(onCanvasVariantColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = null,
                        tint = onCanvasVariantColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        items(films.size, key = { films[it].lut_name }) { index ->
            val film = films[index]
            val isFavorite = film.name in favoriteNames
            val haptics = LocalHapticFeedback.current

            StripTile(
                label = film.name,
                selected = selectedFilm?.name == film.name,
                accentColor = accentColor,
                onCanvasColor = onCanvasColor,
                onCanvasVariantColor = onCanvasVariantColor,
                onClick = { onSelect(film) },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleFavorite(film)
                },
            ) {
                LutThumbnail(
                    film = film,
                    thumbnails = thumbnails,
                    placeholderColor = onCanvasVariantColor.copy(alpha = 0.12f),
                )
                if (isFavorite) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = favoriteColor,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
            }
        }

        item(key = "__browse__") {
            StripTile(
                label = browseLabel,
                selected = false,
                accentColor = accentColor,
                onCanvasColor = onCanvasColor,
                onCanvasVariantColor = onCanvasVariantColor,
                onClick = onBrowseAll,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(onCanvasVariantColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GridView,
                        contentDescription = null,
                        tint = onCanvasVariantColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StripTile(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onCanvasColor: Color,
    onCanvasVariantColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = emphatic(),
        label = "tileScale",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accentColor else Color.Transparent,
        animationSpec = standard(),
        label = "tileBorder",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) accentColor else onCanvasVariantColor,
        animationSpec = standard(),
        label = "tileLabel",
    )

    Column(
        modifier = Modifier.width(TILE),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .scale(scale)
                .size(TILE)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, borderColor, RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(2.dp).clip(RoundedCornerShape(14.dp))) {
                Box(modifier = Modifier.fillMaxSize(), content = content)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            style = eyebrowTextStyle,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The per-LUT preview. Prefers the locally rendered thumbnail of the user's own
 * photo and only falls back to the project's sample image when one hasn't been
 * generated yet — which is why the cache is disabled for the local path: the file
 * name is reused across photos, so a cached bitmap would show the previous shot.
 */
@Composable
fun LutThumbnail(
    film: FilmLut,
    thumbnails: Map<String, String>,
    placeholderColor: Color,
    modifier: Modifier = Modifier,
) {
    val localThumbnail = thumbnails[film.lut_name]
    Box(modifier = modifier.fillMaxSize().background(placeholderColor)) {
        if (!localThumbnail.isNullOrEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data("$systemTemporaryPath/$THUMBNAILS_DIR/$localThumbnail")
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build(),
                contentDescription = film.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data("$GITHUB_BASE_URL${film.image_url}")
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = film.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
