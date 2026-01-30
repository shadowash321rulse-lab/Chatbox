package com.scrapw.chatbox.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.channels.Channel
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

    // ============================================================
    // Preferences (simple, persistent, no DataStore changes needed)
    // ============================================================
    private val prefs by lazy {
        app.getSharedPreferences("vrca_prefs", Context.MODE_PRIVATE)
    }

    private fun saveString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    private fun loadString(key: String, def: String = ""): String {
        return prefs.getString(key, def) ?: def
    }

    private fun afkPresetKey(slot: Int) = "afk_preset_$slot"
    private fun cyclePresetKey(slot: Int) = "cycle_preset_$slot"

    // ============================================================
    // Existing settings flow (keep)
    // ============================================================
    private val storedIpState: StateFlow<String> =
        userPreferencesRepository.ipAddress.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ""
        )

    private val userInputIpState = kotlinx.coroutines.flow.MutableStateFlow("")
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

    // ============================================================
    // Update checker (keep)
    // ============================================================
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
    // IMPORTANT: global send coalescer + safe cooldown
    // ============================================================
    private val BLANK = "\u200B" // zero-width space so VRChat clears without showing text
    private val sendRequests = Channel<Unit>(capacity = Channel.CONFLATED)

    private var lastSendAtMs by mutableLongStateOf(0L)
    private val minSendIntervalMs = 2000L // 2s safety floor (prevents VRChat cutouts)

    // Latest component outputs (these are what Debug shows)
    var debugLastAfkOsc by mutableStateOf("(none)")
    var debugLastCycleOsc by mutableStateOf("(none)")
    var debugLastMusicOsc by mutableStateOf("(none)")
    var debugLastCombinedOsc by mutableStateOf("(none)")

    // Latest "pieces" of the message
    private var latestAfkLine by mutableStateOf("")
    private var latestCycleLine by mutableStateOf("")
    private var latestMusicBlock by mutableStateOf("") // can be multi-line

    private var senderJob: Job? = null

    private fun ensureSenderRunning(local: Boolean = false) {
        if (senderJob != null) return
        senderJob = viewModelScope.launch {
            while (true) {
                sendRequests.receive()

                // enforce cooldown
                val now = System.currentTimeMillis()
                val wait = (lastSendAtMs + minSendIntervalMs) - now
                if (wait > 0) delay(wait)

                val combined = buildCombinedOutgoing()
                val toSend = if (combined.isBlank()) BLANK else combined

                sendToVrchatRaw(toSend, local, addToConversation = false)
                lastSendAtMs = System.currentTimeMillis()

                debugLastCombinedOsc = combined.ifBlank { "(blank/clearing)" }
            }
        }
    }

    private fun requestSend(local: Boolean = false) {
        ensureSenderRunning(local)
        sendRequests.trySend(Unit)
    }

    // ============================================================
    // AFK (top line) — fixed interval, no UI interval picker
    // ============================================================
    var afkEnabled by mutableStateOf(false)

    var afkMessage by mutableStateOf(
        loadString("afk_current_text", "AFK")
    )
        private set

    fun saveAfkPreset(slot: Int, text: String) {
        val s = slot.coerceIn(1, 3)
        saveString(afkPresetKey(s), text)
    }

    fun loadAfkPreset(slot: Int) {
        val s = slot.coerceIn(1, 3)
        val loaded = loadString(afkPresetKey(s), "")
        if (loaded.isNotBlank()) {
            setAfkMessage(loaded)
        }
    }

    fun setAfkMessage(text: String) {
        afkMessage = text
        saveString("afk_current_text", text)
        // update line immediately if AFK running
        if (afkEnabled) {
            latestAfkLine = afkMessage.trim()
            debugLastAfkOsc = latestAfkLine.ifBlank { "(blank)" }
            requestSend()
        }
    }

    private val afkFixedIntervalMs = 10_000L // forced interval (10s)
    private var afkJob: Job? = null

    fun startAfkSender(local: Boolean = false) {
        if (!afkEnabled) return
        afkJob?.cancel()
        latestAfkLine = afkMessage.trim()
        debugLastAfkOsc = latestAfkLine.ifBlank { "(blank)" }
        requestSend(local)

        afkJob = viewModelScope.launch {
            while (afkEnabled) {
                latestAfkLine = afkMessage.trim()
                debugLastAfkOsc = latestAfkLine.ifBlank { "(blank)" }
                requestSend(local)
                delay(afkFixedIntervalMs)
            }
        }
    }

    fun stopAfkSender(local: Boolean = false) {
        afkJob?.cancel()
        afkJob = null
        latestAfkLine = ""
        debugLastAfkOsc = "(stopped)"
        requestSend(local) // clears from VRChat (combined may become blank)
    }

    fun sendAfkNow(local: Boolean = false) {
        if (!afkEnabled) return
        latestAfkLine = afkMessage.trim()
        debugLastAfkOsc = latestAfkLine.ifBlank { "(blank)" }
        requestSend(local)
    }

    // ============================================================
    // Cycle (rotating messages)
    // ============================================================
    var cycleEnabled by mutableStateOf(false)

    var cycleMessages by mutableStateOf(
        loadString("cycle_current_text", "")
    )
        private set

    private var _cycleIntervalSeconds by mutableIntStateOf(loadString("cycle_interval", "3").toIntOrNull() ?: 3)
    var cycleIntervalSeconds: Int
        get() = max(2, _cycleIntervalSeconds) // MIN 2 seconds
        set(value) {
            _cycleIntervalSeconds = max(2, value)
            saveString("cycle_interval", _cycleIntervalSeconds.toString())
        }

    fun setCycleMessages(text: String) {
        cycleMessages = text
        saveString("cycle_current_text", text)
    }

    fun saveCyclePreset(slot: Int, text: String) {
        val s = slot.coerceIn(1, 5)
        saveString(cyclePresetKey(s), text)
    }

    fun loadCyclePreset(slot: Int) {
        val s = slot.coerceIn(1, 5)
        val loaded = loadString(cyclePresetKey(s), "")
        if (loaded.isNotBlank()) {
            setCycleMessages(loaded)
        }
    }

    private var cycleJob: Job? = null
    private var cycleIndex = 0

    fun startCycle(local: Boolean = false) {
        val msgs = cycleMessages.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (!cycleEnabled || msgs.isEmpty()) return

        cycleJob?.cancel()
        cycleJob = viewModelScope.launch {
            cycleIndex = 0
            while (cycleEnabled) {
                val line = msgs[cycleIndex % msgs.size]
                latestCycleLine = line
                debugLastCycleOsc = line
                requestSend(local)

                cycleIndex = (cycleIndex + 1) % msgs.size
                delay(cycleIntervalSeconds.toLong() * 1000L)
            }
        }
    }

    fun stopCycle(local: Boolean = false) {
        cycleJob?.cancel()
        cycleJob = null
        latestCycleLine = ""
        debugLastCycleOsc = "(stopped)"
        requestSend(local) // clears
    }

    // ============================================================
    // Now Playing (phone music) — UI still calls these “spotify”
    // ============================================================
    var spotifyEnabled by mutableStateOf(false)
    var spotifyDemoEnabled by mutableStateOf(false)

    var spotifyPreset by mutableIntStateOf(1)

    private var _musicRefreshSeconds by mutableIntStateOf(loadString("music_interval", "2").toIntOrNull() ?: 2)
    var musicRefreshSeconds: Int
        get() = max(2, _musicRefreshSeconds) // MIN 2 seconds
        set(value) {
            _musicRefreshSeconds = max(2, value)
            saveString("music_interval", _musicRefreshSeconds.toString())
        }

    // Debug fields shown in UI
    var listenerConnected by mutableStateOf(false)
    var activePackage by mutableStateOf("(none)")
    var nowPlayingDetected by mutableStateOf(false)
    var lastNowPlayingTitle by mutableStateOf("(blank)")
    var lastNowPlayingArtist by mutableStateOf("(blank)")
    var lastSentToVrchatAtMs by mutableLongStateOf(0L)

    // Needed by UI
    var nowPlayingIsPlaying by mutableStateOf(false)

    // internal now playing timing
    private var nowPlayingDurationMs by mutableLongStateOf(0L)
    private var nowPlayingPositionMs by mutableLongStateOf(0L)
    private var nowPlayingPositionUpdateTimeMs by mutableLongStateOf(0L)
    private var nowPlayingSpeed by mutableStateOf(1f)

    private var nowPlayingJob: Job? = null

    init {
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
        if (!enabled) {
            stopNowPlayingSender()
        }
    }

    fun setSpotifyDemoFlag(enabled: Boolean) {
        spotifyDemoEnabled = enabled
    }

    fun updateSpotifyPreset(preset: Int) {
        spotifyPreset = preset.coerceIn(1, 5)
        // refresh display immediately
        if (spotifyEnabled) {
            latestMusicBlock = buildNowPlayingBlock().trim()
            debugLastMusicOsc = latestMusicBlock.ifBlank { "(blank)" }
            requestSend()
        }
    }

    fun startNowPlayingSender(local: Boolean = false) {
        if (!spotifyEnabled) return

        nowPlayingJob?.cancel()
        latestMusicBlock = buildNowPlayingBlock().trim()
        debugLastMusicOsc = latestMusicBlock.ifBlank { "(blank)" }
        requestSend(local)

        nowPlayingJob = viewModelScope.launch {
            while (spotifyEnabled) {
                latestMusicBlock = buildNowPlayingBlock().trim()
                debugLastMusicOsc = latestMusicBlock.ifBlank { "(blank)" }
                requestSend(local)
                delay(musicRefreshSeconds.toLong() * 1000L)
            }
        }
    }

    fun stopNowPlayingSender(local: Boolean = false) {
        nowPlayingJob?.cancel()
        nowPlayingJob = null
        latestMusicBlock = ""
        debugLastMusicOsc = "(stopped)"
        requestSend(local) // clears
    }

    fun sendNowPlayingOnce(local: Boolean = false) {
        if (!spotifyEnabled) return
        latestMusicBlock = buildNowPlayingBlock().trim()
        debugLastMusicOsc = latestMusicBlock.ifBlank { "(blank)" }
        requestSend(local)
    }

    fun stopAll(local: Boolean = false) {
        stopAfkSender(local)
        stopCycle(local)
        stopNowPlayingSender(local)
    }

    // ============================================================
    // Build final outgoing message (AFK top, Cycle middle, Music bottom)
    // ============================================================
    private fun buildCombinedOutgoing(): String {
        val parts = mutableListOf<String>()

        if (afkEnabled && latestAfkLine.isNotBlank()) {
            parts += latestAfkLine.trim()
        }

        if (cycleEnabled && latestCycleLine.isNotBlank()) {
            parts += latestCycleLine.trim()
        }

        if (spotifyEnabled && latestMusicBlock.isNotBlank()) {
            parts += latestMusicBlock.trim()
        }

        return joinWithLimit(parts, 144)
    }

    // music block builder
    private fun buildNowPlayingBlock(): String {
        if (!spotifyEnabled) return ""

        // demo values if desired
        val useDemo = spotifyDemoEnabled && !nowPlayingDetected
        val title = if (useDemo) "Pretty Girl" else lastNowPlayingTitle
        val artist = if (useDemo) "Clairo" else lastNowPlayingArtist

        if (!spotifyDemoEnabled && !nowPlayingDetected) return ""

        val safeTitle = title.takeIf { it != "(blank)" } ?: ""
        val safeArtist = artist.takeIf { it != "(blank)" } ?: ""

        val maxLine = 42
        val combined = if (safeArtist.isNotBlank()) "$safeArtist — $safeTitle" else safeTitle
        val line1 = when {
            combined.length <= maxLine -> combined
            safeTitle.length <= maxLine -> safeTitle
            else -> safeTitle.take(maxLine - 1) + "…"
        }.trim()

        val dur = if (useDemo) 80_000L else max(1L, nowPlayingDurationMs)
        val posLive = if (useDemo) 58_000L else estimateLivePositionMs()

        val bar = renderProgressBar(spotifyPreset, posLive, dur)
        val time = "${fmtTime(posLive)} / ${fmtTime(dur)}"

        // show paused if not playing
        val status = if (useDemo) "" else if (nowPlayingIsPlaying) "" else " (Paused)"
        val line2 = "$bar $time$status".trim()

        return listOf(line1, line2).filter { it.isNotBlank() }.joinToString("\n")
    }

    // live progress estimate from snapshot (fixes “only updates when interacted” when possible)
    private fun estimateLivePositionMs(): Long {
        val base = nowPlayingPositionMs
        val dur = max(1L, nowPlayingDurationMs)

        if (!nowPlayingIsPlaying) return min(base, dur)

        val nowElapsed = android.os.SystemClock.elapsedRealtime()
        val delta = max(0L, nowElapsed - nowPlayingPositionUpdateTimeMs)
        val advanced = (delta.toFloat() * max(0f, nowPlayingSpeed)).toLong()
        return min(base + advanced, dur)
    }

    // ============================================================
    // Your 5 preset bars (short / safe)
    // ============================================================
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
