package com.scrapw.chatbox.ui

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
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
        stopAll()
        super.onCleared()
    }

    val conversationUiState = ConversationUiState()

    // --- Existing settings flow (keep) ---
    private val storedIpState: StateFlow<String> =
        userPreferencesRepository.ipAddress.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ""
        )

    private val userInputIpState = kotlinx.coroutines.flow.MutableStateFlow("")
    var ipAddressLocked by mutableStateOf(false)

    private val ipFlow = combine(storedIpState, userInputIpState) { a, b -> if (b.isNotBlank()) b else a }

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

    val messageText = mutableStateOf(TextFieldValue(""))

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

    val isAddressResolvable = mutableStateOf(true)

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

        osc.sendMessage(
            messageText.value.text,
            messengerUiState.value.isSendImmediately,
            messengerUiState.value.isTriggerSFX
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

    // --- Update checker (keep) ---
    private var updateChecked = false
    var updateInfo by mutableStateOf(UpdateInfo(UpdateStatus.NOT_CHECKED))
    fun checkUpdate() {
        if (updateChecked) return
        updateChecked = true
        viewModelScope.launch(Dispatchers.Main) {
            updateInfo = checkUpdate("ScrapW", "Chatbox")
        }
    }

    // =========================================================
    // SAFETY: send throttle (prevents VRChat cutouts)
    // =========================================================
    private val minSendIntervalMs = 2_000L // hard floor (2s)
    private var lastSendAtMs = 0L

    // Debug strings (shown in Debug tab)
    var debugLastAfkOsc by mutableStateOf("")
        private set
    var debugLastCycleOsc by mutableStateOf("")
        private set
    var debugLastMusicOsc by mutableStateOf("")
        private set
    var debugLastCombinedOsc by mutableStateOf("")
        private set

    // =========================================================
    // AFK (top line) — forced interval, separate sender
    // =========================================================
    var afkEnabled by mutableStateOf(false)

    // IMPORTANT: private set + renamed updater avoids JVM clash
    var afkMessage by mutableStateOf("AFK")
        private set

    private val afkForcedIntervalMs = 10_000L // forced; no UI control
    private var afkJob: Job? = null

    // AFK presets (3) - in-memory for now (compile-safe)
    // NOTE: If your repo already has persistent storage, we can wire it later.
    private val afkPresets = Array(3) { "" }

    fun updateAfkMessage(text: String) {
        afkMessage = text
    }

    fun saveAfkPreset(slot: Int, text: String) {
        val idx = (slot - 1).coerceIn(0, 2)
        afkPresets[idx] = text
    }

    fun loadAfkPreset(slot: Int) {
        val idx = (slot - 1).coerceIn(0, 2)
        val t = afkPresets[idx]
        if (t.isNotBlank()) afkMessage = t
    }

    fun startAfkSender(local: Boolean = false) {
        if (!afkEnabled) return
        afkJob?.cancel()
        afkJob = viewModelScope.launch {
            while (afkEnabled) {
                sendCombinedNow(local, reason = "afkTick")
                delay(afkForcedIntervalMs)
            }
        }
    }

    fun stopAfkSender(local: Boolean = false) {
        afkJob?.cancel()
        afkJob = null
        // Remove AFK from chatbox immediately by pushing an updated combined message
        sendCombinedNow(local, reason = "afkStop")
    }

    fun sendAfkNow(local: Boolean = false) {
        sendCombinedNow(local, reason = "afkOnce")
    }

    // =========================================================
    // Cycle (separate sender)
    // =========================================================
    var cycleEnabled by mutableStateOf(false)

    var cycleMessages by mutableStateOf("")
        private set

    var cycleIntervalSeconds by mutableStateOf(3)
    private var cycleJob: Job? = null
    private var cycleIndex = 0

    // Cycle presets (5) - in-memory for now (compile-safe)
    private val cyclePresets = Array(5) { "" }

    fun updateCycleMessages(text: String) {
        cycleMessages = text
    }

    fun saveCyclePreset(slot: Int, text: String) {
        val idx = (slot - 1).coerceIn(0, 4)
        cyclePresets[idx] = text
    }

    fun loadCyclePreset(slot: Int) {
        val idx = (slot - 1).coerceIn(0, 4)
        val t = cyclePresets[idx]
        if (t.isNotBlank()) cycleMessages = t
    }

    fun startCycle(local: Boolean = false) {
        val msgs = cycleMessages.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (!cycleEnabled || msgs.isEmpty()) return

        cycleJob?.cancel()
        cycleJob = viewModelScope.launch {
            cycleIndex = 0
            while (cycleEnabled) {
                // Rotate cycle line, but sending is always a combined render
                cycleIndex = (cycleIndex + 1) % msgs.size
                sendCombinedNow(local, reason = "cycleTick")
                val delaySec = max(2, cycleIntervalSeconds) // floor 2s
                delay(delaySec.toLong() * 1000L)
            }
        }
    }

    fun stopCycle(local: Boolean = false) {
        cycleJob?.cancel()
        cycleJob = null
        // Remove cycle line immediately by pushing an updated combined message
        sendCombinedNow(local, reason = "cycleStop")
    }

    // =========================================================
    // Now Playing (phone music) – UI still calls these as “spotify”
    // =========================================================
    var spotifyEnabled by mutableStateOf(false)
    var spotifyDemoEnabled by mutableStateOf(false)
    var spotifyPreset by mutableStateOf(1)

    // refresh seconds for progress updates (separate from main cycle speed)
    var musicRefreshSeconds by mutableStateOf(2)

    // Debug fields shown in UI
    var listenerConnected by mutableStateOf(false)
    var activePackage by mutableStateOf("(none)")
    var nowPlayingDetected by mutableStateOf(false)
    var lastNowPlayingTitle by mutableStateOf("(blank)")
    var lastNowPlayingArtist by mutableStateOf("(blank)")
    var lastSentToVrchatAtMs by mutableStateOf(0L)

    // internal now playing timing
    private var nowPlayingDurationMs by mutableStateOf(0L)
    private var nowPlayingPositionMs by mutableStateOf(0L)
    private var nowPlayingPositionUpdateTimeMs by mutableStateOf(0L)
    private var nowPlayingSpeed by mutableStateOf(1f)
    var nowPlayingIsPlaying by mutableStateOf(false)
        private set

    private var nowPlayingJob: Job? = null

    init {
        // Bind to listener state (fixes “false/blank/blank” sticking)
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

    fun notificationAccessIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun setSpotifyEnabledFlag(enabled: Boolean) {
        spotifyEnabled = enabled
    }

    fun setSpotifyDemoFlag(enabled: Boolean) {
        spotifyDemoEnabled = enabled
    }

    fun updateSpotifyPreset(preset: Int) {
        spotifyPreset = preset.coerceIn(1, 5)
    }

    fun startNowPlayingSender(local: Boolean = false) {
        if (!spotifyEnabled) return

        nowPlayingJob?.cancel()
        nowPlayingJob = viewModelScope.launch {
            while (spotifyEnabled) {
                sendCombinedNow(local, reason = "musicTick")
                val d = max(2, musicRefreshSeconds) // floor 2s
                delay(d.toLong() * 1000L)
            }
        }
    }

    fun stopNowPlayingSender(local: Boolean = false) {
        nowPlayingJob?.cancel()
        nowPlayingJob = null
        // Remove music block immediately by pushing an updated combined message
        sendCombinedNow(local, reason = "musicStop")
    }

    fun sendNowPlayingOnce(local: Boolean = false) {
        sendCombinedNow(local, reason = "musicOnce")
    }

    fun stopAll(local: Boolean = false) {
        stopCycle(local)
        stopNowPlayingSender(local)
        stopAfkSender(local)
    }

    // =========================================================
    // Combined render + send (AFK top, Cycle middle, Music bottom)
    // =========================================================
    private fun buildCombinedText(): String {
        val lines = mutableListOf<String>()

        // AFK (top)
        val afkLine = buildAfkLine()
        if (afkLine.isNotBlank()) lines += afkLine

        // Cycle (middle)
        val cycleLine = buildCycleLine()
        if (cycleLine.isNotBlank()) lines += cycleLine

        // Music (bottom)
        val musicLines = buildNowPlayingLines()
        lines.addAll(musicLines)

        val combined = joinWithLimit(lines, 144)

        // Debug previews
        debugLastAfkOsc = afkLine
        debugLastCycleOsc = cycleLine
        debugLastMusicOsc = musicLines.joinToString("\n")
        debugLastCombinedOsc = combined

        return combined
    }

    private fun buildAfkLine(): String {
        if (!afkEnabled) return ""
        val t = afkMessage.trim()
        if (t.isBlank()) return "AFK"
        // Keep it short-ish, but don’t hard cut too aggressive
        return t.take(48)
    }

    private fun buildCycleLine(): String {
        if (!cycleEnabled) return ""
        val msgs = cycleMessages.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (msgs.isEmpty()) return ""
        val idx = (cycleIndex % msgs.size).coerceAtLeast(0)
        return msgs[idx]
    }

    private fun buildNowPlayingLines(): List<String> {
        if (!spotifyEnabled) return emptyList()

        // Demo fallback if no real detection
        val title = if (spotifyDemoEnabled && !nowPlayingDetected) "Pretty Girl" else lastNowPlayingTitle
        val artist = if (spotifyDemoEnabled && !nowPlayingDetected) "Clairo" else lastNowPlayingArtist

        // If truly nothing detected and demo off → show nothing
        if (!spotifyDemoEnabled && !nowPlayingDetected) return emptyList()

        val safeTitle = title.takeIf { it != "(blank)" } ?: ""
        val safeArtist = artist.takeIf { it != "(blank)" } ?: ""

        // line 1: artist — title
        val maxLine = 42
        val combined = if (safeArtist.isNotBlank()) "$safeArtist — $safeTitle" else safeTitle
        val line1 = when {
            combined.length <= maxLine -> combined
            safeTitle.length <= maxLine -> safeTitle
            else -> safeTitle.take(maxLine - 1) + "…"
        }.trim()

        // live position estimate (keeps progress moving even if notification isn’t updating)
        val (pos, dur) = getLivePositionAndDuration()

        val bar = renderProgressBar(spotifyPreset, pos, dur)
        val status = if (nowPlayingIsPlaying) "" else " (Paused)"
        val time = "${fmtTime(pos)} / ${fmtTime(if (dur > 0) dur else max(pos, 1L))}$status"
        val line2 = "$bar $time".trim()

        return listOf(line1, line2).filter { it.isNotBlank() }
    }

    private fun getLivePositionAndDuration(): Pair<Long, Long> {
        val dur = if (spotifyDemoEnabled && !nowPlayingDetected) 80_000L else nowPlayingDurationMs

        // demo
        if (spotifyDemoEnabled && !nowPlayingDetected) {
            return 58_000L to dur
        }

        // If paused or no update timestamp, use snapshot
        if (!nowPlayingIsPlaying || nowPlayingPositionUpdateTimeMs <= 0L) {
            return nowPlayingPositionMs to dur
        }

        // Estimate live position using elapsed time since snapshot
        val now = android.os.SystemClock.elapsedRealtime()
        val delta = now - nowPlayingPositionUpdateTimeMs
        val est = nowPlayingPositionMs + (delta * nowPlayingSpeed).toLong()
        val clamped = if (dur > 0) est.coerceIn(0L, dur) else max(0L, est)
        return clamped to dur
    }

    // Your 5 preset bars (short)
    private fun renderProgressBar(preset: Int, posMs: Long, durMs: Long): String {
        val duration = max(1L, durMs)
        val p = min(1f, max(0f, posMs.toFloat() / duration.toFloat()))

        return when (preset.coerceIn(1, 5)) {
            1 -> { // love ♡━━━◉━━━━♡ (10 total incl caps)
                val innerSlots = 8
                val idx = (p * (innerSlots - 1)).toInt()
                val inner = CharArray(innerSlots) { '━' }
                inner[idx] = '◉'
                "♡" + inner.concatToString() + "♡"
            }
            2 -> { // minimal
                val slots = 10
                val idx = (p * (slots - 1)).toInt()
                val bg = CharArray(slots) { '─' }
                bg[idx] = '◉'
                bg.concatToString()
            }
            3 -> { // crystal
                val slots = 10
                val idx = (p * (slots - 1)).toInt()
                val bg = CharArray(slots) { '⟡' }
                bg[idx] = '◉'
                bg.concatToString()
            }
            4 -> { // soundwave
                val bg = charArrayOf('▁','▂','▃','▄','▅','▅','▄','▃','▂','▁')
                val idx = (p * (bg.size - 1)).toInt()
                val out = bg.copyOf()
                out[idx] = '●'
                out.concatToString()
            }
            else -> { // geometry
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

        // Keep bottom content (music) by building bottom-up
        val out = ArrayList<String>()
        var total = 0

        for (i in clean.indices.reversed()) {
            val line = clean[i]
            val add = if (out.isEmpty()) line.length else (1 + line.length)
            if (total + add > limit) {
                if (i == 0 && limit - total > 2) {
                    val remain = limit - total - (if (out.isEmpty()) 0 else 1)
                    val cut = line.take(max(1, remain - 1)) + "…"
                    out.add(0, cut)
                    total = limit
                }
                continue
            }
            out.add(0, line)
            total += add
        }

        return out.joinToString("\n")
    }

    // =========================================================
    // Actual send
    // =========================================================
    private fun sendCombinedNow(local: Boolean, reason: String) {
        val text = buildCombinedText()

        // If everything is OFF, push a blank clear (space) so it disappears immediately
        val finalText = if (text.isBlank()) " " else text

        sendToVrchatThrottled(finalText, local)
    }

    private fun sendToVrchatThrottled(text: String, local: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastSendAtMs < minSendIntervalMs) {
            // Too soon; drop this tick to avoid VRChat cutouts
            return
        }
        lastSendAtMs = now

        val osc = if (!local) remoteChatboxOSC else localChatboxOSC
        osc.sendMessage(
            text,
            messengerUiState.value.isSendImmediately,
            messengerUiState.value.isTriggerSFX
        )

        lastSentToVrchatAtMs = now
    }
}

data class MessengerUiState(
    val ipAddress: String = "127.0.0.1",
    val isRealtimeMsg: Boolean = false,
    val isTriggerSFX: Boolean = true,
    val isTypingIndicator: Boolean = true,
    val isSendImmediately: Boolean = true
)
