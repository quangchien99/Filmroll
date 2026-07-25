package com.filmroll.camera.data.source

import com.filmroll.camera.data.source.local.SettingsStorage
import org.koin.dsl.bind
import org.koin.dsl.module

val settingsRepoModule = module {
    single { SettingsRepositoryImpl(get()) } bind SettingsRepository::class
}
class SettingsRepositoryImpl(
    private val settingsStorage: SettingsStorage
): SettingsRepository {
    override fun getSettings(): SettingsStorage = settingsStorage

}