package com.filmroll.camera

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.util.DebugLogger
import com.filmroll.camera.screens.splash.SplashScreen
import com.filmroll.camera.theme.AppTheme
import org.koin.compose.KoinContext

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
        AppTheme {
            Navigator(screen = SplashScreen()) { navigator ->
                SlideTransition(navigator = navigator)
            }
        }
    }
}
