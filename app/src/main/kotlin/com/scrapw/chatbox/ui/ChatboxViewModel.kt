package com.scrapw.chatbox.ui

import android.content.Intent
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

    // =========================
    // AFK (TOP line) + manual send for ChatboxScreen.kt
    // =========================
    var afkEnabled by mutableStateOf(false)

    // You said: persist the *text* only; toggle state does NOT have to persist
    var afkMessage by mutableStateOf("AFK 🌙 back soon")

    // Forced interval (no chooser)
    private val afkIntervalSeconds: Int = 25

    private var afkJob: Job? = null

    /**
     * ✅ This is the missing function ChatboxScreen.kt is calling.
     * Sends AFK immediately (top line), without needing cycle/music.
     */
    fun sendAfkNow(local: Boolean = false) {
        if (!afkEnabled) return
        sendCombinedToVrchat(local = local, reason = "AFK_NOW")
    }

    fun startAfkSender(local: Boolean = false) {
        if (!afkEnabled) return
        afkJob?.cancel()
        afkJob = viewModelScope.launch {
            while (afkEnabled) {
                sendCombinedToVrchat(local = local, reason = "AFK_TICK")
                delay(afkIntervalSeconds * 1000L)
            }
        }
    }

    fun stopAfkSender() {
        afkJob?.cancel()
        afkJob = null
    }

    // =========================
    // Cycle
    // =========================
    var cycleEnabled by mutableStateOf(false)
    var cycleMessages by mutableStateOf("")
    var cycleIntervalSeconds by mutableStateOf(3)
    private var cycleJob: Job? = null

    private var cycleIndex = 0
    private var lastCycleLine: String = ""

    fun startCycle(local: Boolean = false) {
        val msgs = cycleMessages.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (!cycleEnabled || msgs.isEmpty()) return

        cycleJob?.cancel()
        cycleJob = viewModelScope.launch {
            cycleIndex = 0
            while (cycleEnabled) {
                lastCycleLine = msgs[cycleIndex % msgs.size]
                sendCombinedToVrchat(local = local, reason = "CYCLE_TICK")
                cycleIndex = (cycleIndex + 1) % msgs.size
                delay(cycleIntervalSeconds.coerceAtLeast(1).toLong() * 1000L)
            }
        }
    }

    fun stopCycle() {
        cycleJob?.cancel()
        cycleJob = null
    }

    // =========================
    // Now Playing (phone music) – UI still calls these as “spotify”
    // =========================
    var spotifyEnabled by mutableStateOf(false)
    var spotifyDemoEnabled by mutableStateOf(false)

    // 1..5 (your presets)
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

    // internal now playing timing (raw snapshot)
    private var snapDurationMs by mutableStateOf(0L)
    private var snapPositionMs by mutableStateOf(0L)
    private var snapPositionUpdateTimeMs by mutableStateOf(0L)
    private var snapPlaybackSpeed by mutableStateOf(1f)
    private var snapIsPlaying by mutableStateOf(false)

    private var nowPlayingJob: Job? = null

    init {
        // Bind to listener state
        viewModelScope.launch {
            NowPlayingState.state.collect { s ->
                listenerConnected = s.listenerConnected
                activePackage = if (s.activePackage.isBlank()) "(none)" else s.activePackage
                nowPlayingDetected = s.detected
                lastNowPlayingTitle = if (s.title.isBlank()) "(blank)" else s.title
                lastNowPlayingArtist = if (s.artist.isBlank()) "(blank)" else s.artist

                snapDurationMs = s.durationMs
                snapPositionMs = s.positionMs
                snapPositionUpdateTimeMs = s.positionUpdateTimeMs
                snapPlaybackSpeed = s.playbackSpeed
                snapIsPlaying = s.isPlaying
            }
        }
    }

    fun notificationAccessIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun setSpotifyEnabledFlag(enabled: Boolean) {
        spotifyEnabled = enabled
        if (!enabled) stopNowPlayingSender()
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
                sendCombinedToVrchat(local = local, reason = "MUSIC_TICK")
                delay(musicRefreshSeconds.coerceAtLeast(1).toLong() * 1000L)
            }
        }
    }

    fun stopNowPlayingSender() {
        nowPlayingJob?.cancel()
        nowPlayingJob = null
    }

    fun sendNowPlayingOnce(local: Boolean = false) {
        sendCombinedToVrchat(local = local, reason = "MUSIC_ONCE")
    }

    fun stopAll() {
        stopCycle()
        stopNowPlayingSender()
        stopAfkSender()
    }

    // =========================
    // Combine output WITHOUT cancelling other timers
    // AFK (top) + Cycle + NowPlaying (bottom)
    // =========================
    private fun sendCombinedToVrchat(local: Boolean, reason: String) {
        val outgoing = buildOutgoingText()
        if (outgoing.isBlank()) return
        sendToVrchatRaw(outgoing, local, addToConversation = false)

        // Optional: useful for Debug tab
        Log.d("VRC-A", "OSC SEND [$reason]\n$outgoing")
    }

    private fun buildOutgoingText(): String {
        val lines = mutableListOf<String>()

        // 1) AFK always first (if enabled)
        if (afkEnabled) {
            val t = afkMessage.trim().ifBlank { "AFK" }
            lines += t
        }

        // 2) Cycle line (if enabled + has content)
        if (cycleEnabled) {
            val c = lastCycleLine.trim()
            if (c.isNotEmpty()) lines += c
        }

        // 3) Now Playing block always last
        lines.addAll(buildNowPlayingLines())

        // enforce VRChat chatbox size
        return joinWithLimit(lines, 144)
    }

    private fun buildNowPlayingLines(): List<String> {
        if (!spotifyEnabled) return emptyList()

        // Demo if no real detection
        val title = if (spotifyDemoEnabled && !nowPlayingDetected) "Pretty Girl" else lastNowPlayingTitle
        val artist = if (spotifyDemoEnabled && !nowPlayingDetected) "Clairo" else lastNowPlayingArtist

        if (!spotifyDemoEnabled && !nowPlayingDetected) return emptyList()

        val safeTitle = title.takeIf { it != "(blank)" } ?: ""
        val safeArtist = artist.takeIf { it != "(blank)" } ?: ""

        // line 1: artist — title (drop artist if overflow)
        val maxLine = 42
        val combined = if (safeArtist.isNotBlank()) "$safeArtist — $safeTitle" else safeTitle
        val line1 = when {
            combined.length <= maxLine -> combined
            safeTitle.length <= maxLine -> safeTitle
            else -> safeTitle.take(maxLine - 1) + "…"
        }.trim()

        // live progress (doesn't require notification refresh)
        val dur = if (spotifyDemoEnabled && !nowPlayingDetected) 80_000L else snapDurationMs
        val pos = if (spotifyDemoEnabled && !nowPlayingDetected) 58_000L else currentPositionMs()

        val pausedTag = if (!snapIsPlaying && nowPlayingDetected) " (paused)" else ""
        val bar = renderProgressBar(spotifyPreset, pos, dur)
        val time = "${fmtTime(pos)} / ${fmtTime(if (dur > 0) dur else max(pos, 1L))}"
        val line2 = "$bar $time$pausedTag".trim()

        return listOf(line1, line2).filter { it.isNotBlank() }
    }

    /**
     * Live position using the snapshot’s position + elapsed time.
     */
    private fun currentPositionMs(): Long {
        val base = snapPositionMs
        if (!snapIsPlaying) return base

        val now = android.os.SystemClock.elapsedRealtime()
        val dt = max(0L, now - snapPositionUpdateTimeMs)
        val adv = (dt.toDouble() * snapPlaybackSpeed.toDouble()).toLong()
        val raw = base + adv
        val dur = max(0L, snapDurationMs)

        return if (dur > 0L) raw.coerceIn(0L, dur) else max(0L, raw)
    }

    // =========================
    // Your 5 preset bars (short like the love bar)
    // =========================
    private fun renderProgressBar(preset: Int, posMs: Long, durMs: Long): String {
        val duration = max(1L, durMs)
        val p = min(1f, max(0f, posMs.toFloat() / duration.toFloat()))

        return when (preset.coerceIn(1, 5)) {
            // (love) ♡━━━◉━━━━♡
            1 -> {
                val innerSlots = 8
                val idx = (p * (innerSlots - 1)).toInt()
                val inner = CharArray(innerSlots) { '━' }
                inner[idx] = '◉'
                "♡" + inner.concatToString() + "♡"
            }

            // (minimal)
            2 -> {
                val slots = 10
                val idx = (p * (slots - 1)).toInt()
                val bg = CharArray(slots) { '─' }
                bg[idx] = '◉'
                bg.concatToString()
            }

            // (crystal)
            3 -> {
                val slots = 10
                val idx = (p * (slots - 1)).toInt()
                val bg = CharArray(slots) { '⟡' }
                bg[idx] = '◉'
                bg.concatToString()
            }

            // (soundwave)
            4 -> {
                val bg = charArrayOf('▁','▂','▃','▄','▅','▅','▄','▃','▂','▁')
                val idx = (p * (bg.size - 1)).toInt()
                val out = bg.copyOf()
                out[idx] = '●'
                out.concatToString()
            }

            // (geometry)
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

        // keep bottom-most lines (now playing) if overflow happens
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

    private fun sendToVrchatRaw(text: String, local: Boolean, addToConversation: Boolean) {
        val osc = if (!local) remoteChatboxOSC else localChatboxOSC
        osc.sendMessage(
            text,
            messengerUiState.value.isSendImmediately,
            messengerUiState.value.isTriggerSFX
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
