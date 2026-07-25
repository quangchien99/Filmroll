package com.filmroll.camera.util

expect class Platform(appContext: AppContext) {
    fun getAppVersion(): String
}