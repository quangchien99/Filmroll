package com.filmroll.camera.util

import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

private const val APPLE_LANGUAGES_KEY = "AppleLanguages"

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
actual val isDebugBuild: Boolean
    get() = kotlin.native.Platform.isDebugBinary

actual fun deviceLanguageTag(): String {
    val locale = NSLocale.currentLocale
    val language = locale.languageCode
    val region = locale.countryCode
    return if (region.isNullOrBlank()) language else "$language-$region"
}

actual fun applyAppLanguage(tag: String?): Boolean {
    val defaults = NSUserDefaults.standardUserDefaults
    if (tag.isNullOrBlank()) {
        defaults.removeObjectForKey(APPLE_LANGUAGES_KEY)
    } else {
        defaults.setObject(listOf(tag), APPLE_LANGUAGES_KEY)
    }
    defaults.synchronize()
    // iOS only reads AppleLanguages during launch, so the swap needs a relaunch.
    return false
}

/** iOS apps may not terminate themselves, so the user has to relaunch by hand. */
actual fun restartApp() = Unit
