package com.filmroll.camera.theme

import androidx.compose.ui.graphics.Color

/**
 * The "Darkroom" palette.
 *
 * Two ideas drive it. Neutrals are warm — every grey is pulled a few degrees toward
 * amber so the chrome reads as paper and developer tray rather than as a phone UI.
 * Accents come off a film box: safelight amber for the primary action, a deep
 * darkroom red for favourites and destructive moments, and a muted sage that only
 * ever plays a supporting role.
 *
 * The editor canvas is deliberately *not* part of the scheme — it stays near-black
 * in both themes so the photo is judged against a neutral surround. See
 * [FilmrollTokens] in Theme.kt.
 */

// ---------------------------------------------------------------------------
// Dark — the app's home turf.
// ---------------------------------------------------------------------------

val primaryDark = Color(0xFFFFB877)
val onPrimaryDark = Color(0xFF48240A)
val primaryContainerDark = Color(0xFF6A3A14)
val onPrimaryContainerDark = Color(0xFFFFDCC0)

val secondaryDark = Color(0xFFDDC5AC)
val onSecondaryDark = Color(0xFF3E2E20)
val secondaryContainerDark = Color(0xFF564435)
val onSecondaryContainerDark = Color(0xFFFAE1C7)

val tertiaryDark = Color(0xFFB7CDB0)
val onTertiaryDark = Color(0xFF233522)
val tertiaryContainerDark = Color(0xFF394C37)
val onTertiaryContainerDark = Color(0xFFD3E9CB)

val errorDark = Color(0xFFFFB4A8)
val onErrorDark = Color(0xFF690004)
val errorContainerDark = Color(0xFF93000C)
val onErrorContainerDark = Color(0xFFFFDAD5)

val backgroundDark = Color(0xFF14120F)
val onBackgroundDark = Color(0xFFF1EAE1)
val surfaceDark = Color(0xFF14120F)
val onSurfaceDark = Color(0xFFF1EAE1)
val surfaceVariantDark = Color(0xFF3B342C)
val onSurfaceVariantDark = Color(0xFFC5BAAC)
val outlineDark = Color(0xFF7C7264)
val outlineVariantDark = Color(0xFF3B342C)
val scrimDark = Color(0xFF000000)
val inverseSurfaceDark = Color(0xFFF1EAE1)
val inverseOnSurfaceDark = Color(0xFF322C25)
val inversePrimaryDark = Color(0xFF8A5220)

val surfaceDimDark = Color(0xFF0D0B09)
val surfaceBrightDark = Color(0xFF3A342C)
val surfaceContainerLowestDark = Color(0xFF0A0908)
val surfaceContainerLowDark = Color(0xFF1B1815)
val surfaceContainerDark = Color(0xFF211E1A)
val surfaceContainerHighDark = Color(0xFF2C2823)
val surfaceContainerHighestDark = Color(0xFF37322C)

// ---------------------------------------------------------------------------
// Light — warm paper, not clinical white.
// ---------------------------------------------------------------------------

val primaryLight = Color(0xFF8A5220)
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFFFFDCC0)
val onPrimaryContainerLight = Color(0xFF301500)

val secondaryLight = Color(0xFF6E5B4B)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFFAE1C7)
val onSecondaryContainerLight = Color(0xFF261A0E)

val tertiaryLight = Color(0xFF506350)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFD3E9CB)
val onTertiaryContainerLight = Color(0xFF0E1F11)

val errorLight = Color(0xFFA3322A)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFFFDAD5)
val onErrorContainerLight = Color(0xFF410001)

val backgroundLight = Color(0xFFFCF7F1)
val onBackgroundLight = Color(0xFF1D1A16)
val surfaceLight = Color(0xFFFCF7F1)
val onSurfaceLight = Color(0xFF1D1A16)
val surfaceVariantLight = Color(0xFFEFE0D2)
val onSurfaceVariantLight = Color(0xFF52463A)
val outlineLight = Color(0xFF847668)
val outlineVariantLight = Color(0xFFD6C5B5)
val scrimLight = Color(0xFF000000)
val inverseSurfaceLight = Color(0xFF322E2A)
val inverseOnSurfaceLight = Color(0xFFF6EEE6)
val inversePrimaryLight = Color(0xFFFFB877)

val surfaceDimLight = Color(0xFFDFD8CF)
val surfaceBrightLight = Color(0xFFFCF7F1)
val surfaceContainerLowestLight = Color(0xFFFFFFFF)
val surfaceContainerLowLight = Color(0xFFF8F1E9)
val surfaceContainerLight = Color(0xFFF3EBE2)
val surfaceContainerHighLight = Color(0xFFEDE4DA)
val surfaceContainerHighestLight = Color(0xFFE7DED3)

// ---------------------------------------------------------------------------
// Editor canvas — identical in both themes on purpose.
// ---------------------------------------------------------------------------

/** Backdrop the photo sits on. Near-black so the eye has no colour cast to fight. */
val canvasBlack = Color(0xFF0B0A09)

/** Chrome floating over the canvas: dark enough to read against a bright photo. */
val canvasChrome = Color(0xE01A1815)

/** Text and icons on top of [canvasBlack] / [canvasChrome]. */
val onCanvas = Color(0xFFF3EDE5)
val onCanvasVariant = Color(0xFFA9A096)

/** Safelight red — favourites, and the "recording" feel of a live edit. */
val safelight = Color(0xFFFF5A47)
