package com.filmroll.camera.capture

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

/**
 * The capture device, as the shared code sees it.
 *
 * One object owns the session, the live look and the shutter, because on both
 * platforms those three are entangled: the LUT is uploaded into the same render
 * pipeline the preview frames flow through, and the still comes out of the same
 * session. Splitting them would mean a controller and a renderer that had to be
 * kept in lockstep by hand.
 *
 * Deliberately *not* here: anything about the surface the frames land on. That is
 * a `GLSurfaceView` on Android and a `UIImageView` fed by Core Image on iOS, and
 * neither has a meaningful shared shape — [CameraViewfinder] hides the difference.
 *
 * Instances are created by the screen model and must be [release]d when it is
 * disposed; a leaked session keeps the camera indicator lit.
 */
expect class FilmrollCamera() {

    val status: StateFlow<CameraStatus>

    /** Swaps the LUT and adjustments the preview renders through. Cheap; call it per slider frame. */
    fun setLook(look: LiveLook)

    fun setLens(lens: LensFacing)

    /** Flash for the still only — see [FlashMode]. */
    fun setFlash(mode: FlashMode)

    /** Absolute zoom ratio, clamped to the device's range. */
    fun setZoom(ratio: Float)

    /** Exposure compensation in EV, clamped to the device's range. */
    fun setExposureEv(ev: Float)

    /**
     * Focus and meter at a point in normalized viewfinder coordinates
     * (0,0 = top-left, 1,1 = bottom-right). Silently ignored by devices that
     * can't focus.
     */
    fun focusAt(x: Float, y: Float)

    /**
     * Fires the shutter and returns encoded JPEG bytes, or null if the capture
     * failed. The bytes are the *unfiltered* sensor image — the film look is
     * applied afterwards by the same Skia pipeline the editor uses, at full
     * resolution and full grain quality, so the saved frame is better than the
     * viewfinder rather than merely equal to it.
     */
    suspend fun capture(): ByteArray?

    /** Tears down the session. Safe to call more than once. */
    fun release()
}

/**
 * The live viewfinder. Attaches [camera] to a platform surface for as long as it
 * is composed, and detaches on dispose.
 */
@Composable
expect fun CameraViewfinder(camera: FilmrollCamera, modifier: Modifier = Modifier)
