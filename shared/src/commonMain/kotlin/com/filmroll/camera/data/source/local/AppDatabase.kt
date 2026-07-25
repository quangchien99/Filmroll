package com.filmroll.camera.data.source.local

import com.filmroll.camera.Database
import com.filmroll.camera.util.AppContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appDBModule = module {
    singleOf(::AppDatabase)
    singleOf(::DriverFactory)
}

/**
 * Main endpoint for the app's local database
 */
internal class AppDatabase(driverFactory: DriverFactory) {
    val database = Database(driverFactory.createDriver(appContext = AppContext))
}