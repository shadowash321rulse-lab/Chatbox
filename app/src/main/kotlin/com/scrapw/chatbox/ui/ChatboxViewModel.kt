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

        // Force no SFX regardless of preference
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
    fun checkUpdate() {
        if (updateChecked) return
        updateChecked = true
        viewModelScope.launch(Dispatchers.Main) {
            updateInfo = checkUpdate("ScrapW", "Chatbox")
        }
    }

    // =========================
    // Throttling
    // =========================
    var minSendIntervalSeconds by mutableStateOf(2) // hard floor
        private set
    private var lastCombinedSendMs = 0L

    // =========================
    // AFK
    // =========================
    var afkEnabled by mutableStateOf(false)

    var afkMessage by mutableStateOf("")
        private set

    private val afkForcedIntervalSeconds = 12
    private var afkJob: Job? = null

    // AFK preset slots (3): name + text
    private val afkPresetNames = arrayOf("Preset 1", "Preset 2", "Preset 3")
    private val afkPresetTexts = arrayOf("", "", "")

    // =========================
    // Cycle
    // =========================
    var cycleEnabled by mutableStateOf(false)
    var cycleIntervalSeconds by mutableStateOf(3)
    private var cycleJob: Job? = null
    private var cycleIndex = 0

    // Cycle lines list (max 10)
    val cycleLines = mutableStateListOf<String>()

    // Cycle preset slots (5): name + messages + interval
    private val cyclePresetNames = arrayOf("Preset 1", "Preset 2", "Preset 3", "Preset 4", "Preset 5")
    private val cyclePresetMessages = arrayOf("", "", "", "", "")
    private val cyclePresetIntervals = intArrayOf(3, 3, 3, 3, 3)

    // =========================
    // Now Playing (UI still says spotify)
    // =========================
    var spotifyEnabled by mutableStateOf(false)
    var spotifyDemoEnabled by mutableStateOf(false)
    var spotifyPreset by mutableStateOf(1)
    var musicRefreshSeconds by mutableStateOf(2)
    private var nowPlayingJob: Job? = null

    // Music preset names (editable)
    private val musicPresetNames = arrayOf("Love", "Minimal", "Crystal", "Soundwave", "Geometry")

    // Debug fields shown in UI
    var listenerConnected by mutableStateOf(false)
    var activePackage by mutableStateOf("(none)")
    var nowPlayingDetected by mutableStateOf(false)
    var lastNowPlayingTitle by mutableStateOf("(blank)")
    var lastNowPlayingArtist by mutableStateOf("(blank)")
    var lastSentToVrchatAtMs by mutableStateOf(0L)

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
    // Info doc
    // =========================
    val fullInfoDocumentText: String = """
VRC-A (VRChat Assistant)
Made by: Ashoska Mitsu Sisko
Base: ScrapW’s Chatbox base (heavily revamped)

============================================================
WHAT THIS APP IS
============================================================
VRC-A is an Android app that sends text to VRChat’s Chatbox using OSC.
It’s made for standalone / mobile-friendly setups where you want:
- A quick “Send message” tool
- A cycling status / rotating messages system
- A live “Now Playing” music block (from your phone’s media notifications)
- An AFK tag at the very top

============================================================
IMPORTANT: VRChat OSC MUST BE ON
============================================================
In VRChat:
Settings → OSC → Enable OSC.

If OSC is OFF, nothing will show in your chatbox.

============================================================
TUTORIAL (DUMBED DOWN)
============================================================

1) Put your headset + phone on the SAME Wi-Fi
- If they aren’t on the same Wi-Fi, VRC-A cannot reach VRChat.

2) Find your headset IP address (Quest / Android headset)
- Headset Settings → Wi-Fi
- Tap your connected network
- Look for “IP address” (example: 192.168.1.23)
Sometimes it’s under “Advanced”.

3) Put that IP into VRC-A
- Dashboard → Headset IP address → type it → Apply

4) Test sending
- Dashboard → Manual Send → type “hello” → Send
If VRChat shows it, connection is working.

5) Enable Now Playing detection (phone permission)
- Now Playing page → “Open Notification Access settings”
- Enable access for VRC-A
- Restart VRC-A
- Play music in Spotify / YouTube Music etc
- Return to Now Playing and check “Detected / Artist / Title”

6) Keep the app alive (recommended)
- Settings page includes buttons to open:
  - “Display over other apps” permission
  - “Battery optimization” settings

============================================================
FEATURES
============================================================
- Manual Send (no sound effect)
- Cycle Messages (up to 10 lines, separate boxes, no “press enter” needed)
- Cycle Presets (5): editable name + saved lines + saved speed
- AFK (top line) with forced interval
- AFK Presets (3): editable name + saved text
- Now Playing: title/artist + animated progress bar
- Music presets named: Love / Minimal / Crystal / Soundwave / Geometry (editable)
- Debug page shows what AFK/Cycle/Music are generating + combined output

============================================================
KNOWN ISSUES
============================================================
- Some music apps don’t provide smooth progress updates; VRC-A estimates it while playing.
- If your router blocks device-to-device traffic (client isolation), OSC may fail.

============================================================
END
============================================================
""".trimIndent()

    // =========================
    // Init: load persisted values
    // =========================
    init {
        // Load persisted AFK text
        viewModelScope.launch {
            userPreferencesRepository.afkMessage.collect { afkMessage = it }
        }

        // Load cycle persisted enabled/messages/interval
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

        // AFK presets
        viewModelScope.launch { userPreferencesRepository.afkPreset1Name.collect { afkPresetNames[0] = it } }
        viewModelScope.launch { userPreferencesRepository.afkPreset1Text.collect { afkPresetTexts[0] = it } }

        viewModelScope.launch { userPreferencesRepository.afkPreset2Name.collect { afkPresetNames[1] = it } }
        viewModelScope.launch { userPreferencesRepository.afkPreset2Text.collect { afkPresetTexts[1] = it } }

        viewModelScope.launch { userPreferencesRepository.afkPreset3Name.collect { afkPresetNames[2] = it } }
        viewModelScope.launch { userPreferencesRepository.afkPreset3Text.collect { afkPresetTexts[2] = it } }

        // Cycle presets (5)
        viewModelScope.launch { userPreferencesRepository.cyclePreset1Name.collect { cyclePresetNames[0] = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset1Messages.collect { cyclePresetMessages[0] = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset1Interval.collect { cyclePresetIntervals[0] = it.coerceAtLeast(2) } }

        viewModelScope.launch { userPreferencesRepository.cyclePreset2Name.collect { cyclePresetNames[1] = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset2Messages.collect { cyclePresetMessages[1] = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset2Interval.collect { cyclePresetIntervals[1] = it.coerceAtLeast(2) } }

        viewModelScope.launch { userPreferencesRepository.cyclePreset3Name.collect { cyclePresetNames[2] = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset3Messages.collect { cyclePresetMessages[2] = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset3Interval.collect { cyclePresetIntervals[2] = it.coerceAtLeast(2) } }

        viewModelScope.launch { userPreferencesRepository.cyclePreset4Name.collect { cyclePresetNames[3] = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset4Messages.collect { cyclePresetMessages[3] = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset4Interval.collect { cyclePresetIntervals[3] = it.coerceAtLeast(2) } }

        viewModelScope.launch { userPreferencesRepository.cyclePreset5Name.collect { cyclePresetNames[4] = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset5Messages.collect { cyclePresetMessages[4] = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset5Interval.collect { cyclePresetIntervals[4] = it.coerceAtLeast(2) } }

        // Music preset names (editable)
        viewModelScope.launch { userPreferencesRepository.musicPreset1Name.collect { musicPresetNames[0] = it } }
        viewModelScope.launch { userPreferencesRepository.musicPreset2Name.collect { musicPresetNames[1] = it } }
        viewModelScope.launch { userPreferencesRepository.musicPreset3Name.collect { musicPresetNames[2] = it } }
        viewModelScope.launch { userPreferencesRepository.musicPreset4Name.collect { musicPresetNames[3] = it } }
        viewModelScope.launch { userPreferencesRepository.musicPreset5Name.collect { musicPresetNames[4] = it } }

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
    // Overlay / battery helper intents
    // =========================
    fun overlayPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun batteryOptimizationIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun notificationAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // =========================
    // AFK set/persist
    // =========================
    fun updateAfkText(text: String) {
        afkMessage = text
        viewModelScope.launch { userPreferencesRepository.saveAfkMessage(text) }
        rebuildAndMaybeSendCombined(forceSend = false)
    }

    fun getAfkPresetName(slot: Int): String = afkPresetNames[slot.coerceIn(1, 3) - 1]
    fun getAfkPresetTextPreview(slot: Int): String = afkPresetTexts[slot.coerceIn(1, 3) - 1].trim()

    fun updateAfkPresetName(slot: Int, name: String) {
        val i = slot.coerceIn(1, 3) - 1
        afkPresetNames[i] = name
        // persist without changing text
        viewModelScope.launch {
            val txt = afkPresetTexts[i]
            when (slot.coerceIn(1, 3)) {
                1 -> userPreferencesRepository.saveAfkPreset1(name, txt)
                2 -> userPreferencesRepository.saveAfkPreset2(name, txt)
                else -> userPreferencesRepository.saveAfkPreset3(name, txt)
            }
        }
    }

    fun saveAfkPreset(slot: Int) {
        val s = slot.coerceIn(1, 3)
        val i = s - 1
        val name = afkPresetNames[i].ifBlank { "Preset $s" }
        val text = afkMessage
        afkPresetTexts[i] = text
        viewModelScope.launch {
            when (s) {
                1 -> userPreferencesRepository.saveAfkPreset1(name, text)
                2 -> userPreferencesRepository.saveAfkPreset2(name, text)
                else -> userPreferencesRepository.saveAfkPreset3(name, text)
            }
        }
    }

    fun loadAfkPreset(slot: Int) {
        val s = slot.coerceIn(1, 3)
        viewModelScope.launch {
            val text = when (s) {
                1 -> userPreferencesRepository.afkPreset1Text.first()
                2 -> userPreferencesRepository.afkPreset2Text.first()
                else -> userPreferencesRepository.afkPreset3Text.first()
            }
            updateAfkText(text)
        }
    }

    // =========================
    // Cycle lines (max 10), persist as joined string
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
        rebuildAndMaybeSendCombined(forceSend = false)
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

    private fun persistCycleEnabled() = viewModelScope.launch { userPreferencesRepository.saveCycleEnabled(cycleEnabled) }
    private fun persistCycleInterval() = viewModelScope.launch { userPreferencesRepository.saveCycleInterval(cycleIntervalSeconds.coerceAtLeast(2)) }

    // Cycle presets (5)
    fun getCyclePresetName(slot: Int): String = cyclePresetNames[slot.coerceIn(1, 5) - 1]
    fun getCyclePresetFirstLinePreview(slot: Int): String {
        val i = slot.coerceIn(1, 5) - 1
        return cyclePresetMessages[i].lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }

    fun updateCyclePresetName(slot: Int, name: String) {
        val s = slot.coerceIn(1, 5)
        val i = s - 1
        cyclePresetNames[i] = name
        viewModelScope.launch {
            val msgs = cyclePresetMessages[i]
            val interval = cyclePresetIntervals[i].coerceAtLeast(2)
            when (s) {
                1 -> userPreferencesRepository.saveCyclePreset1(name, msgs, interval)
                2 -> userPreferencesRepository.saveCyclePreset2(name, msgs, interval)
                3 -> userPreferencesRepository.saveCyclePreset3(name, msgs, interval)
                4 -> userPreferencesRepository.saveCyclePreset4(name, msgs, interval)
                else -> userPreferencesRepository.saveCyclePreset5(name, msgs, interval)
            }
        }
    }

    fun saveCyclePreset(slot: Int) {
        val s = slot.coerceIn(1, 5)
        val i = s - 1
        val name = cyclePresetNames[i].ifBlank { "Preset $s" }
        val messages = cycleLines.map { it.trim() }.filter { it.isNotEmpty() }.take(10).joinToString("\n")
        val interval = cycleIntervalSeconds.coerceAtLeast(2)

        cyclePresetMessages[i] = messages
        cyclePresetIntervals[i] = interval

        viewModelScope.launch {
            when (s) {
                1 -> userPreferencesRepository.saveCyclePreset1(name, messages, interval)
                2 -> userPreferencesRepository.saveCyclePreset2(name, messages, interval)
                3 -> userPreferencesRepository.saveCyclePreset3(name, messages, interval)
                4 -> userPreferencesRepository.saveCyclePreset4(name, messages, interval)
                else -> userPreferencesRepository.saveCyclePreset5(name, messages, interval)
            }
        }
    }

    fun loadCyclePreset(slot: Int) {
        val s = slot.coerceIn(1, 5)
        viewModelScope.launch {
            val (name, msgs, interval) = when (s) {
                1 -> Triple(
                    userPreferencesRepository.cyclePreset1Name.first(),
                    userPreferencesRepository.cyclePreset1Messages.first(),
                    userPreferencesRepository.cyclePreset1Interval.first()
                )
                2 -> Triple(
                    userPreferencesRepository.cyclePreset2Name.first(),
                    userPreferencesRepository.cyclePreset2Messages.first(),
                    userPreferencesRepository.cyclePreset2Interval.first()
                )
                3 -> Triple(
                    userPreferencesRepository.cyclePreset3Name.first(),
                    userPreferencesRepository.cyclePreset3Messages.first(),
                    userPreferencesRepository.cyclePreset3Interval.first()
                )
                4 -> Triple(
                    userPreferencesRepository.cyclePreset4Name.first(),
                    userPreferencesRepository.cyclePreset4Messages.first(),
                    userPreferencesRepository.cyclePreset4Interval.first()
                )
                else -> Triple(
                    userPreferencesRepository.cyclePreset5Name.first(),
                    userPreferencesRepository.cyclePreset5Messages.first(),
                    userPreferencesRepository.cyclePreset5Interval.first()
                )
            }

            cycleIntervalSeconds = interval.coerceAtLeast(2)
            persistCycleInterval()
            setCycleLinesFromText(msgs)
            persistCycleLines()

            // update cached name/message/interval arrays too
            val idx = s - 1
            cyclePresetNames[idx] = name
            cyclePresetMessages[idx] = msgs
            cyclePresetIntervals[idx] = interval.coerceAtLeast(2)
        }
    }

    // =========================
    // Music preset names + previews
    // =========================
    fun getMusicPresetName(preset: Int): String = musicPresetNames[preset.coerceIn(1, 5) - 1]

    fun updateMusicPresetName(preset: Int, name: String) {
        val p = preset.coerceIn(1, 5)
        musicPresetNames[p - 1] = name
        viewModelScope.launch { userPreferencesRepository.saveMusicPresetName(p, name) }
    }

    /**
     * Animated preview helper: pass a 0..1 fraction.
     * UI uses this so the preview matches the REAL runtime renderer.
     */
    fun renderMusicPresetPreview(preset: Int, fraction01: Float): String {
        val f = fraction01.coerceIn(0f, 1f)
        val dur = 100_000L
        val pos = (dur * f).toLong()
        return renderProgressBar(preset, pos, dur)
    }

    // =========================
    // Music flags
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

        persistCycleEnabled()
        persistCycleLines()
        persistCycleInterval()

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

        val nothingActive =
            afkLine.isBlank() && cycleLine.isBlank() && musicLines.isEmpty()

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

    /**
     * IMPORTANT: Geometry fixed here.
     * It now FILLS: ▣ (done) ◉ (current) ▢ (remaining)
     */
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
                val slots = 10
                val idx = (p * (slots - 1)).toInt()
                val out = CharArray(slots) { '▢' }
                for (i in 0 until slots) {
                    out[i] = when {
                        i < idx -> '▣'
                        i == idx -> '◉'
                        else -> '▢'
                    }
                }
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
