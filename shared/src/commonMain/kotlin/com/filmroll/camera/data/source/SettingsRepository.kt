package com.filmroll.camera.data.source

import com.filmroll.camera.data.source.local.SettingsStorage

interface SettingsRepository {

    fun getSettings(): SettingsStorage
}