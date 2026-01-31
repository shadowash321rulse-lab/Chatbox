package com.scrapw.chatbox.ui

import android.content.Context
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
import com.scrapw.chatbox.data.UserPreferencesRepository
import com.scrapw.chatbox.osc.ChatboxOSC
import com.scrapw.chatbox.ui.mainScreen.ConversationUiState
import com.scrapw.chatbox.ui.mainScreen.Message
import com.scrapw.chatbox.update.ApkUpdater
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

        // ✅ Always force NO SFX (you asked to remove sound)
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
    // Global send throttling (floor)
    // =========================
    var minSendIntervalSeconds by mutableStateOf(2)
        private set

    private var lastCombinedSendMs = 0L

    // =========================
    // AFK
    // =========================
    var afkEnabled by mutableStateOf(false)
    var afkRunning by mutableStateOf(false)
        private set

    var afkMessage by mutableStateOf("")
        private set

    private val afkForcedIntervalSeconds = 12
    private var afkJob: Job? = null

    // AFK presets (names + texts) in-memory so UI updates instantly
    private val afkPresetNames = arrayOf("Preset 1", "Preset 2", "Preset 3")
    private val afkPresetTexts = arrayOf("", "", "")

    // =========================
    // Cycle
    // =========================
    var cycleEnabled by mutableStateOf(false)
    var cycleRunning by mutableStateOf(false)
        private set

    var cycleIntervalSeconds by mutableStateOf(3)
    private var cycleJob: Job? = null
    private var cycleIndex = 0

    // Max 10 lines
    val cycleLines = mutableStateListOf<String>()

    // Cycle presets (5)
    private val cyclePresetNames = arrayOf("Preset 1","Preset 2","Preset 3","Preset 4","Preset 5")
    private val cyclePresetMessages = arrayOf("","","","","")
    private val cyclePresetIntervals = intArrayOf(3,3,3,3,3)

    // =========================
    // Now Playing
    // =========================
    var musicEnabled by mutableStateOf(false)     // feature allowed
    var musicRunning by mutableStateOf(false)     // sender actually running
        private set

    var spotifyDemoEnabled by mutableStateOf(false)

    // Hard locked names (cannot rename)
    private val musicPresetNames = arrayOf("Love","Minimal","Crystal","Soundwave","Geometry")
    var musicPreset by mutableStateOf(1) // 1..5

    var musicRefreshSeconds by mutableStateOf(2)
    private var nowPlayingJob: Job? = null

    // Debug fields
    var listenerConnected by mutableStateOf(false)
    var activePackage by mutableStateOf("(none)")
    var nowPlayingDetected by mutableStateOf(false)
    var lastNowPlayingTitle by mutableStateOf("(blank)")
    var lastNowPlayingArtist by mutableStateOf("(blank)")
    var lastSentToVrchatAtMs by mutableStateOf(0L)
    var nowPlayingIsPlaying by mutableStateOf(false)
        private set

    private var nowPlayingDurationMs: Long = 0L
    private var nowPlayingPositionMs: Long = 0L
    private var nowPlayingPositionUpdateTimeMs: Long = 0L
    private var nowPlayingSpeed: Float = 1f

    // OSC preview
    var debugLastAfkOsc by mutableStateOf("")
        private set
    var debugLastCycleOsc by mutableStateOf("")
        private set
    var debugLastMusicOsc by mutableStateOf("")
        private set
    var debugLastCombinedOsc by mutableStateOf("")
        private set

    // =========================
    // In-app updater state
    // =========================
    var updateStatus by mutableStateOf("Not checked")
        private set
    var latestVersionLabel by mutableStateOf("")
        private set
    var latestChangelog by mutableStateOf("")
        private set
    private var latestApkUrl: String = ""

    // ✅ SET THESE ONCE (your GitHub repo that hosts the APK release)
    private val updateOwner = "ScrapW"     // change later if needed
    private val updateRepo = "Chatbox"     // change later if needed

    // =========================
    // Info doc (used by UI)
    // =========================
    val fullInfoDocumentText: String = """
VRC-A (VRChat Assistant)
Made by: Ashoska Mitsu Sisko
Base: ScrapW’s Chatbox base (heavily revamped)

============================================================
WHAT THIS APP IS
============================================================
VRC-A sends text to VRChat’s Chatbox using OSC over your Wi-Fi network.
It is designed for standalone/mobile-friendly setups:
- Manual Send
- Cycle (rotating lines)
- Now Playing (phone media notification)
- AFK tag (top line)
- Debug indicators so you can see what’s failing

============================================================
IMPORTANT: VRCHAT OSC MUST BE ON
============================================================
In VRChat:
Settings → OSC → Enable OSC

============================================================
TUTORIAL (EASY)
============================================================
1) Put your phone and headset on the SAME Wi-Fi.
2) Find your headset IP:
   - Headset Settings → Wi-Fi → tap your network → IP Address
3) In VRC-A:
   Dashboard → Headset IP → Apply
4) Test Manual Send:
   Dashboard → Manual Send → type hello → Send
5) Enable Now Playing permission:
   Now Playing → Open Notification Access → enable VRC-A → restart the app
6) Start modules:
   - Cycle page → Start Cycle
   - Now Playing page → Start Music
   - Cycle page → Start AFK

============================================================
KNOWN LIMITATIONS
============================================================
- Android forces user confirmation to install updates (normal).
- Some music apps do not report live progress smoothly, but VRC-A estimates progress
  using the last position + playback speed.

============================================================
END
============================================================
""".trimIndent()

    init {
        // Persisted AFK message
        viewModelScope.launch {
            userPreferencesRepository.afkMessage.collect { afkMessage = it }
        }

        // Cycle persisted state
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

        // AFK presets load
        viewModelScope.launch { userPreferencesRepository.afkPreset1Name.collect { afkPresetNames[0] = it } }
        viewModelScope.launch { userPreferencesRepository.afkPreset1Text.collect { afkPresetTexts[0] = it } }

        viewModelScope.launch { userPreferencesRepository.afkPreset2Name.collect { afkPresetNames[1] = it } }
        viewModelScope.launch { userPreferencesRepository.afkPreset2Text.collect { afkPresetTexts[1] = it } }

        viewModelScope.launch { userPreferencesRepository.afkPreset3Name.collect { afkPresetNames[2] = it } }
        viewModelScope.launch { userPreferencesRepository.afkPreset3Text.collect { afkPresetTexts[2] = it } }

        // Cycle presets load
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

        // NowPlaying binding
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
    // Update: check + download + install
    // =========================
    fun checkForUpdates() {
        updateStatus = "Checking..."
        viewModelScope.launch {
            val latest = ApkUpdater.fetchLatestRelease(updateOwner, updateRepo)
            if (latest == null) {
                updateStatus = "Update check failed"
                return@launch
            }

            latestVersionLabel = "${latest.name} (${latest.tag})"
            latestChangelog = latest.body
            latestApkUrl = latest.apkUrl

            updateStatus = "Latest found"
        }
    }

    fun downloadAndInstallUpdate(context: Context) {
        if (latestApkUrl.isBlank()) {
            updateStatus = "No APK URL loaded yet"
            return
        }

        updateStatus = "Downloading..."
        viewModelScope.launch {
            val file = ApkUpdater.downloadApk(context, latestApkUrl)
            if (file == null) {
                updateStatus = "Download failed"
                return@launch
            }
            updateStatus = "Downloaded. Opening installer…"
            ApkUpdater.promptInstall(context, file)
        }
    }

    fun openUnknownSourcesSettings(context: Context) {
        ApkUpdater.openUnknownSourcesSettings(context)
    }

    // =========================
    // AFK text update
    // =========================
    fun updateAfkText(text: String) {
        afkMessage = text
        viewModelScope.launch { userPreferencesRepository.saveAfkMessage(text) }
        rebuildAndMaybeSendCombined(forceSend = false)
    }

    fun getAfkPresetName(slot: Int): String = afkPresetNames[slot.coerceIn(1,3)-1]
    fun getAfkPresetText(slot: Int): String = afkPresetTexts[slot.coerceIn(1,3)-1]

    fun updateAfkPresetName(slot: Int, name: String) {
        val i = slot.coerceIn(1,3)-1
        afkPresetNames[i] = name
        // Persist name alongside its current text
        val text = afkPresetTexts[i]
        viewModelScope.launch {
            when (slot.coerceIn(1,3)) {
                1 -> userPreferencesRepository.saveAfkPreset1(name, text)
                2 -> userPreferencesRepository.saveAfkPreset2(name, text)
                else -> userPreferencesRepository.saveAfkPreset3(name, text)
            }
        }
    }

    fun saveAfkPreset(slot: Int) {
        val i = slot.coerceIn(1,3)-1
        // ✅ immediate in-memory update so UI refreshes instantly
        afkPresetTexts[i] = afkMessage

        val name = afkPresetNames[i]
        val text = afkPresetTexts[i]
        viewModelScope.launch {
            when (slot.coerceIn(1,3)) {
                1 -> userPreferencesRepository.saveAfkPreset1(name, text)
                2 -> userPreferencesRepository.saveAfkPreset2(name, text)
                else -> userPreferencesRepository.saveAfkPreset3(name, text)
            }
        }
    }

    fun loadAfkPreset(slot: Int) {
        val i = slot.coerceIn(1,3)-1
        updateAfkText(afkPresetTexts[i])
    }

    // =========================
    // Cycle lines management (max 10)
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

    fun addCycleLine(value: String = "") {
        if (cycleLines.size >= 10) return
        cycleLines.add(value)
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

    fun setCycleEnabled(enabled: Boolean) {
        cycleEnabled = enabled
        viewModelScope.launch { userPreferencesRepository.saveCycleEnabled(enabled) }
        if (!enabled) stopCycle(clearFromChatbox = true)
        rebuildAndMaybeSendCombined(forceSend = true)
    }

    fun setCycleIntervalSeconds(seconds: Int) {
        cycleIntervalSeconds = seconds.coerceAtLeast(2)
        viewModelScope.launch { userPreferencesRepository.saveCycleInterval(cycleIntervalSeconds) }
    }

    fun getCyclePresetName(slot: Int): String = cyclePresetNames[slot.coerceIn(1,5)-1]
    fun getCyclePresetFirstLine(slot: Int): String {
        val i = slot.coerceIn(1,5)-1
        return cyclePresetMessages[i].lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }

    fun updateCyclePresetName(slot: Int, name: String) {
        val i = slot.coerceIn(1,5)-1
        cyclePresetNames[i] = name
        // persist name with existing preset values
        val messages = cyclePresetMessages[i]
        val interval = cyclePresetIntervals[i]
        viewModelScope.launch {
            when (slot.coerceIn(1,5)) {
                1 -> userPreferencesRepository.saveCyclePreset1(name, messages, interval)
                2 -> userPreferencesRepository.saveCyclePreset2(name, messages, interval)
                3 -> userPreferencesRepository.saveCyclePreset3(name, messages, interval)
                4 -> userPreferencesRepository.saveCyclePreset4(name, messages, interval)
                else -> userPreferencesRepository.saveCyclePreset5(name, messages, interval)
            }
        }
    }

    fun saveCyclePreset(slot: Int) {
        val i = slot.coerceIn(1,5)-1
        val name = cyclePresetNames[i]
        val messages = cycleLines.map { it.trim() }.filter { it.isNotEmpty() }.take(10).joinToString("\n")
        val interval = cycleIntervalSeconds.coerceAtLeast(2)

        // ✅ immediate memory update (UI refresh instantly)
        cyclePresetMessages[i] = messages
        cyclePresetIntervals[i] = interval

        viewModelScope.launch {
            when (slot.coerceIn(1,5)) {
                1 -> userPreferencesRepository.saveCyclePreset1(name, messages, interval)
                2 -> userPreferencesRepository.saveCyclePreset2(name, messages, interval)
                3 -> userPreferencesRepository.saveCyclePreset3(name, messages, interval)
                4 -> userPreferencesRepository.saveCyclePreset4(name, messages, interval)
                else -> userPreferencesRepository.saveCyclePreset5(name, messages, interval)
            }
        }
    }

    fun loadCyclePreset(slot: Int) {
        val i = slot.coerceIn(1,5)-1
        setCycleIntervalSeconds(cyclePresetIntervals[i])
        setCycleLinesFromText(cyclePresetMessages[i])
        persistCycleLines()
    }

    // =========================
    // Notification Access intent
    // =========================
    fun notificationAccessIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // =========================
    // Music flags + start/stop
    // =========================
    fun setMusicEnabled(enabled: Boolean) {
        musicEnabled = enabled
        if (!enabled) stopNowPlayingSender(clearFromChatbox = true)
        rebuildAndMaybeSendCombined(forceSend = true)
    }

    fun setMusicDemo(enabled: Boolean) {
        spotifyDemoEnabled = enabled
        rebuildAndMaybeSendCombined(forceSend = true)
    }

    fun setMusicPreset(preset: Int) {
        musicPreset = preset.coerceIn(1,5)
        rebuildAndMaybeSendCombined(forceSend = true)
    }

    fun getMusicPresetName(preset: Int): String = musicPresetNames[preset.coerceIn(1,5)-1]

    // =========================
    // AFK sender
    // =========================
    fun startAfkSender(local: Boolean = false) {
        if (!afkEnabled) return
        afkRunning = true

        afkJob?.cancel()
        afkJob = viewModelScope.launch {
            while (afkEnabled && afkRunning) {
                rebuildAndMaybeSendCombined(forceSend = true, local = local)
                delay(afkForcedIntervalSeconds * 1000L)
            }
        }
        rebuildAndMaybeSendCombined(forceSend = true, local = local)
    }

    fun stopAfkSender(clearFromChatbox: Boolean) {
        afkRunning = false
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

        cycleRunning = true

        cycleJob?.cancel()
        cycleJob = viewModelScope.launch {
            cycleIndex = 0
            while (cycleEnabled && cycleRunning) {
                rebuildAndMaybeSendCombined(
                    forceSend = true,
                    local = local,
                    cycleLineOverride = msgs[cycleIndex % msgs.size]
                )
                cycleIndex = (cycleIndex + 1) % msgs.size
                delay(cycleIntervalSeconds.coerceAtLeast(2).toLong() * 1000L)
            }
        }
        rebuildAndMaybeSendCombined(forceSend = true, local = local)
    }

    fun stopCycle(clearFromChatbox: Boolean) {
        cycleRunning = false
        cycleJob?.cancel()
        cycleJob = null

        if (clearFromChatbox) {
            rebuildAndMaybeSendCombined(forceSend = true, forceClearIfAllOff = true)
        }
    }

    // =========================
    // Now Playing sender
    // =========================
    fun startNowPlayingSender(local: Boolean = false) {
        if (!musicEnabled) return
        musicRunning = true

        nowPlayingJob?.cancel()
        nowPlayingJob = viewModelScope.launch {
            while (musicEnabled && musicRunning) {
                rebuildAndMaybeSendCombined(forceSend = true, local = local)
                delay(musicRefreshSeconds.coerceAtLeast(2).toLong() * 1000L)
            }
        }
        rebuildAndMaybeSendCombined(forceSend = true, local = local)
    }

    fun stopNowPlayingSender(clearFromChatbox: Boolean) {
        musicRunning = false
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

        if (clearFromChatbox) clearChatbox()
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
        val afkLine = if (afkEnabled && afkRunning && afkMessage.trim().isNotEmpty()) afkMessage.trim() else ""
        val cycleLine = if (cycleEnabled && cycleRunning) (cycleLineOverride ?: currentCycleLinePreview()) else ""
        val musicLines = if (musicEnabled && musicRunning) buildNowPlayingLines() else emptyList()

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
    // Now Playing block builder (live position estimate)
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

        val bar = renderProgressBar(musicPreset, pos, max(1L, dur))
        val time = "${fmtTime(pos)} / ${fmtTime(max(1L, dur))}"
        val status = if (!nowPlayingIsPlaying) "Paused" else ""

        val line2 = listOf(bar, time, status).filter { it.isNotBlank() }.joinToString(" ").trim()
        return listOf(line1, line2).filter { it.isNotBlank() }
    }

    private fun renderProgressBar(preset: Int, posMs: Long, durMs: Long): String {
        val duration = max(1L, durMs)
        val p = min(1f, max(0f, posMs.toFloat() / duration.toFloat()))
        val idx: Int

        return when (preset.coerceIn(1, 5)) {
            1 -> {
                val innerSlots = 8
                idx = (p * (innerSlots - 1)).toInt()
                val inner = CharArray(innerSlots) { '━' }
                inner[idx] = '◉'
                "♡" + inner.concatToString() + "♡"
            }
            2 -> {
                val slots = 10
                idx = (p * (slots - 1)).toInt()
                val bg = CharArray(slots) { '─' }
                bg[idx] = '◉'
                bg.concatToString()
            }
            3 -> {
                val slots = 10
                idx = (p * (slots - 1)).toInt()
                val bg = CharArray(slots) { '⟡' }
                bg[idx] = '◉'
                bg.concatToString()
            }
            4 -> {
                val bg = charArrayOf('▁','▂','▃','▄','▅','▅','▄','▃','▂','▁')
                idx = (p * (bg.size - 1)).toInt()
                val out = bg.copyOf()
                out[idx] = '●'
                out.concatToString()
            }
            else -> {
                // ✅ Geometry: squares fill BEHIND the dot
                // left of dot = ▣ (filled), right = ▢ (empty), dot = ◉
                val slots = 10
                idx = (p * (slots - 1)).toInt()
                val out = CharArray(slots) { i ->
                    when {
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
                if (remain >= 2) out.add(line.take(remain - 1) + "…")
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
