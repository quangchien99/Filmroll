package com.filmroll.camera.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Bridge for the runtime POST_NOTIFICATIONS prompt, which needs an Activity that the shared
 * module cannot see. `MainActivity` installs [requester] while it is alive.
 */
object NotificationPermission {

    var requester: ((onResult: (Boolean) -> Unit) -> Unit)? = null

    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    suspend fun request(context: Context): Boolean {
        if (isGranted(context)) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Pre-13 there is no runtime permission: notifications were turned off in system
            // settings, and only the user can turn them back on.
            return false
        }
        val requestPermission = requester ?: return false
        return suspendCancellableCoroutine { continuation ->
            requestPermission { granted -> continuation.resume(granted) }
        }
    }
}
