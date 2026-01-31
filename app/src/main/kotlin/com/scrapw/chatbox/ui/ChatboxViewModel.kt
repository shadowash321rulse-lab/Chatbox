package com.scrapw.chatbox.ui

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
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
import com.scrapw.chatbox.NowPlayingState
import com.scrapw.chatbox.UpdateInfo
import com.scrapw.chatbox.UpdateStatus
import com.scrapw.chatbox.checkUpdate
import com.scrapw.chatbox.data.UserPreferencesRepository
import com.scrapw.chatbox.osc.ChatboxOSC
import com.scrapw.chatbox.ui.mainScreen.ConversationUiState
import com.scrapw.chatbox.ui.mainScreen.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

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

        @MainThread
        fun getInstance(): ChatboxViewModel {
            if (!isInstanceInitialized()) throw Exception("ChatboxViewModel is not initialized!")
            return instance
        }

        fun isInstanceInitialized(): Boolean = ::instance.isInitialized
    }

    override fun onCleared() {
        stopAll(clearFromChatbox = false)
        super.onCleared()
    }

    // =========================
    // Conversation / manual messages
    // =========================
    val conversationUiState = ConversationUiState()

    val messageText = mutableStateOf(TextFieldValue(""))

    // =========================
    // Stored + live messenger state
    // =========================
    private val storedIpState: StateFlow<String> =
        userPreferencesRepository.ipAddress.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "127.0.0.1"
        )

    private val userInputIpState = kotlinx.coroutines.flow.MutableStateFlow("")

    private val ipFlow = combine(storedIpState, userInputIpState) { a, b ->
        if (b.isNotBlank()) b else a
    }

    val messengerUiState: StateFlow<MessengerUiState> = combine(
        ipFlow,
        userPreferencesRepository.isRealtimeMsg,
        userPreferencesRepository.isTriggerSfx,
        userPreferencesRepository.isTypingIndicator,
        userPreferencesRepository.isSendImmediately
    ) { ipAddress, isRealtimeMsg, isTriggerSfx, isTypingIndicator, isSendImmediately ->
        MessengerUiState(
            ipAddress = ipAddress,
            isRealtimeMsg = isRealtimeMsg,
            isTriggerSFX = isTriggerSfx,
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

    fun onIpAddressChange(ip: String) {
        userInputIpState.value = ip
    }

    fun ipAddressApply(address: String) {
        remoteChatboxOSC.ipAddress = address
        viewModelScope.launch { userPreferencesRepository.saveIpAddress(address) }
    }

    fun portApply(port: Int) {
        remoteChatboxOSC.port = port
        viewModelScope.launch { userPreferencesRepository.savePort(port) }
    }

    // overlay expects (TextFieldValue, Boolean)
    fun onMessageTextChange(message: TextFieldValue, local: Boolean = false) {
        val osc = if (!local) remoteChatboxOSC else localChatboxOSC
        messageText.value = message

        if (messengerUiState.value.isRealtimeMsg) {
            osc.sendRealtimeMessage(message.text)
        } else if (messengerUiState.value.isTypingIndicator) {
            osc.typing = message.text.isNotEmpty()
        }
    }

    fun sendMessage(local: Boolean = false) {
        val osc = if (!local) remoteChatboxOSC else localChatboxOSC

        // Force no SFX regardless of preference (you asked to remove the sound)
        osc.sendMessage(
            messageText.value.text,
            messengerUiState.value.isSendImmediately,
            triggerSFX = false
        )
        osc.typing = false

        conversationUiState.addMessage(
            Message(messageText.value.text, false, Instant.now())
        )

        messageText.value = TextFieldValue("", TextRange.Zero)
    }

    fun stashMessage(local: Boolean = false) {
        val osc = if (!local) remoteChatboxOSC else localChatboxOSC
        osc.typing = false

        conversationUiState.addMessage(
            Message(messageText.value.text, true, Instant.now())
        )

        messageText.value = TextFieldValue("", TextRange.Zero)
    }

    fun onRealtimeMsgChanged(isChecked: Boolean) =
        viewModelScope.launch { userPreferencesRepository.saveIsRealtimeMsg(isChecked) }

    fun onTriggerSfxChanged(isChecked: Boolean) =
        viewModelScope.launch { userPreferencesRepository.saveIsTriggerSFX(isChecked) }

    fun onTypingIndicatorChanged(isChecked: Boolean) =
        viewModelScope.launch { userPreferencesRepository.saveTypingIndicator(isChecked) }

    fun onSendImmediatelyChanged(isChecked: Boolean) =
        viewModelScope.launch { userPreferencesRepository.saveIsSendImmediately(isChecked) }

    // =========================
    // Update checker (keep)
    // =========================
    private var updateChecked = false
    var updateInfo by mutableStateOf(UpdateInfo(UpdateStatus.NOT_CHECKED))
        private set

    fun checkUpdate() {
        if (updateChecked) return
        updateChecked = true
        viewModelScope.launch(Dispatchers.Main) {
            updateInfo = checkUpdate("ScrapW", "Chatbox")
        }
    }

    // =========================
    // Global send throttling (hard floor)
    // =========================
    var minSendIntervalSeconds by mutableStateOf(2) // hard floor
        private set

    private var lastCombinedSendMs = 0L

    // =========================
    // AFK (TEXT persists, enabled does NOT persist)
    // =========================
    var afkEnabled by mutableStateOf(false)

    var afkMessage by mutableStateOf("")
        private set

    private val afkForcedIntervalSeconds = 12 // forced, no UI slider
    private var afkJob: Job? = null

    // AFK Presets (3) - name + text (both persist)
    private val afkPresetNameState = Array(3) { mutableStateOf("Preset ${it + 1}") }
    private val afkPresetTextState = Array(3) { mutableStateOf("") }

    // =========================
    // Cycle
    // =========================
    var cycleEnabled by mutableStateOf(false)
    var cycleIntervalSeconds by mutableStateOf(3)

    private var cycleJob: Job? = null
    private var cycleIndex = 0

    // New: cycle lines list (max 10)
    val cycleLines = mutableStateListOf<String>()

    // Cycle Presets (5) - name + messages + interval (persist)
    private val cyclePresetNameState = Array(5) { mutableStateOf("Preset ${it + 1}") }
    private val cyclePresetMessagesState = Array(5) { mutableStateOf("") }
    private val cyclePresetIntervalState = Array(5) { mutableStateOf(3) }

    // =========================
    // Now Playing (phone music) – UI still calls these “spotify”
    // =========================
    var spotifyEnabled by mutableStateOf(false)
    var spotifyDemoEnabled by mutableStateOf(false)

    // 1..5 (your presets)
    var spotifyPreset by mutableStateOf(1)

    // refresh seconds for progress updates (separate from cycle speed)
    var musicRefreshSeconds by mutableStateOf(2)
    private var nowPlayingJob: Job? = null

    // Debug fields shown in UI
    var listenerConnected by mutableStateOf(false)
        private set
    var activePackage by mutableStateOf("(none)")
        private set
    var nowPlayingDetected by mutableStateOf(false)
        private set
    var lastNowPlayingTitle by mutableStateOf("(blank)")
        private set
    var lastNowPlayingArtist by mutableStateOf("(blank)")
        private set
    var lastSentToVrchatAtMs by mutableStateOf(0L)
        private set

    var nowPlayingIsPlaying by mutableStateOf(false)
        private set

    // Raw now playing snapshot parts
    private var nowPlayingDurationMs: Long = 0L
    private var nowPlayingPositionMs: Long = 0L
    private var nowPlayingPositionUpdateTimeMs: Long = 0L
    private var nowPlayingSpeed: Float = 1f

    // =========================
    // Debug OSC preview strings
    // =========================
    var debugLastAfkOsc by mutableStateOf("")
        private set
    var debugLastCycleOsc by mutableStateOf("")
        private set
    var debugLastMusicOsc by mutableStateOf("")
        private set
    var debugLastCombinedOsc by mutableStateOf("")
        private set

    // =========================
    // Info doc (Full Doc tab)
    // =========================
    val fullInfoDocumentText: String = """
VRC-A (VRChat Assistant)
Made by: Ashoska Mitsu Sisko
Base: ScrapW’s Chatbox base (heavily revamped)

============================================================
WHAT THIS APP IS
============================================================
VRC-A is an Android app that sends text to VRChat’s Chatbox using OSC.
It’s meant for standalone / mobile-friendly setups where you want:
- A quick “Send message” tool
- A cycling status / rotating messages system
- A live “Now Playing” music block (from your phone’s media notifications)
- An AFK tag at the very top

VRC-A is designed to be “easy to test” with debug indicators so you can tell
what’s failing (connection, permissions, detection, etc).

============================================================
IMPORTANT: VRChat OSC MUST BE ON
============================================================
VRChat → Settings → OSC → Enable OSC.

============================================================
END
============================================================
""".trimIndent()

    // =========================
    // Init: bind DataStore flows
    // =========================
    init {
        // AFK text persists
        viewModelScope.launch {
            userPreferencesRepository.afkMessage.collect { afkMessage = it }
        }

        // Cycle state (enabled/messages/interval)
        viewModelScope.launch {
            userPreferencesRepository.cycleEnabled.collect { cycleEnabled = it }
        }
        viewModelScope.launch {
            userPreferencesRepository.cycleMessages.collect { text ->
                setCycleLinesFromText(text)
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.cycleInterval.collect { cycleIntervalSeconds = it.coerceAtLeast(2) }
        }

        // AFK preset names + texts (3)
        viewModelScope.launch { userPreferencesRepository.afkPreset1Name.collect { afkPresetNameState[0].value = it } }
        viewModelScope.launch { userPreferencesRepository.afkPreset1Text.collect { afkPresetTextState[0].value = it } }

        viewModelScope.launch { userPreferencesRepository.afkPreset2Name.collect { afkPresetNameState[1].value = it } }
        viewModelScope.launch { userPreferencesRepository.afkPreset2Text.collect { afkPresetTextState[1].value = it } }

        viewModelScope.launch { userPreferencesRepository.afkPreset3Name.collect { afkPresetNameState[2].value = it } }
        viewModelScope.launch { userPreferencesRepository.afkPreset3Text.collect { afkPresetTextState[2].value = it } }

        // Cycle preset names + messages + intervals (5)
        viewModelScope.launch { userPreferencesRepository.cyclePreset1Name.collect { cyclePresetNameState[0].value = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset1Messages.collect { cyclePresetMessagesState[0].value = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset1Interval.collect { cyclePresetIntervalState[0].value = it.coerceAtLeast(2) } }

        viewModelScope.launch { userPreferencesRepository.cyclePreset2Name.collect { cyclePresetNameState[1].value = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset2Messages.collect { cyclePresetMessagesState[1].value = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset2Interval.collect { cyclePresetIntervalState[1].value = it.coerceAtLeast(2) } }

        viewModelScope.launch { userPreferencesRepository.cyclePreset3Name.collect { cyclePresetNameState[2].value = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset3Messages.collect { cyclePresetMessagesState[2].value = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset3Interval.collect { cyclePresetIntervalState[2].value = it.coerceAtLeast(2) } }

        viewModelScope.launch { userPreferencesRepository.cyclePreset4Name.collect { cyclePresetNameState[3].value = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset4Messages.collect { cyclePresetMessagesState[3].value = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset4Interval.collect { cyclePresetIntervalState[3].value = it.coerceAtLeast(2) } }

        viewModelScope.launch { userPreferencesRepository.cyclePreset5Name.collect { cyclePresetNameState[4].value = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset5Messages.collect { cyclePresetMessagesState[4].value = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset5Interval.collect { cyclePresetIntervalState[4].value = it.coerceAtLeast(2) } }

        // Bind NowPlayingState into fields
        viewModelScope.launch {
            NowPlayingState.state.collect { s ->
                listenerConnected = s.listenerConnected
                activePackage = if (s.activePackage.isBlank()) "(none)" else s.activePackage
                nowPlayingDetected = s.detected
                lastNowPlayingTitle = if (s.title.isBlank()) "(blank)" else s.title
                lastNowPlayingArtist = if (s.artist.isBlank()) "(blank)" else s.artist

                nowPlayingDurationMs = s.durationMs
                nowPlayingPositionMs = s.positionMs
                nowPlayingPositionUpdateTimeMs = s.positionUpdateTimeMs
                nowPlayingSpeed = s.playbackSpeed
                nowPlayingIsPlaying = s.isPlaying
            }
        }
    }

    // =========================
    // Notification Access intent
    // =========================
    fun notificationAccessIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // =========================
    // AFK text update (persists) + immediate rebuild
    // =========================
    fun updateAfkText(text: String) {
        afkMessage = text
        viewModelScope.launch { userPreferencesRepository.saveAfkMessage(text) }
        rebuildAndMaybeSendCombined(forceSend = false)
    }

    // =========================
    // Cycle persistence + list editing (max 10)
    // =========================
    private fun setCycleLinesFromText(text: String) {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(10)
        cycleLines.clear()
        cycleLines.addAll(lines)
        rebuildAndMaybeSendCombined(forceSend = false)
    }

    private fun persistCycleLines() {
        val joined = cycleLines.map { it.trim() }.filter { it.isNotEmpty() }.take(10).joinToString("\n")
        viewModelScope.launch { userPreferencesRepository.saveCycleMessages(joined) }
    }

    fun addCycleLine() {
        if (cycleLines.size >= 10) return
        cycleLines.add("")
        persistCycleLines()
    }

    fun removeCycleLine(index: Int) {
        if (index !in cycleLines.indices) return
        cycleLines.removeAt(index)
        persistCycleLines()
        rebuildAndMaybeSendCombined(forceSend = false)
    }

    fun updateCycleLine(index: Int, value: String) {
        if (index !in cycleLines.indices) return
        cycleLines[index] = value
        persistCycleLines()
        rebuildAndMaybeSendCombined(forceSend = false)
    }

    fun clearCycleLines() {
        cycleLines.clear()
        persistCycleLines()
        rebuildAndMaybeSendCombined(forceSend = false)
    }

    fun persistCycleEnabledNow() {
        viewModelScope.launch { userPreferencesRepository.saveCycleEnabled(cycleEnabled) }
    }

    fun updateCycleInterval(seconds: Int) {
        cycleIntervalSeconds = seconds.coerceAtLeast(2)
        viewModelScope.launch { userPreferencesRepository.saveCycleInterval(cycleIntervalSeconds) }
    }

    // =========================
    // AFK Presets UI API (3)
    // =========================
    fun getAfkPresetName(slot: Int): String {
        val i = slot.coerceIn(1, 3) - 1
        return afkPresetNameState[i].value
    }

    fun updateAfkPresetName(slot: Int, name: String) {
        val i = slot.coerceIn(1, 3) - 1
        afkPresetNameState[i].value = name
        // save name but keep existing text
        val text = afkPresetTextState[i].value
        viewModelScope.launch { userPreferencesRepository.saveAfkPreset(slot.coerceIn(1, 3), name, text) }
    }

    fun getAfkPresetTextPreview(slot: Int): String {
        val i = slot.coerceIn(1, 3) - 1
        return afkPresetTextState[i].value.trim()
    }

    fun loadAfkPresetSlot(slot: Int) {
        val i = slot.coerceIn(1, 3) - 1
        updateAfkText(afkPresetTextState[i].value)
    }

    fun saveAfkPresetSlot(slot: Int) {
        val i = slot.coerceIn(1, 3) - 1
        val name = afkPresetNameState[i].value
        val text = afkMessage
        afkPresetTextState[i].value = text // instant UI update
        viewModelScope.launch { userPreferencesRepository.saveAfkPreset(slot.coerceIn(1, 3), name, text) }
    }

    // =========================
    // Cycle Presets UI API (5)
    // =========================
    fun getCyclePresetName(slot: Int): String {
        val i = slot.coerceIn(1, 5) - 1
        return cyclePresetNameState[i].value
    }

    fun updateCyclePresetName(slot: Int, name: String) {
        val i = slot.coerceIn(1, 5) - 1
        cyclePresetNameState[i].value = name

        val messages = cyclePresetMessagesState[i].value
        val interval = cyclePresetIntervalState[i].value
        viewModelScope.launch {
            userPreferencesRepository.saveCyclePreset(slot.coerceIn(1, 5), name, messages, interval)
        }
    }

    fun getCyclePresetFirstLinePreview(slot: Int): String {
        val i = slot.coerceIn(1, 5) - 1
        val firstLine = cyclePresetMessagesState[i].value
            .lines()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()
        return firstLine
    }

    fun loadCyclePresetSlot(slot: Int) {
        val i = slot.coerceIn(1, 5) - 1
        val messages = cyclePresetMessagesState[i].value
        val interval = cyclePresetIntervalState[i].value.coerceAtLeast(2)

        cycleIntervalSeconds = interval
        viewModelScope.launch { userPreferencesRepository.saveCycleInterval(cycleIntervalSeconds) }

        setCycleLinesFromText(messages)
        persistCycleLines()
        rebuildAndMaybeSendCombined(forceSend = false)
    }

    fun saveCyclePresetSlot(slot: Int) {
        val i = slot.coerceIn(1, 5) - 1

        val name = cyclePresetNameState[i].value
        val messages = cycleLines.map { it.trim() }.filter { it.isNotEmpty() }.take(10).joinToString("\n")
        val interval = cycleIntervalSeconds.coerceAtLeast(2)

        cyclePresetMessagesState[i].value = messages
        cyclePresetIntervalState[i].value = interval

        viewModelScope.launch {
            userPreferencesRepository.saveCyclePreset(slot.coerceIn(1, 5), name, messages, interval)
        }
    }

    // =========================
    // Music: flags + preset selector
    // =========================
    fun setSpotifyEnabledFlag(enabled: Boolean) {
        spotifyEnabled = enabled
        rebuildAndMaybeSendCombined(forceSend = true)
        if (!enabled) stopNowPlayingSender(clearFromChatbox = true)
    }

    fun setSpotifyDemoFlag(enabled: Boolean) {
        spotifyDemoEnabled = enabled
        rebuildAndMaybeSendCombined(forceSend = true)
    }

    fun updateSpotifyPreset(preset: Int) {
        spotifyPreset = preset.coerceIn(1, 5)
        rebuildAndMaybeSendCombined(forceSend = true)
    }

    /**
     * UI animated preview helper (t = 0..1).
     * This does NOT affect sending, it is only for the Now Playing preset preview list.
     */
    fun renderMusicPreview(presetId: Int, t: Float): String {
        val p = t.coerceIn(0f, 1f)
        val slots = when (presetId.coerceIn(1, 5)) {
            1 -> 8
            2 -> 10
            3 -> 10
            4 -> 10
            else -> 10
        }
        val idx = (p * (slots - 1)).toInt().coerceIn(0, slots - 1)

        return when (presetId.coerceIn(1, 5)) {
            1 -> { // Love
                val inner = CharArray(8) { '━' }
                inner[idx.coerceIn(0, 7)] = '◉'
                "♡" + inner.concatToString() + "♡"
            }
            2 -> { // Minimal
                val bg = CharArray(10) { '─' }
                bg[idx] = '◉'
                bg.concatToString()
            }
            3 -> { // Crystal
                val bg = CharArray(10) { '⟡' }
                bg[idx] = '◉'
                bg.concatToString()
            }
            4 -> { // Soundwave
                val bg = charArrayOf('▁','▂','▃','▄','▅','▅','▄','▃','▂','▁')
                val out = bg.copyOf()
                out[idx] = '●'
                out.concatToString()
            }
            else -> { // Geometry
                val bg = charArrayOf('▣','▣','▣','▢','▢','▢','▢','▢','▢','▢')
                val out = bg.copyOf()
                out[idx] = '◉'
                out.concatToString()
            }
        }
    }

    // =========================
    // AFK sender
    // =========================
    fun startAfkSender(local: Boolean = false) {
        if (!afkEnabled) return

        afkJob?.cancel()
        afkJob = viewModelScope.launch {
            while (afkEnabled) {
                rebuildAndMaybeSendCombined(forceSend = true, local = local)
                delay(afkForcedIntervalSeconds * 1000L)
            }
        }
    }

    fun stopAfkSender(clearFromChatbox: Boolean) {
        afkJob?.cancel()
        afkJob = null

        if (clearFromChatbox) {
            rebuildAndMaybeSendCombined(forceSend = true, forceClearIfAllOff = true)
        }
    }

    fun sendAfkNow(local: Boolean = false) {
        rebuildAndMaybeSendCombined(forceSend = true, local = local)
    }

    // =========================
    // Cycle sender
    // =========================
    fun startCycle(local: Boolean = false) {
        val msgs = cycleLines.map { it.trim() }.filter { it.isNotEmpty() }.take(10)
        if (!cycleEnabled || msgs.isEmpty()) return

        persistCycleEnabledNow()
        persistCycleLines()
        updateCycleInterval(cycleIntervalSeconds)

        cycleJob?.cancel()
        cycleJob = viewModelScope.launch {
            cycleIndex = 0
            while (cycleEnabled) {
                rebuildAndMaybeSendCombined(
                    forceSend = true,
                    local = local,
                    cycleLineOverride = msgs[cycleIndex % msgs.size]
                )
                cycleIndex = (cycleIndex + 1) % msgs.size
                delay(cycleIntervalSeconds.coerceAtLeast(2).toLong() * 1000L)
            }
        }
    }

    fun stopCycle(clearFromChatbox: Boolean) {
        cycleJob?.cancel()
        cycleJob = null
        if (clearFromChatbox) {
            rebuildAndMaybeSendCombined(forceSend = true, forceClearIfAllOff = true)
        }
    }

    // =========================
    // Now Playing sender (independent)
    // =========================
    fun startNowPlayingSender(local: Boolean = false) {
        if (!spotifyEnabled) return

        nowPlayingJob?.cancel()
        nowPlayingJob = viewModelScope.launch {
            while (spotifyEnabled) {
                rebuildAndMaybeSendCombined(forceSend = true, local = local)
                delay(musicRefreshSeconds.coerceAtLeast(2).toLong() * 1000L)
            }
        }
    }

    fun stopNowPlayingSender(clearFromChatbox: Boolean) {
        nowPlayingJob?.cancel()
        nowPlayingJob = null
        if (clearFromChatbox) {
            rebuildAndMaybeSendCombined(forceSend = true, forceClearIfAllOff = true)
        }
    }

    fun sendNowPlayingOnce(local: Boolean = false) {
        rebuildAndMaybeSendCombined(forceSend = true, local = local)
    }

    fun stopAll(clearFromChatbox: Boolean) {
        stopCycle(clearFromChatbox = false)
        stopNowPlayingSender(clearFromChatbox = false)
        stopAfkSender(clearFromChatbox = false)

        if (clearFromChatbox) {
            clearChatbox()
        }
    }

    // =========================
    // Combined builder + throttled sender
    // =========================
    private fun rebuildAndMaybeSendCombined(
        forceSend: Boolean,
        local: Boolean = false,
        cycleLineOverride: String? = null,
        forceClearIfAllOff: Boolean = false
    ) {
        val afkLine = if (afkEnabled && afkMessage.trim().isNotEmpty()) afkMessage.trim() else ""
        val cycleLine = if (cycleEnabled) (cycleLineOverride ?: currentCycleLinePreview()) else ""
        val musicLines = if (spotifyEnabled) buildNowPlayingLines() else emptyList()

        debugLastAfkOsc = afkLine
        debugLastCycleOsc = cycleLine
        debugLastMusicOsc = musicLines.joinToString("\n")

        val lines = mutableListOf<String>()
        if (afkLine.isNotBlank()) lines += afkLine
        if (cycleLine.isNotBlank()) lines += cycleLine
        lines.addAll(musicLines)

        val combined = joinWithLimit(lines, 144)
        debugLastCombinedOsc = combined

        val nothingActive = afkLine.isBlank() && cycleLine.isBlank() && musicLines.isEmpty()

        if (forceClearIfAllOff && nothingActive) {
            clearChatbox(local)
            return
        }

        if (!forceSend) return

        val nowMs = System.currentTimeMillis()
        val minMs = minSendIntervalSeconds.coerceAtLeast(2) * 1000L
        if (nowMs - lastCombinedSendMs < minMs) return
        if (combined.isBlank()) return

        sendToVrchatRaw(combined, local, addToConversation = false)
        lastCombinedSendMs = nowMs
    }

    private fun clearChatbox(local: Boolean = false) {
        sendToVrchatRaw("", local, addToConversation = false)
    }

    private fun currentCycleLinePreview(): String {
        val msgs = cycleLines.map { it.trim() }.filter { it.isNotEmpty() }.take(10)
        if (msgs.isEmpty()) return ""
        return msgs.getOrNull(cycleIndex % msgs.size).orEmpty()
    }

    // =========================
    // Now Playing block builder (with live position estimate)
    // =========================
    private fun buildNowPlayingLines(): List<String> {
        val title = if (spotifyDemoEnabled && !nowPlayingDetected) "Pretty Girl" else lastNowPlayingTitle
        val artist = if (spotifyDemoEnabled && !nowPlayingDetected) "Clairo" else lastNowPlayingArtist

        if (!spotifyDemoEnabled && !nowPlayingDetected) return emptyList()

        val safeTitle = title.takeIf { it != "(blank)" } ?: ""
        val safeArtist = artist.takeIf { it != "(blank)" } ?: ""

        val maxLine = 42
        val combined = if (safeArtist.isNotBlank()) "$safeArtist — $safeTitle" else safeTitle
        val line1 = when {
            combined.length <= maxLine -> combined
            safeTitle.length <= maxLine -> safeTitle
            else -> safeTitle.take(maxLine - 1) + "…"
        }.trim()

        val dur = if (spotifyDemoEnabled && !nowPlayingDetected) 80_000L else nowPlayingDurationMs
        val posSnapshot = if (spotifyDemoEnabled && !nowPlayingDetected) 58_000L else nowPlayingPositionMs

        val pos = if (nowPlayingIsPlaying && dur > 0L) {
            val elapsed = SystemClock.elapsedRealtime() - nowPlayingPositionUpdateTimeMs
            val adj = (elapsed * nowPlayingSpeed).toLong()
            (posSnapshot + max(0L, adj)).coerceAtMost(dur)
        } else {
            posSnapshot
        }

        val bar = renderProgressBar(spotifyPreset, pos, max(1L, dur))
        val time = "${fmtTime(pos)} / ${fmtTime(max(1L, dur))}"
        val status = if (!nowPlayingIsPlaying) "Paused" else ""

        val line2 = listOf(bar, time, status).filter { it.isNotBlank() }.joinToString(" ").trim()

        return listOf(line1, line2).filter { it.isNotBlank() }
    }

    private fun renderProgressBar(preset: Int, posMs: Long, durMs: Long): String {
        val duration = max(1L, durMs)
        val p = min(1f, max(0f, posMs.toFloat() / duration.toFloat()))

        return when (preset.coerceIn(1, 5)) {
            1 -> {
                val innerSlots = 8
                val idx = (p * (innerSlots - 1)).toInt()
                val inner = CharArray(innerSlots) { '━' }
                inner[idx] = '◉'
                "♡" + inner.concatToString() + "♡"
            }
            2 -> {
                val slots = 10
                val idx = (p * (slots - 1)).toInt()
                val bg = CharArray(slots) { '─' }
                bg[idx] = '◉'
                bg.concatToString()
            }
            3 -> {
                val slots = 10
                val idx = (p * (slots - 1)).toInt()
                val bg = CharArray(slots) { '⟡' }
                bg[idx] = '◉'
                bg.concatToString()
            }
            4 -> {
                val bg = charArrayOf('▁','▂','▃','▄','▅','▅','▄','▃','▂','▁')
                val idx = (p * (bg.size - 1)).toInt()
                val out = bg.copyOf()
                out[idx] = '●'
                out.concatToString()
            }
            else -> {
                val bg = charArrayOf('▣','▣','▣','▢','▢','▢','▢','▢','▢','▢')
                val idx = (p * (bg.size - 1)).toInt()
                val out = bg.copyOf()
                out[idx] = '◉'
                out.concatToString()
            }
        }
    }

    private fun fmtTime(ms: Long): String {
        val totalSec = max(0L, ms) / 1000L
        val m = totalSec / 60L
        val s = (totalSec % 60L).toInt()
        return "${m}:${s.toString().padStart(2, '0')}"
    }

    private fun joinWithLimit(lines: List<String>, limit: Int): String {
        if (lines.isEmpty()) return ""
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return ""

        val out = ArrayList<String>()
        var total = 0

        for (i in clean.indices) {
            val line = clean[i]
            val add = if (out.isEmpty()) line.length else (1 + line.length)
            if (total + add > limit) {
                val remain = limit - total - (if (out.isEmpty()) 0 else 1)
                if (remain >= 2) {
                    out.add(line.take(remain - 1) + "…")
                }
                break
            }
            out.add(line)
            total += add
        }

        return out.joinToString("\n")
    }

    private fun sendToVrchatRaw(text: String, local: Boolean, addToConversation: Boolean) {
        val osc = if (!local) remoteChatboxOSC else localChatboxOSC
        osc.sendMessage(
            text,
            messengerUiState.value.isSendImmediately,
            triggerSFX = false
        )
        lastSentToVrchatAtMs = System.currentTimeMillis()

        if (addToConversation) {
            conversationUiState.addMessage(Message(text, false, Instant.now()))
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
