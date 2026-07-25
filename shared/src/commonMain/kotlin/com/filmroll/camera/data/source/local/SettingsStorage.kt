package com.filmroll.camera.data.source.local

import com.filmroll.camera.screens.settings.DefaultPickerType
import com.filmroll.camera.screens.settings.ExportFormat
import kotlinx.coroutines.flow.Flow


interface SettingsStorage {

    var exportQuality: Int
    var exportFormat: ExportFormat
    var defaultPicker: DefaultPickerType
    var hasShownLutDownloadDialog: Boolean

    /** True once the user has picked a language on the first-run language screen. */
    var isLanguageChosen: Boolean

    /** BCP-47 tag of the chosen language, or null while the device locale is being followed. */
    var languageTag: String?

    /** True once the user has walked through (or skipped) onboarding. */
    var isOnboardingFinished: Boolean

    var themeMode: ThemeMode

    var dailyReminderEnabled: Boolean

    fun defaultPickerListener(callback: (DefaultPickerType) -> Unit)

    /** Emits on every write so the whole app can re-theme without a restart. */
    fun themeModeFlow(): Flow<ThemeMode>

    fun cleanStorage()
}

enum class StorageKeys {
    EXPORT_QUALITY,
    EXPORT_FORMAT,
    HAS_SHOWN_LUT_DOWNLOAD_DIALOG,
    DEFAULT_PICKER,
    IS_LANGUAGE_CHOSEN,
    LANGUAGE_TAG,
    IS_ONBOARDING_FINISHED,
    THEME_MODE,
    DAILY_REMINDER_ENABLED;

    val key get() = this.name
}
