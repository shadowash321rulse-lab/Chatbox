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
        const val ERROR_READ = "Error reading preferences."

        // Network
        val IP_ADDRESS = stringPreferencesKey("ip_address")
        val PORT = intPreferencesKey("port")

        // Messaging options
        val MSG_REALTIME = booleanPreferencesKey("msg_realtime")
        val MSG_TRIGGER_SFX = booleanPreferencesKey("msg_trigger_sfx")
        val MSG_TYPING_INDICATOR = booleanPreferencesKey("msg_typing_indicator")
        val MSG_SEND_DIRECTLY = booleanPreferencesKey("msg_send_directly")

        // Cycle / AFK / Presets
        val CYCLE_ENABLED = booleanPreferencesKey("cycle_enabled")
        val CYCLE_MESSAGES = stringPreferencesKey("cycle_messages")
        val CYCLE_INTERVAL = intPreferencesKey("cycle_interval_seconds")

        val AFK_ENABLED = booleanPreferencesKey("afk_enabled")
        val AFK_MESSAGE = stringPreferencesKey("afk_message")

        val PRESETS_JSON = stringPreferencesKey("presets_json")
        val SELECTED_PRESET = stringPreferencesKey("selected_preset")

        // Theme
        // values: "System", "Light", "Dark"
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    // ─────────────────────────────────────
    // Network
    // ─────────────────────────────────────

    val ipAddress: Flow<String> = get(IP_ADDRESS, "127.0.0.1")
    suspend fun saveIpAddress(value: String) = save(IP_ADDRESS, value)

    suspend fun savePort(value: Int) = save(PORT, value)

    // ─────────────────────────────────────
    // Messaging options
    // ─────────────────────────────────────

    val isRealtimeMsg: Flow<Boolean> = get(MSG_REALTIME, false)
    suspend fun saveIsRealtimeMsg(value: Boolean) = save(MSG_REALTIME, value)

    val isTriggerSfx: Flow<Boolean> = get(MSG_TRIGGER_SFX, true)
    suspend fun saveIsTriggerSFX(value: Boolean) = save(MSG_TRIGGER_SFX, value)

    val isTypingIndicator: Flow<Boolean> = get(MSG_TYPING_INDICATOR, true)
    suspend fun saveTypingIndicator(value: Boolean) = save(MSG_TYPING_INDICATOR, value)

    val isSendImmediately: Flow<Boolean> = get(MSG_SEND_DIRECTLY, true)
    suspend fun saveIsSendImmediately(value: Boolean) = save(MSG_SEND_DIRECTLY, value)

    // ─────────────────────────────────────
    // Cycle
    // ─────────────────────────────────────

    val cycleEnabled: Flow<Boolean> = get(CYCLE_ENABLED, false)
    suspend fun saveCycleEnabled(value: Boolean) = save(CYCLE_ENABLED, value)

    val cycleMessages: Flow<String> = get(CYCLE_MESSAGES, "")
    suspend fun saveCycleMessages(value: String) = save(CYCLE_MESSAGES, value)

    val cycleInterval: Flow<Int> = get(CYCLE_INTERVAL, 3)
    suspend fun saveCycleInterval(value: Int) = save(CYCLE_INTERVAL, value)

    // ─────────────────────────────────────
    // AFK
    // ─────────────────────────────────────

    val afkEnabled: Flow<Boolean> = get(AFK_ENABLED, false)
    suspend fun saveAfkEnabled(value: Boolean) = save(AFK_ENABLED, value)

    val afkMessage: Flow<String> = get(AFK_MESSAGE, "AFK 🌙 back soon")
    suspend fun saveAfkMessage(value: String) = save(AFK_MESSAGE, value)

    // ─────────────────────────────────────
    // Presets
    // ─────────────────────────────────────

    val presetsJson: Flow<String> = get(PRESETS_JSON, "")
    suspend fun savePresetsJson(value: String) = save(PRESETS_JSON, value)

    val selectedPreset: Flow<String> = get(SELECTED_PRESET, "")
    suspend fun saveSelectedPreset(value: String) = save(SELECTED_PRESET, value)

    // ─────────────────────────────────────
    // Theme
    // ─────────────────────────────────────

    val themeMode: Flow<String> = get(THEME_MODE, "System")
    suspend fun saveThemeMode(value: String) = save(THEME_MODE, value)

    // ─────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────

    private fun <T> get(
        key: Preferences.Key<T>,
        defaultValue: T
    ): Flow<T> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    Log.e(TAG, ERROR_READ, exception)
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[key] ?: defaultValue
            }
    }

    private suspend fun <T> save(
        key: Preferences.Key<T>,
        value: T
    ) {
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }
}
