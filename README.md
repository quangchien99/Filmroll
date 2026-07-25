# Filmroll: Vintage Camera

Filmroll is a Kotlin Multiplatform app for Android and iOS that gives digital photos the look of
analog film. It applies 3D LUTs sampled from classic colour and black-and-white film stocks, lets
you fine-tune the result, and exports at full resolution — optionally keeping the original EXIF
metadata.

- **Package id:** `com.filmroll.camera`
- **Platforms:** Android (minSdk 24, target 37) and iOS 16+
- **UI:** Compose Multiplatform + Material 3

## Features

- Apply film-like 3D LUTs to any photo, with a thumbnail preview generated per LUT.
- Fine-tune exposure, contrast, shadows, highlights, saturation, temperature and grain.
- Favourite the looks you keep coming back to.
- Download the full LUT library for offline use.
- Export as JPEG, or in the source format with EXIF metadata preserved.
- First-run flow: **splash → language → onboarding → home**.
- 11 languages, switchable at runtime from Settings.
- Light / dark / follow-system theming.
- Opt-in daily reminder notification.
- Launcher "Uninstall" shortcut.
- Debug builds get a **clear all app data** action so the onboarding flow can be replayed.

## Project layout

```
shared/                  Kotlin Multiplatform module — all business logic and UI
  src/commonMain/        Shared Compose UI, screen models, repositories, resources
  src/androidMain/       Android actuals: file/gallery IO, notifications, locale
  src/iosMain/           iOS actuals: Photos export, notifications, locale
androidApp/              Android entry point (Application, MainActivity, launcher resources)
iosApp/                  Xcode project wrapping the shared Compose UI
```

Everything lives under the `com.filmroll.camera` package.

### Architecture

- **MVVM** using Voyager `ScreenModel`s with Kotlin flows for state.
- **Repository pattern** over local (SQLDelight) and network (Ktor) data sources.
- **Koin** for dependency injection; modules are aggregated in `di/AppModule.kt`.
- **Compose Resources** for strings, drawables and bundled LUT files, so both platforms read the
  same translated resources.

### Key modules

| Path | Responsibility |
| --- | --- |
| `screens/splash` | Cold-start branding; decides the first destination |
| `screens/language` | Language picker — first-run step and Settings entry |
| `screens/onboarding` | Three-page intro pager |
| `screens/home` | Image picking, LUT selection, adjustments, export |
| `screens/settings` | Appearance, language, notifications, export options, debug tools |
| `data/source/FilmRepository` | LUT data from the local DB and the network |
| `data/source/local/SettingsStorage` | Key-value preferences, including the onboarding flags |
| `lut/LutDownloadManager` | Downloads and caches `.cube` LUT files |
| `image/SkiaImageProcessor` | Skia runtime-shader LUT and adjustment pipeline |
| `notification/DailyReminder` | Daily reminder scheduling on both platforms |
| `util/AppEnvironment` | Debug detection, locale application, app restart |

## Building

### Android

```bash
./gradlew androidApp:assembleDebug     # debug APK
./gradlew androidApp:assembleRelease   # R8-minified release APK
./gradlew androidApp:installDebug      # install on a connected device
```

Release builds run R8 with resource shrinking; keep rules live in
`androidApp/proguard-rules.pro`.

### iOS

```bash
./gradlew shared:compileKotlinIosSimulatorArm64   # compile the shared module
open iosApp/iosApp.xcworkspace                    # then build and run from Xcode
```

The iOS app consumes the shared module through CocoaPods. If the workspace has not been set up
yet, run `./gradlew :shared:generateDummyFramework` and then `pod install` inside `iosApp/`.

### Everything

```bash
./gradlew clean
./gradlew build
```

## Localization

UI strings live in `shared/src/commonMain/composeResources/values/strings.xml`, with translations in
sibling `values-<qualifier>` folders. Shipped locales: English, Vietnamese, Spanish, French, German,
Portuguese (Brazil), Japanese, Korean, Simplified Chinese, Hindi and Indonesian. Missing keys fall
back to English.

Adding a language means adding a `values-<qualifier>/strings.xml` folder **and** an entry in
`i18n/AppLanguage.kt` — the picker only lists locales the app actually ships strings for.

Runtime switching uses per-app locales: `AppCompatDelegate.setApplicationLocales` on Android (which
is why `MainActivity` is an `AppCompatActivity` with an AppCompat theme), and the `AppleLanguages`
default on iOS, where the change takes effect on the next launch.

## Development notes

- **Threading:** image processing runs on `Dispatchers.IO`; the LUT pipeline is Skia-based and
  shared between platforms.
- **Memory:** large bitmaps need careful recycling on Android; exports stream through the app
  cache directory.
- **Permissions:** gallery access and `POST_NOTIFICATIONS` are platform-specific.
  `NotificationPermission` bridges the Android runtime prompt into the shared module, since the
  shared code cannot reach an `Activity`.
- **Debug tooling:** `isDebugBuild` gates the Settings → Debug section. Clearing app data wipes
  preferences, the local database and the cache, then relaunches at the splash screen.

## LUTs acknowledgment

The film LUTs are sourced from the public
[YahiaAngelo/Film-Luts](https://github.com/YahiaAngelo/Film-Luts) repository and are not owned by
this project.

## Credits

Filmroll started as a fork of the MIT-licensed
[Film Simulator](https://github.com/YahiaAngelo/Film-Simulator) project by YahiaAngelo, and has
since been rebranded and extended.

## License

Distributed under the MIT License. See [`LICENSE.txt`](LICENSE.txt).
