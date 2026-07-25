package com.filmroll.camera.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.filmroll.camera.util.AppContext

/**
 * Android's permission API cannot tell "never asked" apart from "refused" without
 * an Activity, and the shared module has none. Rather than reach for one, this
 * treats *any* not-granted state as [CameraPermissionStatus.UNKNOWN] and lets the
 * launcher settle it: a first-time user sees the system prompt, and a user who
 * has already refused gets an immediate `false` back with no prompt shown, which
 * lands them on the "open settings" pane. Same two outcomes, no Activity.
 */
private class AndroidCameraPermissionState(
    private val state: MutableState<CameraPermissionStatus>,
    private val onRequest: () -> Unit,
) : CameraPermissionState {

    override val status: CameraPermissionStatus get() = state.value

    override fun request() = onRequest()
}

@Composable
actual fun rememberCameraPermission(): CameraPermissionState {
    val context = LocalContext.current
    val state = remember {
        mutableStateOf(
            if (context.hasCameraPermission()) {
                CameraPermissionStatus.GRANTED
            } else {
                CameraPermissionStatus.UNKNOWN
            },
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        state.value = if (granted) {
            CameraPermissionStatus.GRANTED
        } else {
            CameraPermissionStatus.DENIED
        }
    }

    return remember(launcher) {
        AndroidCameraPermissionState(state) { launcher.launch(Manifest.permission.CAMERA) }
    }
}

actual fun openAppSettings() {
    val context = AppContext.get() ?: return
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
