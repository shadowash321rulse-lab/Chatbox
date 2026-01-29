package com.scrapw.chatbox.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

class UserPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) {

    private companion object {
        const val TAG = "UserPreferencesRepo"

        // Network
        val IP_ADDRESS = stringPreferencesKey("ip_address")
        val PORT = intPreferencesKey("port")

        // Message behavior
        val MSG_REALTIME = booleanPreferencesKey("msg_realtime")
        val MSG_TRIGGER_SFX = booleanPreferencesKey("msg_trigger_sfx")
        val MSG_TYPING_INDICATOR = booleanPreferencesKey("msg_typing_indicator")
        val MSG_SEND_DIRECTLY = booleanPreferencesKey("msg_send_directly")

        // Cycle
        val CYCLE_ENABLED = booleanPreferencesKey("cycle_enabled")
        val CYCLE_MESSAGES = stringPreferencesKey("cycle_messages")
        val CYCLE_INTERVAL = intPreferencesKey("cycle_interval")

        // AFK
        val AFK_ENABLED = booleanPreferencesKey("afk_enabled")
        val AFK_MESSAGE = stringPreferencesKey("afk_message")

        // Presets
        val SELECTED_PRESET = stringPreferencesKey("selected_preset")
        val PRESETS_JSON = stringPreferencesKey("presets_json")

        // Spotify (token persistence – auth stays logged in)
        val SPOTIFY_ACCESS_TOKEN = stringPreferencesKey("spotify_access_token")
        val SPOTIFY_REFRESH_TOKEN = stringPreferencesKey("spotify_refresh_token")
        val SPOTIFY_TOKEN_EXPIRY = longPreferencesKey("spotify_token_expiry")
    }

    /* ---------------------------------------------------------
     * Generic helpers
     * --------------------------------------------------------- */

    private fun <T> get(key: Preferences.Key<T>, default: T): Flow<T> =
        dataStore.data
            .catch { e ->
                if (e is IOException) {
                    Log.e(TAG, "Error reading preferences", e)
                    emit(emptyPreferences())
                } else {
                    throw e
                }
            }
            .map { it[key] ?: default }

    private suspend fun <T> save(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    /* ---------------------------------------------------------
     * Network
     * --------------------------------------------------------- */

    val ipAddress = get(IP_ADDRESS, "127.0.0.1")
    suspend fun saveIpAddress(value: String) = save(IP_ADDRESS, value)

    val port = get(PORT, 9000)
    suspend fun savePort(value: Int) = save(PORT, value)

    /* ---------------------------------------------------------
     * Message options
     * --------------------------------------------------------- */

    val isRealtimeMsg = get(MSG_REALTIME, false)
    suspend fun saveIsRealtimeMsg(v: Boolean) = save(MSG_REALTIME, v)

    val isTriggerSfx = get(MSG_TRIGGER_SFX, true)
    suspend fun saveIsTriggerSFX(v: Boolean) = save(MSG_TRIGGER_SFX, v)

    val isTypingIndicator = get(MSG_TYPING_INDICATOR, true)
    suspend fun saveTypingIndicator(v: Boolean) = save(MSG_TYPING_INDICATOR, v)

    val isSendImmediately = get(MSG_SEND_DIRECTLY, true)
    suspend fun saveIsSendImmediately(v: Boolean) = save(MSG_SEND_DIRECTLY, v)

    /* ---------------------------------------------------------
     * Cycle
     * --------------------------------------------------------- */

    val cycleEnabled = get(CYCLE_ENABLED, false)
    suspend fun saveCycleEnabled(v: Boolean) = save(CYCLE_ENABLED, v)

    val cycleMessages = get(CYCLE_MESSAGES, "")
    suspend fun saveCycleMessages(v: String) = save(CYCLE_MESSAGES, v)

    val cycleInterval = get(CYCLE_INTERVAL, 3)
    suspend fun saveCycleInterval(v: Int) = save(CYCLE_INTERVAL, v)

    /* ---------------------------------------------------------
     * AFK
     * --------------------------------------------------------- */

    val afkEnabled = get(AFK_ENABLED, false)
    suspend fun saveAfkEnabled(v: Boolean) = save(AFK_ENABLED, v)

    val afkMessage = get(AFK_MESSAGE, "AFK 🌙 back soon")
    suspend fun saveAfkMessage(v: String) = save(AFK_MESSAGE, v)

    /* ---------------------------------------------------------
     * Presets
     * --------------------------------------------------------- */

    val selectedPreset = get(SELECTED_PRESET, "")
    suspend fun saveSelectedPreset(v: String) = save(SELECTED_PRESET, v)

    val presetsJson = get(PRESETS_JSON, "")
    suspend fun savePresetsJson(v: String) = save(PRESETS_JSON, v)

    /* ---------------------------------------------------------
     * Spotify auth persistence
     * (this is why login survives app restarts)
     * --------------------------------------------------------- */

    val spotifyAccessToken = get(SPOTIFY_ACCESS_TOKEN, "")
    suspend fun saveSpotifyAccessToken(v: String) =
        save(SPOTIFY_ACCESS_TOKEN, v)

    val spotifyRefreshToken = get(SPOTIFY_REFRESH_TOKEN, "")
    suspend fun saveSpotifyRefreshToken(v: String) =
        save(SPOTIFY_REFRESH_TOKEN, v)

    val spotifyTokenExpiry = get(SPOTIFY_TOKEN_EXPIRY, 0L)
    suspend fun saveSpotifyTokenExpiry(v: Long) =
        save(SPOTIFY_TOKEN_EXPIRY, v)

    suspend fun clearSpotifyAuth() {
        dataStore.edit {
            it.remove(SPOTIFY_ACCESS_TOKEN)
            it.remove(SPOTIFY_REFRESH_TOKEN)
            it.remove(SPOTIFY_TOKEN_EXPIRY)
        }
    }
}
