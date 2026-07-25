package com.filmroll.camera.capture

import com.filmroll.camera.image.CubeLut
import com.filmroll.camera.image.ImageAdjustments

enum class LensFacing { BACK, FRONT }

/** Flash behaviour for the still capture. The viewfinder itself never lights up. */
enum class FlashMode { OFF, AUTO, ON }

enum class CameraPermissionStatus {
    /** Not asked yet, or still resolving. */
    UNKNOWN,
    GRANTED,
    /** Refused. May or may not be re-askable — the OS decides. */
    DENIED,
}

/**
 * What the viewfinder is rendering.
 *
 * The whole point of shooting inside Filmroll rather than in the system camera is
 * that you frame the shot through the film, not through a neutral feed you then
 * hope will suit a stock later. So this rides along with every frame.
 *
 * [adjustments] is the same type the editor uses, and it travels with the capture
 * into the editor untouched — what you dialled in at the viewfinder is exactly
 * where the editor's sliders start. The live renderers implement the subset that
 * is cheap enough for 30-60 fps (LUT strength, contrast, saturation, warmth,
 * grain); exposure is deliberately *not* in that subset because the camera has a
 * real exposure control and faking it in post throws away highlight data the
 * sensor could have kept.
 */
data class LiveLook(
    val cube: CubeLut? = null,
    val filmName: String? = null,
    val adjustments: ImageAdjustments = ImageAdjustments(),
)

/**
 * Everything the UI needs to know about the device behind the viewfinder.
 *
 * Exposure is expressed in EV rather than in the platform's own units — Android
 * counts in integer steps of `exposureCompensationStep`, iOS takes a float bias —
 * so the shared slider can speak the photographer's language and each actual
 * converts on the way in.
 */
data class CameraStatus(
    /** True once frames are actually flowing. */
    val isReady: Boolean = false,
    val lens: LensFacing = LensFacing.BACK,
    val hasFrontLens: Boolean = true,
    val hasFlashUnit: Boolean = false,
    val zoom: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val exposureEv: Float = 0f,
    val minExposureEv: Float = 0f,
    val maxExposureEv: Float = 0f,
    /** Non-null when the session failed to start; the screen shows it instead of a black rectangle. */
    val errorMessage: String? = null,
) {
    val canZoom: Boolean get() = maxZoom > minZoom + 0.01f
    val canAdjustExposure: Boolean get() = maxExposureEv > minExposureEv + 0.01f
}
