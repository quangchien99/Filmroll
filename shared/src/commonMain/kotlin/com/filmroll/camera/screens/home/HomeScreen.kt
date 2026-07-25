package com.filmroll.camera.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.filmroll.camera.FilmLut
import com.filmroll.camera.resources.Res
import com.filmroll.camera.resources.action_change_photo
import com.filmroll.camera.resources.action_choose_photo
import com.filmroll.camera.resources.action_compare
import com.filmroll.camera.resources.action_export
import com.filmroll.camera.resources.action_open_camera
import com.filmroll.camera.resources.action_reset
import com.filmroll.camera.resources.badge_original
import com.filmroll.camera.resources.category_favorites
import com.filmroll.camera.resources.editor_tab_adjust
import com.filmroll.camera.resources.editor_tab_film
import com.filmroll.camera.resources.film_none
import com.filmroll.camera.resources.home_empty_body
import com.filmroll.camera.resources.home_empty_title
import com.filmroll.camera.resources.no_favorites_body
import com.filmroll.camera.resources.settings
import com.filmroll.camera.resources.strip_browse
import com.filmroll.camera.screens.camera.CameraScreen
import com.filmroll.camera.screens.settings.DefaultPickerType
import com.filmroll.camera.screens.settings.SettingsScreen
import com.filmroll.camera.theme.FilmrollTheme
import com.filmroll.camera.theme.emphatic
import com.filmroll.camera.theme.eyebrowTextStyle
import com.filmroll.camera.theme.standard
import com.filmroll.camera.util.supportedImageExtensions
import com.filmroll.camera.view.ChromeIconButton
import com.filmroll.camera.view.ChromePanel
import com.filmroll.camera.view.FilmBrowserSheet
import com.filmroll.camera.view.FilmStrip
import com.filmroll.camera.view.FilmrollChip
import com.filmroll.camera.view.InlineBusyIndicator
import com.filmroll.camera.view.LutDownloadDialog
import com.filmroll.camera.view.LutDownloadProgressDialog
import com.filmroll.camera.view.ModifiedDot
import com.filmroll.camera.view.ProgressDialog
import com.filmroll.camera.view.SegmentedTabs
import com.filmroll.camera.view.ToolSlider
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import com.github.panpf.zoomimage.ZoomImage
import com.github.panpf.zoomimage.compose.rememberZoomState

/** Height reserved for the deck's swapping content, so switching modes doesn't resize it. */
private val DECK_CONTENT_HEIGHT = 150.dp

private enum class EditorMode { FILM, ADJUST }

/**
 * The editor.
 *
 * The photo owns the screen and the controls float over or under it. That is the
 * whole idea, and it is the opposite of what this screen used to do — a fixed
 * 360dp preview card at the top with nine always-visible sliders scrolling
 * beneath it, so the thing being edited got about a third of the display and
 * every adjustment was made while looking at a thumbnail.
 *
 * Three rules follow from it:
 *  - Only one control is expanded at a time. Film *or* adjust, and within adjust,
 *    one slider — chosen from a rail that shows at a glance which tools you've
 *    already touched.
 *  - Comparing is a press-and-hold, not a toggle. You cannot mis-set a button you
 *    have to keep holding, and it keeps the before/after in the same instant.
 *  - Nothing blocks except a full-resolution export.
 */
class HomeScreen : Screen {

    // Workaround for voyager#546: without a unique key the AndroidScreenLifecycleOwner
    // is reused across activity restarts and gets disposed mid-flight, causing the
    // empty-LUTs UI and the "DESTROYED cannot be moved to STARTED" crash.
    override val key: ScreenKey = uniqueScreenKey

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<HomeScreenModel>()
        val state by vm.uiState.collectAsState()
        val tokens = FilmrollTheme.tokens
        val snackbarHostState = remember { SnackbarHostState() }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        var mode by remember { mutableStateOf(EditorMode.FILM) }
        var activeTool by remember { mutableStateOf(AdjustmentTool.EXPOSURE) }

        // Strength only exists while a film is applied; drop back to a tool that
        // still has something to control rather than showing a dead slider.
        LaunchedEffect(state.selectedFilm) {
            if (state.selectedFilm == null && activeTool == AdjustmentTool.STRENGTH) {
                activeTool = AdjustmentTool.EXPOSURE
            }
        }

        val imagePicker = rememberFilePickerLauncher(
            type = when (state.defaultPickerType) {
                DefaultPickerType.IMAGES -> PickerType.Image
                DefaultPickerType.FILES -> PickerType.File(supportedImageExtensions.toList())
            },
            mode = PickerMode.Single,
            onResult = vm::onImagePickerResult,
        )

        state.userMessage?.let { message ->
            LaunchedEffect(message) {
                snackbarHostState.showSnackbar(message)
                vm.snackbarMessageShown()
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(tokens.canvas)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (state.hasImage) {
                        PhotoCanvas(state = state)
                    } else {
                        WelcomePane(
                            onOpenCamera = { navigator.push(CameraScreen()) },
                            onChoosePhoto = imagePicker::launch,
                        )
                    }

                    TopScrim()

                    TopChrome(
                        hasImage = state.hasImage,
                        comparing = !state.showAdjustments,
                        canReset = state.hasEdits,
                        onOpenCamera = { navigator.push(CameraScreen()) },
                        onPickImage = imagePicker::launch,
                        onCompareChange = vm::setShowOriginal,
                        onReset = vm::resetImage,
                        onSettings = { navigator.push(SettingsScreen()) },
                    )

                    OriginalBadge(
                        visible = !state.showAdjustments,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 66.dp),
                    )
                }

                AnimatedVisibility(
                    visible = state.hasImage,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    ControlDeck(
                        state = state,
                        mode = mode,
                        onModeChange = { mode = it },
                        activeTool = activeTool,
                        onToolChange = { activeTool = it },
                        onAdjust = vm::updateAdjustment,
                        onSelectFilm = { film ->
                            if (film == null) vm.clearFilmLut() else vm.selectFilmLut(film)
                        },
                        onToggleFavorite = vm::toggleFavorite,
                        onSelectCategory = vm::selectCategory,
                        onBrowseAll = vm::showBrowser,
                        onExport = vm::exportImage,
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }

        if (state.showBrowser) {
            FilmBrowserSheet(
                films = state.filmLuts,
                categories = state.categories,
                favoriteNames = state.favoriteNames,
                thumbnails = state.filmThumbnails,
                selectedFilm = state.selectedFilm,
                sheetState = sheetState,
                onSelect = {
                    vm.selectFilmLut(it)
                    vm.dismissBrowser()
                },
                onToggleFavorite = vm::toggleFavorite,
                onCategoryShown = vm::generateThumbnailsForCategory,
                onDismiss = vm::dismissBrowser,
            )
        }

        LutDownloadDialog(
            isVisible = state.showDownloadDialog,
            onDismiss = vm::dismissDownloadDialog,
            onConfirm = vm::confirmDownloadLuts,
        )

        LutDownloadProgressDialog(
            isVisible = state.showDownloadProgress,
            current = state.downloadProgress.first,
            total = state.downloadProgress.second,
            onDismiss = vm::dismissDownloadDialog,
        )

        if (state.isLoading) {
            ProgressDialog(
                loadingMessage = state.loadingMessage,
                progress = state.loadingProgress,
            )
        }
    }
}

// ---------------------------------------------------------------------- canvas

/**
 * The photo. Decoding happens off the main thread and the previous frame is held
 * on screen until the new one is ready, so dragging a slider re-renders without
 * the canvas ever flashing empty.
 */
@Composable
private fun PhotoCanvas(state: HomeUiState, modifier: Modifier = Modifier) {
    val zoomState = rememberZoomState()
    var painter by remember { mutableStateOf<BitmapPainter?>(null) }

    LaunchedEffect(state.previewToken) {
        val bytes = state.previewImage ?: return@LaunchedEffect
        painter = withContext(Dispatchers.Default) {
            BitmapPainter(bytes.decodeToImageBitmap())
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        painter?.let {
            ZoomImage(
                modifier = Modifier.fillMaxSize(),
                zoomState = zoomState,
                painter = it,
                contentDescription = null,
                scrollBar = null,
            )
        }
    }
}

/** Keeps the top chrome legible over a bright sky without dimming the whole photo. */
@Composable
private fun TopScrim() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.42f), Color.Transparent),
                ),
            ),
    )
}

@Composable
private fun TopChrome(
    hasImage: Boolean,
    comparing: Boolean,
    canReset: Boolean,
    onOpenCamera: () -> Unit,
    onPickImage: () -> Unit,
    onCompareChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onSettings: () -> Unit,
) {
    val tokens = FilmrollTheme.tokens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasImage) {
            ChromeIconButton(
                onClick = onOpenCamera,
                imageVector = Icons.Rounded.PhotoCamera,
                contentDescription = stringResource(Res.string.action_open_camera),
                tint = tokens.onCanvas,
            )
            Spacer(modifier = Modifier.width(8.dp))
            ChromeIconButton(
                onClick = onPickImage,
                imageVector = Icons.Rounded.PhotoLibrary,
                contentDescription = stringResource(Res.string.action_change_photo),
                tint = tokens.onCanvas,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (hasImage) {
            HoldToCompareButton(active = comparing, onActiveChange = onCompareChange)
            Spacer(modifier = Modifier.width(8.dp))
            ChromeIconButton(
                onClick = onReset,
                imageVector = Icons.Rounded.Restore,
                contentDescription = stringResource(Res.string.action_reset),
                tint = tokens.onCanvas,
                enabled = canReset,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        ChromeIconButton(
            onClick = onSettings,
            imageVector = Icons.Rounded.Settings,
            contentDescription = stringResource(Res.string.settings),
            tint = tokens.onCanvas,
        )
    }
}

/**
 * Press and hold to see the untouched photo.
 *
 * A toggle would be cheaper to build and worse to use: you can leave a toggle in
 * the wrong state and spend a minute editing the original by mistake, and you
 * lose the direct A/B that only works when both states are a fraction of a
 * second apart.
 */
@Composable
private fun HoldToCompareButton(
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
) {
    val tokens = FilmrollTheme.tokens
    val scale by animateFloatAsState(
        targetValue = if (active) 0.92f else 1f,
        animationSpec = emphatic(),
        label = "compareScale",
    )
    val background by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
        } else {
            tokens.onCanvas.copy(alpha = 0.10f)
        },
        animationSpec = standard(),
        label = "compareBackground",
    )
    val tint by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.onPrimary else tokens.onCanvas,
        animationSpec = standard(),
        label = "compareTint",
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onActiveChange(true)
                        tryAwaitRelease()
                        onActiveChange(false)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Compare,
            contentDescription = stringResource(Res.string.action_compare),
            tint = tint,
            modifier = Modifier.size(21.dp),
        )
    }
}

/** Says out loud which of the two images you are currently looking at. */
@Composable
private fun OriginalBadge(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(standard()),
        exit = fadeOut(standard()),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = stringResource(Res.string.badge_original),
                style = eyebrowTextStyle,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/**
 * The empty editor.
 *
 * Two ways in, and the order is the argument: shooting through the film is the
 * thing this app can do that a photo library cannot, so it leads. Importing an
 * existing frame is still one tap away, just no longer the only door.
 */
@Composable
private fun WelcomePane(
    onOpenCamera: () -> Unit,
    onChoosePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = FilmrollTheme.tokens

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = stringResource(Res.string.home_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = tokens.onCanvas,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = stringResource(Res.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.onCanvasVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onOpenCamera)
                .padding(horizontal = 26.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(Res.string.action_open_camera),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onChoosePhoto)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.PhotoLibrary,
                contentDescription = null,
                tint = tokens.onCanvasVariant,
                modifier = Modifier.size(17.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(Res.string.action_choose_photo),
                style = MaterialTheme.typography.labelLarge,
                color = tokens.onCanvasVariant,
            )
        }
    }
}

// ------------------------------------------------------------------------ deck

@Composable
private fun ControlDeck(
    state: HomeUiState,
    mode: EditorMode,
    onModeChange: (EditorMode) -> Unit,
    activeTool: AdjustmentTool,
    onToolChange: (AdjustmentTool) -> Unit,
    onAdjust: (AdjustmentTool, Float) -> Unit,
    onSelectFilm: (FilmLut?) -> Unit,
    onToggleFavorite: (FilmLut) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onBrowseAll: () -> Unit,
    onExport: () -> Unit,
) {
    val tokens = FilmrollTheme.tokens

    ChromePanel(
        modifier = Modifier.fillMaxWidth(),
        chromeColor = tokens.chrome,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 14.dp, bottom = 12.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                if (state.isApplyingFilm || state.isCatalogLoading) {
                    InlineBusyIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SegmentedTabs(
                    options = listOf(
                        stringResource(Res.string.editor_tab_film),
                        stringResource(Res.string.editor_tab_adjust),
                    ),
                    selectedIndex = mode.ordinal,
                    onSelect = { onModeChange(EditorMode.entries[it]) },
                    modifier = Modifier.weight(1f),
                    trackColor = tokens.onCanvas.copy(alpha = 0.08f),
                    thumbColor = tokens.onCanvas.copy(alpha = 0.16f),
                    selectedTextColor = tokens.onCanvas,
                    unselectedTextColor = tokens.onCanvasVariant,
                )

                Spacer(modifier = Modifier.width(12.dp))

                ExportButton(onClick = onExport)
            }

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn(standard()) togetherWith fadeOut(standard()) },
                modifier = Modifier.fillMaxWidth().height(DECK_CONTENT_HEIGHT),
                label = "deckMode",
            ) { current ->
                when (current) {
                    EditorMode.FILM -> FilmPane(
                        state = state,
                        onSelectFilm = onSelectFilm,
                        onToggleFavorite = onToggleFavorite,
                        onSelectCategory = onSelectCategory,
                        onBrowseAll = onBrowseAll,
                    )

                    EditorMode.ADJUST -> AdjustPane(
                        state = state,
                        activeTool = activeTool,
                        onToolChange = onToolChange,
                        onAdjust = onAdjust,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.SaveAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(17.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.action_export),
            style = eyebrowTextStyle,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun FilmPane(
    state: HomeUiState,
    onSelectFilm: (FilmLut?) -> Unit,
    onToggleFavorite: (FilmLut) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onBrowseAll: () -> Unit,
) {
    val tokens = FilmrollTheme.tokens

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "__favorites__") {
                FilmrollChip(
                    label = stringResource(Res.string.category_favorites),
                    selected = state.selectedCategory == null,
                    onClick = { onSelectCategory(null) },
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = tokens.onCanvas.copy(alpha = 0.08f),
                    selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedTextColor = tokens.onCanvasVariant,
                )
            }
            items(state.categories.size, key = { state.categories[it] }) { index ->
                val category = state.categories[index]
                FilmrollChip(
                    label = category,
                    selected = state.selectedCategory == category,
                    onClick = { onSelectCategory(category) },
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = tokens.onCanvas.copy(alpha = 0.08f),
                    selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedTextColor = tokens.onCanvasVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (state.stripFilms.isEmpty() && state.selectedCategory == null) {
            Box(
                modifier = Modifier.fillMaxWidth().height(94.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.no_favorites_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.onCanvasVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
            }
        } else {
            FilmStrip(
                films = state.stripFilms,
                thumbnails = state.filmThumbnails,
                selectedFilm = state.selectedFilm,
                favoriteNames = state.favoriteNames,
                onSelect = onSelectFilm,
                onToggleFavorite = onToggleFavorite,
                onBrowseAll = onBrowseAll,
                originalLabel = stringResource(Res.string.film_none),
                browseLabel = stringResource(Res.string.strip_browse),
                accentColor = MaterialTheme.colorScheme.primary,
                onCanvasColor = tokens.onCanvas,
                onCanvasVariantColor = tokens.onCanvasVariant,
                favoriteColor = tokens.safelight,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AdjustPane(
    state: HomeUiState,
    activeTool: AdjustmentTool,
    onToolChange: (AdjustmentTool) -> Unit,
    onAdjust: (AdjustmentTool, Float) -> Unit,
) {
    val tokens = FilmrollTheme.tokens
    val tools = AdjustmentTool.entries.filter { !it.requiresFilm || state.selectedFilm != null }

    Column(modifier = Modifier.fillMaxWidth()) {
        ToolSlider(
            label = stringResource(activeTool.labelRes),
            value = activeTool.read(state.imageAdjustments),
            valueRange = activeTool.range,
            defaultValue = activeTool.neutral,
            onValueChange = { onAdjust(activeTool, it) },
            valueLabel = activeTool::format,
            accentColor = MaterialTheme.colorScheme.primary,
            onCanvasColor = tokens.onCanvas,
            onCanvasVariantColor = tokens.onCanvasVariant,
            resetContentDescription = stringResource(Res.string.action_reset),
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(tools.size, key = { tools[it].name }) { index ->
                val tool = tools[index]
                ToolRailItem(
                    tool = tool,
                    selected = tool == activeTool,
                    modified = tool.isModified(state.imageAdjustments),
                    onClick = { onToolChange(tool) },
                )
            }
        }
    }
}

/**
 * One entry on the adjust rail. The dot is the point of it: without a marker, a
 * collapsed rail hides which of nine tools you have already moved, and the only
 * way to find out is to open each one.
 */
@Composable
private fun ToolRailItem(
    tool: AdjustmentTool,
    selected: Boolean,
    modified: Boolean,
    onClick: () -> Unit,
) {
    val tokens = FilmrollTheme.tokens
    val accent = MaterialTheme.colorScheme.primary

    val background by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.18f) else Color.Transparent,
        animationSpec = standard(),
        label = "toolBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) accent else tokens.onCanvasVariant,
        animationSpec = standard(),
        label = "toolContent",
    )

    Column(
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(19.dp),
            )
            ModifiedDot(
                visible = modified && !selected,
                color = accent,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 3.dp),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            // A touch smaller than the standard eyebrow: the rail has nine entries
            // and the longest labels need to survive at 64dp without truncating.
            text = stringResource(tool.labelRes),
            style = eyebrowTextStyle.copy(fontSize = 9.sp, letterSpacing = 0.4.sp),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
