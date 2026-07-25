package com.filmroll.camera.screens.splash

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.filmroll.camera.data.source.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.dsl.module

val splashScreenModule = module {
    factory { SplashScreenModel(get()) }
}

enum class SplashDestination { Undecided, Language, Onboarding, Home }

private const val SPLASH_MIN_VISIBLE_MS = 1_500L

class SplashScreenModel(private val repository: SettingsRepository) : ScreenModel {

    private val _destination = MutableStateFlow(SplashDestination.Undecided)
    val destination: StateFlow<SplashDestination> = _destination.asStateFlow()

    init {
        screenModelScope.launch {
            delay(SPLASH_MIN_VISIBLE_MS)
            val settings = repository.getSettings()
            _destination.value = when {
                !settings.isLanguageChosen -> SplashDestination.Language
                !settings.isOnboardingFinished -> SplashDestination.Onboarding
                else -> SplashDestination.Home
            }
        }
    }
}
