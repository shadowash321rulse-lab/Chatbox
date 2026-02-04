// ChatboxViewModel.kt
package com.scrapw.chatbox.ui

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

        // ✅ Locked delays (per your request)
        private const val CYCLE_INTERVAL_SECONDS_LOCKED = 10
        private const val MUSIC_REFRESH_SECONDS_LOCKED = 2

        @MainThread
        fun isInstanceInitialized(): Boolean = ::instance.isInitialized

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
            if (!::instance.isInitialized) throw Exception("ChatboxViewModel is not initialized!")
            return instance
        }
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

    var stashedMessage by mutableStateOf("")
        private set

    fun stashMessage(local: Boolean = false) {
        val osc = if (!local) remoteChatboxOSC else localChatboxOSC
        osc.typing = false

        val txt = messageText.value.text
        conversationUiState.addMessage(
            Message(txt, true, Instant.now())
        )

        messageText.value = TextFieldValue("", TextRange.Zero)
        stashedMessage = ""
    }

    fun stashMessage(text: String) {
        stashedMessage = text
        messageText.value = TextFieldValue(text, TextRange(text.length))
    }

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

    private val ipFlow = combine(storedIpState, userInputIpState) { stored, typed ->
        if (typed.isNotBlank()) typed else stored
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

    fun onRealtimeMsgChanged(value: Boolean) {
        viewModelScope.launch { userPreferencesRepository.saveIsRealtimeMsg(value) }
    }

    fun onTriggerSfxChanged(value: Boolean) {
        viewModelScope.launch { userPreferencesRepository.saveIsTriggerSFX(value) }
    }

    fun onTypingIndicatorChanged(value: Boolean) {
        viewModelScope.launch { userPreferencesRepository.saveTypingIndicator(value) }
    }

    fun onSendImmediatelyChanged(value: Boolean) {
        viewModelScope.launch { userPreferencesRepository.saveIsSendImmediately(value) }
    }

    fun onMessageTextChange(message: TextFieldValue, local: Boolean = false) {
        val osc = if (!local) remoteChatboxOSC else localChatboxOSC
        messageText.value = message
        stashedMessage = message.text

        if (messengerUiState.value.isRealtimeMsg) {
            osc.sendRealtimeMessage(message.text)
        } else if (messengerUiState.value.isTypingIndicator) {
            osc.typing = message.text.isNotEmpty()
        }
    }

    fun sendMessage(local: Boolean = false) {
        val osc = if (!local) remoteChatboxOSC else localChatboxOSC

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
        stashedMessage = ""
    }

    // =========================
    // Throttling (hard floor)
    // =========================
    var minSendIntervalSeconds by mutableStateOf(2)
        private set

    private var lastCombinedSendMs = 0L

    // =========================
    // AFK
    // =========================
    var afkEnabled by mutableStateOf(false)
        private set

    var afkMessage by mutableStateOf("")
        private set

    private val afkForcedIntervalSeconds = 12
    private var afkJob: Job? = null

    // =========================
    // Cycle
    // =========================
    var cycleEnabled by mutableStateOf(false)
        private set

    // ✅ Locked to 10s (no UI control anymore)
    var cycleIntervalSeconds by mutableStateOf(CYCLE_INTERVAL_SECONDS_LOCKED)
        private set

    private var cycleJob: Job? = null
    private var cycleIndex = 0
    val cycleLines = mutableStateListOf<String>()

    // =========================
    // Now Playing
    // =========================
    var spotifyEnabled by mutableStateOf(false)
        private set
    var spotifyDemoEnabled by mutableStateOf(false)
        private set

    var spotifyPreset by mutableStateOf(1)
        private set

    // ✅ Locked to 2s (no UI control anymore)
    var musicRefreshSeconds by mutableStateOf(MUSIC_REFRESH_SECONDS_LOCKED)
        private set

    private var nowPlayingJob: Job? = null

    // =========================
    // UI clutter controls (persisted)
    // =========================
    var afkPresetsCollapsed by mutableStateOf(true)
        private set
    var cyclePresetsCollapsed by mutableStateOf(true)
        private set

    fun updateAfkPresetsCollapsed(value: Boolean) {
        afkPresetsCollapsed = value
        viewModelScope.launch { userPreferencesRepository.saveAfkPresetsCollapsed(value) }
    }

    fun updateCyclePresetsCollapsed(value: Boolean) {
        cyclePresetsCollapsed = value
        viewModelScope.launch { userPreferencesRepository.saveCyclePresetsCollapsed(value) }
    }

    // =========================
    // Debug fields shown in UI
    // =========================
    var listenerConnected by mutableStateOf(false)
    var activePackage by mutableStateOf("(none)")
    var nowPlayingDetected by mutableStateOf(false)
    var lastNowPlayingTitle by mutableStateOf("(blank)")
    var lastNowPlayingArtist by mutableStateOf("(blank)")
    var lastSentToVrchatAtMs by mutableStateOf(0L)

    // Raw playbackState from notifications
    var nowPlayingIsPlaying by mutableStateOf(false)
        private set

    // ✅ Derived playback: fixes "Paused sticks after skipping songs"
    private var derivedIsPlaying: Boolean = false

    private var nowPlayingDurationMs: Long = 0L
    private var nowPlayingPositionMs: Long = 0L
    private var nowPlayingPositionUpdateTimeMs: Long = 0L
    private var nowPlayingSpeed: Float = 1f

    // For movement detection + track-change grace
    private var lastRawPosMs: Long = 0L
    private var lastRawUpdateTimeMs: Long = 0L
    private var lastTrackKey: String = ""
    private var lastTrackChangeElapsedMs: Long = 0L

    var debugLastAfkOsc by mutableStateOf("")
        private set
    var debugLastCycleOsc by mutableStateOf("")
        private set
    var debugLastMusicOsc by mutableStateOf("")
        private set
    var debugLastCombinedOsc by mutableStateOf("")
        private set

    var combinedPreviewText by mutableStateOf("")
        private set

    private val afkPresetTexts = mutableStateListOf("", "", "")
    private val cyclePresetMessages = mutableStateListOf("", "", "", "", "")
    private val cyclePresetIntervals = mutableStateListOf(
        CYCLE_INTERVAL_SECONDS_LOCKED,
        CYCLE_INTERVAL_SECONDS_LOCKED,
        CYCLE_INTERVAL_SECONDS_LOCKED,
        CYCLE_INTERVAL_SECONDS_LOCKED,
        CYCLE_INTERVAL_SECONDS_LOCKED
    )

    init {
        viewModelScope.launch {
            userPreferencesRepository.afkMessage.collect {
                afkMessage = it
                rebuildCombinedPreviewOnly()
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.cycleEnabled.collect {
                cycleEnabled = it
                rebuildCombinedPreviewOnly()
            }
        }

        // ✅ IMPORTANT FIX (cursor jump):
        // Do NOT trim/normalize user text when loading cycle lines.
        viewModelScope.launch {
            userPreferencesRepository.cycleMessages.collect { text ->
                setCycleLinesFromTextPreserve(text)
            }
        }

        // ✅ Always enforce locked cycle interval
        viewModelScope.launch {
            userPreferencesRepository.cycleInterval.collect {
                cycleIntervalSeconds = CYCLE_INTERVAL_SECONDS_LOCKED
                rebuildCombinedPreviewOnly()
            }
        }

        viewModelScope.launch { userPreferencesRepository.afkPreset1.collect { afkPresetTexts[0] = it } }
        viewModelScope.launch { userPreferencesRepository.afkPreset2.collect { afkPresetTexts[1] = it } }
        viewModelScope.launch { userPreferencesRepository.afkPreset3.collect { afkPresetTexts[2] = it } }

        viewModelScope.launch { userPreferencesRepository.cyclePreset1Messages.collect { cyclePresetMessages[0] = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset1Interval.collect { cyclePresetIntervals[0] = CYCLE_INTERVAL_SECONDS_LOCKED } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset2Messages.collect { cyclePresetMessages[1] = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset2Interval.collect { cyclePresetIntervals[1] = CYCLE_INTERVAL_SECONDS_LOCKED } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset3Messages.collect { cyclePresetMessages[2] = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset3Interval.collect { cyclePresetIntervals[2] = CYCLE_INTERVAL_SECONDS_LOCKED } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset4Messages.collect { cyclePresetMessages[3] = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset4Interval.collect { cyclePresetIntervals[3] = CYCLE_INTERVAL_SECONDS_LOCKED } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset5Messages.collect { cyclePresetMessages[4] = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset5Interval.collect { cyclePresetIntervals[4] = CYCLE_INTERVAL_SECONDS_LOCKED } }

        viewModelScope.launch {
            userPreferencesRepository.spotifyPreset.collect { saved ->
                spotifyPreset = saved.coerceIn(1, 5)
                rebuildCombinedPreviewOnly()
            }
        }

        viewModelScope.launch { userPreferencesRepository.afkPresetsCollapsed.collect { afkPresetsCollapsed = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePresetsCollapsed.collect { cyclePresetsCollapsed = it } }

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

                updateDerivedPlaybackState()
                rebuildCombinedPreviewOnly()
            }
        }
    }

    // =========================
    // Derived playback (progress-moving-based)
    // =========================
    private fun makeTrackKey(): String {
        val pkg = activePackage
        val t = lastNowPlayingTitle
        val a = lastNowPlayingArtist
        val d = nowPlayingDurationMs
        return "$pkg|$d|$a|$t"
    }

    private fun updateDerivedPlaybackState() {
        val nowElapsed = SystemClock.elapsedRealtime()

        val key = makeTrackKey()
        val trackLooksValid = (lastNowPlayingTitle != "(blank)" && lastNowPlayingTitle.isNotBlank()) ||
            (lastNowPlayingArtist != "(blank)" && lastNowPlayingArtist.isNotBlank())

        // Track-change grace: avoid "Paused" sticking during metadata swaps / skips.
        if (trackLooksValid && key != lastTrackKey) {
            lastTrackKey = key
            lastTrackChangeElapsedMs = nowElapsed
            // reset movement sampler so first update doesn't falsely "stick"
            lastRawPosMs = nowPlayingPositionMs
            lastRawUpdateTimeMs = nowPlayingPositionUpdateTimeMs
        }

        // Movement detection: if position updates and actually moves, treat as playing.
        var moved = false
        if (nowPlayingPositionUpdateTimeMs != 0L && nowPlayingPositionUpdateTimeMs != lastRawUpdateTimeMs) {
            val dp = nowPlayingPositionMs - lastRawPosMs
            // > ~0.35s movement is "definitely playing"
            moved = dp > 350L
            lastRawPosMs = nowPlayingPositionMs
            lastRawUpdateTimeMs = nowPlayingPositionUpdateTimeMs
        }

        val inGrace = (nowElapsed - lastTrackChangeElapsedMs) < 2500L

        derivedIsPlaying = when {
            nowPlayingIsPlaying -> true
            moved -> true
            inGrace && trackLooksValid -> true
            else -> false
        }
    }

    // =========================
    // System intents
    // =========================
    fun notificationAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun overlayPermissionIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${getApplicationIdSafely()}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun batteryOptimizationIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${getApplicationIdSafely()}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun getApplicationIdSafely(): String = "com.scrapw.chatbox"

    // =========================
    // KILL switch
    // =========================
    fun killStopAndClear(local: Boolean = false) {
        stopAll(clearFromChatbox = false)
        clearChatbox(local)
        rebuildCombinedPreviewOnly(forceClearIfAllOff = true)
    }

    // =========================
    // Enable flags (dashboard toggles)
    // =========================
    fun setAfkEnabledFlag(enabled: Boolean) {
        afkEnabled = enabled
        rebuildCombinedPreviewOnly()
        if (!enabled) stopAfkSender(clearFromChatbox = true)
    }

    fun setCycleEnabledFlag(enabled: Boolean) {
        cycleEnabled = enabled
        viewModelScope.launch { userPreferencesRepository.saveCycleEnabled(enabled) }
        rebuildCombinedPreviewOnly()
        if (!enabled) stopCycle(clearFromChatbox = true)
    }

    fun setSpotifyEnabledFlag(enabled: Boolean) {
        spotifyEnabled = enabled
        rebuildCombinedPreviewOnly()
        if (!enabled) stopNowPlayingSender(clearFromChatbox = true)
    }

    fun setSpotifyDemoFlag(enabled: Boolean) {
        spotifyDemoEnabled = enabled
        rebuildCombinedPreviewOnly()
    }

    fun updateSpotifyPreset(preset: Int) {
        val v = preset.coerceIn(1, 5)
        spotifyPreset = v
        viewModelScope.launch { userPreferencesRepository.saveSpotifyPreset(v) }
        rebuildCombinedPreviewOnly()
    }

    // =========================
    // AFK text update
    // =========================
    fun updateAfkText(text: String) {
        afkMessage = text
        viewModelScope.launch { userPreferencesRepository.saveAfkMessage(text) }
        rebuildCombinedPreviewOnly()
    }

    // =========================
    // Cycle lines management
    // =========================
    private fun setCycleLinesFromTextPreserve(text: String) {
        val raw = text.split("\n")
        val lines = raw.take(10)
        cycleLines.clear()
        cycleLines.addAll(lines)
        rebuildCombinedPreviewOnly()
    }

    private fun persistCycleLinesPreserve() {
        val joined = cycleLines.take(10).joinToString("\n")
        viewModelScope.launch { userPreferencesRepository.saveCycleMessages(joined) }
    }

    fun addCycleLine() {
        if (cycleLines.size >= 10) return
        cycleLines.add("")
        persistCycleLinesPreserve()
        rebuildCombinedPreviewOnly()
    }

    fun removeCycleLine(index: Int) {
        if (index !in cycleLines.indices) return
        cycleLines.removeAt(index)
        persistCycleLinesPreserve()
        rebuildCombinedPreviewOnly()
    }

    fun updateCycleLine(index: Int, value: String) {
        if (index !in cycleLines.indices) return
        cycleLines[index] = value
        persistCycleLinesPreserve()
        rebuildCombinedPreviewOnly()
    }

    fun clearCycleLines() {
        cycleLines.clear()
        persistCycleLinesPreserve()
        rebuildCombinedPreviewOnly()
    }

    // =========================
    // Preset previews
    // =========================
    fun getAfkPresetPreview(slot: Int): String {
        val i = slot.coerceIn(1, 3) - 1
        return afkPresetTexts[i].trim()
    }

    fun getCyclePresetPreview(slot: Int): String {
        val i = slot.coerceIn(1, 5) - 1
        val firstLine = cyclePresetMessages[i].lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return firstLine
    }

    fun getMusicPresetName(preset: Int): String = when (preset.coerceIn(1, 5)) {
        1 -> "Love"
        2 -> "Minimal"
        3 -> "Crystal"
        4 -> "Soundwave"
        else -> "Geometry"
    }

    fun renderMusicPresetPreview(preset: Int, t: Float): String {
        val p = t.coerceIn(0f, 1f)
        val pos = (p * 1000f).toLong()
        return renderProgressBar(preset, pos, 1000L, isPlaying = true)
    }

    // =========================
    // Preset save/load
    // =========================
    suspend fun saveAfkPreset(slot: Int, text: String) {
        val s = slot.coerceIn(1, 3)
        val idx = s - 1
        afkPresetTexts[idx] = text
        when (s) {
            1 -> userPreferencesRepository.saveAfkPreset1(text)
            2 -> userPreferencesRepository.saveAfkPreset2(text)
            else -> userPreferencesRepository.saveAfkPreset3(text)
        }
    }

    suspend fun loadAfkPreset(slot: Int) {
        val txt = when (slot.coerceIn(1, 3)) {
            1 -> userPreferencesRepository.afkPreset1.first()
            2 -> userPreferencesRepository.afkPreset2.first()
            else -> userPreferencesRepository.afkPreset3.first()
        }
        updateAfkText(txt)
    }

    suspend fun saveCyclePreset(slot: Int, lines: List<String>) {
        val s = slot.coerceIn(1, 5)
        val idx = s - 1

        val messages = lines.map { it.trim() }.filter { it.isNotEmpty() }.take(10).joinToString("\n")
        val interval = CYCLE_INTERVAL_SECONDS_LOCKED

        cyclePresetMessages[idx] = messages
        cyclePresetIntervals[idx] = interval

        when (s) {
            1 -> userPreferencesRepository.saveCyclePreset1(messages, interval)
            2 -> userPreferencesRepository.saveCyclePreset2(messages, interval)
            3 -> userPreferencesRepository.saveCyclePreset3(messages, interval)
            4 -> userPreferencesRepository.saveCyclePreset4(messages, interval)
            else -> userPreferencesRepository.saveCyclePreset5(messages, interval)
        }
    }

    suspend fun loadCyclePreset(slot: Int) {
        val s = slot.coerceIn(1, 5)
        val (messages, _) = when (s) {
            1 -> userPreferencesRepository.cyclePreset1Messages.first() to userPreferencesRepository.cyclePreset1Interval.first()
            2 -> userPreferencesRepository.cyclePreset2Messages.first() to userPreferencesRepository.cyclePreset2Interval.first()
            3 -> userPreferencesRepository.cyclePreset3Messages.first() to userPreferencesRepository.cyclePreset3Interval.first()
            4 -> userPreferencesRepository.cyclePreset4Messages.first() to userPreferencesRepository.cyclePreset4Interval.first()
            else -> userPreferencesRepository.cyclePreset5Messages.first() to userPreferencesRepository.cyclePreset5Interval.first()
        }

        cycleIntervalSeconds = CYCLE_INTERVAL_SECONDS_LOCKED
        viewModelScope.launch { userPreferencesRepository.saveCycleInterval(cycleIntervalSeconds) }

        setCycleLinesFromTextPreserve(messages)
        persistCycleLinesPreserve()
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
                delay(12_000L)
            }
        }
    }

    fun stopAfkSender(clearFromChatbox: Boolean) {
        afkJob?.cancel()
        afkJob = null
        if (clearFromChatbox) rebuildAndMaybeSendCombined(forceSend = true, forceClearIfAllOff = true)
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

        viewModelScope.launch { userPreferencesRepository.saveCycleEnabled(true) }
        persistCycleLinesPreserve()
        cycleIntervalSeconds = CYCLE_INTERVAL_SECONDS_LOCKED
        viewModelScope.launch { userPreferencesRepository.saveCycleInterval(cycleIntervalSeconds) }

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
                delay(CYCLE_INTERVAL_SECONDS_LOCKED.toLong() * 1000L)
            }
        }
    }

    fun stopCycle(clearFromChatbox: Boolean) {
        cycleJob?.cancel()
        cycleJob = null
        if (clearFromChatbox) rebuildAndMaybeSendCombined(forceSend = true, forceClearIfAllOff = true)
    }

    // =========================
    // Now Playing sender
    // =========================
    fun startNowPlayingSender(local: Boolean = false) {
        if (!spotifyEnabled) return
        nowPlayingJob?.cancel()
        nowPlayingJob = viewModelScope.launch {
            while (spotifyEnabled) {
                rebuildAndMaybeSendCombined(forceSend = true, local = local)
                delay(MUSIC_REFRESH_SECONDS_LOCKED.toLong() * 1000L)
            }
        }
    }

    fun stopNowPlayingSender(clearFromChatbox: Boolean) {
        nowPlayingJob?.cancel()
        nowPlayingJob = null
        if (clearFromChatbox) rebuildAndMaybeSendCombined(forceSend = true, forceClearIfAllOff = true)
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
    // Combined builder + sender
    // =========================
    private fun rebuildCombinedPreviewOnly(forceClearIfAllOff: Boolean = false) {
        val built = buildCombinedText(cycleLineOverride = null)
        combinedPreviewText = built
        if (forceClearIfAllOff && built.isBlank()) combinedPreviewText = ""
    }

    private fun rebuildAndMaybeSendCombined(
        forceSend: Boolean,
        local: Boolean = false,
        cycleLineOverride: String? = null,
        forceClearIfAllOff: Boolean = false
    ) {
        val combined = buildCombinedText(cycleLineOverride)

        if (forceClearIfAllOff && combined.isBlank()) {
            clearChatbox(local)
            combinedPreviewText = ""
            return
        }

        combinedPreviewText = combined
        if (!forceSend) return
        if (combined.isBlank()) return

        val nowMs = System.currentTimeMillis()
        val minMs = 2_000L
        if (nowMs - lastCombinedSendMs < minMs) return

        sendToVrchatRaw(combined, local, addToConversation = false)
        lastCombinedSendMs = nowMs
    }

    private fun buildCombinedText(cycleLineOverride: String?): String {
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
        return combined
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
    // Now Playing builder
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

        val dur = if (spotifyDemoEnabled && !nowPlayingDetected) 205_000L else nowPlayingDurationMs
        val posSnapshot = if (spotifyDemoEnabled && !nowPlayingDetected) 78_000L else nowPlayingPositionMs

        val playing = derivedIsPlaying

        val pos = if (playing && dur > 0L) {
            val elapsed = SystemClock.elapsedRealtime() - nowPlayingPositionUpdateTimeMs
            val adj = (elapsed * nowPlayingSpeed).toLong()
            (posSnapshot + max(0L, adj)).coerceAtMost(dur)
        } else {
            posSnapshot
        }

        val bar = renderProgressBar(
            preset = spotifyPreset,
            posMs = pos,
            durMs = max(1L, dur),
            isPlaying = playing
        )

        val time = "${fmtTime(pos)} / ${fmtTime(max(1L, dur))}"
        val status = if (!playing) "Paused" else ""

        val line2 = listOf(bar, time).joinToString(" ").trim()
        val line3 = status.takeIf { it.isNotBlank() }

        return listOfNotNull(line1.takeIf { it.isNotBlank() }, line2.takeIf { it.isNotBlank() }, line3)
    }

    // =========================
    // Progress bars
    // =========================
    private fun renderProgressBar(preset: Int, posMs: Long, durMs: Long, isPlaying: Boolean): String {
        val duration = max(1L, durMs)
        val p = min(1f, max(0f, posMs.toFloat() / duration.toFloat()))

        return when (preset.coerceIn(1, 5)) {
            1 -> { // Love (10 chars total)
                val innerSlots = 8
                val idx = (p * (innerSlots - 1)).toInt()
                val inner = CharArray(innerSlots) { '━' }
                inner[idx] = '◉'
                "♡" + inner.concatToString() + "♡"
            }

            2 -> { // Minimal (10 chars)
                val slots = 10
                val idx = (p * (slots - 1)).toInt()
                val bg = CharArray(slots) { '─' }
                bg[idx] = '◉'
                bg.concatToString()
            }

            3 -> { // Crystal (10 chars)
                val slots = 10
                val idx = (p * (slots - 1)).toInt()
                val bg = CharArray(slots) { '⟡' }
                bg[idx] = '◉'
                bg.concatToString()
            }

            4 -> renderSoundwaveBar(p, posMs, isPlaying) // ✅ soundwave uses square marker

            else -> { // Geometry (10 chars)
                val slots = 10
                val idx = (p * (slots - 1)).toInt()
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

    private val soundwavePatterns: List<IntArray> = listOf(
        intArrayOf(6, 3, 6, 4, 7, 3, 6, 4, 7, 4, 6, 3),
        intArrayOf(7, 4, 6, 3, 7, 5, 6, 3, 7, 4, 6, 5),
        intArrayOf(5, 7, 4, 6, 3, 7, 4, 6, 3, 7, 4, 6),
        intArrayOf(6, 4, 7, 5, 3, 6, 4, 7, 5, 3, 6, 4),
        intArrayOf(7, 5, 3, 6, 4, 7, 5, 3, 6, 4, 7, 5),
        intArrayOf(6, 3, 5, 7, 4, 6, 3, 5, 7, 4, 6, 3),
        intArrayOf(5, 6, 7, 4, 3, 7, 6, 5, 4, 7, 6, 5),
        intArrayOf(7, 6, 4, 7, 5, 3, 6, 7, 4, 6, 7, 5),
        intArrayOf(6, 7, 5, 3, 6, 7, 5, 4, 7, 6, 4, 5),
        intArrayOf(7, 4, 6, 7, 3, 5, 7, 4, 6, 7, 3, 5)
    )

    private val soundwavePaused: IntArray = intArrayOf(4, 5, 4, 6, 4, 5, 4, 6, 4, 5, 4, 6)

    /**
     * ✅ EXACT LENGTH = 10 chars total:
     * - 8 wave chars
     * - plus "[" and "]" around the progress index → +2
     * ✅ Marker inside brackets is square: uses ▣
     */
    private fun renderSoundwaveBar(progress01: Float, posMs: Long, isPlaying: Boolean): String {
        val slots = 8
        val idx = (progress01 * (slots - 1)).toInt().coerceIn(0, slots - 1)

        val patternIndex = if (isPlaying) {
            ((posMs / 1400L) % soundwavePatterns.size).toInt()
        } else -1

        val base = if (patternIndex >= 0) soundwavePatterns[patternIndex] else soundwavePaused
        val phase = if (isPlaying) ((posMs / 180L) % base.size).toInt() else ((posMs / 900L) % base.size).toInt()

        val chars = CharArray(slots) { i ->
            val amp = base[(i + phase) % base.size].coerceIn(1, 8)
            ampToChar(amp)
        }

        val out = StringBuilder(10)
        for (i in 0 until slots) {
            if (i == idx) out.append('[')
            // replace the tracked "dot" with square marker at the index
            out.append(if (i == idx) '▣' else chars[i])
            if (i == idx) out.append(']')
        }
        return out.toString()
    }

    private fun ampToChar(a: Int): Char = when (a.coerceIn(1, 8)) {
        1 -> '▁'
        2 -> '▂'
        3 -> '▃'
        4 -> '▄'
        5 -> '▅'
        6 -> '▆'
        7 -> '▇'
        else -> '█'
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

        for (line in clean) {
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
