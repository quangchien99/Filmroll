package com.filmroll.camera.util

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale
import kotlin.system.exitProcess

actual val isDebugBuild: Boolean
    get() {
        val flags = AppContext.get()?.applicationInfo?.flags ?: return false
        return flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

actual fun deviceLanguageTag(): String = Locale.getDefault().toLanguageTag()

actual fun applyAppLanguage(tag: String?): Boolean {
    // Per-app locales; AppCompat recreates the activity so Compose Resources re-resolve.
    AppCompatDelegate.setApplicationLocales(
        if (tag.isNullOrBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
    )
    return true
}

actual fun restartApp() {
    val context = AppContext.get() ?: return
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    context.startActivity(Intent.makeRestartActivityTask(launchIntent.component))
    exitProcess(0)
}
