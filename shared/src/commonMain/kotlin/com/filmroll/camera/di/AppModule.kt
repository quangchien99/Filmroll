package com.filmroll.camera.di


import com.filmroll.camera.data.source.filmRepoModule
import com.filmroll.camera.data.source.local.appDBModule
import com.filmroll.camera.data.source.local.filmLocalDataSourceModule
import com.filmroll.camera.data.source.local.settingsStorageImplModule
import com.filmroll.camera.data.source.network.filmNetworkDataSourceModule
import com.filmroll.camera.data.source.network.httpClientModule
import com.filmroll.camera.data.source.settingsRepoModule
import com.filmroll.camera.lut.lutDownloadManagerModule
import com.filmroll.camera.screens.language.languageScreenModule
import com.filmroll.camera.screens.onboarding.onboardingScreenModule
import com.filmroll.camera.screens.settings.settingsScreenModel
import com.filmroll.camera.screens.splash.splashScreenModule
import com.filmroll.camera.screens.home.homeScreenModule

/**
 * DI modules
 */
fun appModule() = listOf(
    homeScreenModule,
    httpClientModule,
    appDBModule,
    filmLocalDataSourceModule,
    filmNetworkDataSourceModule,
    filmRepoModule,
    splashScreenModule,
    languageScreenModule,
    onboardingScreenModule,
    settingsScreenModel,
    settingsRepoModule,
    settingsStorageImplModule,
    lutDownloadManagerModule,
)