# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Filmroll ("Filmroll: Vintage Camera", applicationId `com.filmroll.camera`) is a cross-platform
mobile app built with Kotlin Multiplatform and Compose Multiplatform. It applies film-like 3D LUTs
and image adjustments to photos on Android and iOS.

It began as a fork of [YahiaAngelo/Film-Simulator](https://github.com/YahiaAngelo/Film-Simulator)
and has since been rebranded, repackaged and extended.

## Build Commands

### Android
```bash
./gradlew androidApp:assembleDebug      # debug APK
./gradlew androidApp:assembleRelease    # R8-minified release APK
./gradlew androidApp:installDebug       # install on a device
```

### iOS
```bash
./gradlew shared:compileKotlinIosSimulatorArm64   # fastest way to type-check iosMain
./gradlew shared:linkDebugFrameworkIosSimulatorArm64
open iosApp/iosApp.xcworkspace
```

### Cross-platform
```bash
./gradlew clean
./gradlew build
```

The first build on a clean machine downloads a JetBrains JDK 21 toolchain and the Kotlin/Native
distribution — expect 20+ minutes before any task output appears. Gradle's configuration cache is
enabled, so two builds cannot run against this project concurrently.

## Architecture

### Multiplatform structure
- **`shared/src/commonMain/`**: shared business logic, Compose UI, resources
- **`shared/src/androidMain/`**: Android actuals + the shared module's own AndroidManifest
- **`shared/src/iosMain/`**: iOS actuals
- **`androidApp/`**: Android entry point
- **`iosApp/`**: iOS entry point

Every Kotlin source lives under `com.filmroll.camera`. Compose Resources generate into
`com.filmroll.camera.resources` (pinned in `shared/build.gradle.kts`, not derived from the project
name), and SQLDelight generates into `com.filmroll.camera`.

### Core patterns
- **MVVM** with Voyager `ScreenModel`s and Kotlin flows
- **Repository pattern** over SQLDelight (local) and Ktor (network)
- **Koin** DI; every module is registered in `di/AppModule.kt` — a new screen model needs an entry
  there or `koinScreenModel<T>()` will throw at runtime

### Navigation
`App()` hosts a Voyager `Navigator` rooted at `SplashScreen`. Splash reads the stored flags and
`replaceAll`s to the first screen the user still owes us:

```
SplashScreen → LanguageScreen(isFirstLaunch = true) → OnboardingScreen → HomeScreen
```

Returning users skip straight to `HomeScreen`. `LanguageScreen(isFirstLaunch = false)` is the
Settings entry and pops instead of continuing.

### Image processing pipeline
1. **Load** — image picked via FileKit, copied into the app cache (`FileHandler`)
2. **Process** — `SkiaImageProcessor` applies the LUT and adjustments via runtime shaders
3. **Thumbnail** — generated per LUT for the picker
4. **Export** — full resolution to the gallery, JPEG or source format with EXIF

## Development Notes

### Preferences
`SettingsStorage` (multiplatform-settings) holds export options plus the flow-control flags:
`isLanguageChosen`, `languageTag`, `isOnboardingFinished`, `themeMode`, `dailyReminderEnabled`.
`themeModeFlow()` is collected in `App()`, so theme changes apply without a restart.

### Localization
Strings live in `composeResources/values/strings.xml` with `values-<qualifier>` translations for 10
further locales. **Adding a language requires both** a `values-<qualifier>` folder and an
`AppLanguage` entry — the picker only lists locales with shipped strings.

Runtime switching goes through `applyAppLanguage()`: `AppCompatDelegate.setApplicationLocales` on
Android (hence `AppCompatActivity` + an AppCompat theme; do not revert either), and `AppleLanguages`
on iOS, which returns `false` to signal that a relaunch is needed.

### Notifications
`DailyReminder` is an `expect object`. Android uses an inexact repeating AlarmManager alarm plus
`BootCompletedReceiver` to survive reboots; iOS uses a repeating `UNCalendarNotificationTrigger`.
`setEnabled(true)` returns whether the reminder was *actually* scheduled, and the Settings switch
only stores `true` when it was — do not simplify that away, or the switch will lie.

The Android POST_NOTIFICATIONS prompt needs an Activity the shared module cannot see, so
`MainActivity` installs a callback into `NotificationPermission.requester`.

### Debug tooling
`isDebugBuild` (Android: `FLAG_DEBUGGABLE`; iOS: `Platform.isDebugBinary`) gates Settings → Debug.
"Clear all app data" wipes preferences, local tables and cache, then relaunches — the relaunch waits
for the wipe to finish, since firing it inline kills the process mid-write.

### Release builds
R8 and resource shrinking are on. Keep rules are in `androidApp/proguard-rules.pro` and cover
Koin-resolved classes, the manifest-only receivers, enums round-tripped through `valueOf()`, and
skiko. Adding reflection or a new receiver usually means adding a rule.

### Common issues
- **Memory**: large bitmaps need careful recycling on Android
- **Threading**: image processing runs on `Dispatchers.IO`
- **Permissions**: gallery access is platform-specific
- **Build**: Gradle's configuration cache lock means only one build at a time
