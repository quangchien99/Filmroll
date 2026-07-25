package com.filmroll.camera.screens.home

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.plusmobileapps.konnectivity.Konnectivity
import io.github.vinceglb.filekit.core.PlatformFile
import io.github.vinceglb.filekit.core.extension
import com.filmroll.camera.FavoriteLut
import com.filmroll.camera.FilmLut
import com.filmroll.camera.capture.CaptureRelay
import com.filmroll.camera.capture.CapturedPhoto
import com.filmroll.camera.data.source.FilmRepository
import com.filmroll.camera.data.source.SettingsRepository
import com.filmroll.camera.data.source.toFavoriteLut
import com.filmroll.camera.image.ImageAdjustments
import com.filmroll.camera.image.SkiaImageProcessor
import com.filmroll.camera.lut.LutDownloadManager
import com.filmroll.camera.resources.Res
import com.filmroll.camera.resources.loading_exporting
import com.filmroll.camera.resources.loading_saving
import com.filmroll.camera.resources.msg_capture_failed
import com.filmroll.camera.resources.msg_catalog_refresh_failed
import com.filmroll.camera.resources.msg_choose_image_first
import com.filmroll.camera.resources.msg_export_failed
import com.filmroll.camera.resources.msg_export_success
import com.filmroll.camera.resources.msg_favorite_added
import com.filmroll.camera.resources.msg_favorite_removed
import com.filmroll.camera.resources.msg_film_load_failed
import com.filmroll.camera.resources.msg_unsupported_image
import com.filmroll.camera.screens.settings.DefaultPickerType
import com.filmroll.camera.util.AppContext
import com.filmroll.camera.util.convertImageToJpeg
import com.filmroll.camera.util.fixImageOrientation
import com.filmroll.camera.util.supportedImageExtensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.koin.dsl.module
import com.filmroll.camera.util.EDITED_IMAGE_FILE_NAME
import com.filmroll.camera.util.IMAGE_FILE_NAME
import com.filmroll.camera.util.ORIGINAL_IMAGE_FILE_PREFIX
import com.filmroll.camera.util.THUMBNAILS_DIR
import com.filmroll.camera.util.createDirectory
import com.filmroll.camera.util.readImageFile
import com.filmroll.camera.util.saveImageFile
import com.filmroll.camera.util.saveImageToGallery
import kotlin.coroutines.cancellation.CancellationException

val homeScreenModule = module {
    factory { HomeScreenModel(get(), get(), get(), get()) }
}

/** Debounce window before re-rendering the preview after a slider change. */
private const val PREVIEW_DEBOUNCE_MS = 25L

/**
 * Source formats that the Skia pipeline can't decode at full quality on its own —
 * they need to be transcoded to JPEG first via the platform converter. For RAW
 * formats this also ensures we read the embedded full-size preview rather than
 * the tiny thumbnail BitmapFactory returns by default (Pixel DNGs etc.).
 */
private val formatsNeedingConversion = setOf(
    "heic", "heif",
    "dng", "raw", "cr2", "nef", "orf", "arw", "raf", "pef", "sr2", "rw2",
)

/** JPEG quality used for the preview pipeline — lower than export to keep encode fast. */
private const val PREVIEW_QUALITY = 80

/**
 * State of the editor.
 *
 * Data only — the screen wires its own callbacks straight to the model rather than
 * carrying a second copy of this class stuffed with lambdas, which is what the
 * previous version did and which meant every keystroke of UI wiring had to be
 * declared in three places.
 */
data class HomeUiState(
    val previewImage: ByteArray? = null,
    val previewToken: Long = 0L,
    val hasImage: Boolean = false,
    val selectedFilm: FilmLut? = null,
    /** Non-null while a blocking task (export) owns the screen. */
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    /**
     * 0..1 progress for the loading dialog. When null, the dialog falls back
     * to its indeterminate spinner; when set, it renders a linear progress
     * bar driven by this value. Only the export path populates this — other
     * loading states leave it null.
     */
    val loadingProgress: Float? = null,
    /** True while the catalogue is being fetched. Shown inline, never blocking. */
    val isCatalogLoading: Boolean = false,
    /** True while a LUT is being fetched for the strip. Shown inline, never blocking. */
    val isApplyingFilm: Boolean = false,
    val defaultPickerType: DefaultPickerType = DefaultPickerType.IMAGES,
    val filmLuts: List<FilmLut> = emptyList(),
    val favoriteLuts: List<FavoriteLut> = emptyList(),
    val categories: List<String> = emptyList(),
    /** Category feeding the film strip. `null` means the favourites shelf. */
    val selectedCategory: String? = null,
    val userMessage: String? = null,
    /** False only while the user is holding the compare button. */
    val showAdjustments: Boolean = true,
    val imageAdjustments: ImageAdjustments = ImageAdjustments(),
    val showBrowser: Boolean = false,
    val showDownloadDialog: Boolean = false,
    val showDownloadProgress: Boolean = false,
    val downloadProgress: Pair<Int, Int> = 0 to 0,
    val filmThumbnails: Map<String, String> = emptyMap(),
) {
    // Lazy rather than `get()`: the editor recomposes on every preview frame, and
    // these two filter the whole catalogue. Computing them once per state instance
    // keeps a slider drag from re-scanning a few hundred LUTs each frame.
    val favoriteNames: Set<String> by lazy { favoriteLuts.mapTo(mutableSetOf()) { it.name } }

    /** Films shown in the strip for the current shelf. */
    val stripFilms: List<FilmLut> by lazy {
        when (val category = selectedCategory) {
            null -> filmLuts.filter { it.name in favoriteNames }
            else -> filmLuts.filter { it.category == category }
        }
    }

    val hasEdits: Boolean get() = selectedFilm != null || imageAdjustments.hasAdjustments()
}

/**
 * ViewModel for the editor.
 *
 * Preview pipeline: the screen model holds the decoded source image bytes and the
 * currently selected LUT bytes. Every change to either (or to [ImageAdjustments])
 * triggers a debounced re-render through [SkiaImageProcessor] at a preview-friendly
 * resolution; the resulting JPEG bytes are pushed into [HomeUiState.previewImage]
 * for the canvas to display. Export reuses the same processor at full resolution.
 */
data class HomeScreenModel(
    val repository: FilmRepository,
    val settingsRepository: SettingsRepository,
    val lutDownloadManager: LutDownloadManager,
    val captureRelay: CaptureRelay,
) : ScreenModel {

    private val _uiState: MutableStateFlow<HomeUiState> = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState
    private val konnectivity = Konnectivity()

    private val processor = SkiaImageProcessor()
    private val previewTrigger: MutableStateFlow<PreviewRequest?> = MutableStateFlow(null)
    private var previewVersion = 0L
    private var sourceBytes: ByteArray? = null
    private var currentLutBytes: ByteArray? = null
    private var currentAdjustments: ImageAdjustments = ImageAdjustments()
    private var currentThumbnailJob: Job? = null

    /** Guards the one-time pick of an opening shelf — see [refresh]. */
    private var shelfInitialized = false

    /** Filename in app cache holding the user's unmodified original picked image, with original extension. */
    private var originalImageFileName: String? = null

    private data class PreviewRequest(
        val sourceKey: Int,
        val lutKey: Int,
        val adjustments: ImageAdjustments,
        val showOriginal: Boolean,
    )

    init {
        refresh()
        addSettingsListeners()
        observeCaptures()
        screenModelScope.launch {
            lutDownloadManager.uiState.collect { downloadState ->
                updateUiState {
                    it.copy(
                        showDownloadDialog = downloadState.showDownloadDialog,
                        showDownloadProgress = downloadState.showDownloadProgress,
                        downloadProgress = downloadState.downloadProgress,
                    )
                }
            }
        }
        startPreviewLoop()
    }

    private fun updateUiState(update: (HomeUiState) -> HomeUiState) {
        _uiState.value = update(_uiState.value)
    }

    /**
     * Pulls the catalogue. Deliberately non-blocking: this runs on every cold start,
     * and a modal spinner over an empty editor made the app feel like it was booting
     * rather than ready. Failures are worth a word; success is not.
     */
    fun refresh() {
        screenModelScope.launch {
            try {
                updateUiState { it.copy(isCatalogLoading = true) }
                if (konnectivity.isConnected) {
                    repository.refresh()
                }
                val films = repository.getFilmsStream().first()
                val favorites = repository.getFavoriteFilmsStream().first()
                val categories = films.map { it.category }.distinct().sorted()
                // Land on favourites when there are some, otherwise the first shelf — an
                // empty strip on first run would look broken. Only ever chosen once:
                // `null` is a real shelf (favourites), so this can't be an elvis default.
                val shelf = if (shelfInitialized) {
                    _uiState.value.selectedCategory
                } else {
                    shelfInitialized = true
                    if (favorites.isEmpty()) categories.firstOrNull() else null
                }
                updateUiState {
                    it.copy(
                        filmLuts = films,
                        favoriteLuts = favorites,
                        categories = categories,
                        defaultPickerType = settingsRepository.getSettings().defaultPicker,
                        selectedCategory = shelf,
                    )
                }
                refreshThumbnailsForShelf()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                showMessage(getString(Res.string.msg_catalog_refresh_failed))
            } finally {
                updateUiState { it.copy(isCatalogLoading = false) }
            }
        }
    }

    fun dismissDownloadDialog() = lutDownloadManager.dismissDownloadDialog()

    fun confirmDownloadLuts() {
        lutDownloadManager.confirmDownloadLuts(screenModelScope) { _, message ->
            message?.let { showMessage(it) }
        }
    }

    // ---------------------------------------------------------------- adjustments

    fun updateAdjustment(tool: AdjustmentTool, value: Float) {
        currentAdjustments = tool.write(currentAdjustments, value)
        updateUiState { it.copy(imageAdjustments = currentAdjustments) }
        requestPreview()
    }

    fun resetAdjustments() {
        currentAdjustments = ImageAdjustments()
        updateUiState { it.copy(imageAdjustments = currentAdjustments) }
        requestPreview()
    }

    // ---------------------------------------------------------------------- films

    /**
     * Applies a film. No blocking dialog on purpose — this is now a single tap in
     * the strip, and a full-screen spinner between every tap would make comparing
     * two stocks unbearable. The inline flag is enough to explain a slow fetch.
     */
    fun selectFilmLut(filmLut: FilmLut) {
        screenModelScope.launch {
            try {
                updateUiState { it.copy(isApplyingFilm = true) }
                val lutBytes = withContext(Dispatchers.IO) { repository.getLutBytes(filmLut) }
                if (lutBytes == null) {
                    showMessage(getString(Res.string.msg_film_load_failed))
                    return@launch
                }
                currentLutBytes = lutBytes
                updateUiState { it.copy(selectedFilm = filmLut) }
                requestPreview()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                showMessage(getString(Res.string.msg_film_load_failed))
            } finally {
                updateUiState { it.copy(isApplyingFilm = false) }
            }
        }
    }

    /** Back to the untouched photo, keeping any manual adjustments. */
    fun clearFilmLut() {
        currentLutBytes = null
        updateUiState { it.copy(selectedFilm = null) }
        requestPreview()
    }

    fun selectCategory(category: String?) {
        if (_uiState.value.selectedCategory == category) return
        updateUiState { it.copy(selectedCategory = category) }
        refreshThumbnailsForShelf()
    }

    fun showBrowser() = updateUiState { it.copy(showBrowser = true) }

    fun dismissBrowser() = updateUiState { it.copy(showBrowser = false) }

    // ----------------------------------------------------------------- thumbnails

    /**
     * Renders the user's own photo through every LUT on the current shelf, so the
     * strip previews the actual picture rather than a stock sample. Already-rendered
     * entries are skipped, and the job is cancelled when the shelf changes — flicking
     * across the category chips shouldn't queue up hundreds of renders.
     */
    private fun refreshThumbnailsForShelf() {
        currentThumbnailJob?.cancel()
        if (sourceBytes == null) return

        val state = _uiState.value
        val films = state.stripFilms
        if (films.isEmpty()) return

        currentThumbnailJob = screenModelScope.launch {
            try {
                createDirectory(THUMBNAILS_DIR)
                val thumbnails = _uiState.value.filmThumbnails.toMutableMap()
                for (film in films) {
                    if (!isActive) return@launch
                    if (thumbnails.containsKey(film.lut_name)) continue
                    thumbnails[film.lut_name] = repository.generateLutThumbnail(film, IMAGE_FILE_NAME)
                    updateUiState { it.copy(filmThumbnails = thumbnails.toMap()) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // A thumbnail that fails to render simply falls back to the sample
                // image in the strip — not worth interrupting the user for.
            }
        }
    }

    /** Called by the browse sheet when the user opens a category it hasn't rendered yet. */
    fun generateThumbnailsForCategory(category: String) {
        if (sourceBytes == null) return
        screenModelScope.launch {
            try {
                createDirectory(THUMBNAILS_DIR)
                val films = repository.getFilms(false).filter { it.category == category }
                val thumbnails = _uiState.value.filmThumbnails.toMutableMap()
                for (film in films) {
                    if (!isActive) return@launch
                    if (thumbnails.containsKey(film.lut_name)) continue
                    thumbnails[film.lut_name] = repository.generateLutThumbnail(film, IMAGE_FILE_NAME)
                    updateUiState { it.copy(filmThumbnails = thumbnails.toMap()) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    // ---------------------------------------------------------------------- image

    fun onImagePickerResult(file: PlatformFile?) {
        val platformFile = file ?: return
        val extension = platformFile.extension.lowercase()
        if (!supportedImageExtensions.contains(extension)) {
            screenModelScope.launch { showMessage(getString(Res.string.msg_unsupported_image)) }
            return
        }

        screenModelScope.launch {
            val originalBytes = platformFile.readBytes()
            // Stash the raw original (with original extension) so export can read its
            // EXIF / detect format when "original format" export is selected.
            val originalName = "$ORIGINAL_IMAGE_FILE_PREFIX.$extension"
            saveImageFile(originalName, originalBytes)
            originalImageFileName = originalName

            saveImageFile(IMAGE_FILE_NAME, originalBytes)
            if (formatsNeedingConversion.contains(extension)) {
                convertImageToJpeg(IMAGE_FILE_NAME)
            }
            fixImageOrientation(image = IMAGE_FILE_NAME)

            val bytes = withContext(Dispatchers.IO) { readImageFile(IMAGE_FILE_NAME) }
            sourceBytes = bytes
            processor.clearCache()

            // Everything tied to the previous image is now wrong, including every
            // rendered thumbnail — they are keyed by LUT, not by photo.
            currentAdjustments = ImageAdjustments()
            currentLutBytes = null
            updateUiState {
                it.copy(
                    hasImage = true,
                    imageAdjustments = ImageAdjustments(),
                    filmThumbnails = emptyMap(),
                    selectedFilm = null,
                )
            }

            refreshThumbnailsForShelf()
            requestPreview()
        }
    }

    /**
     * Picks up frames shot in the viewfinder.
     *
     * The editor is the navigator root, so a capture cannot arrive as a screen
     * argument — it comes over [CaptureRelay] instead, and is consumed on arrival
     * so a configuration change doesn't re-import the same frame.
     */
    private fun observeCaptures() {
        screenModelScope.launch {
            captureRelay.pending.filterNotNull().collect { captured ->
                captureRelay.consume(captured)
                try {
                    adoptCapture(captured)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    showMessage(getString(Res.string.msg_capture_failed))
                }
            }
        }
    }

    private suspend fun adoptCapture(captured: CapturedPhoto) {
        // Already JPEG, so no format conversion — but the still carries its
        // rotation in EXIF and Skia reads pixels, not metadata.
        fixImageOrientation(image = captured.fileName)
        originalImageFileName = captured.originalFileName

        val bytes = withContext(Dispatchers.IO) { readImageFile(captured.fileName) }
        sourceBytes = bytes
        processor.clearCache()

        // The viewfinder's own settings open the editor, so the first thing on
        // screen is the frame as it was composed rather than a neutral render of it.
        currentAdjustments = captured.adjustments
        currentLutBytes = null
        updateUiState {
            it.copy(
                hasImage = true,
                imageAdjustments = captured.adjustments,
                filmThumbnails = emptyMap(),
                selectedFilm = null,
            )
        }

        refreshThumbnailsForShelf()

        val film = captured.filmName?.let { name ->
            _uiState.value.filmLuts.firstOrNull { it.name == name }
                ?: repository.getFilms(false).firstOrNull { it.name == name }
        }
        if (film != null) selectFilmLut(film) else requestPreview()
    }

    fun snackbarMessageShown() = updateUiState { it.copy(userMessage = null) }

    /** Held down by the compare button: shows the untouched photo for as long as it's true. */
    fun setShowOriginal(showOriginal: Boolean) {
        updateUiState { it.copy(showAdjustments = !showOriginal) }
        requestPreview()
    }

    /** Drops the film and every adjustment, back to the photo as imported. */
    fun resetImage() {
        currentAdjustments = ImageAdjustments()
        currentLutBytes = null
        updateUiState {
            it.copy(selectedFilm = null, imageAdjustments = ImageAdjustments())
        }
        requestPreview()
    }

    fun exportImage() {
        val bytes = sourceBytes ?: run {
            screenModelScope.launch { showMessage(getString(Res.string.msg_choose_image_first)) }
            return
        }
        screenModelScope.launch {
            try {
                // Resolved up front: `updateUiState` takes a plain lambda, and
                // `getString` is suspending.
                val exportingMessage = getString(Res.string.loading_exporting)
                val savingMessage = getString(Res.string.loading_saving)

                updateUiState {
                    it.copy(
                        isLoading = true,
                        loadingMessage = exportingMessage,
                        loadingProgress = 0f,
                    )
                }

                val exportedBytes = processor.process(
                    imageBytes = bytes,
                    lutBytes = currentLutBytes,
                    adjustments = currentAdjustments,
                    maxDimension = null,
                    quality = settingsRepository.getSettings().exportQuality,
                    grainSeed = (kotlin.random.Random.nextFloat() * 1000f),
                    highQualityGrain = true,
                    onProgress = { p -> updateUiState { it.copy(loadingProgress = p) } },
                ) ?: throw IllegalStateException("Failed to process image")

                withContext(Dispatchers.IO) {
                    saveImageFile(EDITED_IMAGE_FILE_NAME, exportedBytes)
                }

                // Switch back to indeterminate for the gallery save — PhotoKit and
                // MediaStore don't expose progress, so a bar there would be a lie.
                updateUiState {
                    it.copy(loadingMessage = savingMessage, loadingProgress = null)
                }
                saveImageToGallery(
                    image = EDITED_IMAGE_FILE_NAME,
                    appContext = AppContext,
                    format = settingsRepository.getSettings().exportFormat,
                    originalImage = originalImageFileName,
                )

                showMessage(getString(Res.string.msg_export_success))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                showMessage(getString(Res.string.msg_export_failed))
            } finally {
                updateUiState { it.copy(isLoading = false, loadingProgress = null) }
            }
        }
    }

    // ------------------------------------------------------------------ favourites

    fun toggleFavorite(filmLut: FilmLut) {
        val isFavorite = _uiState.value.favoriteLuts.any { it.name == filmLut.name }
        if (isFavorite) removeFavoriteFilm(filmLut) else addFavoriteFilm(filmLut)
    }

    fun addFavoriteFilm(filmLut: FilmLut) {
        screenModelScope.launch {
            try {
                val favorites = repository.addFavoriteFilm(filmLut.toFavoriteLut())
                updateUiState { it.copy(favoriteLuts = favorites) }
                showMessage(getString(Res.string.msg_favorite_added))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun removeFavoriteFilm(filmLut: FilmLut) {
        screenModelScope.launch {
            try {
                val favorites = repository.removeFavoriteFilm(filmLut.name)
                updateUiState { it.copy(favoriteLuts = favorites) }
                showMessage(getString(Res.string.msg_favorite_removed))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    // --------------------------------------------------------------------- private

    private fun showMessage(message: String) = updateUiState { it.copy(userMessage = message) }

    private fun addSettingsListeners() {
        settingsRepository.getSettings().defaultPickerListener { defaultPicker ->
            updateUiState { it.copy(defaultPickerType = defaultPicker) }
        }
    }

    private fun requestPreview() {
        val source = sourceBytes ?: return
        previewTrigger.value = PreviewRequest(
            sourceKey = source.contentHashCode(),
            lutKey = currentLutBytes?.contentHashCode() ?: 0,
            adjustments = currentAdjustments,
            showOriginal = !_uiState.value.showAdjustments,
        )
    }

    @OptIn(FlowPreview::class)
    private fun startPreviewLoop() {
        screenModelScope.launch {
            previewTrigger
                .filterNotNull()
                .debounce(PREVIEW_DEBOUNCE_MS)
                .collectLatest { request ->
                    val source = sourceBytes ?: return@collectLatest
                    val bytes = withContext(Dispatchers.Default) {
                        processor.process(
                            imageBytes = source,
                            lutBytes = if (request.showOriginal) null else currentLutBytes,
                            adjustments = if (request.showOriginal) {
                                ImageAdjustments()
                            } else {
                                currentAdjustments
                            },
                            maxDimension = SkiaImageProcessor.PREVIEW_MAX_DIMENSION,
                            quality = PREVIEW_QUALITY,
                        )
                    } ?: return@collectLatest
                    val token = ++previewVersion
                    updateUiState { it.copy(previewImage = bytes, previewToken = token) }
                }
        }
    }
}
