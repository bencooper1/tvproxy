package com.tvproxy.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tvproxy.app.core.model.AppSettings
import com.tvproxy.app.core.model.AppTheme
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Settings store (architecture.md §4 "AppSettings", §5). Thin typed facade over
 * DataStore Preferences; every write is immediately observable via [settings].
 * The parental PIN does NOT live here — it goes through SecurePrefs (M7, ADR-011).
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val USER_AGENT = stringPreferencesKey("user_agent")
        val BUFFER_MS = intPreferencesKey("buffer_ms")
        val AUTO_FRAME_RATE = booleanPreferencesKey("auto_frame_rate")
        val START_ON_LAST_CHANNEL = booleanPreferencesKey("start_on_last_channel")
        val LAST_PLAYED_CHANNEL_ID = longPreferencesKey("last_played_channel_id")
        val EPG_UPDATE_INTERVAL_HOURS = intPreferencesKey("epg_update_interval_hours")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { prefs -> prefs.toAppSettings() }

    suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setUserAgent(userAgent: String?) {
        dataStore.edit { prefs ->
            if (userAgent.isNullOrBlank()) prefs.remove(Keys.USER_AGENT) else prefs[Keys.USER_AGENT] = userAgent.trim()
        }
    }

    suspend fun setBufferMs(bufferMs: Int) {
        dataStore.edit { it[Keys.BUFFER_MS] = bufferMs.coerceAtLeast(0) }
    }

    suspend fun setAutoFrameRate(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_FRAME_RATE] = enabled }
    }

    suspend fun setStartOnLastChannel(enabled: Boolean) {
        dataStore.edit { it[Keys.START_ON_LAST_CHANNEL] = enabled }
    }

    suspend fun setLastPlayedChannelId(channelId: Long?) {
        dataStore.edit { prefs ->
            if (channelId == null) prefs.remove(Keys.LAST_PLAYED_CHANNEL_ID) else prefs[Keys.LAST_PLAYED_CHANNEL_ID] = channelId
        }
    }

    suspend fun setEpgUpdateIntervalHours(hours: Int) {
        dataStore.edit { it[Keys.EPG_UPDATE_INTERVAL_HOURS] = hours.coerceIn(MIN_EPG_INTERVAL_H, MAX_EPG_INTERVAL_H) }
    }

    private fun Preferences.toAppSettings(): AppSettings = AppSettings(
        theme = this[Keys.THEME].toAppTheme(),
        userAgent = this[Keys.USER_AGENT]?.takeIf { it.isNotBlank() },
        bufferMs = this[Keys.BUFFER_MS] ?: AppSettings.DEFAULT_BUFFER_MS,
        autoFrameRate = this[Keys.AUTO_FRAME_RATE] ?: false,
        startOnLastChannel = this[Keys.START_ON_LAST_CHANNEL] ?: false,
        lastPlayedChannelId = this[Keys.LAST_PLAYED_CHANNEL_ID],
        epgUpdateIntervalHours = this[Keys.EPG_UPDATE_INTERVAL_HOURS] ?: AppSettings.DEFAULT_EPG_INTERVAL_H,
    )

    private fun String?.toAppTheme(): AppTheme =
        AppTheme.entries.firstOrNull { it.name == this } ?: AppTheme.SYSTEM

    companion object {
        private const val MIN_EPG_INTERVAL_H = 1
        private const val MAX_EPG_INTERVAL_H = 168
    }
}
