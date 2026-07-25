package com.filmroll.camera.android

import android.app.Application
import com.filmroll.camera.di.appModule
import com.filmroll.camera.Filmroll
import com.filmroll.camera.FilmrollConfig
import com.filmroll.camera.util.AppContext
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class MainApplication: Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@MainApplication)
            androidLogger()
            modules(appModule())
        }

        Filmroll.initialize(
            FilmrollConfig(
                appContext = AppContext.apply { set(applicationContext) }
            )
        )
    }
}