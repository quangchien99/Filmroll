package com.filmroll.camera.screens.onboarding

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.filmroll.camera.data.source.SettingsRepository
import com.filmroll.camera.resources.Res
import com.filmroll.camera.resources.ic_film_negative_color
import com.filmroll.camera.resources.ic_film_print
import com.filmroll.camera.resources.ic_upload_24
import com.filmroll.camera.resources.onboarding_desc_1
import com.filmroll.camera.resources.onboarding_desc_2
import com.filmroll.camera.resources.onboarding_desc_3
import com.filmroll.camera.resources.onboarding_title_1
import com.filmroll.camera.resources.onboarding_title_2
import com.filmroll.camera.resources.onboarding_title_3
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.koin.dsl.module

val onboardingScreenModule = module {
    factory { OnboardingScreenModel(get()) }
}

enum class OnboardingPage(
    val illustration: DrawableResource,
    val title: StringResource,
    val description: StringResource,
) {
    FILM_LOOKS(
        illustration = Res.drawable.ic_film_negative_color,
        title = Res.string.onboarding_title_1,
        description = Res.string.onboarding_desc_1,
    ),
    ADJUSTMENTS(
        illustration = Res.drawable.ic_film_print,
        title = Res.string.onboarding_title_2,
        description = Res.string.onboarding_desc_2,
    ),
    EXPORT(
        illustration = Res.drawable.ic_upload_24,
        title = Res.string.onboarding_title_3,
        description = Res.string.onboarding_desc_3,
    ),
}

class OnboardingScreenModel(private val repository: SettingsRepository) : ScreenModel {

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    fun finishOnboarding() {
        screenModelScope.launch {
            repository.getSettings().isOnboardingFinished = true
            _finished.value = true
        }
    }
}
