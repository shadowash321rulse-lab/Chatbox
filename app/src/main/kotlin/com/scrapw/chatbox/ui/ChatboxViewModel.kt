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
import com.scrapw.chatbox.NowPlayingSnapshot
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
    // Existing networking settings
    // ----------------------------
    private val storedIpState: StateFlow<String> =
        userPreferencesRepository.ipAddress.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ""
        )

    private val userInputIpState = MutableStateFlow("")
    var ipAddressLocked by mutableStateOf(false)

    private val ipFlow: Flow<String> = combine(storedIpState, userInputIpState) { stored, typed ->
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
    // Update checker (keep)
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

    // =========================
    // Cycle (independent timer)
    // =========================
    var cycleEnabled by mutableStateOf(false)
    var cycleMessages by mutableStateOf("")
    var cycleIntervalSeconds by mutableStateOf(3)

    private var cycleJob: Job? = null
    private val cycleLineFlow = MutableStateFlow<String?>(null)

    fun startCycle(local: Boolean = false) {
        val msgs = cycleMessages.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (!cycleEnabled || msgs.isEmpty()) return

        cycleJob?.cancel()
        cycleJob = viewModelScope.launch {
            var i = 0
            while (cycleEnabled) {
                cycleLineFlow.value = msgs[i % msgs.size]
                i = (i + 1) % msgs.size
                delay(cycleIntervalSeconds.coerceAtLeast(1).toLong() * 1000L)
            }
        }
    }

    fun stopCycle() {
        cycleJob?.cancel()
        cycleJob = null
        cycleLineFlow.value = null
    }

    // =========================
    // Now Playing (independent timer)
    // =========================
    var spotifyEnabled by mutableStateOf(false)          // UI name kept
    var spotifyDemoEnabled by mutableStateOf(false)
    var spotifyPreset by mutableStateOf(1)              // 1..5
    var musicRefreshSeconds by mutableStateOf(2)

    // Debug shown in UI
    var listenerConnected by mutableStateOf(false)
    var activePackage by mutableStateOf("(none)")
    var nowPlayingDetected by mutableStateOf(false)
    var lastNowPlayingTitle by mutableStateOf("(blank)")
    var lastNowPlayingArtist by mutableStateOf("(blank)")
    var lastSentToVrchatAtMs by mutableStateOf(0L)

    // last snapshot (for pause freeze + progress)
    private var lastSnapshot: NowPlayingSnapshot = NowPlayingSnapshot()

    // Music “tick” flow so renderer can update progress WITHOUT syncing to cycle timer
    private val musicTickFlow = MutableStateFlow(0L)
    private var nowPlayingJob: Job? = null

    init {
        // This is the “collect into fields” part you asked for (clean + direct)
        viewModelScope.launch {
            NowPlayingState.state.collect { s ->
                lastSnapshot = s

                listenerConnected = s.listenerConnected
                activePackage = if (s.activePackage.isBlank()) "(none)" else s.activePackage
                nowPlayingDetected = s.detected

                lastNowPlayingTitle = if (s.title.isBlank()) "(blank)" else s.title
                lastNowPlayingArtist = if (s.artist.isBlank()) "(blank)" else s.artist

                // If play/pause changes, force one render immediately
                musicTickFlow.value = musicTickFlow.value + 1L
            }
        }

        // Renderer: combines cycle line + now playing + tick, and only sends when output changes.
        startRenderer()
    }

    fun notificationAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun setSpotifyEnabledFlag(enabled: Boolean) {
        spotifyEnabled = enabled
        // force a re-render
        musicTickFlow.value = musicTickFlow.value + 1L
    }

    fun setSpotifyDemoFlag(enabled: Boolean) {
        spotifyDemoEnabled = enabled
        musicTickFlow.value = musicTickFlow.value + 1L
    }

    fun updateSpotifyPreset(preset: Int) {
        spotifyPreset = preset.coerceIn(1, 5)
        musicTickFlow.value = musicTickFlow.value + 1L
    }

    fun startNowPlayingSender(local: Boolean = false) {
        if (!spotifyEnabled) return

        nowPlayingJob?.cancel()
        nowPlayingJob = viewModelScope.launch {
            while (spotifyEnabled) {
                // Only tick while playing (FREEZE when paused)
                if (lastSnapshot.isPlaying) {
                    musicTickFlow.value = musicTickFlow.value + 1L
                }
                delay(musicRefreshSeconds.coerceAtLeast(1).toLong() * 1000L)
            }
        }
    }

    fun stopNowPlayingSender() {
        nowPlayingJob?.cancel()
        nowPlayingJob = null
    }

    fun sendNowPlayingOnce(local: Boolean = false) {
        // force a render+send right now
        musicTickFlow.value = musicTickFlow.value + 1L
    }

    fun stopAll() {
        stopCycle()
        stopNowPlayingSender()
        // renderer stays running; it just won’t send if nothing enabled
    }

    // =========================
    // Renderer (prevents timing sync)
    // =========================
    private var rendererJob: Job? = null
    private var lastSentText: String = ""

    private fun startRenderer() {
        rendererJob?.cancel()
        rendererJob = viewModelScope.launch {
            combine(
                cycleLineFlow,
                NowPlayingState.state,
                musicTickFlow
            ) { cycleLine, snapshot, _ ->
                buildOutgoingText(cycleLine = cycleLine, snap = snapshot)
            }
                .map { it.trimEnd() }
                .distinctUntilChanged()
                .collect { outgoing ->
                    // Don’t spam empty
                    if (outgoing.isBlank()) return@collect

                    // Send only if changed
                    if (outgoing != lastSentText) {
                        sendToVrchatRaw(outgoing, local = false, addToConversation = false)
                        lastSentText = outgoing
                    }
                }
        }
    }

    // =========================
    // Compose outgoing text (Cycle on top, NowPlaying always under)
    // =========================
    private fun buildOutgoingText(cycleLine: String?, snap: NowPlayingSnapshot): String {
        val lines = mutableListOf<String>()

        val cycle = cycleLine?.trim().orEmpty()
        if (cycle.isNotEmpty()) lines += cycle

        lines.addAll(buildNowPlayingLines(snap))

        return joinWithLimit(lines, 144)
    }

    private fun buildNowPlayingLines(s: NowPlayingSnapshot): List<String> {
        if (!spotifyEnabled) return emptyList()

        // Demo fallback if nothing detected
        val detected = s.detected
        val title = when {
            detected -> s.title
            spotifyDemoEnabled -> "Pretty Girl"
            else -> ""
        }.trim()

        val artist = when {
            detected -> s.artist
            spotifyDemoEnabled -> "Clairo"
            else -> ""
        }.trim()

        if (!detected && !spotifyDemoEnabled) return emptyList()

        // line 1: artist — title (drop artist if overflow)
        val safeTitle = title.ifBlank { "(blank)" }
        val safeArtist = artist.ifBlank { "" }

        val maxLine = 42
        val combined = if (safeArtist.isNotBlank()) "$safeArtist — $safeTitle" else safeTitle

        val line1 = when {
            combined.length <= maxLine -> combined
            safeTitle.length <= maxLine -> safeTitle // drop artist when overflow
            else -> safeTitle.take(maxLine - 1) + "…"
        }.trim()

        // Progress: freeze while paused
        val dur = if (spotifyDemoEnabled && !detected) 80_000L else s.durationMs
        val pos = if (spotifyDemoEnabled && !detected) {
            58_000L
        } else {
            // live progress only while playing, else frozen
            if (s.isPlaying) computeLivePositionMs(s) else s.positionMs
        }

        val timeText = if (!s.isPlaying && (detected || spotifyDemoEnabled)) {
            // show paused indicator
            "⏸ Paused"
        } else {
            "${fmtTime(pos)} / ${fmtTime(max(1L, dur))}"
        }

        val bar = renderProgressBar(spotifyPreset, pos, max(1L, dur))
        val line2 = "$bar $timeText".trim()

        return listOf(line1, line2).filter { it.isNotBlank() }
    }

    // Live position while playing: positionMs + elapsedRealtime delta * speed
    private fun computeLivePositionMs(s: NowPlayingSnapshot): Long {
        val base = max(0L, s.positionMs)
        val dur = max(1L, s.durationMs)
        val dt = max(0L, SystemClock.elapsedRealtime() - s.positionUpdateTimeMs)
        val advanced = (dt.toDouble() * s.playbackSpeed.toDouble()).toLong()
        return min(dur, base + advanced)
    }

    // =========================
    // Your 5 preset bars (short, 1 line, point per symbol)
    // =========================
    private fun renderProgressBar(preset: Int, posMs: Long, durMs: Long): String {
        val duration = max(1L, durMs)
        val p = min(1f, max(0f, posMs.toFloat() / duration.toFloat()))

        return when (preset.coerceIn(1, 5)) {
            // (love) ♡ + 8 inner slots + ♡
            1 -> {
                val innerSlots = 8
                val idx = (p * (innerSlots - 1)).toInt()
                val inner = CharArray(innerSlots) { '━' }
                inner[idx] = '◉'
                "♡" + inner.concatToString() + "♡"
            }

            // (minimal) 10 slots
            2 -> {
                val slots = 10
                val idx = (p * (slots - 1)).toInt()
                val bg = CharArray(slots) { '─' }
                bg[idx] = '◉'
                bg.concatToString()
            }

            // (crystal) 10 slots
            3 -> {
                val slots = 10
                val idx = (p * (slots - 1)).toInt()
                val bg = CharArray(slots) { '⟡' }
                bg[idx] = '◉'
                bg.concatToString()
            }

            // (soundwave) 10 slots with ●
            4 -> {
                val bg = charArrayOf('▁','▂','▃','▄','▅','▅','▄','▃','▂','▁')
                val idx = (p * (bg.size - 1)).toInt()
                val out = bg.copyOf()
                out[idx] = '●'
                out.concatToString()
            }

            // (geometry) 10 slots
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

        // Build from bottom up so Now Playing stays if we must drop something
        val out = ArrayList<String>()
        var total = 0

        for (i in clean.indices.reversed()) {
            val line = clean[i]
            val add = if (out.isEmpty()) line.length else (1 + line.length)
            if (total + add > limit) {
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

