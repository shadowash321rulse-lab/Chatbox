package com.scrapw.chatbox.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    private companion object {
        const val TAG = "UserPreferencesRepo"
        const val ERROR_READ = "Error reading preferences."

        // Existing
        val IP_ADDRESS = stringPreferencesKey("ip_address")
        val PORT = intPreferencesKey("port")

        val MSG_REALTIME = booleanPreferencesKey("msg_realtime")
        val MSG_TRIGGER_SFX = booleanPreferencesKey("msg_trigger_sfx")
        val MSG_TYPING_INDICATOR = booleanPreferencesKey("msg_typing_indicator")
        val MSG_SEND_DIRECTLY = booleanPreferencesKey("msg_send_directly")

        // Cycle persistence
        val CYCLE_ENABLED = booleanPreferencesKey("cycle_enabled")
        val CYCLE_MESSAGES = stringPreferencesKey("cycle_messages")
        val CYCLE_INTERVAL = intPreferencesKey("cycle_interval_seconds")

        // AFK persistence (TEXT ONLY)
        val AFK_MESSAGE = stringPreferencesKey("afk_message")

        // AFK presets (3)
        val AFK_PRESET_1 = stringPreferencesKey("afk_preset_1")
        val AFK_PRESET_2 = stringPreferencesKey("afk_preset_2")
        val AFK_PRESET_3 = stringPreferencesKey("afk_preset_3")

        // Cycle presets (5) messages + interval
        val CYCLE_PRESET_1_MESSAGES = stringPreferencesKey("cycle_preset_1_messages")
        val CYCLE_PRESET_1_INTERVAL = intPreferencesKey("cycle_preset_1_interval")

        val CYCLE_PRESET_2_MESSAGES = stringPreferencesKey("cycle_preset_2_messages")
        val CYCLE_PRESET_2_INTERVAL = intPreferencesKey("cycle_preset_2_interval")

        val CYCLE_PRESET_3_MESSAGES = stringPreferencesKey("cycle_preset_3_messages")
        val CYCLE_PRESET_3_INTERVAL = intPreferencesKey("cycle_preset_3_interval")

        val CYCLE_PRESET_4_MESSAGES = stringPreferencesKey("cycle_preset_4_messages")
        val CYCLE_PRESET_4_INTERVAL = intPreferencesKey("cycle_preset_4_interval")

        val CYCLE_PRESET_5_MESSAGES = stringPreferencesKey("cycle_preset_5_messages")
        val CYCLE_PRESET_5_INTERVAL = intPreferencesKey("cycle_preset_5_interval")
    }

    // ----------------------------
    // Existing settings
    // ----------------------------
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

    // ----------------------------
    // Cycle persistence
    // ----------------------------
    val cycleEnabled = get(CYCLE_ENABLED, false)
    suspend fun saveCycleEnabled(value: Boolean) = save(CYCLE_ENABLED, value)

    val cycleMessages = get(CYCLE_MESSAGES, "")
    suspend fun saveCycleMessages(value: String) = save(CYCLE_MESSAGES, value)

    val cycleInterval = get(CYCLE_INTERVAL, 3)
    suspend fun saveCycleInterval(value: Int) = save(CYCLE_INTERVAL, value)

    // ----------------------------
    // AFK persistence (TEXT ONLY)
    // ----------------------------
    val afkMessage = get(AFK_MESSAGE, "AFK 🌙 back soon")
    suspend fun saveAfkMessage(value: String) = save(AFK_MESSAGE, value)

    // ----------------------------
    // AFK Presets (3)
    // ----------------------------
    suspend fun saveAfkPreset(slot: Int, text: String) {
        val key = when (slot.coerceIn(1, 3)) {
            1 -> AFK_PRESET_1
            2 -> AFK_PRESET_2
            else -> AFK_PRESET_3
        }
        save(key, text)
    }

    suspend fun getAfkPresetOnce(slot: Int): String {
        val prefs = dataStore.data.first()
        return when (slot.coerceIn(1, 3)) {
            1 -> prefs[AFK_PRESET_1] ?: ""
            2 -> prefs[AFK_PRESET_2] ?: ""
            else -> prefs[AFK_PRESET_3] ?: ""
        }
    }

    // ----------------------------
    // Cycle Presets (5)
    // ----------------------------
    suspend fun saveCyclePreset(slot: Int, messages: String, intervalSeconds: Int) {
        val s = slot.coerceIn(1, 5)
        dataStore.edit {
            when (s) {
                1 -> {
                    it[CYCLE_PRESET_1_MESSAGES] = messages
                    it[CYCLE_PRESET_1_INTERVAL] = intervalSeconds
                }
                2 -> {
                    it[CYCLE_PRESET_2_MESSAGES] = messages
                    it[CYCLE_PRESET_2_INTERVAL] = intervalSeconds
                }
                3 -> {
                    it[CYCLE_PRESET_3_MESSAGES] = messages
                    it[CYCLE_PRESET_3_INTERVAL] = intervalSeconds
                }
                4 -> {
                    it[CYCLE_PRESET_4_MESSAGES] = messages
                    it[CYCLE_PRESET_4_INTERVAL] = intervalSeconds
                }
                else -> {
                    it[CYCLE_PRESET_5_MESSAGES] = messages
                    it[CYCLE_PRESET_5_INTERVAL] = intervalSeconds
                }
            }
        }
    }

    suspend fun getCyclePresetOnce(slot: Int): Pair<String, Int> {
        val prefs = dataStore.data.first()
        return when (slot.coerceIn(1, 5)) {
            1 -> (prefs[CYCLE_PRESET_1_MESSAGES] ?: "") to (prefs[CYCLE_PRESET_1_INTERVAL] ?: 3)
            2 -> (prefs[CYCLE_PRESET_2_MESSAGES] ?: "") to (prefs[CYCLE_PRESET_2_INTERVAL] ?: 3)
            3 -> (prefs[CYCLE_PRESET_3_MESSAGES] ?: "") to (prefs[CYCLE_PRESET_3_INTERVAL] ?: 3)
            4 -> (prefs[CYCLE_PRESET_4_MESSAGES] ?: "") to (prefs[CYCLE_PRESET_4_INTERVAL] ?: 3)
            else -> (prefs[CYCLE_PRESET_5_MESSAGES] ?: "") to (prefs[CYCLE_PRESET_5_INTERVAL] ?: 3)
        }
    }

    // ----------------------------
    // helpers
    // ----------------------------
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
            .map { prefs -> prefs[key] ?: defaultValue }
    }

    private suspend fun <T> save(key: Preferences.Key<T>, value: T) {
        dataStore.edit { prefs -> prefs[key] = value }
    }
}
