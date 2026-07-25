package com.filmroll.camera.android

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.filmroll.camera.App
import com.filmroll.camera.notification.NotificationPermission

/** Action fired by the launcher "Uninstall" shortcut (see res/xml/shortcuts.xml). */
const val ACTION_UNINSTALL = "com.filmroll.camera.action.UNINSTALL"

class MainActivity : AppCompatActivity() {

    private var onPermissionResult: ((Boolean) -> Unit)? = null

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            onPermissionResult?.invoke(granted)
            onPermissionResult = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == ACTION_UNINSTALL) {
            startUninstall()
            return
        }

        // The shared module owns the daily-reminder logic but cannot reach an Activity, so
        // lend it this one for as long as it is alive.
        NotificationPermission.requester = { onResult ->
            onPermissionResult = onResult
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        enableEdgeToEdge()
        setContent {
            App()
        }
    }

    override fun onDestroy() {
        NotificationPermission.requester = null
        super.onDestroy()
    }

    /** Hands the user straight to the system uninstall dialog, then gets out of the way. */
    private fun startUninstall() {
        startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}
