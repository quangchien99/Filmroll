package com.filmroll.camera.screens.settings

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.filmroll.camera.data.source.SettingsRepository
import com.filmroll.camera.data.source.local.ThemeMode
import com.filmroll.camera.i18n.AppLanguage
import com.filmroll.camera.notification.DailyReminder
import com.filmroll.camera.resources.Res
import com.filmroll.camera.resources.debug_clear_data_done
import com.filmroll.camera.resources.export_format_jpeg
import com.filmroll.camera.resources.export_format_original
import com.filmroll.camera.resources.files
import com.filmroll.camera.resources.images
import com.filmroll.camera.resources.notification_permission_denied
import com.filmroll.camera.data.source.FilmRepository
import com.filmroll.camera.util.clearAppCache
import com.filmroll.camera.util.WhileUiSubscribed
import com.filmroll.camera.util.deviceLanguageTag
import com.filmroll.camera.util.isDebugBuild
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.koin.dsl.module

val settingsScreenModel = module {
    factory { SettingsScreenModel(get(), get()) }
}

data class SettingsUiState(
    val exportQuality: Int = 0,
    val exportFormat: ExportFormat = ExportFormat.JPEG,
    val defaultPicker: DefaultPickerType = DefaultPickerType.IMAGES,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dailyReminderEnabled: Boolean = false,
    val language: AppLanguage = AppLanguage.DEFAULT,
    val showDebugTools: Boolean = false,
    val userMessage: String? = null,
)

enum class DefaultPickerType {
    IMAGES,
    FILES;

    fun getString(): StringResource {
        return when (this) {
            IMAGES -> Res.string.images
            FILES -> Res.string.files
        }
    }
}

enum class ExportFormat {
    JPEG,
    ORIGINAL;

    fun getString(): StringResource {
        return when (this) {
            JPEG -> Res.string.export_format_jpeg
            ORIGINAL -> Res.string.export_format_original
        }
    }
}

class SettingsScreenModel(
    val repository: SettingsRepository,
    private val filmRepository: FilmRepository,
) : ScreenModel {

    private val settings get() = repository.getSettings()

    private val _exportQuality = MutableStateFlow(settings.exportQuality)
    private val _exportFormat = MutableStateFlow(settings.exportFormat)
    private val _defaultPicker = MutableStateFlow(settings.defaultPicker)
    private val _themeMode = MutableStateFlow(settings.themeMode)
    private val _dailyReminder = MutableStateFlow(settings.dailyReminderEnabled)
    private val _language = MutableStateFlow(currentLanguage())
    private val _userMessage = MutableStateFlow<String?>(null)

    /** Raised once the debug wipe has finished, so the screen can relaunch the app. */
    private val _dataCleared = MutableStateFlow(false)
    val dataCleared: StateFlow<Boolean> = _dataCleared.asStateFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(_exportQuality, _exportFormat, _defaultPicker, ::Triple),
        combine(_themeMode, _dailyReminder, _language, ::Triple),
        _userMessage,
    ) { editing, appearance, userMessage ->
        SettingsUiState(
            exportQuality = editing.first,
            exportFormat = editing.second,
            defaultPicker = editing.third,
            themeMode = appearance.first,
            dailyReminderEnabled = appearance.second,
            language = appearance.third,
            showDebugTools = isDebugBuild,
            userMessage = userMessage,
        )
    }.stateIn(
        scope = screenModelScope,
        started = WhileUiSubscribed,
        initialValue = SettingsUiState(showDebugTools = isDebugBuild),
    )

    /** Re-read on every resume so a language change made on the picker shows up here. */
    fun refresh() {
        _language.value = currentLanguage()
        _themeMode.value = settings.themeMode
    }

    fun snackbarMessageShown() {
        _userMessage.value = null
    }

    fun updateExportQualitySettings(quality: Float) {
        screenModelScope.launch {
            settings.exportQuality = quality.toInt()
            _exportQuality.emit(quality.toInt())
        }
    }

    fun updateExportFormatSettings(format: ExportFormat) {
        screenModelScope.launch {
            settings.exportFormat = format
            _exportFormat.emit(format)
        }
    }

    fun updateDefaultPickerSettings(defaultPicker: DefaultPickerType) {
        screenModelScope.launch {
            settings.defaultPicker = defaultPicker
            _defaultPicker.emit(defaultPicker)
        }
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        screenModelScope.launch {
            settings.themeMode = themeMode
            _themeMode.emit(themeMode)
        }
    }

    fun setDailyReminderEnabled(enabled: Boolean) {
        screenModelScope.launch {
            val scheduled = DailyReminder.setEnabled(enabled)
            // Only remember the preference as "on" if the OS actually let us schedule it,
            // otherwise the switch would lie about a reminder that will never arrive.
            settings.dailyReminderEnabled = scheduled
            _dailyReminder.emit(scheduled)
            if (enabled && !scheduled) {
                _userMessage.emit(getString(Res.string.notification_permission_denied))
            }
        }
    }

    /** Debug-only: wipes preferences, local tables and cached files so onboarding replays. */
    fun clearAllData() {
        screenModelScope.launch {
            DailyReminder.setEnabled(false)
            settings.cleanStorage()
            filmRepository.clearLocalData()
            clearAppCache()
            _dataCleared.emit(true)
        }
    }

    private fun currentLanguage(): AppLanguage =
        AppLanguage.fromTag(settings.languageTag)
            ?: AppLanguage.fromDeviceTag(deviceLanguageTag())
            ?: AppLanguage.DEFAULT
}
