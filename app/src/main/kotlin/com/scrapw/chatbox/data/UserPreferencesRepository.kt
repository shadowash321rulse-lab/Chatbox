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
import kotlinx.coroutines.flow.map
import java.io.IOException

class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    private companion object {
        const val TAG = "UserPreferencesRepo"
        const val ERROR_READ = "Error reading preferences."

        // Existing base settings
        val IP_ADDRESS = stringPreferencesKey("ip_address")
        val PORT = intPreferencesKey("port")

        val MSG_REALTIME = booleanPreferencesKey("msg_realtime")
        val MSG_TRIGGER_SFX = booleanPreferencesKey("msg_trigger_sfx")
        val MSG_TYPING_INDICATOR = booleanPreferencesKey("msg_typing_indicator")
        val MSG_SEND_DIRECTLY = booleanPreferencesKey("msg_send_directly")

        // Cycle live state (current working set)
        val CYCLE_ENABLED = booleanPreferencesKey("cycle_enabled")
        val CYCLE_MESSAGES = stringPreferencesKey("cycle_messages")
        val CYCLE_INTERVAL = intPreferencesKey("cycle_interval_seconds")

        // AFK persistence (TEXT ONLY, not enabled state)
        val AFK_MESSAGE = stringPreferencesKey("afk_message")

        // ----------------------------
        // AFK Presets (3) - name + text
        // ----------------------------
        val AFK_PRESET_1_NAME = stringPreferencesKey("afk_preset_1_name")
        val AFK_PRESET_1_TEXT = stringPreferencesKey("afk_preset_1_text")

        val AFK_PRESET_2_NAME = stringPreferencesKey("afk_preset_2_name")
        val AFK_PRESET_2_TEXT = stringPreferencesKey("afk_preset_2_text")

        val AFK_PRESET_3_NAME = stringPreferencesKey("afk_preset_3_name")
        val AFK_PRESET_3_TEXT = stringPreferencesKey("afk_preset_3_text")

        // ----------------------------
        // Cycle Presets (5) - name + messages + interval
        // ----------------------------
        val CYCLE_PRESET_1_NAME = stringPreferencesKey("cycle_preset_1_name")
        val CYCLE_PRESET_1_MESSAGES = stringPreferencesKey("cycle_preset_1_messages")
        val CYCLE_PRESET_1_INTERVAL = intPreferencesKey("cycle_preset_1_interval")

        val CYCLE_PRESET_2_NAME = stringPreferencesKey("cycle_preset_2_name")
        val CYCLE_PRESET_2_MESSAGES = stringPreferencesKey("cycle_preset_2_messages")
        val CYCLE_PRESET_2_INTERVAL = intPreferencesKey("cycle_preset_2_interval")

        val CYCLE_PRESET_3_NAME = stringPreferencesKey("cycle_preset_3_name")
        val CYCLE_PRESET_3_MESSAGES = stringPreferencesKey("cycle_preset_3_messages")
        val CYCLE_PRESET_3_INTERVAL = intPreferencesKey("cycle_preset_3_interval")

        val CYCLE_PRESET_4_NAME = stringPreferencesKey("cycle_preset_4_name")
        val CYCLE_PRESET_4_MESSAGES = stringPreferencesKey("cycle_preset_4_messages")
        val CYCLE_PRESET_4_INTERVAL = intPreferencesKey("cycle_preset_4_interval")

        val CYCLE_PRESET_5_NAME = stringPreferencesKey("cycle_preset_5_name")
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
    // Cycle persistence (current)
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
    val afkPreset1Name = get(AFK_PRESET_1_NAME, "Preset 1")
    val afkPreset1Text = get(AFK_PRESET_1_TEXT, "")

    val afkPreset2Name = get(AFK_PRESET_2_NAME, "Preset 2")
    val afkPreset2Text = get(AFK_PRESET_2_TEXT, "")

    val afkPreset3Name = get(AFK_PRESET_3_NAME, "Preset 3")
    val afkPreset3Text = get(AFK_PRESET_3_TEXT, "")

    suspend fun saveAfkPreset(slot: Int, name: String, text: String) {
        dataStore.edit { p ->
            when (slot.coerceIn(1, 3)) {
                1 -> { p[AFK_PRESET_1_NAME] = name; p[AFK_PRESET_1_TEXT] = text }
                2 -> { p[AFK_PRESET_2_NAME] = name; p[AFK_PRESET_2_TEXT] = text }
                else -> { p[AFK_PRESET_3_NAME] = name; p[AFK_PRESET_3_TEXT] = text }
            }
        }
    }

    // ----------------------------
    // Cycle Presets (5)
    // ----------------------------
    val cyclePreset1Name = get(CYCLE_PRESET_1_NAME, "Preset 1")
    val cyclePreset1Messages = get(CYCLE_PRESET_1_MESSAGES, "")
    val cyclePreset1Interval = get(CYCLE_PRESET_1_INTERVAL, 3)

    val cyclePreset2Name = get(CYCLE_PRESET_2_NAME, "Preset 2")
    val cyclePreset2Messages = get(CYCLE_PRESET_2_MESSAGES, "")
    val cyclePreset2Interval = get(CYCLE_PRESET_2_INTERVAL, 3)

    val cyclePreset3Name = get(CYCLE_PRESET_3_NAME, "Preset 3")
    val cyclePreset3Messages = get(CYCLE_PRESET_3_MESSAGES, "")
    val cyclePreset3Interval = get(CYCLE_PRESET_3_INTERVAL, 3)

    val cyclePreset4Name = get(CYCLE_PRESET_4_NAME, "Preset 4")
    val cyclePreset4Messages = get(CYCLE_PRESET_4_MESSAGES, "")
    val cyclePreset4Interval = get(CYCLE_PRESET_4_INTERVAL, 3)

    val cyclePreset5Name = get(CYCLE_PRESET_5_NAME, "Preset 5")
    val cyclePreset5Messages = get(CYCLE_PRESET_5_MESSAGES, "")
    val cyclePreset5Interval = get(CYCLE_PRESET_5_INTERVAL, 3)

    suspend fun saveCyclePreset(slot: Int, name: String, messages: String, intervalSeconds: Int) {
        dataStore.edit { p ->
            val interval = intervalSeconds.coerceAtLeast(2)
            when (slot.coerceIn(1, 5)) {
                1 -> { p[CYCLE_PRESET_1_NAME] = name; p[CYCLE_PRESET_1_MESSAGES] = messages; p[CYCLE_PRESET_1_INTERVAL] = interval }
                2 -> { p[CYCLE_PRESET_2_NAME] = name; p[CYCLE_PRESET_2_MESSAGES] = messages; p[CYCLE_PRESET_2_INTERVAL] = interval }
                3 -> { p[CYCLE_PRESET_3_NAME] = name; p[CYCLE_PRESET_3_MESSAGES] = messages; p[CYCLE_PRESET_3_INTERVAL] = interval }
                4 -> { p[CYCLE_PRESET_4_NAME] = name; p[CYCLE_PRESET_4_MESSAGES] = messages; p[CYCLE_PRESET_4_INTERVAL] = interval }
                else -> { p[CYCLE_PRESET_5_NAME] = name; p[CYCLE_PRESET_5_MESSAGES] = messages; p[CYCLE_PRESET_5_INTERVAL] = interval }
            }
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
