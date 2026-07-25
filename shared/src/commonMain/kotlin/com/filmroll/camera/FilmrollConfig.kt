package com.filmroll.camera

import com.filmroll.camera.util.AppContext

/**
 * Config file used to initiate context for the androidMain module
 */
class FilmrollConfig(
    val appContext: AppContext
)

object Filmroll {
    fun initialize(config: FilmrollConfig) {
        val commonContext = config.appContext
    }
}