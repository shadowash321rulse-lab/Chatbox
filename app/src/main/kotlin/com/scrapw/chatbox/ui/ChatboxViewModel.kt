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
import kotlinx.coroutines.flow.*
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

    // ----------------------------
    // Existing settings flow (KEEP)
    // ----------------------------
    private val storedIpState: StateFlow<String> =
        userPreferencesRepository.ipAddress.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ""
        )

    private val userInputIpState = MutableStateFlow("")
    var ipAddressLocked by mutableStateOf(false)

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

    // ==========================================================
    // ✅ FIXED SENDER MODEL:
    // We store "current" values (AFK line, cycle line, now playing),
    // and EVERY send composes the full block so nothing overwrites.
    // ==========================================================

    // ----------------------------
    // AFK (top, independent)
    // ----------------------------
    var afkEnabled by mutableStateOf(false)
    var afkMessage by mutableStateOf("AFK 🌙 back soon")
    var afkIntervalSeconds by mutableStateOf(20) // independent delay
    private var afkJob: Job? = null

    fun sendAfkNow(local: Boolean = false) {
        // Send full composed block (AFK + whatever else is active)
        sendComposedToVrchat(local)
    }

    private fun startAfkSender(local: Boolean = false) {
        afkJob?.cancel()
        if (!afkEnabled) return

        afkJob = viewModelScope.launch {
            while (afkEnabled) {
                sendComposedToVrchat(local)
                delay(afkIntervalSeconds.coerceAtLeast(1).toLong() * 1000L)
            }
        }
    }

    private fun stopAfkSender() {
        afkJob?.cancel()
        afkJob = null
    }

    // ----------------------------
    // Cycle (independent)
    // ----------------------------
    var cycleEnabled by mutableStateOf(false)
    var cycleMessages by mutableStateOf("")
    var cycleIntervalSeconds by mutableStateOf(3)

    private var cycleJob: Job? = null
    private var cycleIndex = 0
    private var currentCycleLine by mutableStateOf("")

    fun startCycle(local: Boolean = false) {
        val msgs = cycleMessages.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (!cycleEnabled || msgs.isEmpty()) return

        cycleJob?.cancel()
        cycleJob = viewModelScope.launch {
            cycleIndex = 0
            // set immediately, send immediately
            currentCycleLine = msgs[cycleIndex % msgs.size]
            sendComposedToVrchat(local)

            while (cycleEnabled) {
                delay(cycleIntervalSeconds.coerceAtLeast(1).toLong() * 1000L)
                if (!cycleEnabled) break
                cycleIndex = (cycleIndex + 1) % msgs.size
                currentCycleLine = msgs[cycleIndex % msgs.size]
                sendComposedToVrchat(local)
            }
        }
    }

    fun stopCycle() {
        cycleJob?.cancel()
        cycleJob = null
        currentCycleLine = ""
        // Don’t force-send here; user might still be using NowPlaying/AFK
    }

    // ----------------------------
    // Now Playing (independent)
    // ----------------------------
    var spotifyEnabled by mutableStateOf(false)
    var spotifyDemoEnabled by mutableStateOf(false)

    // 1..5 (your presets)
    var spotifyPreset by mutableStateOf(1)

    // independent refresh speed
    var musicRefreshSeconds by mutableStateOf(2)

    // Debug fields shown in UI
    var listenerConnected by mutableStateOf(false)
    var activePackage by mutableStateOf("(none)")
    var nowPlayingDetected by mutableStateOf(false)
    var lastNowPlayingTitle by mutableStateOf("(blank)")
    var lastNowPlayingArtist by mutableStateOf("(blank)")
    var lastSentToVrchatAtMs by mutableStateOf(0L)

    // ✅ UI wants this for paused/playing label
    var nowPlayingIsPlaying by mutableStateOf(false)

    // internal snapshot timing
    private var nowPlayingDurationMs by mutableStateOf(0L)
    private var nowPlayingPositionMs by mutableStateOf(0L)
    private var nowPlayingPositionUpdateTimeMs by mutableStateOf(0L)
    private var nowPlayingPlaybackSpeed by mutableStateOf(1f)

    private var nowPlayingJob: Job? = null

    init {
        // Keep NowPlayingState → fields synced
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
                nowPlayingPlaybackSpeed = s.playbackSpeed
                nowPlayingIsPlaying = s.isPlaying
            }
        }

        // ✅ AFK should work even if Cycle/NowPlaying are off
        // If user toggles AFK, start/stop its independent sender automatically
        viewModelScope.launch {
            snapshotFlow { afkEnabled }.collect { enabled ->
                if (enabled) startAfkSender(local = false) else stopAfkSender()
            }
        }
    }

    fun notificationAccessIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun setSpotifyEnabledFlag(enabled: Boolean) {
        spotifyEnabled = enabled
        // do not auto-start; UI has Start/Stop buttons
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
            // send immediately
            sendComposedToVrchat(local)

            while (spotifyEnabled) {
                delay(musicRefreshSeconds.coerceAtLeast(1).toLong() * 1000L)
                if (!spotifyEnabled) break
                // ✅ send full composed block (keeps cycle + afk visible)
                sendComposedToVrchat(local)
            }
        }
    }

    fun stopNowPlayingSender() {
        nowPlayingJob?.cancel()
        nowPlayingJob = null
        // don’t clear anything; just stop updating
    }

    fun sendNowPlayingOnce(local: Boolean = false) {
        sendComposedToVrchat(local)
    }

    fun stopAll() {
        stopCycle()
        stopNowPlayingSender()
        stopAfkSender()
    }

    // ==========================================================
    // ✅ COMPOSER (AFK top, NowPlaying bottom)
    // ==========================================================
    private fun buildComposedText(): String {
        val lines = mutableListOf<String>()

        // AFK always at top if enabled
        if (afkEnabled) {
            val afk = afkMessage.trim().ifEmpty { "AFK" }
            lines += afk
        }

        // Cycle line next (only if enabled and has something)
        if (cycleEnabled) {
            val c = currentCycleLine.trim()
            if (c.isNotEmpty()) lines += c
        }

        // Now playing at bottom (if enabled)
        lines.addAll(buildNowPlayingLines())

        // Enforce VRChat chatbox size (144)
        return joinWithLimitKeepBottom(lines, 144)
    }

    private fun buildNowPlayingLines(): List<String> {
        if (!spotifyEnabled) return emptyList()

        // If not detected and demo off → show nothing
        if (!nowPlayingDetected && !spotifyDemoEnabled) return emptyList()

        val title = if (spotifyDemoEnabled && !nowPlayingDetected) "Pretty Girl" else lastNowPlayingTitle
        val artist = if (spotifyDemoEnabled && !nowPlayingDetected) "Clairo" else lastNowPlayingArtist

        val safeTitle = title.takeIf { it != "(blank)" } ?: ""
        val safeArtist = artist.takeIf { it != "(blank)" } ?: ""

        // ✅ LIVE position calculation
        val dur = if (spotifyDemoEnabled && !nowPlayingDetected) 80_000L else nowPlayingDurationMs
        val rawPos = if (spotifyDemoEnabled && !nowPlayingDetected) 58_000L else nowPlayingPositionMs

        val effectivePos = if ((spotifyDemoEnabled && !nowPlayingDetected) || !nowPlayingIsPlaying) {
            rawPos
        } else {
            // advance using elapsedRealtime delta * speed
            val now = SystemClock.elapsedRealtime()
            val delta = max(0L, now - nowPlayingPositionUpdateTimeMs)
            val advanced = rawPos + (delta.toFloat() * nowPlayingPlaybackSpeed).toLong()
            advanced
        }.coerceAtLeast(0L)

        val effectiveDur = max(1L, dur)

        // line 1: "Artist — Title" (drop artist if overflow)
        val maxLine = 42
        val combined = if (safeArtist.isNotBlank()) "$safeArtist — $safeTitle" else safeTitle
        val line1 = when {
            combined.length <= maxLine -> combined
            safeTitle.length <= maxLine -> safeTitle
            else -> safeTitle.take(maxLine - 1) + "…"
        }.trim()

        // line 2: bar + time (+ paused label)
        val bar = renderProgressBar(spotifyPreset, effectivePos, effectiveDur)
        val time = "${fmtTime(effectivePos)} / ${fmtTime(effectiveDur)}"
        val pausedTag = if (!nowPlayingIsPlaying) " (Paused)" else ""
        val line2 = "$bar $time$pausedTag".trim()

        return listOf(line1, line2).filter { it.isNotBlank() }
    }

    // ==========================================================
    // Your 5 preset bars (short, no overflow)
    // ==========================================================
    private fun renderProgressBar(preset: Int, posMs: Long, durMs: Long): String {
        val duration = max(1L, durMs)
        val p = min(1f, max(0f, posMs.toFloat() / duration.toFloat()))

        return when (preset.coerceIn(1, 5)) {
            1 -> { // love ♡━━━◉━━━━♡ (8 inner slots)
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

    /**
     * Keeps bottom lines first (Now Playing stays even if we must drop top lines)
     */
    private fun joinWithLimitKeepBottom(lines: List<String>, limit: Int): String {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return ""

        val out = ArrayList<String>()
        var total = 0

        // build from bottom up so bottom content survives
        for (i in clean.indices.reversed()) {
            val line = clean[i]
            val add = if (out.isEmpty()) line.length else (1 + line.length)

            if (total + add > limit) {
                // allow truncation only if this is the very top-most possible addition
                if (i == 0 && limit - total > 2) {
                    val remain = limit - total - (if (out.isEmpty()) 0 else 1)
                    val cut = line.take(max(0, remain - 1)) + "…"
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

    // ==========================================================
    // ✅ SINGLE SEND PATH (fixes “canceling each other out”)
    // ==========================================================
    private fun sendComposedToVrchat(local: Boolean = false) {
        val composed = buildComposedText()
        if (composed.isBlank()) return

        val osc = if (!local) remoteChatboxOSC else localChatboxOSC
        osc.sendMessage(
            composed,
            messengerUiState.value.isSendImmediately,
            messengerUiState.value.isTriggerSFX
        )
        lastSentToVrchatAtMs = System.currentTimeMillis()
    }
}

data class MessengerUiState(
    val ipAddress: String = "127.0.0.1",
    val isRealtimeMsg: Boolean = false,
    val isTriggerSFX: Boolean = true,
    val isTypingIndicator: Boolean = true,
    val isSendImmediately: Boolean = true
)
