package com.filmroll.camera.screens.camera

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.filmroll.camera.FavoriteLut
import com.filmroll.camera.FilmLut
import com.filmroll.camera.capture.CameraStatus
import com.filmroll.camera.capture.CapturedPhoto
import com.filmroll.camera.capture.CaptureRelay
import com.filmroll.camera.capture.FilmrollCamera
import com.filmroll.camera.capture.FlashMode
import com.filmroll.camera.capture.LensFacing
import com.filmroll.camera.capture.LiveLook
import com.filmroll.camera.data.source.FilmRepository
import com.filmroll.camera.data.source.toFavoriteLut
import com.filmroll.camera.image.CubeLut
import com.filmroll.camera.image.ImageAdjustments
import com.filmroll.camera.image.parseCubeLut
import com.filmroll.camera.resources.Res
import com.filmroll.camera.resources.msg_capture_failed
import com.filmroll.camera.resources.msg_favorite_added
import com.filmroll.camera.resources.msg_favorite_removed
import com.filmroll.camera.resources.msg_film_load_failed
import com.filmroll.camera.screens.home.AdjustmentTool
import com.filmroll.camera.util.IMAGE_FILE_NAME
import com.filmroll.camera.util.ORIGINAL_IMAGE_FILE_PREFIX
import com.filmroll.camera.util.saveImageFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.koin.dsl.module
import kotlin.coroutines.cancellation.CancellationException

val cameraScreenModule = module {
    factory { CameraScreenModel(get(), get()) }
}

/** Extension the capture is written under. The still always comes back as JPEG. */
private const val CAPTURE_EXTENSION = "jpg"

data class CameraUiState(
    val filmLuts: List<FilmLut> = emptyList(),
    val favoriteLuts: List<FavoriteLut> = emptyList(),
    val categories: List<String> = emptyList(),
    /** Shelf feeding the strip. `null` means favourites, same rule as the editor. */
    val selectedCategory: String? = null,
    val selectedFilm: FilmLut? = null,
    val adjustments: ImageAdjustments = ImageAdjustments(),
    val camera: CameraStatus = CameraStatus(),
    val flash: FlashMode = FlashMode.OFF,
    val showGrid: Boolean = false,
    val showBrowser: Boolean = false,
    /** True while a LUT is being fetched for the viewfinder. Inline, never blocking. */
    val isLoadingFilm: Boolean = false,
    val isCapturing: Boolean = false,
    /** Frames shot since the screen opened — the little counter on the deck. */
    val framesShot: Int = 0,
    /** Bumped on every shutter press; the screen animates a blink off it. */
    val shutterToken: Long = 0L,
    val userMessage: String? = null,
) {
    val favoriteNames: Set<String> by lazy { favoriteLuts.mapTo(mutableSetOf()) { it.name } }

    val stripFilms: List<FilmLut> by lazy {
        when (val category = selectedCategory) {
            null -> filmLuts.filter { it.name in favoriteNames }
            else -> filmLuts.filter { it.category == category }
        }
    }
}

/**
 * The viewfinder's model.
 *
 * It owns a [FilmrollCamera] outright rather than taking one from DI, because the
 * session's lifetime *is* this screen's lifetime — a camera that outlived the
 * screen would keep the hardware indicator lit while the user reads their
 * settings. [onDispose] is the only place it gets torn down.
 *
 * The look is pushed to the camera rather than pulled: every film pick and every
 * slider frame calls [pushLook], which hands the parsed cube and the adjustments
 * straight into the render pipeline. Parsing is the expensive half and it only
 * happens when the *film* changes, so dragging a slider costs a uniform upload.
 */
class CameraScreenModel(
    private val repository: FilmRepository,
    private val captureRelay: CaptureRelay,
) : ScreenModel {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState

    val camera = FilmrollCamera()

    /** Parsed cube for [CameraUiState.selectedFilm]; null while none is loaded. */
    private var currentCube: CubeLut? = null
    private var filmJob: Job? = null

    init {
        screenModelScope.launch {
            camera.status.collect { status -> updateUiState { it.copy(camera = status) } }
        }
        loadCatalog()
    }

    private fun updateUiState(update: (CameraUiState) -> CameraUiState) {
        _uiState.value = update(_uiState.value)
    }

    /**
     * Reads whatever the editor has already cached. No network refresh: the
     * catalogue is the editor's job, and a viewfinder that stalls on a cold
     * network before it will show you a picture is a broken camera.
     */
    private fun loadCatalog() {
        screenModelScope.launch {
            try {
                val films = repository.getFilms(false)
                val favorites = repository.getFavoriteFilms()
                val categories = films.map { it.category }.distinct().sorted()
                val favoriteNames = favorites.mapTo(mutableSetOf()) { it.name }
                updateUiState {
                    it.copy(
                        filmLuts = films,
                        favoriteLuts = favorites,
                        categories = categories,
                        selectedCategory = if (films.none { film -> film.name in favoriteNames }) {
                            categories.firstOrNull()
                        } else {
                            null
                        },
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // The strip simply stays empty; you can still shoot without a film.
            }
        }
    }

    // ----------------------------------------------------------------------- film

    fun selectFilm(film: FilmLut?) {
        filmJob?.cancel()
        if (film == null) {
            currentCube = null
            updateUiState { it.copy(selectedFilm = null, isLoadingFilm = false) }
            pushLook()
            return
        }
        filmJob = screenModelScope.launch {
            try {
                updateUiState { it.copy(isLoadingFilm = true) }
                val bytes = withContext(Dispatchers.IO) { repository.getLutBytes(film) }
                val cube = bytes?.let { withContext(Dispatchers.Default) { parseCubeLut(it) } }
                if (cube == null) {
                    showMessage(getString(Res.string.msg_film_load_failed))
                    return@launch
                }
                currentCube = cube
                updateUiState { it.copy(selectedFilm = film) }
                pushLook()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                showMessage(getString(Res.string.msg_film_load_failed))
            } finally {
                updateUiState { it.copy(isLoadingFilm = false) }
            }
        }
    }

    fun selectCategory(category: String?) {
        if (_uiState.value.selectedCategory == category) return
        updateUiState { it.copy(selectedCategory = category) }
    }

    fun toggleFavorite(film: FilmLut) {
        screenModelScope.launch {
            try {
                val isFavorite = _uiState.value.favoriteLuts.any { it.name == film.name }
                val favorites = if (isFavorite) {
                    repository.removeFavoriteFilm(film.name)
                } else {
                    repository.addFavoriteFilm(film.toFavoriteLut())
                }
                updateUiState { it.copy(favoriteLuts = favorites) }
                showMessage(
                    getString(
                        if (isFavorite) Res.string.msg_favorite_removed
                        else Res.string.msg_favorite_added,
                    ),
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun showBrowser() = updateUiState { it.copy(showBrowser = true) }

    fun dismissBrowser() = updateUiState { it.copy(showBrowser = false) }

    // ----------------------------------------------------------------- live look

    fun updateAdjustment(tool: AdjustmentTool, value: Float) {
        updateUiState { it.copy(adjustments = tool.write(it.adjustments, value)) }
        pushLook()
    }

    fun resetLook() {
        updateUiState { it.copy(adjustments = ImageAdjustments()) }
        camera.setExposureEv(0f)
        pushLook()
    }

    private fun pushLook() {
        val state = _uiState.value
        camera.setLook(
            LiveLook(
                cube = currentCube,
                filmName = state.selectedFilm?.name,
                adjustments = state.adjustments,
            ),
        )
    }

    // -------------------------------------------------------------------- device

    fun flipLens() {
        val next = if (_uiState.value.camera.lens == LensFacing.BACK) {
            LensFacing.FRONT
        } else {
            LensFacing.BACK
        }
        camera.setLens(next)
    }

    /** Cycles off → auto → on, the order every camera app uses. */
    fun cycleFlash() {
        val next = when (_uiState.value.flash) {
            FlashMode.OFF -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.ON
            FlashMode.ON -> FlashMode.OFF
        }
        updateUiState { it.copy(flash = next) }
        camera.setFlash(next)
    }

    fun toggleGrid() = updateUiState { it.copy(showGrid = !it.showGrid) }

    fun setExposureEv(ev: Float) = camera.setExposureEv(ev)

    fun setZoom(ratio: Float) = camera.setZoom(ratio)

    fun focusAt(x: Float, y: Float) = camera.focusAt(x, y)

    // ------------------------------------------------------------------- shutter

    /**
     * Fires the shutter and hands the frame to the editor.
     *
     * [onCaptured] runs only on success, and is where the screen pops — the
     * editor is already listening on the relay, so by the time the pop animation
     * finishes the photo is loaded and the film is applied.
     */
    fun capture(onCaptured: () -> Unit) {
        if (_uiState.value.isCapturing) return
        screenModelScope.launch {
            updateUiState { it.copy(isCapturing = true, shutterToken = it.shutterToken + 1) }
            try {
                val bytes = camera.capture()
                if (bytes == null) {
                    showMessage(getString(Res.string.msg_capture_failed))
                    return@launch
                }
                // Both copies are the same bytes: one becomes the editor's working
                // file, the other stays pristine so an "original format" export
                // still has EXIF to copy from. Matches what the picker does.
                val originalName = "$ORIGINAL_IMAGE_FILE_PREFIX.$CAPTURE_EXTENSION"
                withContext(Dispatchers.IO) {
                    saveImageFile(originalName, bytes)
                    saveImageFile(IMAGE_FILE_NAME, bytes)
                }
                val state = _uiState.value
                captureRelay.publish(
                    CapturedPhoto(
                        fileName = IMAGE_FILE_NAME,
                        originalFileName = originalName,
                        filmName = state.selectedFilm?.name,
                        adjustments = state.adjustments,
                    ),
                )
                updateUiState { it.copy(framesShot = it.framesShot + 1) }
                onCaptured()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                showMessage(getString(Res.string.msg_capture_failed))
            } finally {
                updateUiState { it.copy(isCapturing = false) }
            }
        }
    }

    fun snackbarMessageShown() = updateUiState { it.copy(userMessage = null) }

    private fun showMessage(message: String) = updateUiState { it.copy(userMessage = message) }

    override fun onDispose() {
        camera.release()
        super.onDispose()
    }
}
