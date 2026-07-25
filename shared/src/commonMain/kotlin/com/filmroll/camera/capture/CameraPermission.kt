package com.filmroll.camera.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * Camera permission, as a piece of Compose state.
 *
 * [status] is snapshot-backed, so a screen that reads it re-composes the moment
 * the user answers the system prompt — no manual re-check on resume.
 */
@Stable
interface CameraPermissionState {
    val status: CameraPermissionStatus

    /**
     * Shows the system prompt. On both platforms the OS only ever asks once; a
     * second call after a refusal does nothing, which is why the denied pane
     * offers [openAppSettings] instead of a retry button.
     */
    fun request()
}

@Composable
expect fun rememberCameraPermission(): CameraPermissionState

/** Opens this app's page in the system settings, where the refusal can be undone. */
expect fun openAppSettings()
