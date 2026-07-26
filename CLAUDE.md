# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Filmroll ("Filmroll: Vintage Camera", applicationId `com.filmroll.camera`) is a cross-platform
mobile app built with Kotlin Multiplatform and Compose Multiplatform. It applies film-like 3D LUTs
and image adjustments to photos on Android and iOS.

It is a private project, built for personal use and not distributed.

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
                                                                            ↕
                                                                       CameraScreen
```

Returning users skip straight to `HomeScreen`. `LanguageScreen(isFirstLaunch = false)` is the
Settings entry and pops instead of continuing. `CameraScreen` is pushed from the editor and pops
back; because the editor is the navigator root it cannot take the shot as a constructor argument,
so the capture travels over `CaptureRelay` (a Koin `single`) and `HomeScreenModel` consumes it.

### Image processing pipeline
1. **Load** — image picked via FileKit *or shot in the viewfinder*, copied into the app cache
   (`FileHandler`)
2. **Process** — `SkiaImageProcessor` applies the LUT and adjustments via runtime shaders
3. **Thumbnail** — the user's own photo is re-rendered through each LUT on the visible shelf,
   so the film strip previews the actual picture rather than a stock sample
4. **Export** — full resolution to the gallery, JPEG or source format with EXIF

`image/CubeLut.kt` parses `.cube` files and packs them for each consumer (`toRgba8()` for Skia and
GLES, `toRgbaFloats()` for Core Image). It is the single source of truth — three renderers draw the
same film, and a stock must not look different depending on which one drew it.

### Live camera
`capture/` is an `expect class FilmrollCamera` plus a `CameraViewfinder` composable and a
permission state. Both platforms deliberately skip the stock preview widget (`PreviewView`,
`AVCaptureVideoPreviewLayer`): they render the sensor feed themselves and leave no seam for a LUT.

- **Android** — CameraX drives the session; `Preview` is pointed at a `SurfaceTexture` owned by
  `ViewfinderRenderer`, a `GLSurfaceView` renderer that samples the external OES texture through
  the packed LUT texture in one GLES 2.0 pass (`ViewfinderShader`, a narrow port of the SkSL with
  identical constants). Orientation is handled entirely in texture coordinates — rotating the quad
  looks simpler and is wrong, because NDC is already stretched to the viewport.
- **iOS** — `AVCaptureVideoDataOutput` frames go through a Core Image chain
  (`CIColorCubeWithColorSpace` in sRGB, `CIColorControls`, `CIColorMatrix`) and land in a
  `UIImageView`. Strength is baked into the cube via `CubeLut.mixedWithIdentity()` rather than
  blended per frame, because Core Image has no primitive that extrapolates past a full-strength LUT.

The viewfinder renders LUT strength, contrast, saturation, warmth and grain — the subset that holds
30-60 fps. Exposure is *not* in it: `ViewfinderTool.EXPOSURE` biases the sensor through the camera's
own exposure compensation, so a brightened frame carries real data instead of a stretched copy.
Shadows, highlights and fringing are editor-only.

The still is captured **unfiltered** and pushed through the same `SkiaImageProcessor` the editor
exports with, at full resolution and full grain quality. The preview is never the source of the
saved frame — keep it that way.

### Design system
`theme/` holds the "Darkroom" system: a warm-neutral palette with a safelight-amber primary
(`Color.kt`), a hand-tuned type scale plus the `readoutTextStyle` / `eyebrowTextStyle` used by the
editor (`Type.kt`), and `Theme.kt`, which wires those into `AppTheme` and adds what Material has no
slot for — `FilmrollTokens` (the fixed near-black editor canvas and its on-colours, reachable via
`FilmrollTheme.tokens`) and the two shared motion specs, `emphatic()` for finger-driven changes and
`standard()` for everything else. The canvas is deliberately identical in light and dark so the
photo is always judged against a neutral surround.

`view/` holds the reusable pieces: `ToolSlider` (the editor's only slider — fill drawn from the
neutral point outward, snap-to-default detent, haptic tick), `FilmStrip`, `FilmBrowserSheet`,
`Chrome.kt` (panels, icon buttons, `SegmentedTabs`, chips), `SettingsUi.kt` (grouped-card rows) and
`AppDialog`. There is no blur anywhere — Compose's blur is Android 12+ only and absent on iOS, so
translucent fills stand in for glass.

### Editor layout
`HomeScreen` is a full-bleed canvas with floating chrome, not a form. `AdjustmentTool` describes
every adjustment once (range, neutral value, icon, label, read/write accessors) and the adjust rail
renders generically from it — adding an adjustment means adding an enum entry and a string. Rules
worth preserving: one control expanded at a time; compare is press-and-hold (`setShowOriginal`),
never a toggle; and nothing blocks the UI except a full-resolution export.

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
- **Permissions**: gallery access is platform-specific. Camera access is `CameraPermissionState`;
  Android cannot tell "never asked" from "refused" without an Activity, so it reports both as
  `UNKNOWN` and lets the launcher settle it — a prior refusal returns `false` with no prompt
- **Build**: Gradle's configuration cache lock means only one build at a time
