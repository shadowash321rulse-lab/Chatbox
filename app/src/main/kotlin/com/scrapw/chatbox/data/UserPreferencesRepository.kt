package com.scrapw.chatbox.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
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

        // NEW – cycle / afk / presets
        val CYCLE_ENABLED = booleanPreferencesKey("cycle_enabled")
        val CYCLE_MESSAGES = stringPreferencesKey("cycle_messages")
        val CYCLE_INTERVAL = intPreferencesKey("cycle_interval")

        val AFK_ENABLED = booleanPreferencesKey("afk_enabled")
        val AFK_MESSAGE = stringPreferencesKey("afk_message")

        val PRESETS_JSON = stringPreferencesKey("presets_json")
        val SELECTED_PRESET = stringPreferencesKey("selected_preset")
    }

    val ipAddress = get(IP_ADDRESS, "127.0.0.1")
    suspend fun saveIpAddress(value: String) = save(IP_ADDRESS, value)

    suspend fun savePort(value: Int) = save(PORT, value)

    val isRealtimeMsg = get(MSG_REALTIME, false)
    suspend fun saveIsRealtimeMsg(value: Boolean) = save(MSG_REALTIME, value)

    val isTriggerSfx = get(MSG_TRIGGER_SFX, true)
    suspend fun saveIsTriggerSFX(value: Boolean) = save(MSG_TRIGGER_SFX, value)

    val isTypingIndicator = get(MSG_TYPING_INDICATOR, true)
    suspend fun saveTypingIndicator(value: Boolean) = save(MSG_TYPING_INDICATOR, value)

    val isSendImmediately = get(MSG_SEND_DIRECTLY, true)
    suspend fun saveIsSendImmediately(value: Boolean) = save(MSG_SEND_DIRECTLY, value)

    // ===== Persistent cycle / AFK / presets =====
    val cycleEnabled = get(CYCLE_ENABLED, false)
    suspend fun saveCycleEnabled(v: Boolean) = save(CYCLE_ENABLED, v)

    val cycleMessages = get(CYCLE_MESSAGES, "")
    suspend fun saveCycleMessages(v: String) = save(CYCLE_MESSAGES, v)

    val cycleInterval = get(CYCLE_INTERVAL, 3)
    suspend fun saveCycleInterval(v: Int) = save(CYCLE_INTERVAL, v)

    val afkEnabled = get(AFK_ENABLED, false)
    suspend fun saveAfkEnabled(v: Boolean) = save(AFK_ENABLED, v)

    val afkMessage = get(AFK_MESSAGE, "AFK 🌙 back soon")
    suspend fun saveAfkMessage(v: String) = save(AFK_MESSAGE, v)

    val presetsJson = get(PRESETS_JSON, "")
    suspend fun savePresetsJson(v: String) = save(PRESETS_JSON, v)

    val selectedPreset = get(SELECTED_PRESET, "")
    suspend fun saveSelectedPreset(v: String) = save(SELECTED_PRESET, v)

    // ===== helpers =====
    private fun <T> get(key: Preferences.Key<T>, defaultValue: T): Flow<T> =
        dataStore.data
            .catch {
                if (it is IOException) {
                    Log.e(TAG, ERROR_READ, it)
                    emit(emptyPreferences())
                } else throw it
            }
            .map { it[key] ?: defaultValue }

    private suspend fun <T> save(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }
}
