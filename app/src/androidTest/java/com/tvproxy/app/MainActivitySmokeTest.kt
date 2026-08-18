package com.tvproxy.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M0 smoke test: the app launches and shows the placeholder on the target API
 * level. CI runs this on API 23 and API 35 emulators (the two min/target
 * bounds of the supported range).
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @Test
    fun app_launchesAndShowsPlaceholder() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertThat(activity).isNotNull()
                val title = activity.findViewById<android.widget.TextView>(R.id.placeholder_title)
                assertThat(title).isNotNull()
                assertThat(title?.text?.toString()).contains("M0 scaffold")
            }
        }
    }
}
