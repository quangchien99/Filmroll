package com.filmroll.camera.util

actual class Platform actual constructor(private val appContext: AppContext) {
    actual fun getAppVersion(): String {
        val packageManager = appContext.get()?.packageManager
        val packageName = appContext.get()?.packageName
        return packageManager?.getPackageInfo(packageName ?: "", 0)?.versionName ?: "Unknown"
    }
}