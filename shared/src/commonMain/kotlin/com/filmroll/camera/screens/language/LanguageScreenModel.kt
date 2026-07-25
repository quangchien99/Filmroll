package com.filmroll.camera.screens.language

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.filmroll.camera.data.source.SettingsRepository
import com.filmroll.camera.i18n.AppLanguage
import com.filmroll.camera.util.applyAppLanguage
import com.filmroll.camera.util.deviceLanguageTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.dsl.module

val languageScreenModule = module {
    factory { LanguageScreenModel(get()) }
}

data class LanguageUiState(
    val query: String = "",
    val selected: AppLanguage = AppLanguage.DEFAULT,
    val suggested: List<AppLanguage> = emptyList(),
    val others: List<AppLanguage> = emptyList(),
    /** Flips once the choice is persisted, which is the screen's cue to navigate on. */
    val applied: Boolean = false,
    /** True when the platform could not swap the locale live and needs a relaunch. */
    val restartRequired: Boolean = false,
)

class LanguageScreenModel(private val repository: SettingsRepository) : ScreenModel {

    private val _uiState = MutableStateFlow(LanguageUiState())
    val uiState: StateFlow<LanguageUiState> = _uiState.asStateFlow()

    init {
        val stored = repository.getSettings().languageTag
        val initial = AppLanguage.fromTag(stored)
            ?: AppLanguage.fromDeviceTag(deviceLanguageTag())
            ?: AppLanguage.DEFAULT
        _uiState.update { it.copy(selected = initial) }
        rebuildLists()
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        rebuildLists()
    }

    fun onLanguageSelected(language: AppLanguage) {
        _uiState.update { it.copy(selected = language) }
        rebuildLists()
    }

    fun confirmSelection() {
        screenModelScope.launch {
            val language = _uiState.value.selected
            repository.getSettings().apply {
                languageTag = language.tag
                isLanguageChosen = true
            }
            val appliedLive = applyAppLanguage(language.tag)
            _uiState.update { it.copy(applied = true, restartRequired = !appliedLive) }
        }
    }

    private fun rebuildLists() {
        _uiState.update { state ->
            val query = state.query.trim()
            val matches = { language: AppLanguage ->
                query.isEmpty() ||
                    language.endonym.contains(query, ignoreCase = true) ||
                    language.englishName.contains(query, ignoreCase = true) ||
                    language.tag.contains(query, ignoreCase = true)
            }
            // The current pick is always pinned to the top so it never scrolls out of reach.
            val suggested = (listOf(state.selected) + AppLanguage.SUGGESTED)
                .distinct()
                .filter(matches)
            val others = AppLanguage.entries
                .filter { it !in suggested }
                .sortedBy { it.englishName }
                .filter(matches)
            state.copy(suggested = suggested, others = others)
        }
    }
}
