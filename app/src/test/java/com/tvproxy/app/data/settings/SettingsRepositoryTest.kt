package com.tvproxy.app.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.tvproxy.app.core.model.AppSettings
import com.tvproxy.app.core.model.AppTheme
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * SettingsRepository unit tests (pure JVM — DataStore Preferences does not need
 * Android classes, only a writable directory).
 */
class SettingsRepositoryTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private fun kotlinx.coroutines.test.TestScope.newRepository(): SettingsRepository {
        val dir = tmpFolder.newFolder("store-${System.nanoTime()}")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(dir, "settings.preferences_pb") },
        )
        return SettingsRepository(dataStore)
    }

    @Test
    fun defaults_matchAppSettingsDefaults() = runTest {
        val repository = newRepository()

        assertThat(repository.settings.first()).isEqualTo(AppSettings())
    }

    @Test
    fun setters_persistAndRoundTripThroughFlow() = runTest {
        val repository = newRepository()

        repository.setTheme(AppTheme.BLACK)
        repository.setBufferMs(8_000)
        repository.setUserAgent("CustomAgent/9")
        repository.setAutoFrameRate(true)
        repository.setStartOnLastChannel(true)
        repository.setLastPlayedChannelId(42L)
        repository.setEpgUpdateIntervalHours(6)

        val settings = repository.settings.first()
        assertThat(settings.theme).isEqualTo(AppTheme.BLACK)
        assertThat(settings.bufferMs).isEqualTo(8_000)
        assertThat(settings.userAgent).isEqualTo("CustomAgent/9")
        assertThat(settings.autoFrameRate).isTrue()
        assertThat(settings.startOnLastChannel).isTrue()
        assertThat(settings.lastPlayedChannelId).isEqualTo(42L)
        assertThat(settings.epgUpdateIntervalHours).isEqualTo(6)
    }

    @Test
    fun setters_blankUserAgent_clearsToNull() = runTest {
        val repository = newRepository()
        repository.setUserAgent("Agent")
        repository.setUserAgent("   ")

        assertThat(repository.settings.first().userAgent).isNull()
    }

    @Test
    fun setters_intervalClampedToBounds() = runTest {
        val repository = newRepository()

        repository.setEpgUpdateIntervalHours(0)
        assertThat(repository.settings.first().epgUpdateIntervalHours).isEqualTo(1)

        repository.setEpgUpdateIntervalHours(9_999)
        assertThat(repository.settings.first().epgUpdateIntervalHours).isEqualTo(168)
    }
}
