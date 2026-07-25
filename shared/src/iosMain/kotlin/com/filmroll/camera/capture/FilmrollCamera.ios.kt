package com.filmroll.camera.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.filmroll.camera.image.CubeLut
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureFlashMode
import platform.AVFoundation.AVCaptureFlashModeAuto
import platform.AVFoundation.AVCaptureFlashModeOff
import platform.AVFoundation.AVCaptureFlashModeOn
import platform.AVFoundation.AVCaptureFocusModeAutoFocus
import platform.AVFoundation.AVCaptureExposureModeContinuousAutoExposure
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetPhoto
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeLeft
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeRight
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoOrientationPortraitUpsideDown
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVVideoCodecKey
import platform.AVFoundation.AVVideoCodecTypeJPEG
import platform.AVFoundation.defaultDeviceWithDeviceType
import platform.AVFoundation.exposurePointOfInterest
import platform.AVFoundation.exposureTargetBias
import platform.AVFoundation.fileDataRepresentation
import platform.AVFoundation.flashMode
import platform.AVFoundation.focusPointOfInterest
import platform.AVFoundation.focusPointOfInterestSupported
import platform.AVFoundation.hasFlash
import platform.AVFoundation.maxAvailableVideoZoomFactor
import platform.AVFoundation.maxExposureTargetBias
import platform.AVFoundation.minAvailableVideoZoomFactor
import platform.AVFoundation.minExposureTargetBias
import platform.AVFoundation.setExposureMode
import platform.AVFoundation.setExposureTargetBias
import platform.AVFoundation.setFocusMode
import platform.AVFoundation.videoZoomFactor
import platform.CoreGraphics.CGPointMake
import platform.CoreImage.CIContext
import platform.CoreImage.CIImage
import platform.CoreImage.createCGImage
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.QuartzCore.CATransaction
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation
import platform.UIKit.UIDeviceOrientationDidChangeNotification
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIView
import platform.UIKit.UIViewContentMode
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import platform.posix.memcpy
import kotlin.coroutines.resume

/**
 * AVFoundation for the session, Core Image for the look.
 *
 * `AVCaptureVideoPreviewLayer` — the obvious choice for a viewfinder — is
 * deliberately unused, because it renders the sensor feed directly and there is
 * no seam in it for a LUT. Instead frames come off an
 * [AVCaptureVideoDataOutput], go through the filter chain, and are drawn into a
 * plain [UIImageView]. That costs one GPU render and one image hand-off per
 * frame, which under the `.photo` preset (whose video stream is already
 * preview-sized rather than full sensor resolution) comfortably holds 30 fps.
 *
 * Everything that touches the session runs on [sessionQueue] and everything that
 * touches the view runs on the main queue; the two never overlap.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class FilmrollCamera actual constructor() {

    private val _status = MutableStateFlow(CameraStatus())
    actual val status: StateFlow<CameraStatus> = _status

    private val session = AVCaptureSession()
    private val sessionQueue = dispatch_queue_create("com.filmroll.camera.session", null)
    private val videoQueue = dispatch_queue_create("com.filmroll.camera.video", null)
    private val videoOutput = AVCaptureVideoDataOutput()
    private val photoOutput = AVCapturePhotoOutput()
    private val ciContext = CIContext.contextWithOptions(null)

    private var device: AVCaptureDevice? = null
    private var deviceInput: AVCaptureDeviceInput? = null
    private var imageView: UIImageView? = null
    private var orientationObserver: Any? = null

    private var lens: LensFacing = LensFacing.BACK
    private var flash: FlashMode = FlashMode.OFF
    private var configured = false
    private var released = false

    // Read on the video queue, written from the main thread.
    private var look: LiveLook = LiveLook()

    /**
     * The strength-baked cube and its `NSData`, rebuilt only when the film or the
     * strength changes. Both are expensive enough (a 33³ rebuild plus a 570 KB
     * copy) that doing them per frame would cost more than the render.
     */
    private var preparedCubeSize: Int = 0
    private var preparedCubeData: NSData? = null
    private var preparedFor: Pair<CubeLut?, Float> = null to Float.NaN

    private var frameCounter: Long = 0

    private val frameDelegate = FrameDelegate { sampleBuffer ->
        onSampleBuffer(sampleBuffer)
    }

    // -------------------------------------------------------------- shared API

    actual fun setLook(look: LiveLook) {
        this.look = look
    }

    actual fun setLens(lens: LensFacing) {
        if (this.lens == lens) return
        this.lens = lens
        _status.value = _status.value.copy(lens = lens)
        val orientation = UIDevice.currentDevice.orientation
        dispatch_async(sessionQueue) { swapInput(orientation) }
    }

    actual fun setFlash(mode: FlashMode) {
        flash = mode
    }

    actual fun setZoom(ratio: Float) {
        val target = device ?: return
        val state = _status.value
        val clamped = ratio.coerceIn(state.minZoom, state.maxZoom)
        withDeviceLock(target) { it.videoZoomFactor = clamped.toDouble() }
        _status.value = state.copy(zoom = clamped)
    }

    actual fun setExposureEv(ev: Float) {
        val target = device ?: return
        val state = _status.value
        val clamped = ev.coerceIn(state.minExposureEv, state.maxExposureEv)
        withDeviceLock(target) { it.setExposureTargetBias(clamped, null) }
        _status.value = state.copy(exposureEv = clamped)
    }

    actual fun focusAt(x: Float, y: Float) {
        val target = device ?: return
        if (!target.focusPointOfInterestSupported) return
        // The device's point-of-interest space is the sensor's own, with the origin
        // at the top-left of a landscape-right frame. For a portrait viewfinder that
        // is the axes swapped and one of them flipped.
        val point = CGPointMake(y.coerceIn(0f, 1f).toDouble(), (1f - x.coerceIn(0f, 1f)).toDouble())
        withDeviceLock(target) {
            it.focusPointOfInterest = point
            it.setFocusMode(AVCaptureFocusModeAutoFocus)
            it.exposurePointOfInterest = point
            it.setExposureMode(AVCaptureExposureModeContinuousAutoExposure)
        }
    }

    actual suspend fun capture(): ByteArray? = suspendCancellableCoroutine { continuation ->
        if (!session.running) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        // JPEG rather than the HEIC default: the rest of the pipeline decodes it
        // everywhere without a platform-specific transcode step.
        val settings = AVCapturePhotoSettings.photoSettingsWithFormat(
            mapOf(AVVideoCodecKey to AVVideoCodecTypeJPEG),
        )
        val requestedFlash = flash.toAvFlashMode()
        // `supportedFlashModes` is an NSArray of NSNumbers, so it has to be unwrapped
        // rather than compared against the raw constant.
        if (photoOutput.supportedFlashModes.any { (it as? NSNumber)?.longValue == requestedFlash }) {
            settings.flashMode = requestedFlash
        }

        // AVFoundation holds the capture delegate weakly, so it has to be kept
        // alive here for the round trip. One slot is enough: the screen model
        // refuses a second shutter press while one is in flight.
        val delegate = PhotoDelegate { data ->
            pendingPhotoDelegate = null
            if (continuation.isActive) continuation.resume(data?.toByteArray())
        }
        pendingPhotoDelegate = delegate

        // Read on the main thread — UIDevice.orientation is not safe to touch
        // anywhere else — and carried onto the session queue.
        val orientation = UIDevice.currentDevice.orientation
        dispatch_async(sessionQueue) {
            applyOrientation(orientation)
            photoOutput.capturePhotoWithSettings(settings, delegate)
        }
    }

    actual fun release() {
        released = true
        orientationObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        orientationObserver = null
        UIDevice.currentDevice.endGeneratingDeviceOrientationNotifications()
        dispatch_async(sessionQueue) {
            if (session.running) session.stopRunning()
            videoOutput.setSampleBufferDelegate(null, null)
        }
        imageView = null
        pendingPhotoDelegate = null
    }

    // -------------------------------------------------------------- iOS-only API

    internal fun createPreviewView(): UIView {
        val view = UIImageView()
        view.contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill
        view.clipsToBounds = true
        imageView = view
        return view
    }

    internal fun startSession() {
        if (released) return
        observeOrientation()
        val orientation = UIDevice.currentDevice.orientation
        dispatch_async(sessionQueue) {
            if (!configured) {
                configureSession()
                configured = true
            }
            if (!session.running) session.startRunning()
            applyOrientation(orientation)
            dispatch_async(dispatch_get_main_queue()) { publishCapabilities() }
        }
    }

    internal fun stopSession() {
        dispatch_async(sessionQueue) {
            if (session.running) session.stopRunning()
        }
        _status.value = _status.value.copy(isReady = false)
    }

    // ------------------------------------------------------------------ private

    private fun configureSession() {
        session.beginConfiguration()
        // `.photo` gives the stills full sensor resolution while handing the video
        // output a preview-sized stream — exactly the asymmetry a camera app wants.
        if (session.canSetSessionPreset(AVCaptureSessionPresetPhoto)) {
            session.sessionPreset = AVCaptureSessionPresetPhoto
        }

        attachInput(lens)

        videoOutput.alwaysDiscardsLateVideoFrames = true
        videoOutput.setSampleBufferDelegate(frameDelegate, videoQueue)
        if (session.canAddOutput(videoOutput)) session.addOutput(videoOutput)
        if (session.canAddOutput(photoOutput)) session.addOutput(photoOutput)

        session.commitConfiguration()
    }

    private fun attachInput(facing: LensFacing) {
        val position = when (facing) {
            LensFacing.BACK -> AVCaptureDevicePositionBack
            LensFacing.FRONT -> AVCaptureDevicePositionFront
        }
        val target = AVCaptureDevice.defaultDeviceWithDeviceType(
            deviceType = AVCaptureDeviceTypeBuiltInWideAngleCamera,
            mediaType = AVMediaTypeVideo,
            position = position,
        ) ?: return
        val input = AVCaptureDeviceInput.deviceInputWithDevice(target, null) ?: return
        if (!session.canAddInput(input)) return
        session.addInput(input)
        device = target
        deviceInput = input
    }

    private fun swapInput(orientation: UIDeviceOrientation) {
        session.beginConfiguration()
        deviceInput?.let { session.removeInput(it) }
        deviceInput = null
        device = null
        attachInput(lens)
        session.commitConfiguration()
        applyOrientation(orientation)
        dispatch_async(dispatch_get_main_queue()) { publishCapabilities() }
    }

    private fun publishCapabilities() {
        val target = device
        val hasFront = AVCaptureDevice.defaultDeviceWithDeviceType(
            deviceType = AVCaptureDeviceTypeBuiltInWideAngleCamera,
            mediaType = AVMediaTypeVideo,
            position = AVCaptureDevicePositionFront,
        ) != null

        if (target == null) {
            _status.value = _status.value.copy(isReady = false, hasFrontLens = hasFront)
            return
        }

        _status.value = _status.value.copy(
            isReady = session.running,
            lens = lens,
            hasFrontLens = hasFront,
            hasFlashUnit = target.hasFlash,
            zoom = target.videoZoomFactor.toFloat(),
            minZoom = target.minAvailableVideoZoomFactor.toFloat(),
            // Real devices report ludicrous digital-zoom ceilings (100×+). Past ~10×
            // there is nothing left to see, so the pinch stops where the picture does.
            maxZoom = target.maxAvailableVideoZoomFactor.toFloat().coerceAtMost(10f),
            exposureEv = target.exposureTargetBias,
            minExposureEv = target.minExposureTargetBias,
            maxExposureEv = target.maxExposureTargetBias,
            errorMessage = null,
        )
    }

    private fun observeOrientation() {
        if (orientationObserver != null) return
        UIDevice.currentDevice.beginGeneratingDeviceOrientationNotifications()
        orientationObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIDeviceOrientationDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            val orientation = UIDevice.currentDevice.orientation
            dispatch_async(sessionQueue) { applyOrientation(orientation) }
        }
    }

    /**
     * Points every connection the right way up.
     *
     * The default is a landscape-right buffer no matter how the phone is held, so
     * without this a portrait viewfinder shows the world on its side.
     */
    private fun applyOrientation(deviceOrientation: UIDeviceOrientation) {
        // Face-up, face-down and unknown all fall through to portrait: the device is
        // flat, there is no meaningful "up", and snapping the feed sideways because
        // the phone was laid on a table is worse than leaving it where it was.
        val orientation = when (deviceOrientation) {
            UIDeviceOrientation.UIDeviceOrientationLandscapeLeft ->
                AVCaptureVideoOrientationLandscapeRight
            UIDeviceOrientation.UIDeviceOrientationLandscapeRight ->
                AVCaptureVideoOrientationLandscapeLeft
            UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown ->
                AVCaptureVideoOrientationPortraitUpsideDown
            else -> AVCaptureVideoOrientationPortrait
        }
        listOf(
            videoOutput.connectionWithMediaType(AVMediaTypeVideo) to true,
            photoOutput.connectionWithMediaType(AVMediaTypeVideo) to false,
        ).forEach { (connection, mirrorPreview) ->
            connection ?: return@forEach
            configureConnection(connection, orientation, mirrorPreview)
        }
    }

    private fun configureConnection(
        connection: AVCaptureConnection,
        orientation: Long,
        mirrorPreview: Boolean,
    ) {
        if (connection.supportsVideoOrientation) {
            connection.videoOrientation = orientation
        }
        if (connection.supportsVideoMirroring) {
            connection.automaticallyAdjustsVideoMirroring = false
            // The preview is mirrored on the front lens because that is what a
            // mirror does and every phone camera has taught people to expect it.
            // The saved frame is not, so text in the shot stays readable.
            connection.videoMirrored = mirrorPreview && lens == LensFacing.FRONT
        }
    }

    // ------------------------------------------------------------- frame render

    private fun onSampleBuffer(sampleBuffer: CMSampleBufferRef?) {
        if (released) return
        val pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) ?: return
        val current = look

        var image = CIImage.imageWithCVPixelBuffer(pixelBuffer)
        val extent = image.extent

        val cube = current.cube
        if (cube != null) {
            val data = prepareCube(cube, current.adjustments.lutIntensity)
            if (data != null) image = ViewfinderFilters.applyLut(image, preparedCubeSize, data)
        }
        image = ViewfinderFilters.applyTone(image, current.adjustments)
        frameCounter += 1
        image = ViewfinderFilters.applyGrain(
            image = image,
            extent = extent,
            grain = current.adjustments.grain,
            seed = (frameCounter % 997L).toFloat(),
        )

        val cgImage = ciContext.createCGImage(image, fromRect = extent) ?: return
        val uiImage = UIImage.imageWithCGImage(cgImage)

        dispatch_async(dispatch_get_main_queue()) {
            // Implicit CALayer animations on `contents` would smear one frame into
            // the next; a viewfinder must cut, not dissolve.
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            imageView?.image = uiImage
            CATransaction.commit()
        }
    }

    /** Bakes strength into the cube and wraps it, reusing the last result when nothing moved. */
    private fun prepareCube(cube: CubeLut, intensity: Float): NSData? {
        val key = cube to intensity
        if (preparedFor.first === key.first && preparedFor.second == key.second) {
            return preparedCubeData
        }
        val mixed = cube.mixedWithIdentity((intensity / 100f).coerceIn(0f, 2f))
        preparedCubeSize = mixed.size
        preparedCubeData = ViewfinderFilters.cubeData(mixed)
        preparedFor = key
        return preparedCubeData
    }

    private fun withDeviceLock(target: AVCaptureDevice, block: (AVCaptureDevice) -> Unit) {
        if (!target.lockForConfiguration(null)) return
        runCatching { block(target) }
        target.unlockForConfiguration()
    }

    /** Keeps the in-flight capture delegate alive; see [capture]. */
    private var pendingPhotoDelegate: PhotoDelegate? = null
}

private fun FlashMode.toAvFlashMode(): AVCaptureFlashMode = when (this) {
    FlashMode.OFF -> AVCaptureFlashModeOff
    FlashMode.AUTO -> AVCaptureFlashModeAuto
    FlashMode.ON -> AVCaptureFlashModeOn
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class FrameDelegate(
    private val onFrame: (CMSampleBufferRef?) -> Unit,
) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection,
    ) {
        onFrame(didOutputSampleBuffer)
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class PhotoDelegate(
    private val onPhoto: (NSData?) -> Unit,
) : NSObject(), AVCapturePhotoCaptureDelegateProtocol {

    override fun captureOutput(
        output: AVCapturePhotoOutput,
        didFinishProcessingPhoto: AVCapturePhoto,
        error: NSError?,
    ) {
        onPhoto(if (error != null) null else didFinishProcessingPhoto.fileDataRepresentation())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun CameraViewfinder(camera: FilmrollCamera, modifier: Modifier) {
    UIKitView(
        factory = { camera.createPreviewView() },
        modifier = modifier,
    )

    DisposableEffect(Unit) {
        camera.startSession()
        onDispose { camera.stopSession() }
    }
}
