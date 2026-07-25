package com.filmroll.camera.data.source.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.filmroll.camera.Database
import com.filmroll.camera.util.AppContext

actual class DriverFactory {
    actual fun createDriver(appContext: AppContext): SqlDriver {
        return NativeSqliteDriver(Database.Schema, "film.db")
    }
}