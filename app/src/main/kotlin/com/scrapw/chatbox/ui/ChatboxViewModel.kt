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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

class ChatboxViewModel(
    private val app: ChatboxApplication,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    companion object {
        private lateinit var instance: ChatboxViewModel

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as ChatboxApplication)
                instance = ChatboxViewModel(application, application.userPreferencesRepository)
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

    // ============================================================
    // SAFETY MINIMUMS (YOUR REQUEST)
    // ============================================================
    private val minUserIntervalSeconds = 2 // <= this is the key change

    // ============================================================
    // CENTRAL SENDER (prevents burst spam without you “thinking about it”)
    // NOTE: Even if you don’t want it called “global cooldown”, this is required
    // to stop Cycle/Music/AFK firing at the same time and getting dropped.
    // ============================================================

    private val sendMutex = Mutex()
    private var lastChatboxSendAtMs: Long = 0L
    private val minSendIntervalMs: Long = (minUserIntervalSeconds * 1000).toLong()

    private val clearChatboxText = "\u200B" // clears instantly better than ""

    var debugLastAfkOsc by mutableStateOf("")
    var debugLastCycleOsc by mutableStateOf("")
    var debugLastMusicOsc by mutableStateOf("")
    var debugLastCombinedOsc by mutableStateOf("")

    private suspend fun sendSafely(text: String, local: Boolean) {
        sendMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastChatboxSendAtMs
            if (elapsed < minSendIntervalMs) delay(minSendIntervalMs - elapsed)

            sendToVrchatRawImmediate(text, local, addToConversation = false)
            lastChatboxSendAtMs = System.currentTimeMillis()
        }
    }

    // ============================================================
    // PRESET STORAGE (DataStore just for VRC-A presets)
    // ============================================================

    // AFK presets (3)
    var afkPreset1 by mutableStateOf("AFK")
    var afkPreset2 by mutableStateOf("AFK - grabbing water")
    var afkPreset3 by mutableStateOf("AFK - brb")

    // Cycle presets (5)
    var cyclePreset1 by mutableStateOf("")
    var cyclePreset2 by mutableStateOf("")
    var cyclePreset3 by mutableStateOf("")
    var cyclePreset4 by mutableStateOf("")
    var cyclePreset5 by mutableStateOf("")

    init {
        // Load presets on startup (and keep them live)
        viewModelScope.launch {
            VrcaPresetStore.flow(app).collect { p ->
                afkPreset1 = p.afk1
                afkPreset2 = p.afk2
                afkPreset3 = p.afk3
                cyclePreset1 = p.cycle1
                cyclePreset2 = p.cycle2
                cyclePreset3 = p.cycle3
                cyclePreset4 = p.cycle4
                cyclePreset5 = p.cycle5
            }
        }

        // Bind NowPlaying state -> ViewModel fields
        viewModelScope.launch {
            NowPlayingState.state.collect { s ->
                listenerConnected = s.listenerConnected
                activePackage = if (s.activePackage.isBlank()) "(none)" else s.activePackage
                nowPlayingDetected = s.detected
                lastNowPlayingTitle = if (s.title.isBlank()) "(blank)" else s.title
                lastNowPlayingArtist = if (s.artist.isBlank()) "(blank)" else s.artist
                nowPlayingDurationMs = s.durationMs
                nowPlayingPositionMs = computeLivePositionMs(s)
                nowPlayingIsPlaying = s.isPlaying
            }
        }
    }

    fun saveAfkPreset(slot: Int, value: String) {
        viewModelScope.launch {
            VrcaPresetStore.saveAfk(app, slot, value)
        }
    }

    fun saveCyclePreset(slot: Int, value: String) {
        viewModelScope.launch {
            VrcaPresetStore.saveCycle(app, slot, value)
        }
    }

    fun loadAfkPreset(slot: Int) {
        afkMessage = when (slot) {
            1 -> afkPreset1
            2 -> afkPreset2
            else -> afkPreset3
        }
    }

    fun loadCyclePreset(slot: Int) {
        cycleMessages = when (slot) {
            1 -> cyclePreset1
            2 -> cyclePreset2
            3 -> cyclePreset3
            4 -> cyclePreset4
            else -> cyclePreset5
        }
    }

    // ============================================================
    // AFK
    // ============================================================

    var afkEnabled by mutableStateOf(false)
    var afkMessage by mutableStateOf("AFK")
    private var afkJob: Job? = null

    private val afkIntervalSecondsForced = 12

    fun startAfkSender(local: Boolean = false) {
        if (!afkEnabled) return
        afkJob?.cancel()
        afkJob = viewModelScope.launch {
            while (afkEnabled) {
                sendCombinedNow(local)
                delay(afkIntervalSecondsForced * 1000L)
            }
        }
    }

    fun stopAfkSender(local: Boolean = false) {
        afkJob?.cancel()
        afkJob = null
        viewModelScope.launch { sendCombinedNow(local) } // removes AFK immediately
    }

    fun sendAfkNow(local: Boolean = false) {
        viewModelScope.launch { sendCombinedNow(local) }
    }

    // ============================================================
    // Cycle
    // ============================================================

    var cycleEnabled by mutableStateOf(false)
    var cycleMessages by mutableStateOf("")
    var cycleIntervalSeconds by mutableStateOf(3)
        set(value) { field = max(minUserIntervalSeconds, value) } // enforce min

    private var cycleJob: Job? = null
    private var cycleIndex = 0
    private var currentCycleLine by mutableStateOf("")

    fun startCycle(local: Boolean = false) {
        val msgs = cycleMessages.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (!cycleEnabled || msgs.isEmpty()) return

        cycleJob?.cancel()
        cycleJob = viewModelScope.launch {
            cycleIndex = 0
            while (cycleEnabled) {
                currentCycleLine = msgs[cycleIndex % msgs.size]
                sendCombinedNow(local)
                cycleIndex = (cycleIndex + 1) % msgs.size
                delay(max(minUserIntervalSeconds, cycleIntervalSeconds).toLong() * 1000L)
            }
        }
    }

    fun stopCycle(local: Boolean = false) {
        cycleJob?.cancel()
        cycleJob = null
        currentCycleLine = ""
        viewModelScope.launch { sendCombinedNow(local) } // removes cycle immediately
    }

    // ============================================================
    // Now Playing (phone music)
    // ============================================================

    var spotifyEnabled by mutableStateOf(false)
    var spotifyDemoEnabled by mutableStateOf(false)
    var spotifyPreset by mutableStateOf(1)

    var musicRefreshSeconds by mutableStateOf(2)
        set(value) { field = max(minUserIntervalSeconds, value) } // enforce min

    var listenerConnected by mutableStateOf(false)
    var activePackage by mutableStateOf("(none)")
    var nowPlayingDetected by mutableStateOf(false)
    var lastNowPlayingTitle by mutableStateOf("(blank)")
    var lastNowPlayingArtist by mutableStateOf("(blank)")
    var lastSentToVrchatAtMs by mutableStateOf(0L)

    private var nowPlayingDurationMs by mutableStateOf(0L)
    private var nowPlayingPositionMs by mutableStateOf(0L)
    var nowPlayingIsPlaying by mutableStateOf(false)
        private set

    private var nowPlayingJob: Job? = null

    fun notificationAccessIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
                sendCombinedNow(local)
                delay(max(minUserIntervalSeconds, musicRefreshSeconds).toLong() * 1000L)
            }
        }
    }

    fun stopNowPlayingSender(local: Boolean = false) {
        nowPlayingJob?.cancel()
        nowPlayingJob = null
        viewModelScope.launch { sendCombinedNow(local) } // removes music immediately
    }

    fun sendNowPlayingOnce(local: Boolean = false) {
        viewModelScope.launch { sendCombinedNow(local) }
    }

    fun stopAll(local: Boolean = false) {
        stopCycle(local)
        stopNowPlayingSender(local)
        stopAfkSender(local)
    }

    // ============================================================
    // Combined output
    // ============================================================

    private suspend fun sendCombinedNow(local: Boolean) {
        val combined = buildCombinedOutgoingText()
        debugLastCombinedOsc = combined

        if (combined.isBlank()) {
            sendSafely(clearChatboxText, local)
        } else {
            sendSafely(combined, local)
        }
    }

    private fun buildCombinedOutgoingText(): String {
        val lines = mutableListOf<String>()

        // AFK top
        if (afkEnabled) {
            val afk = afkMessage.trim()
            debugLastAfkOsc = afk
            if (afk.isNotBlank()) lines += afk
        } else debugLastAfkOsc = ""

        // Cycle second
        val cycle = if (cycleEnabled) currentCycleLine.trim() else ""
        debugLastCycleOsc = cycle
        if (cycle.isNotBlank()) lines += cycle

        // Music bottom
        val np = buildNowPlayingLines()
        lines.addAll(np)

        return joinWithLimit(lines, 144)
    }

    private fun buildNowPlayingLines(): List<String> {
        if (!spotifyEnabled) {
            debugLastMusicOsc = ""
            return emptyList()
        }

        val title = if (spotifyDemoEnabled && !nowPlayingDetected) "Pretty Girl" else lastNowPlayingTitle
        val artist = if (spotifyDemoEnabled && !nowPlayingDetected) "Clairo" else lastNowPlayingArtist

        if (!spotifyDemoEnabled && !nowPlayingDetected) {
            debugLastMusicOsc = ""
            return emptyList()
        }

        val safeTitle = title.takeIf { it != "(blank)" } ?: ""
        val safeArtist = artist.takeIf { it != "(blank)" } ?: ""
        val pausedTag = if (!nowPlayingIsPlaying) " (Paused)" else ""

        val maxLine = 42
        val combined = if (safeArtist.isNotBlank()) "$safeArtist — $safeTitle$pausedTag" else "$safeTitle$pausedTag"
        val line1 = when {
            combined.length <= maxLine -> combined
            safeTitle.length <= maxLine -> safeTitle + pausedTag
            else -> safeTitle.take(maxLine - 1) + "…"
        }.trim()

        val dur = if (spotifyDemoEnabled && !nowPlayingDetected) 80_000L else nowPlayingDurationMs
        val pos = if (spotifyDemoEnabled && !nowPlayingDetected) 58_000L else nowPlayingPositionMs

        val bar = renderProgressBar(spotifyPreset, pos, dur)
        val time = "${fmtTime(pos)} / ${fmtTime(if (dur > 0) dur else max(pos, 1L))}"
        val line2 = "$bar $time".trim()

        debugLastMusicOsc = listOf(line1, line2).joinToString("\n")
        return listOf(line1, line2).filter { it.isNotBlank() }
    }

    private fun computeLivePositionMs(s: com.scrapw.chatbox.NowPlayingSnapshot): Long {
        val base = max(0L, s.positionMs)
        if (!s.isPlaying) return base
        if (s.positionUpdateTimeMs <= 0L) return base

        val now = android.os.SystemClock.elapsedRealtime()
        val delta = max(0L, now - s.positionUpdateTimeMs)
        val advanced = (delta.toFloat() * s.playbackSpeed).toLong()
        val live = base + advanced
        val dur = max(1L, s.durationMs)
        return min(live, dur)
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

    private fun sendToVrchatRawImmediate(text: String, local: Boolean, addToConversation: Boolean) {
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
