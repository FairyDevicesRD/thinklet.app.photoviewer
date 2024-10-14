package com.example.fd.thinkletvision

import android.app.Application
import com.example.fd.thinkletvision.util.Logging
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class VisionApp : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(Logging.DEFAULT_LEVEL)
            androidContext(this@VisionApp)
            modules(appModule)
        }
    }
}