package com.tvproxy.app

import android.app.Application
import timber.log.Timber

/**
 * TVProxy application entry point.
 *
 * M0: initializes logging. Hilt wiring, Room, and the player session factory
 * land with their milestones (M1/M2).
 */
class TvProxyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
