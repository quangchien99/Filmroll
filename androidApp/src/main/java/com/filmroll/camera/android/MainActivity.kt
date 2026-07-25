package com.filmroll.camera.android

import android.Manifest
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.filmroll.camera.App
import com.filmroll.camera.notification.NotificationPermission

class MainActivity : AppCompatActivity() {

    private var onPermissionResult: ((Boolean) -> Unit)? = null

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            onPermissionResult?.invoke(granted)
            onPermissionResult = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
}
