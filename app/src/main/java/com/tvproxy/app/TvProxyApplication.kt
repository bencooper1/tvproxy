package com.tvproxy.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * TVProxy application entry point.
 *
 * M0: initializes logging.
 * M1: Hilt DI root (`@HiltAndroidApp`); the player session factory lands with M2.
 */
@HiltAndroidApp
class TvProxyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
