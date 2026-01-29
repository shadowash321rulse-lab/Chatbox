package com.scrapw.chatbox.ui

import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.*
import com.scrapw.chatbox.ChatboxApplication
import com.scrapw.chatbox.data.UserPreferencesRepository
import com.scrapw.chatbox.osc.ChatboxOSC
import com.scrapw.chatbox.ui.mainScreen.ConversationUiState
import com.scrapw.chatbox.ui.mainScreen.Message
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

class ChatboxViewModel(
    private val repo: UserPreferencesRepository
) : ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ChatboxApplication
                ChatboxViewModel(app.userPreferencesRepository)
            }
        }
    }

    val conversationUiState = ConversationUiState()
    val messageText = mutableStateOf(TextFieldValue(""))
    val isAddressResolvable = mutableStateOf(true)

    private val osc = ChatboxOSC(runBlocking { repo.ipAddress.first() }, 9000)

    // ===== persisted states =====
    var cycleEnabled by mutableStateOf(false)
    var cycleMessages by mutableStateOf("")
    var cycleIntervalSeconds by mutableStateOf(3)

    var afkEnabled by mutableStateOf(false)
    var afkMessage by mutableStateOf("AFK 🌙 back soon")

    var selectedPresetName by mutableStateOf("")

    data class Preset(val name: String, val interval: Int, val messages: String)
    var presets by mutableStateOf(listOf<Preset>())

    private var cycleJob: Job? = null

    init {
        viewModelScope.launch {
            cycleEnabled = repo.cycleEnabled.first()
            cycleMessages = repo.cycleMessages.first()
            cycleIntervalSeconds = repo.cycleInterval.first()
            afkEnabled = repo.afkEnabled.first()
            afkMessage = repo.afkMessage.first()
            selectedPresetName = repo.selectedPreset.first()

            presets = loadPresets(repo.presetsJson.first())
        }
    }

    // ===== persistence helpers =====
    private suspend fun persistAll() {
        repo.saveCycleEnabled(cycleEnabled)
        repo.saveCycleMessages(cycleMessages)
        repo.saveCycleInterval(cycleIntervalSeconds)
        repo.saveAfkEnabled(afkEnabled)
        repo.saveAfkMessage(afkMessage)
        repo.saveSelectedPreset(selectedPresetName)
        repo.savePresetsJson(savePresets())
    }

    // ===== cycle =====
    fun startCycle() {
        val lines = cycleMessages.lines().filter { it.isNotBlank() }
        if (!cycleEnabled || lines.isEmpty()) return

        cycleJob?.cancel()
        cycleJob = viewModelScope.launch {
            var i = 0
            while (cycleEnabled) {
                sendText(lines[i])
                i = (i + 1) % lines.size
                delay(cycleIntervalSeconds.coerceAtLeast(1) * 1000L)
            }
        }
        viewModelScope.launch { persistAll() }
    }

    fun stopCycle() {
        cycleJob?.cancel()
        cycleJob = null
        viewModelScope.launch { persistAll() }
    }

    // ===== AFK =====
    fun sendAfkNow() {
        if (!afkEnabled) return
        sendText(afkMessage.ifBlank { "AFK" })
    }

    // ===== presets =====
    fun applyPreset(p: Preset) {
        selectedPresetName = p.name
        cycleMessages = p.messages
        cycleIntervalSeconds = p.interval
        cycleEnabled = true
        viewModelScope.launch { persistAll() }
    }

    fun savePreset(name: String) {
        if (name.isBlank()) return
        presets = presets.filter { it.name != name } +
                Preset(name, cycleIntervalSeconds, cycleMessages)
        selectedPresetName = name
        viewModelScope.launch { persistAll() }
    }

    fun deletePreset(name: String) {
        presets = presets.filter { it.name != name }
        if (selectedPresetName == name) selectedPresetName = ""
        viewModelScope.launch { persistAll() }
    }

    // ===== messaging =====
    fun onMessageTextChange(v: TextFieldValue) {
        messageText.value = v
    }

    fun sendMessage() {
        sendText(messageText.value.text)
        messageText.value = TextFieldValue("", TextRange.Zero)
    }

    private fun sendText(text: String) {
        osc.sendMessage(text, true, true)
        conversationUiState.addMessage(Message(text, false, Instant.now()))
    }

    // ===== JSON =====
    private fun savePresets(): String {
        val arr = JSONArray()
        presets.forEach {
            arr.put(JSONObject().apply {
                put("name", it.name)
                put("interval", it.interval)
                put("messages", it.messages)
            })
        }
        return arr.toString()
    }

    private fun loadPresets(json: String): List<Preset> {
        if (json.isBlank()) return emptyList()
        val arr = JSONArray(json)
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            Preset(o.getString("name"), o.getInt("interval"), o.getString("messages"))
        }
    }
}
