package com.filmroll.camera

interface Platform {
    val name: PlatformName
}

enum class PlatformName {
    ANDROID,
    IOS,
    DESKTOP
}

expect fun getPlatform(): Platform

expect fun getAndroidSdkVersion(): Int