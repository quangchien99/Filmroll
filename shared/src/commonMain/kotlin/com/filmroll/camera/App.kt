package com.filmroll.camera

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.util.DebugLogger
import com.filmroll.camera.data.source.SettingsRepository
import com.filmroll.camera.data.source.local.ThemeMode
import com.filmroll.camera.screens.splash.SplashScreen
import com.filmroll.camera.theme.AppTheme
import org.koin.compose.KoinContext
import org.koin.mp.KoinPlatform.getKoin

/**
 * Entry point for the shared module and the App.
 *
 * Every cold start lands on [SplashScreen], which decides whether the user still owes us a
 * language choice or an onboarding run before it hands over to Home.
 */
@Composable
fun App() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .logger(DebugLogger())
            .build()
    }
    KoinContext {
        val settings = remember { getKoin().get<SettingsRepository>().getSettings() }
        // Collected rather than read once, so flipping the switch in Settings re-themes the
        // app immediately instead of only on the next launch.
        val themeMode by settings.themeModeFlow().collectAsState(initial = settings.themeMode)
        val darkTheme = when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

        AppTheme(darkTheme = darkTheme) {
            Navigator(screen = SplashScreen()) { navigator ->
                SlideTransition(navigator = navigator)
            }
        }
    }
}
