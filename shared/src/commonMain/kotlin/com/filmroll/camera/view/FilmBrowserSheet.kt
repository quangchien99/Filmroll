package com.filmroll.camera.view

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.filmroll.camera.FilmLut
import com.filmroll.camera.resources.Res
import com.filmroll.camera.resources.browse_films
import com.filmroll.camera.resources.category_all
import com.filmroll.camera.resources.category_favorites
import com.filmroll.camera.resources.close
import com.filmroll.camera.resources.no_films_found
import com.filmroll.camera.resources.search_films_hint
import com.filmroll.camera.theme.eyebrowTextStyle
import com.filmroll.camera.theme.sheetShape
import org.jetbrains.compose.resources.stringResource

/**
 * The full catalogue.
 *
 * The strip covers picking a look while staring at the photo; this covers finding
 * one by name out of several hundred. The old sheet made you drill from a category
 * list into a grid and back out again to try a different category — search was a
 * separate mode that replaced the tabs. Here search and category filtering are the
 * same surface and compose with each other, so "the Portra in the negatives" is one
 * gesture rather than three.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmBrowserSheet(
    films: List<FilmLut>,
    categories: List<String>,
    favoriteNames: Set<String>,
    thumbnails: Map<String, String>,
    selectedFilm: FilmLut?,
    sheetState: SheetState,
    onSelect: (FilmLut) -> Unit,
    onToggleFavorite: (FilmLut) -> Unit,
    onCategoryShown: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // null = every category; "" sentinel is avoided so the favourites shelf can be
    // a first-class filter rather than a magic string.
    var category by remember { mutableStateOf<String?>(null) }
    var favoritesOnly by remember { mutableStateOf(false) }

    // Rendering a category's previews is expensive, so only ask for the one on screen.
    LaunchedEffect(category) { category?.let(onCategoryShown) }

    val visibleFilms = films.filter { film ->
        (query.isBlank() || film.name.contains(query, ignoreCase = true)) &&
            (category == null || film.category == category) &&
            (!favoritesOnly || film.name in favoriteNames)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = sheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.92f)
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.browse_films),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                ChromeIconButton(
                    onClick = onDismiss,
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(Res.string.close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    backgroundAlpha = 0.08f,
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                placeholder = {
                    Text(
                        text = stringResource(Res.string.search_films_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        ChromeIconButton(
                            onClick = { query = "" },
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(Res.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            backgroundAlpha = 0f,
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )

            Spacer(modifier = Modifier.height(14.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "__all__") {
                    FilmrollChip(
                        label = stringResource(Res.string.category_all),
                        selected = category == null && !favoritesOnly,
                        onClick = {
                            category = null
                            favoritesOnly = false
                        },
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item(key = "__favorites__") {
                    FilmrollChip(
                        label = stringResource(Res.string.category_favorites),
                        selected = favoritesOnly,
                        onClick = {
                            favoritesOnly = !favoritesOnly
                            category = null
                        },
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(categories.size, key = { categories[it] }) { index ->
                    val name = categories[index]
                    FilmrollChip(
                        label = name,
                        selected = category == name,
                        onClick = {
                            category = if (category == name) null else name
                            favoritesOnly = false
                        },
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (visibleFilms.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.no_films_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 40.dp),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(visibleFilms, key = { it.lut_name }) { film ->
                        BrowserTile(
                            film = film,
                            thumbnails = thumbnails,
                            selected = selectedFilm?.name == film.name,
                            isFavorite = film.name in favoriteNames,
                            onClick = { onSelect(film) },
                            onToggleFavorite = { onToggleFavorite(film) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowserTile(
    film: FilmLut,
    thumbnails: Map<String, String>,
    selected: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .border(2.dp, borderColor, RoundedCornerShape(18.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleFavorite()
                    },
                ),
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(2.dp).clip(RoundedCornerShape(16.dp))) {
                LutThumbnail(
                    film = film,
                    thumbnails = thumbnails,
                    placeholderColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
            if (isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = film.name,
            style = eyebrowTextStyle,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
