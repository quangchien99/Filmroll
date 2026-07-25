package com.filmroll.camera.screens.camera

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Grid3x3
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Cameraswitch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.filmroll.camera.FilmLut
import com.filmroll.camera.capture.CameraPermissionStatus
import com.filmroll.camera.capture.CameraStatus
import com.filmroll.camera.capture.CameraViewfinder
import com.filmroll.camera.capture.FilmrollCamera
import com.filmroll.camera.capture.FlashMode
import com.filmroll.camera.capture.openAppSettings
import com.filmroll.camera.capture.rememberCameraPermission
import com.filmroll.camera.resources.Res
import com.filmroll.camera.resources.action_allow_camera
import com.filmroll.camera.resources.action_flip_camera
import com.filmroll.camera.resources.action_grid
import com.filmroll.camera.resources.action_open_settings
import com.filmroll.camera.resources.action_reset
import com.filmroll.camera.resources.action_shutter
import com.filmroll.camera.resources.camera_frames_label
import com.filmroll.camera.resources.camera_permission_body
import com.filmroll.camera.resources.camera_permission_denied_body
import com.filmroll.camera.resources.camera_permission_title
import com.filmroll.camera.resources.camera_starting
import com.filmroll.camera.resources.camera_tab_look
import com.filmroll.camera.resources.category_favorites
import com.filmroll.camera.resources.close
import com.filmroll.camera.resources.editor_tab_film
import com.filmroll.camera.resources.film_none
import com.filmroll.camera.resources.flash_auto
import com.filmroll.camera.resources.flash_off
import com.filmroll.camera.resources.flash_on
import com.filmroll.camera.resources.no_favorites_body
import com.filmroll.camera.resources.strip_browse
import com.filmroll.camera.screens.home.AdjustmentTool
import com.filmroll.camera.theme.FilmrollTheme
import com.filmroll.camera.theme.emphatic
import com.filmroll.camera.theme.eyebrowTextStyle
import com.filmroll.camera.theme.readoutTextStyle
import com.filmroll.camera.theme.standard
import com.filmroll.camera.view.ChromeIconButton
import com.filmroll.camera.view.ChromePanel
import com.filmroll.camera.view.FilmBrowserSheet
import com.filmroll.camera.view.FilmStrip
import com.filmroll.camera.view.FilmrollChip
import com.filmroll.camera.view.InlineBusyIndicator
import com.filmroll.camera.view.ModifiedDot
import com.filmroll.camera.view.SegmentedTabs
import com.filmroll.camera.view.ToolSlider
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Matches the editor's deck so the two screens feel like one instrument. */
private val DECK_CONTENT_HEIGHT = 150.dp

/** How long the tap-to-focus reticle stays on screen after the tap. */
private const val RETICLE_MS = 900L

private enum class ViewfinderMode { FILM, LOOK }

/**
 * The viewfinder.
 *
 * Filmroll was a darkroom with no camera attached: you shot somewhere else, came
 * back, and *guessed* which stock the light you had already committed to would
 * suit. This screen closes that loop — the LUT runs on the live feed, so the film
 * is a thing you frame through rather than a thing you try on afterwards.
 *
 * The rules from the editor carry over deliberately. One control is expanded at a
 * time. Nothing blocks. The film strip sits under the image so swapping stocks is
 * a single tap with the scene still in front of you — which here also means the
 * light hasn't changed between the two candidates you are comparing.
 *
 * What the viewfinder shows is an honest preview and not the final frame: the
 * still is captured unfiltered and pushed through the same Skia pipeline the
 * editor exports with, at full resolution and full grain quality. The preview is
 * the cheap sibling of that pass, never the source of it.
 */
class CameraScreen : Screen {

    override val key: ScreenKey = uniqueScreenKey

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<CameraScreenModel>()
        val state by vm.uiState.collectAsState()
        val tokens = FilmrollTheme.tokens
        val permission = rememberCameraPermission()
        val snackbarHostState = remember { SnackbarHostState() }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        var mode by remember { mutableStateOf(ViewfinderMode.FILM) }
        var activeTool by remember { mutableStateOf(ViewfinderTool.EXPOSURE) }

        // Strength has nothing to control without a film, same as in the editor.
        LaunchedEffect(state.selectedFilm) {
            if (state.selectedFilm == null && activeTool.requiresFilm) {
                activeTool = ViewfinderTool.EXPOSURE
            }
        }

        // UNKNOWN means "we have not asked yet" — so ask, without making the user
        // press a button whose only possible answer is yes.
        LaunchedEffect(permission.status) {
            if (permission.status == CameraPermissionStatus.UNKNOWN) permission.request()
        }

        state.userMessage?.let { message ->
            LaunchedEffect(message) {
                snackbarHostState.showSnackbar(message)
                vm.snackbarMessageShown()
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(tokens.canvas)) {
            when (permission.status) {
                CameraPermissionStatus.GRANTED -> LiveViewfinder(
                    camera = vm.camera,
                    status = state.camera,
                    showGrid = state.showGrid,
                    onFocus = vm::focusAt,
                    onZoom = vm::setZoom,
                )

                CameraPermissionStatus.DENIED -> PermissionPane(
                    body = stringResource(Res.string.camera_permission_denied_body),
                    actionLabel = stringResource(Res.string.action_open_settings),
                    onAction = { openAppSettings() },
                )

                CameraPermissionStatus.UNKNOWN -> PermissionPane(
                    body = stringResource(Res.string.camera_permission_body),
                    actionLabel = stringResource(Res.string.action_allow_camera),
                    onAction = permission::request,
                )
            }

            TopScrim()

            TopChrome(
                flash = state.flash,
                gridVisible = state.showGrid,
                canFlip = state.camera.hasFrontLens,
                onClose = navigator::pop,
                onCycleFlash = vm::cycleFlash,
                onToggleGrid = vm::toggleGrid,
                onFlipLens = vm::flipLens,
            )

            ShutterBlink(token = state.shutterToken)

            // No deck without a feed: a film strip over a permission prompt invites
            // the user to load a stock into a camera they haven't switched on yet.
            AnimatedVisibility(
                visible = permission.status == CameraPermissionStatus.GRANTED,
                enter = fadeIn(standard()),
                exit = fadeOut(standard()),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                ViewfinderDeck(
                    state = state,
                    mode = mode,
                    onModeChange = { mode = it },
                    activeTool = activeTool,
                    onToolChange = { activeTool = it },
                    onAdjust = vm::updateAdjustment,
                    onExposure = vm::setExposureEv,
                    onResetLook = vm::resetLook,
                    onSelectFilm = vm::selectFilm,
                    onToggleFavorite = vm::toggleFavorite,
                    onSelectCategory = vm::selectCategory,
                    onBrowseAll = vm::showBrowser,
                    onShutter = { vm.capture { navigator.pop() } },
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 64.dp),
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
                // The viewfinder has no photo of the user's to render through each
                // stock, so the browser falls back to the catalogue's own samples.
                thumbnails = emptyMap(),
                selectedFilm = state.selectedFilm,
                sheetState = sheetState,
                onSelect = {
                    vm.selectFilm(it)
                    vm.dismissBrowser()
                },
                onToggleFavorite = vm::toggleFavorite,
                onCategoryShown = {},
                onDismiss = vm::dismissBrowser,
            )
        }
    }
}

// ------------------------------------------------------------------ viewfinder

/**
 * The live feed, plus everything drawn straight onto it.
 *
 * Both gestures live here rather than on the platform surface: pinch scales the
 * optical zoom and a tap meters where you tapped. Doing them in Compose keeps the
 * two actuals down to "render frames", which is the only part that genuinely has
 * to differ.
 */
@Composable
private fun LiveViewfinder(
    camera: FilmrollCamera,
    status: CameraStatus,
    showGrid: Boolean,
    onFocus: (Float, Float) -> Unit,
    onZoom: (Float) -> Unit,
) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusToken by remember { mutableStateOf(0L) }

    LaunchedEffect(focusToken) {
        if (focusToken == 0L) return@LaunchedEffect
        delay(RETICLE_MS)
        focusPoint = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewSize = it }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (viewSize.width == 0 || viewSize.height == 0) return@detectTapGestures
                    focusPoint = offset
                    focusToken += 1
                    onFocus(
                        offset.x / viewSize.width,
                        offset.y / viewSize.height,
                    )
                }
            }
            .pointerInput(status.minZoom, status.maxZoom, status.canZoom) {
                detectTransformGestures { _, _, zoomChange, _ ->
                    if (!status.canZoom) return@detectTransformGestures
                    onZoom((status.zoom * zoomChange).coerceIn(status.minZoom, status.maxZoom))
                }
            },
    ) {
        CameraViewfinder(camera = camera, modifier = Modifier.fillMaxSize())

        if (showGrid) GridOverlay()

        focusPoint?.let { point -> FocusReticle(point = point, token = focusToken) }

        ZoomReadout(
            zoom = status.zoom,
            visible = status.canZoom && status.zoom > 1.01f,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        )

        if (!status.isReady) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = status.errorMessage ?: stringResource(Res.string.camera_starting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FilmrollTheme.tokens.onCanvasVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
            }
        }
    }
}

/**
 * Rule-of-thirds guides. Thin, low-contrast and drawn as hairlines rather than
 * 1dp strokes, so they read as an aid over a bright scene without ever competing
 * with it.
 */
@Composable
private fun GridOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val line = Color.White.copy(alpha = 0.22f)
        val stroke = 1f
        for (i in 1..2) {
            val x = size.width * i / 3f
            val y = size.height * i / 3f
            drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = stroke)
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
        }
    }
}

/** The square that lands where you tapped and settles onto the metering point. */
@Composable
private fun FocusReticle(point: Offset, token: Long) {
    val density = LocalDensity.current
    val scale = remember(token) { Animatable(1.35f) }
    val alpha = remember(token) { Animatable(1f) }

    LaunchedEffect(token) {
        scale.animateTo(1f, animationSpec = emphatic())
    }
    LaunchedEffect(token) {
        delay(RETICLE_MS - 250)
        alpha.animateTo(0f, animationSpec = tween(250))
    }

    val sizeDp = 76.dp
    val half = with(density) { sizeDp.toPx() / 2f }

    Box(
        modifier = Modifier
            .offset {
                androidx.compose.ui.unit.IntOffset(
                    (point.x - half).roundToInt(),
                    (point.y - half).roundToInt(),
                )
            }
            .size(sizeDp)
            .scale(scale.value)
            .alpha(alpha.value)
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
    )
}

/** Current optical zoom, shown only while it is doing something. */
@Composable
private fun ZoomReadout(zoom: Float, visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(standard()),
        exit = fadeOut(standard()),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text(
                text = "${((zoom * 10f).roundToInt() / 10f)}×",
                style = readoutTextStyle,
                color = Color.White,
            )
        }
    }
}

/**
 * The shutter, drawn as a shutter: the frame goes black for an instant and comes
 * back. A white flash is the more common choice and it is the wrong one here —
 * this app's whole surround is a neutral near-black, and a white strobe blows out
 * the eye you are asking to judge colour with.
 */
@Composable
private fun ShutterBlink(token: Long) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(token) {
        if (token == 0L) return@LaunchedEffect
        alpha.snapTo(1f)
        alpha.animateTo(0f, animationSpec = tween(durationMillis = 340))
    }
    if (alpha.value > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = alpha.value)),
        )
    }
}

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
    flash: FlashMode,
    gridVisible: Boolean,
    canFlip: Boolean,
    onClose: () -> Unit,
    onCycleFlash: () -> Unit,
    onToggleGrid: () -> Unit,
    onFlipLens: () -> Unit,
) {
    val tokens = FilmrollTheme.tokens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChromeIconButton(
            onClick = onClose,
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(Res.string.close),
            tint = tokens.onCanvas,
        )

        Spacer(modifier = Modifier.weight(1f))

        ChromeIconButton(
            onClick = onCycleFlash,
            imageVector = when (flash) {
                FlashMode.OFF -> Icons.Rounded.FlashOff
                FlashMode.AUTO -> Icons.Rounded.FlashAuto
                FlashMode.ON -> Icons.Rounded.FlashOn
            },
            contentDescription = stringResource(
                when (flash) {
                    FlashMode.OFF -> Res.string.flash_off
                    FlashMode.AUTO -> Res.string.flash_auto
                    FlashMode.ON -> Res.string.flash_on
                },
            ),
            tint = tokens.onCanvas,
            active = flash != FlashMode.OFF,
            activeTint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.width(8.dp))

        ChromeIconButton(
            onClick = onToggleGrid,
            imageVector = Icons.Rounded.Grid3x3,
            contentDescription = stringResource(Res.string.action_grid),
            tint = tokens.onCanvas,
            active = gridVisible,
            activeTint = MaterialTheme.colorScheme.primary,
        )

        if (canFlip) {
            Spacer(modifier = Modifier.width(8.dp))
            ChromeIconButton(
                onClick = onFlipLens,
                imageVector = Icons.Rounded.Cameraswitch,
                contentDescription = stringResource(Res.string.action_flip_camera),
                tint = tokens.onCanvas,
            )
        }
    }
}

@Composable
private fun PermissionPane(
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val tokens = FilmrollTheme.tokens

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
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
            text = stringResource(Res.string.camera_permission_title),
            style = MaterialTheme.typography.headlineSmall,
            color = tokens.onCanvas,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.onCanvasVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onAction)
                .padding(horizontal = 26.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

// ------------------------------------------------------------------------ deck

@Composable
private fun ViewfinderDeck(
    state: CameraUiState,
    mode: ViewfinderMode,
    onModeChange: (ViewfinderMode) -> Unit,
    activeTool: ViewfinderTool,
    onToolChange: (ViewfinderTool) -> Unit,
    onAdjust: (AdjustmentTool, Float) -> Unit,
    onExposure: (Float) -> Unit,
    onResetLook: () -> Unit,
    onSelectFilm: (FilmLut?) -> Unit,
    onToggleFavorite: (FilmLut) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onBrowseAll: () -> Unit,
    onShutter: () -> Unit,
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
                if (state.isLoadingFilm) {
                    InlineBusyIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SegmentedTabs(
                    options = listOf(
                        stringResource(Res.string.editor_tab_film),
                        stringResource(Res.string.camera_tab_look),
                    ),
                    selectedIndex = mode.ordinal,
                    onSelect = { onModeChange(ViewfinderMode.entries[it]) },
                    modifier = Modifier.weight(1f),
                    trackColor = tokens.onCanvas.copy(alpha = 0.08f),
                    thumbColor = tokens.onCanvas.copy(alpha = 0.16f),
                    selectedTextColor = tokens.onCanvas,
                    unselectedTextColor = tokens.onCanvasVariant,
                )

                Spacer(modifier = Modifier.width(12.dp))

                ChromeIconButton(
                    onClick = onResetLook,
                    imageVector = Icons.Rounded.Restore,
                    contentDescription = stringResource(Res.string.action_reset),
                    tint = tokens.onCanvas,
                    enabled = state.adjustments.hasAdjustments(),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn(standard()) togetherWith fadeOut(standard()) },
                modifier = Modifier.fillMaxWidth().height(DECK_CONTENT_HEIGHT),
                label = "viewfinderMode",
            ) { current ->
                when (current) {
                    ViewfinderMode.FILM -> FilmPane(
                        state = state,
                        onSelectFilm = onSelectFilm,
                        onToggleFavorite = onToggleFavorite,
                        onSelectCategory = onSelectCategory,
                        onBrowseAll = onBrowseAll,
                    )

                    ViewfinderMode.LOOK -> LookPane(
                        state = state,
                        activeTool = activeTool,
                        onToolChange = onToolChange,
                        onAdjust = onAdjust,
                        onExposure = onExposure,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            ShutterRow(
                framesShot = state.framesShot,
                filmName = state.selectedFilm?.name,
                enabled = state.camera.isReady && !state.isCapturing,
                onShutter = onShutter,
            )
        }
    }
}

@Composable
private fun FilmPane(
    state: CameraUiState,
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
                thumbnails = emptyMap(),
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
private fun LookPane(
    state: CameraUiState,
    activeTool: ViewfinderTool,
    onToolChange: (ViewfinderTool) -> Unit,
    onAdjust: (AdjustmentTool, Float) -> Unit,
    onExposure: (Float) -> Unit,
) {
    val tokens = FilmrollTheme.tokens
    val tools = ViewfinderTool.entries.filter { tool ->
        when {
            tool.requiresFilm && state.selectedFilm == null -> false
            tool == ViewfinderTool.EXPOSURE -> state.camera.canAdjustExposure
            else -> true
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        val adjustment = activeTool.adjustment
        if (adjustment == null) {
            // Exposure: the device's own range in EV, so the slider reads in the
            // same units the camera's metering does.
            ToolSlider(
                label = stringResource(activeTool.labelRes),
                value = state.camera.exposureEv,
                valueRange = state.camera.minExposureEv..state.camera.maxExposureEv,
                defaultValue = 0f,
                onValueChange = onExposure,
                valueLabel = { ev -> formatEv(ev) },
                accentColor = MaterialTheme.colorScheme.primary,
                onCanvasColor = tokens.onCanvas,
                onCanvasVariantColor = tokens.onCanvasVariant,
                resetContentDescription = stringResource(Res.string.action_reset),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        } else {
            ToolSlider(
                label = stringResource(activeTool.labelRes),
                value = adjustment.read(state.adjustments),
                valueRange = adjustment.range,
                defaultValue = adjustment.neutral,
                onValueChange = { onAdjust(adjustment, it) },
                valueLabel = adjustment::format,
                accentColor = MaterialTheme.colorScheme.primary,
                onCanvasColor = tokens.onCanvas,
                onCanvasVariantColor = tokens.onCanvasVariant,
                resetContentDescription = stringResource(Res.string.action_reset),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

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
                    modified = tool.adjustment?.isModified(state.adjustments) ?: false,
                    onClick = { onToolChange(tool) },
                )
            }
        }
    }
}

@Composable
private fun ToolRailItem(
    tool: ViewfinderTool,
    selected: Boolean,
    modified: Boolean,
    onClick: () -> Unit,
) {
    val tokens = FilmrollTheme.tokens
    val accent = MaterialTheme.colorScheme.primary

    val background by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.18f) else Color.Transparent,
        animationSpec = standard(),
        label = "viewfinderToolBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) accent else tokens.onCanvasVariant,
        animationSpec = standard(),
        label = "viewfinderToolContent",
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
            modifier = Modifier.size(38.dp).clip(CircleShape).background(background),
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
            text = stringResource(tool.labelRes),
            style = eyebrowTextStyle.copy(fontSize = 9.sp, letterSpacing = 0.4.sp),
            color = contentColor,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Frame counter, shutter, loaded stock.
 *
 * The counter is not bookkeeping — it is the one thing a film camera always told
 * you and a phone never does, and having it there changes how the screen is used:
 * you notice you are on your ninth frame of the same subject.
 */
@Composable
private fun ShutterRow(
    framesShot: Int,
    filmName: String?,
    enabled: Boolean,
    onShutter: () -> Unit,
) {
    val tokens = FilmrollTheme.tokens

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(88.dp)) {
            Text(
                text = framesShot.toString().padStart(2, '0'),
                style = readoutTextStyle,
                color = tokens.onCanvas,
            )
            Text(
                text = stringResource(Res.string.camera_frames_label),
                style = eyebrowTextStyle.copy(fontSize = 9.sp),
                color = tokens.onCanvasVariant,
            )
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            ShutterButton(enabled = enabled, onClick = onShutter)
        }

        Text(
            text = filmName ?: stringResource(Res.string.film_none),
            style = eyebrowTextStyle,
            color = if (filmName == null) tokens.onCanvasVariant else MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            maxLines = 2,
            modifier = Modifier.width(88.dp),
        )
    }
}

/** Ring plus disc — the one shape everyone already knows means "shutter". */
@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val label = stringResource(Res.string.action_shutter)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = emphatic(),
        label = "shutterScale",
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .size(72.dp)
            .clip(CircleShape)
            .border(3.dp, FilmrollTheme.tokens.onCanvas.copy(alpha = if (enabled) 0.9f else 0.3f), CircleShape)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .pointerInput(enabled) {
                detectTapGestures(
                    onPress = {
                        if (!enabled) return@detectTapGestures
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = {
                        if (!enabled) return@detectTapGestures
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) MaterialTheme.colorScheme.primary
                    else FilmrollTheme.tokens.onCanvas.copy(alpha = 0.25f),
                ),
        )
    }
}

/** "+1.3 EV" — one decimal, because a third of a stop is the finest step anything offers. */
private fun formatEv(ev: Float): String {
    val rounded = (ev * 10f).roundToInt() / 10f
    val sign = if (rounded > 0f) "+" else ""
    return "$sign$rounded EV"
}
