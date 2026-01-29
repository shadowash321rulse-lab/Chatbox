package com.scrapw.chatbox.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {
    private companion object {
        const val TAG = "UserPreferencesRepo"
        const val ERROR_READ = "Error reading preferences."

        val IP_ADDRESS = stringPreferencesKey("ip_address")
        val PORT = intPreferencesKey("port")

        val MSG_REALTIME = booleanPreferencesKey("msg_realtime")
        val MSG_TRIGGER_SFX = booleanPreferencesKey("msg_trigger_sfx")
        val MSG_TYPING_INDICATOR = booleanPreferencesKey("msg_typing_indicator")
        val MSG_SEND_DIRECTLY = booleanPreferencesKey("msg_send_directly")

        // Cycle
        val CYCLE_ENABLED = booleanPreferencesKey("cycle_enabled")
        val CYCLE_MESSAGES = stringPreferencesKey("cycle_messages")
        val CYCLE_INTERVAL = intPreferencesKey("cycle_interval")

        // Spotify prefs + tokens
        val SPOTIFY_ENABLED = booleanPreferencesKey("spotify_enabled")
        val SPOTIFY_DEMO = booleanPreferencesKey("spotify_demo")
        val SPOTIFY_PRESET = intPreferencesKey("spotify_preset")
        val SPOTIFY_CLIENT_ID = stringPreferencesKey("spotify_client_id")

        val SPOTIFY_ACCESS_TOKEN = stringPreferencesKey("spotify_access_token")
        val SPOTIFY_REFRESH_TOKEN = stringPreferencesKey("spotify_refresh_token")
        val SPOTIFY_EXPIRES_AT = longPreferencesKey("spotify_expires_at_epoch_sec")
        val SPOTIFY_CODE_VERIFIER = stringPreferencesKey("spotify_code_verifier")
        val SPOTIFY_STATE = stringPreferencesKey("spotify_state")
    }

    val ipAddress = get(IP_ADDRESS, "127.0.0.1")
    suspend fun saveIpAddress(value: String) = save(IP_ADDRESS, value)

    val port = get(PORT, 9000)
    suspend fun savePort(value: Int) = save(PORT, value)

    val isRealtimeMsg = get(MSG_REALTIME, false)
    suspend fun saveIsRealtimeMsg(value: Boolean) = save(MSG_REALTIME, value)

    val isTriggerSfx = get(MSG_TRIGGER_SFX, true)
    suspend fun saveIsTriggerSFX(value: Boolean) = save(MSG_TRIGGER_SFX, value)

    val isTypingIndicator = get(MSG_TYPING_INDICATOR, true)
    suspend fun saveTypingIndicator(value: Boolean) = save(MSG_TYPING_INDICATOR, value)

    val isSendImmediately = get(MSG_SEND_DIRECTLY, true)
    suspend fun saveIsSendImmediately(value: Boolean) = save(MSG_SEND_DIRECTLY, value)

    // Cycle
    val cycleEnabled = get(CYCLE_ENABLED, false)
    suspend fun saveCycleEnabled(value: Boolean) = save(CYCLE_ENABLED, value)

    val cycleMessages = get(CYCLE_MESSAGES, "")
    suspend fun saveCycleMessages(value: String) = save(CYCLE_MESSAGES, value)

    val cycleInterval = get(CYCLE_INTERVAL, 3)
    suspend fun saveCycleInterval(value: Int) = save(CYCLE_INTERVAL, value)

    // Spotify
    val spotifyEnabled = get(SPOTIFY_ENABLED, false)
    suspend fun saveSpotifyEnabled(value: Boolean) = save(SPOTIFY_ENABLED, value)

    val spotifyDemo = get(SPOTIFY_DEMO, false)
    suspend fun saveSpotifyDemo(value: Boolean) = save(SPOTIFY_DEMO, value)

    val spotifyPreset = get(SPOTIFY_PRESET, 1)
    suspend fun saveSpotifyPreset(value: Int) = save(SPOTIFY_PRESET, value)

    val spotifyClientId = get(SPOTIFY_CLIENT_ID, "")
    suspend fun saveSpotifyClientId(value: String) = save(SPOTIFY_CLIENT_ID, value)

    // Tokens
    val spotifyAccessToken = get(SPOTIFY_ACCESS_TOKEN, "")
    suspend fun saveSpotifyAccessToken(value: String) = save(SPOTIFY_ACCESS_TOKEN, value)

    val spotifyRefreshToken = get(SPOTIFY_REFRESH_TOKEN, "")
    suspend fun saveSpotifyRefreshToken(value: String) = save(SPOTIFY_REFRESH_TOKEN, value)

    val spotifyExpiresAtEpochSec = get(SPOTIFY_EXPIRES_AT, 0L)
    suspend fun saveSpotifyExpiresAtEpochSec(value: Long) = save(SPOTIFY_EXPIRES_AT, value)

    val spotifyCodeVerifier = get(SPOTIFY_CODE_VERIFIER, "")
    suspend fun saveSpotifyCodeVerifier(value: String) = save(SPOTIFY_CODE_VERIFIER, value)

    val spotifyState = get(SPOTIFY_STATE, "")
    suspend fun saveSpotifyState(value: String) = save(SPOTIFY_STATE, value)

    suspend fun clearSpotifyTokens() {
        dataStore.edit { prefs ->
            prefs.remove(SPOTIFY_ACCESS_TOKEN)
            prefs.remove(SPOTIFY_REFRESH_TOKEN)
            prefs.remove(SPOTIFY_EXPIRES_AT)
            prefs.remove(SPOTIFY_CODE_VERIFIER)
            prefs.remove(SPOTIFY_STATE)
        }
    }

    private fun <T> get(key: Preferences.Key<T>, defaultValue: T): Flow<T> {
        return dataStore.data
            .catch {
                if (it is IOException) {
                    Log.e(TAG, ERROR_READ, it)
                    emit(emptyPreferences())
                } else {
                    throw it
                }
            }
            .map { preferences -> preferences[key] ?: defaultValue }
    }

    private suspend fun <T> save(key: Preferences.Key<T>, value: T) {
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }
}
