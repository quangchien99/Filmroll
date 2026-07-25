package com.filmroll.camera.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS distinguishes "not determined" from "denied" natively, so the three states
 * map exactly and the screen never has to guess.
 */
private class IosCameraPermissionState(
    private val state: MutableState<CameraPermissionStatus>,
) : CameraPermissionState {

    override val status: CameraPermissionStatus get() = state.value

    override fun request() {
        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
            // The callback lands on an arbitrary queue; snapshot state is main-thread.
            dispatch_async(dispatch_get_main_queue()) {
                state.value = if (granted) {
                    CameraPermissionStatus.GRANTED
                } else {
                    CameraPermissionStatus.DENIED
                }
            }
        }
    }
}

@Composable
actual fun rememberCameraPermission(): CameraPermissionState {
    val state = remember { mutableStateOf(currentAuthorization()) }
    return remember { IosCameraPermissionState(state) }
}

actual fun openAppSettings() {
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
    val application = UIApplication.sharedApplication
    if (application.canOpenURL(url)) {
        application.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}

private fun currentAuthorization(): CameraPermissionStatus =
    when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
        AVAuthorizationStatusAuthorized -> CameraPermissionStatus.GRANTED
        AVAuthorizationStatusNotDetermined -> CameraPermissionStatus.UNKNOWN
        else -> CameraPermissionStatus.DENIED
    }
