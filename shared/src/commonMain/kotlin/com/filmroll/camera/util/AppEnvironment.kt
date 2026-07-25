package com.filmroll.camera.util

/** True for developer builds; gates the destructive debug tools in Settings. */
expect val isDebugBuild: Boolean

/** The device's preferred language tag, e.g. `pt-BR`. */
expect fun deviceLanguageTag(): String

/**
 * Switches the app's UI language to [tag] (or back to the device default when null).
 *
 * @return true when the running UI picked the new locale up immediately; false means the
 *   change is stored but only takes effect after the user relaunches the app.
 */
expect fun applyAppLanguage(tag: String?): Boolean

/** Relaunches the app from its launcher entry point. No-op where the platform forbids it. */
expect fun restartApp()
