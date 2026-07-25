package com.filmroll.camera.capture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DisplayOrientedMeteringPointFactory
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * CameraX for the session, GLES for the look.
 *
 * The split is the point: CameraX owns lens selection, metering, zoom, exposure
 * and the still, all of which are genuinely hard and already solved; the film
 * look is a single fragment shader over the preview stream, which CameraX has no
 * opinion about. `PreviewView` is deliberately unused — it renders the feed
 * itself and offers no seam to put a LUT into, so the [Preview] use case is
 * pointed at a [SurfaceTexture] owned by [ViewfinderRenderer] instead.
 *
 * Binding waits on two independent events — the GL surface being created and the
 * composable handing over a lifecycle — and either can arrive first, which is
 * what [bindIfReady] is for.
 */
actual class FilmrollCamera actual constructor() {

    private val _status = MutableStateFlow(CameraStatus())
    actual val status: StateFlow<CameraStatus> = _status

    private val mainHandler = Handler(Looper.getMainLooper())

    private var appContext: Context? = null

    /**
     * The Activity-backed context the viewfinder was composed in. Kept apart from
     * [appContext] because only a *visual* context can be asked for a display, and
     * the display is what tells us which way up to record the still. Cleared in
     * [detach] and [release], which is what keeps it from outliving the screen.
     */
    private var viewContext: Context? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var glSurfaceView: GLSurfaceView? = null
    private var renderer: ViewfinderRenderer? = null
    private var previewSurfaceTexture: SurfaceTexture? = null

    private var look: LiveLook = LiveLook()
    private var lens: LensFacing = LensFacing.BACK
    private var flash: FlashMode = FlashMode.OFF
    private var released = false

    // -------------------------------------------------------------- shared API

    actual fun setLook(look: LiveLook) {
        this.look = look
        renderer?.setLook(look)
        glSurfaceView?.requestRender()
    }

    actual fun setLens(lens: LensFacing) {
        if (this.lens == lens) return
        this.lens = lens
        renderer?.setMirror(lens == LensFacing.FRONT)
        _status.value = _status.value.copy(lens = lens, isReady = false)
        bindIfReady()
    }

    actual fun setFlash(mode: FlashMode) {
        flash = mode
        imageCapture?.flashMode = mode.toImageCaptureFlashMode()
    }

    actual fun setZoom(ratio: Float) {
        val control = camera?.cameraControl ?: return
        val state = _status.value
        val clamped = ratio.coerceIn(state.minZoom, state.maxZoom)
        control.setZoomRatio(clamped)
        _status.value = state.copy(zoom = clamped)
    }

    actual fun setExposureEv(ev: Float) {
        val info = camera?.cameraInfo ?: return
        val control = camera?.cameraControl ?: return
        val exposure = info.exposureState
        if (!exposure.isExposureCompensationSupported) return
        val step = exposure.exposureCompensationStep.toFloat()
        if (step <= 0f) return
        val range = exposure.exposureCompensationRange
        val index = (ev / step).roundToInt().coerceIn(range.lower, range.upper)
        control.setExposureCompensationIndex(index)
        _status.value = _status.value.copy(exposureEv = index * step)
    }

    actual fun focusAt(x: Float, y: Float) {
        val bound = camera ?: return
        val display = currentDisplay() ?: return
        // Display-oriented rather than surface-oriented: the tap arrives in
        // normalized *view* coordinates, and this factory is the one that knows
        // how those relate to the sensor once rotation and a front lens are in
        // play. Getting that wrong metres the opposite corner of the frame.
        val point = DisplayOrientedMeteringPointFactory(display, bound.cameraInfo, 1f, 1f)
            .createPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        val action = FocusMeteringAction.Builder(point)
            .setAutoCancelDuration(4, TimeUnit.SECONDS)
            .build()
        runCatching { bound.cameraControl.startFocusAndMetering(action) }
    }

    actual suspend fun capture(): ByteArray? = suspendCancellableCoroutine { continuation ->
        val capture = imageCapture
        if (capture == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val executor = appContext?.let { ContextCompat.getMainExecutor(it) }
        if (executor == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        capture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bytes = runCatching {
                        val buffer = image.planes[0].buffer
                        ByteArray(buffer.remaining()).also { buffer.get(it) }
                    }.getOrNull()
                    image.close()
                    if (continuation.isActive) continuation.resume(bytes)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (continuation.isActive) continuation.resume(null)
                }
            },
        )
    }

    actual fun release() {
        released = true
        mainHandler.post {
            runCatching { cameraProvider?.unbindAll() }
            camera = null
            imageCapture = null
            cameraProvider = null
            lifecycleOwner = null
            appContext = null
            viewContext = null
            renderer?.release()
            renderer = null
            glSurfaceView = null
            previewSurfaceTexture = null
        }
    }

    // ---------------------------------------------------------- Android-only API

    /** Builds the surface the frames land on. Called once per [CameraViewfinder]. */
    internal fun createPreviewView(context: Context): GLSurfaceView {
        val view = GLSurfaceView(context)
        val renderer = ViewfinderRenderer(
            onFrameAvailable = { view.requestRender() },
            onSurfaceTextureReady = { texture ->
                // Arrives on the GL thread; CameraX must be bound from the main one.
                mainHandler.post {
                    previewSurfaceTexture = texture
                    bindIfReady()
                }
            },
        )
        renderer.setLook(look)
        renderer.setMirror(lens == LensFacing.FRONT)
        view.setEGLContextClientVersion(2)
        view.setRenderer(renderer)
        // Frames drive the loop rather than a 60 Hz timer: a still scene should
        // not keep the GPU busy, and a moving one already ticks fast enough.
        view.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        view.preserveEGLContextOnPause = true
        this.glSurfaceView = view
        this.renderer = renderer
        return view
    }

    internal fun attach(context: Context, owner: LifecycleOwner) {
        if (released) return
        appContext = context.applicationContext
        viewContext = context
        lifecycleOwner = owner
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                if (released) return@addListener
                cameraProvider = runCatching { future.get() }.getOrNull()
                if (cameraProvider == null) {
                    _status.value = _status.value.copy(isReady = false, errorMessage = null)
                    return@addListener
                }
                _status.value = _status.value.copy(
                    hasFrontLens = runCatching {
                        cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) == true
                    }.getOrDefault(false),
                )
                bindIfReady()
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    internal fun detach() {
        runCatching { cameraProvider?.unbindAll() }
        camera = null
        viewContext = null
        _status.value = _status.value.copy(isReady = false)
    }

    internal fun onViewResumed() {
        glSurfaceView?.onResume()
    }

    internal fun onViewPaused() {
        glSurfaceView?.onPause()
    }

    // ------------------------------------------------------------------ private

    /**
     * The display the preview is actually on. The attached view knows best; the
     * composed context is the fallback for the window between binding and attach.
     */
    private fun currentDisplay(): Display? =
        glSurfaceView?.display ?: viewContext?.defaultDisplay()

    @SuppressLint("RestrictedApi")
    private fun bindIfReady() {
        val provider = cameraProvider ?: return
        val owner = lifecycleOwner ?: return
        val texture = previewSurfaceTexture ?: return
        val context = appContext ?: return
        if (released) return

        val executor = ContextCompat.getMainExecutor(context)
        val rotation = currentDisplay()?.rotation ?: Surface.ROTATION_0

        val preview = Preview.Builder()
            .setTargetRotation(rotation)
            .build()

        preview.setSurfaceProvider(executor) { request ->
            val resolution = request.resolution
            texture.setDefaultBufferSize(resolution.width, resolution.height)
            renderer?.setBufferSize(resolution.width, resolution.height)
            request.setTransformationInfoListener(executor) { info ->
                renderer?.setRotation(info.rotationDegrees)
                glSurfaceView?.requestRender()
            }
            val surface = Surface(texture)
            request.provideSurface(surface, executor) { surface.release() }
        }

        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(rotation)
            .build()
            .apply { flashMode = flash.toImageCaptureFlashMode() }

        val selector = when (lens) {
            LensFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            LensFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
        }

        val bound = runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(owner, selector, preview, capture)
        }.getOrNull()

        if (bound == null) {
            // Most often a device with no front lens, or another app holding the
            // camera. Either way the screen says so rather than showing black.
            _status.value = _status.value.copy(isReady = false, errorMessage = null)
            return
        }

        camera = bound
        imageCapture = capture
        publishCapabilities(bound)
    }

    /** Reads back what this particular lens can actually do, so the UI hides what it cannot. */
    private fun publishCapabilities(bound: Camera) {
        val info = bound.cameraInfo
        val zoom = info.zoomState.value
        val exposure = info.exposureState
        val step = exposure.exposureCompensationStep.toFloat()
        val supportsExposure = exposure.isExposureCompensationSupported && step > 0f
        val range = exposure.exposureCompensationRange

        _status.value = _status.value.copy(
            isReady = true,
            lens = lens,
            hasFlashUnit = info.hasFlashUnit(),
            zoom = zoom?.zoomRatio ?: 1f,
            minZoom = zoom?.minZoomRatio ?: 1f,
            maxZoom = zoom?.maxZoomRatio ?: 1f,
            exposureEv = if (supportsExposure) exposure.exposureCompensationIndex * step else 0f,
            minExposureEv = if (supportsExposure) range.lower * step else 0f,
            maxExposureEv = if (supportsExposure) range.upper * step else 0f,
            errorMessage = null,
        )
    }
}

private fun FlashMode.toImageCaptureFlashMode(): Int = when (this) {
    FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
    FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
    FlashMode.ON -> ImageCapture.FLASH_MODE_ON
}

/**
 * `Context.getDisplay` only exists from API 30, and below that the deprecated
 * `WindowManager.getDefaultDisplay` is the only option — hence the split. Throws
 * on a non-visual context, so never call it with an application context.
 */
@Suppress("DEPRECATION")
private fun Context.defaultDisplay(): Display? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display
    } else {
        (getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
    }

@Composable
actual fun CameraViewfinder(camera: FilmrollCamera, modifier: Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    AndroidView(
        factory = { ctx -> camera.createPreviewView(ctx) },
        modifier = modifier,
    )

    DisposableEffect(context, lifecycleOwner) {
        // GLSurfaceView has to be told about the lifecycle by hand, and CameraX
        // wants an owner to bind against — one observer covers both.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> camera.onViewResumed()
                Lifecycle.Event.ON_PAUSE -> camera.onViewPaused()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        camera.attach(context, lifecycleOwner)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            camera.onViewPaused()
            camera.detach()
        }
    }
}
