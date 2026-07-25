package com.filmroll.camera.screens.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.filmroll.camera.resources.Res
import com.filmroll.camera.resources.app_full_name
import com.filmroll.camera.resources.ic_film_negative_color
import com.filmroll.camera.resources.splash_tagline
import com.filmroll.camera.screens.home.HomeScreen
import com.filmroll.camera.screens.language.LanguageScreen
import com.filmroll.camera.screens.onboarding.OnboardingScreen
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val LOGO_ANIM_DURATION_MS = 500

/**
 * First screen on every cold start. Shows the brand for a beat while it decides whether the
 * user still owes us a language choice or an onboarding run.
 */
class SplashScreen : Screen {

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<SplashScreenModel>()
        val destination by vm.destination.collectAsState()

        LaunchedEffect(destination) {
            when (destination) {
                SplashDestination.Undecided -> Unit
                SplashDestination.Language -> navigator.replaceAll(LanguageScreen(isFirstLaunch = true))
                SplashDestination.Onboarding -> navigator.replaceAll(OnboardingScreen())
                SplashDestination.Home -> navigator.replaceAll(HomeScreen())
            }
        }

        SplashContent()
    }
}

@Composable
private fun SplashContent(modifier: Modifier = Modifier) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    0.0f to colorScheme.surfaceContainerLowest,
                    0.52f to colorScheme.surfaceContainer,
                    1.0f to colorScheme.primaryContainer,
                ),
            )
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to colorScheme.primary.copy(alpha = 0.38f),
                        0.55f to colorScheme.secondary.copy(alpha = 0.18f),
                        1.0f to Color.Transparent,
                        center = Offset(size.width / 2f, size.height * 0.42f),
                        radius = size.minDimension * 0.7f,
                    ),
                    radius = size.minDimension * 0.7f,
                    center = Offset(size.width / 2f, size.height * 0.42f),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = scaleIn(
                initialScale = 0.4f,
                animationSpec = tween(durationMillis = LOGO_ANIM_DURATION_MS),
            ) + fadeIn(animationSpec = tween(durationMillis = LOGO_ANIM_DURATION_MS)),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(
                    modifier = Modifier.size(132.dp),
                    shape = CircleShape,
                    color = colorScheme.surface.copy(alpha = 0.9f),
                    contentColor = colorScheme.primary,
                    tonalElevation = 8.dp,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_film_negative_color),
                        contentDescription = null,
                        modifier = Modifier.padding(32.dp),
                    )
                }

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(Res.string.app_full_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(Res.string.splash_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
