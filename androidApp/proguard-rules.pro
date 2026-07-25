# Most of the stack (Compose, Coil, SQLDelight, Ktor, kotlinx.serialization, skiko) ships
# consumer rules in its own artifacts. What follows covers the gaps R8 cannot infer.

# --- Kotlin coroutines / reflection metadata -----------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# --- Koin --------------------------------------------------------------------------
# Definitions are resolved by KClass at runtime, so the classes DI hands out must keep
# their identity and their constructors.
-keep class com.filmroll.camera.**ScreenModel { *; }
-keep class com.filmroll.camera.data.source.** { *; }
-keep class com.filmroll.camera.lut.** { *; }
-keep class com.filmroll.camera.capture.CaptureRelay { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.* <methods>;
}

# --- Broadcast receivers referenced only from the merged manifest --------------------
-keep class com.filmroll.camera.notification.DailyReminderReceiver { <init>(); }
-keep class com.filmroll.camera.notification.BootCompletedReceiver { <init>(); }

# --- Enums persisted by name in preferences -----------------------------------------
# SettingsStorage round-trips these through valueOf(), which R8 cannot see.
-keepclassmembers enum com.filmroll.camera.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Skiko / native image pipeline ---------------------------------------------------
-keep class org.jetbrains.skia.** { *; }
-keep class org.jetbrains.skiko.** { *; }
-dontwarn org.jetbrains.skiko.**

# --- Ktor ----------------------------------------------------------------------------
-dontwarn io.ktor.**
-dontwarn org.slf4j.**

# --- Keep source line numbers so Play Console stack traces stay readable --------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
