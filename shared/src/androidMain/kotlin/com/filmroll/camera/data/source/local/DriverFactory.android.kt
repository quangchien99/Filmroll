package com.filmroll.camera.data.source.local

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.filmroll.camera.Database
import com.filmroll.camera.util.AppContext

actual class DriverFactory {
    actual fun createDriver(appContext: AppContext): SqlDriver {
        return AndroidSqliteDriver(Database.Schema, appContext.get()!!, "film.db")
    }
}