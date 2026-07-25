package com.filmroll.camera.data.source.local

import app.cash.sqldelight.db.SqlDriver
import com.filmroll.camera.util.AppContext

/**
 * A db driver interface to create a db driver suited for every platform
 */
expect class DriverFactory() {
    fun createDriver(appContext: AppContext): SqlDriver
}
