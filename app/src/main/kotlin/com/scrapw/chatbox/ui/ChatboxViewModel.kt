package com.scrapw.chatbox.ui

import android.util.Log
import androidx.annotation.MainThread
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scrapw.chatbox.ChatboxApplication
import com.scrapw.chatbox.UpdateInfo
import com.scrapw.chatbox.UpdateStatus
import com.scrapw.chatbox.checkUpdate
import com.scrapw.chatbox.data.UserPreferencesRepository
import com.scrapw.chatbox.osc.ChatboxOSC
import com.scrapw.chatbox.ui.mainScreen.ConversationUiState
import com.scrapw.chatbox.ui.mainScreen.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

class ChatboxViewModel(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    companion object {
        private lateinit var instance: ChatboxViewModel

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as ChatboxApplication)
                instance = ChatboxViewModel(application.userPreferencesRepository)
                Log.d("ChatboxViewModel", "Init")
                instance
            }
        }

        // https://stackoverflow.com/a/61918988
        @MainThread
        fun getInstance(): ChatboxViewModel {
            if (!isInstanceInitialized()) {
                throw Exception("ChatboxViewModel is not initialized!")
            }
            Log.d("ChatboxViewModel", "getInstance()")
            return instance
        }

        fun isInstanceInitialized(): Boolean = ::instance.isInitialized
    }

    override fun onCleared() {
        Log.d("ChatboxViewModel", "onCleared()")
        stopCycle()
        super.onCleared()
    }

    val conversationUiState = ConversationUiState()

    private val storedIpState: StateFlow<String> =
        userPreferencesRepository.ipAddress
            .map { it }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ""
            )

    private val userInputIpState = MutableStateFlow("")
    var ipAddressLocked by mutableStateOf(false)

    private val ipFlow = listOf(storedIpState, userInputIpState).asFlow().flattenMerge()

    // KEEP: used all over the app
    val messengerUiState: StateFlow<MessengerUiState> = combine(
        ipFlow,
        userPreferencesRepository.isRealtimeMsg,
        userPreferencesRepository.isTriggerSfx,
        userPreferencesRepository.isTypingIndicator,
        userPreferencesRepository.isSendImmediately
    ) { ipAddress, isRealtimeMsg, isTriggerSFX, isTypingIndicator, isSendImmediately ->
        MessengerUiState(
            ipAddress = ipAddress,
            isRealtimeMsg = isRealtimeMsg,
            isTriggerSFX = isTriggerSFX,
            isTypingIndicator = isTypingIndicator,
            isSendImmediately = isSendImmediately
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MessengerUiState()
    )

    private val remoteChatboxOSC = ChatboxOSC(
        ipAddress = runBlocking { userPreferencesRepository.ipAddress.first() },
        port = 9000
    )

    private val localChatboxOSC = ChatboxOSC(
        ipAddress = "localhost",
        port = 9000
    )

    val messageText = mutableStateOf(TextFieldValue(""))

    fun onIpAddressChange(ip: String) {
        userInputIpState.value = ip
    }

    fun ipAddressApply(address: String) {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.IO) {
                remoteChatboxOSC.ipAddress = address
                isAddressResolvable.value = remoteChatboxOSC.addressResolvable
                if (!isAddressResolvable.value) {
                    ipAddressLocked = false
                }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.saveIpAddress(address)
        }
    }

    fun portApply(port: Int) {
        remoteChatboxOSC.port = port
        viewModelScope.launch {
            userPreferencesRepository.savePort(port)
        }
    }

    val isAddressResolvable = mutableStateOf(true)

    // KEEP signature: overlay expects (TextFieldValue, Boolean)
    fun onMessageTextChange(message: TextFieldValue, local: Boolean = false) {
        val osc = if (!local) remoteChatboxOSC else localChatboxOSC

        messageText.value = message
        if (messengerUiState.value.isRealtimeMsg) {
            osc.sendRealtimeMessage(message.text)
        } else {
            if (messengerUiState.value.isTypingIndicator) {
                osc.typing = message.text.isNotEmpty()
            }
        }
    }

    // KEEP signature: overlay calls sendMessage(local=true/false)
    fun sendMessage(local: Boolean = false) {
        val osc = if (!local) remoteChatboxOSC else localChatboxOSC

        osc.sendMessage(
            messageText.value.text,
            messengerUiState.value.isSendImmediately,
            messengerUiState.value.isTriggerSFX
        )
        osc.typing = false

        conversationUiState.addMessage(
            Message(
                messageText.value.text,
                false,
                Instant.now()
            )
        )

        messageText.value = TextFieldValue("", TextRange.Zero)
    }

    // KEEP signature: overlay uses stashMessage(local=true/false)
    fun stashMessage(local: Boolean = false) {
        val osc = if (!local) remoteChatboxOSC else localChatboxOSC
        osc.typing = false

        conversationUiState.addMessage(
            Message(
                messageText.value.text,
                true,
                Instant.now()
            )
        )

        messageText.value = TextFieldValue("", TextRange.Zero)
    }

    fun onRealtimeMsgChanged(isChecked: Boolean) {
        viewModelScope.launch { userPreferencesRepository.saveIsRealtimeMsg(isChecked) }
    }

    fun onTriggerSfxChanged(isChecked: Boolean) {
        viewModelScope.launch { userPreferencesRepository.saveIsTriggerSFX(isChecked) }
    }

    fun onTypingIndicatorChanged(isChecked: Boolean) {
        viewModelScope.launch { userPreferencesRepository.saveTypingIndicator(isChecked) }
    }

    fun onSendImmediatelyChanged(isChecked: Boolean) {
        viewModelScope.launch { userPreferencesRepository.saveIsSendImmediately(isChecked) }
    }

    // ----------------------------
    // Update checker (KEEP)
    // ----------------------------
    private var updateChecked = false
    var updateInfo by mutableStateOf(UpdateInfo(UpdateStatus.NOT_CHECKED))
    fun checkUpdate() {
        if (updateChecked) return
        updateChecked = true

        viewModelScope.launch(Dispatchers.Main) {
            updateInfo = checkUpdate("ScrapW", "Chatbox")
        }
    }

    // ============================
    // NEW: Persistent Cycle / AFK / Presets
    // ============================

    // Cycle
    var cycleEnabled by mutableStateOf(false)
    var cycleMessages by mutableStateOf("")
    var cycleIntervalSeconds by mutableStateOf(3)
    private var cycleJob: Job? = null

    fun startCycle(local: Boolean = false) {
        val msgs = cycleMessages.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (!cycleEnabled || msgs.isEmpty()) return

        cycleJob?.cancel()
        cycleJob = viewModelScope.launch {
            var i = 0
            while (cycleEnabled) {
                // For now this sends raw lines; Spotify comes next version
                messageText.value = TextFieldValue(msgs[i], TextRange(msgs[i].length))
                sendMessage(local)
                i = (i + 1) % msgs.size
                delay(cycleIntervalSeconds.coerceAtLeast(1).toLong() * 1000L)
            }
        }
    }

    fun stopCycle() {
        cycleJob?.cancel()
        cycleJob = null
    }

    // AFK (toggle, not automatic)
    var afkEnabled by mutableStateOf(false)
    var afkMessage by mutableStateOf("AFK 🌙 back soon")

    fun sendAfkNow(local: Boolean = false) {
        if (!afkEnabled) return
        val text = afkMessage.trim().ifEmpty { "AFK" }
        messageText.value = TextFieldValue(text, TextRange(text.length))
        sendMessage(local)
    }

    // Presets (persist custom presets as JSON)
    data class TextPreset(val name: String, val intervalSeconds: Int, val messages: String)

    private val builtInPresets = listOf(
        TextPreset("Cute intro 💕", 3, "hi hi 💗\nfollow me on vrchat\n{SPOTIFY}"),
        TextPreset("Chill ✨", 5, "vibing ✨\nbe kind 🤍\n{SPOTIFY}"),
        TextPreset("Minimal", 6, "{SPOTIFY}")
    )

    private val customPresets = mutableStateListOf<TextPreset>()
    val presets: List<TextPreset> get() = builtInPresets + customPresets

    var selectedPresetName by mutableStateOf(builtInPresets.first().name)

    fun applyPresetByName(name: String) {
        val p = presets.firstOrNull { it.name == name } ?: return
        selectedPresetName = p.name
        cycleEnabled = true
        cycleIntervalSeconds = p.intervalSeconds
        cycleMessages = p.messages
    }

    fun saveCurrentAsPreset(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val p = TextPreset(clean, cycleIntervalSeconds.coerceAtLeast(1), cycleMessages)
        val idx = customPresets.indexOfFirst { it.name == clean }
        if (idx >= 0) customPresets[idx] = p else customPresets.add(p)
        selectedPresetName = clean
    }

    fun deleteCustomPreset(name: String) {
        val idx = customPresets.indexOfFirst { it.name == name }
        if (idx >= 0) customPresets.removeAt(idx)
        if (selectedPresetName == name) selectedPresetName = builtInPresets.first().name
    }

    fun isCustomPreset(name: String): Boolean = customPresets.any { it.name == name }

    // ---- Persistence wiring ----
    init {
        // load once
        viewModelScope.launch {
            cycleEnabled = userPreferencesRepository.cycleEnabled.first()
            cycleMessages = userPreferencesRepository.cycleMessages.first()
            cycleIntervalSeconds = userPreferencesRepository.cycleInterval.first()

            afkEnabled = userPreferencesRepository.afkEnabled.first()
            afkMessage = userPreferencesRepository.afkMessage.first()

            selectedPresetName = userPreferencesRepository.selectedPreset.first()
                .ifBlank { builtInPresets.first().name }

            val json = userPreferencesRepository.presetsJson.first()
            customPresets.clear()
            customPresets.addAll(decodePresetsJson(json))
        }

        // save on change (captures UI direct assignments too)
        viewModelScope.launch {
            snapshotFlow { cycleEnabled }.collect { userPreferencesRepository.saveCycleEnabled(it) }
        }
        viewModelScope.launch {
            snapshotFlow { cycleMessages }.collect { userPreferencesRepository.saveCycleMessages(it) }
        }
        viewModelScope.launch {
            snapshotFlow { cycleIntervalSeconds }.collect { userPreferencesRepository.saveCycleInterval(it) }
        }
        viewModelScope.launch {
            snapshotFlow { afkEnabled }.collect { userPreferencesRepository.saveAfkEnabled(it) }
        }
        viewModelScope.launch {
            snapshotFlow { afkMessage }.collect { userPreferencesRepository.saveAfkMessage(it) }
        }
        viewModelScope.launch {
            snapshotFlow { selectedPresetName }.collect { userPreferencesRepository.saveSelectedPreset(it) }
        }
        viewModelScope.launch {
            snapshotFlow { customPresets.toList() }.collect {
                userPreferencesRepository.savePresetsJson(encodePresetsJson(it))
            }
        }
    }

    private fun encodePresetsJson(list: List<TextPreset>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("name", it.name)
                put("interval", it.intervalSeconds)
                put("messages", it.messages)
            })
        }
        return arr.toString()
    }

    private fun decodePresetsJson(json: String): List<TextPreset> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { idx ->
                val o = arr.getJSONObject(idx)
                TextPreset(
                    name = o.getString("name"),
                    intervalSeconds = o.getInt("interval"),
                    messages = o.getString("messages")
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}

data class MessengerUiState(
    val ipAddress: String = "127.0.0.1",
    val isRealtimeMsg: Boolean = false,
    val isTriggerSFX: Boolean = true,
    val isTypingIndicator: Boolean = true,
    val isSendImmediately: Boolean = true
)

