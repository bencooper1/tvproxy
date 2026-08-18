package com.tvproxy.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import timber.log.Timber

/**
 * M0 placeholder host activity.
 *
 * The real home shell (bottom nav: Live / Movies / Series / Recordings /
 * Settings) is delivered in M3 by Agent A4. This placeholder exists so the
 * M0 smoke test can verify the app launches on API 23 and API 35.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Timber.d("TVProxy M0 placeholder launched")
    }
}
