package com.scrapw.chatbox.ui

import android.content.Intent
import android.os.SystemClock
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
import java.util.concurrent.atomic.AtomicBoolean
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
    // Existing settings flow (keep)
    // ----------------------------
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

    // =========================================================
    // SEND COORDINATOR
    // =========================================================
    private val MIN_SEND_COOLDOWN_MS = 2_000L
    private var lastSendAtMs: Long = 0L
    private var sendCoordinatorJob: Job? = null
    private val sendRequested = AtomicBoolean(false)

    var debugLastAfkOsc by mutableStateOf("")
    var debugLastCycleOsc by mutableStateOf("")
    var debugLastMusicOsc by mutableStateOf("")
    var debugLastCombinedOsc by mutableStateOf("")

    // =========================================================
    // AFK
    // =========================================================
    var afkEnabled by mutableStateOf(false)
    var afkMessage by mutableStateOf("AFK 🌙 back soon")
    private val AFK_FORCED_INTERVAL_SEC = 15
    private var afkJob: Job? = null
    private var afkRunning by mutableStateOf(false)

    private val afkPresetPreview = Array(3) { "" }
    fun getAfkPresetPreview(slot: Int): String {
        val i = slot.coerceIn(1, 3) - 1
        val t = afkPresetPreview[i].trim()
        return if (t.isBlank()) "(empty)" else t.lineSequence().first().take(24)
    }

    fun updateAfkMessage(text: String) {
        afkMessage = text
        viewModelScope.launch { userPreferencesRepository.saveAfkMessage(text) }
        requestCombinedSend()
    }

    fun startAfkSender() {
        if (!afkEnabled) return
        if (afkRunning) return
        afkRunning = true

        afkJob?.cancel()
        afkJob = viewModelScope.launch {
            requestCombinedSend(immediate = true)
            while (afkRunning && afkEnabled) {
                delay(AFK_FORCED_INTERVAL_SEC * 1000L)
                requestCombinedSend()
            }
        }
    }

    fun stopAfkSender() {
        afkRunning = false
        afkJob?.cancel()
        afkJob = null
        requestCombinedSend(immediate = true)
    }

    fun sendAfkNow() {
        if (!afkEnabled) return
        requestCombinedSend(immediate = true)
    }

    fun saveAfkPreset(slot: Int, text: String) {
        viewModelScope.launch {
            userPreferencesRepository.saveAfkPreset(slot.coerceIn(1, 3), text)
            refreshPresetPreviews()
        }
    }

    fun loadAfkPreset(slot: Int) {
        viewModelScope.launch {
            val s = slot.coerceIn(1, 3)
            val loaded = userPreferencesRepository.getAfkPresetOnce(s)
            updateAfkMessage(loaded)
        }
    }

    // =========================================================
    // CYCLE
    // =========================================================
    var cycleEnabled by mutableStateOf(false)
    var cycleMessages by mutableStateOf("")
    var cycleIntervalSeconds by mutableStateOf(3)

    private var cycleJob: Job? = null
    private var cycleIndex = 0
    private var cycleRunning by mutableStateOf(false)
    private var cycleCurrentLine by mutableStateOf("")

    fun setCycleEnabledFlag(enabled: Boolean) {
        cycleEnabled = enabled
        viewModelScope.launch { userPreferencesRepository.saveCycleEnabled(enabled) }
        if (!enabled) stopCycle() else requestCombinedSend(immediate = true)
    }

    fun setCycleIntervalSecondsFlag(seconds: Int) {
        val s = seconds.coerceAtLeast(2)
        cycleIntervalSeconds = s
        viewModelScope.launch { userPreferencesRepository.saveCycleInterval(s) }
    }

    private val cyclePresetPreview = Array(5) { "(empty)" }
    fun getCyclePresetPreview(slot: Int): String {
        val i = slot.coerceIn(1, 5) - 1
        return cyclePresetPreview[i]
    }

    fun updateCycleMessages(text: String) {
        cycleMessages = text
        viewModelScope.launch { userPreferencesRepository.saveCycleMessages(text) }
        requestCombinedSend()
    }

    fun startCycle(local: Boolean = false) {
        val msgs = cycleMessages.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (!cycleEnabled || msgs.isEmpty()) return

        cycleRunning = true
        cycleJob?.cancel()
        cycleJob = viewModelScope.launch {
            cycleIndex = 0
            while (cycleRunning && cycleEnabled) {
                cycleCurrentLine = msgs[cycleIndex % msgs.size]
                debugLastCycleOsc = cycleCurrentLine
                requestCombinedSend()

                cycleIndex = (cycleIndex + 1) % msgs.size
                delay(cycleIntervalSeconds.coerceAtLeast(2).toLong() * 1000L)
            }
        }
    }

    fun stopCycle() {
        cycleRunning = false
        cycleJob?.cancel()
        cycleJob = null
        cycleCurrentLine = ""
        debugLastCycleOsc = ""
        requestCombinedSend(immediate = true)
    }

    fun saveCyclePreset(slot: Int, messages: String) {
        viewModelScope.launch {
            val s = slot.coerceIn(1, 5)
            userPreferencesRepository.saveCyclePreset(
                s,
                messages,
                cycleIntervalSeconds.coerceAtLeast(2)
            )
            refreshPresetPreviews()
        }
    }

    fun loadCyclePreset(slot: Int) {
        viewModelScope.launch {
            val s = slot.coerceIn(1, 5)
            val (msgs, interval) = userPreferencesRepository.getCyclePresetOnce(s)
            cycleMessages = msgs
            cycleIntervalSeconds = interval.coerceAtLeast(2)
            userPreferencesRepository.saveCycleMessages(cycleMessages)
            userPreferencesRepository.saveCycleInterval(cycleIntervalSeconds)
            requestCombinedSend(immediate = true)
        }
    }

    // =========================================================
    // NOW PLAYING
    // =========================================================
    var spotifyEnabled by mutableStateOf(false)
    var spotifyDemoEnabled by mutableStateOf(false)
    var spotifyPreset by mutableStateOf(1)
    var musicRefreshSeconds by mutableStateOf(2)

    var listenerConnected by mutableStateOf(false)
    var activePackage by mutableStateOf("(none)")
    var nowPlayingDetected by mutableStateOf(false)
    var lastNowPlayingTitle by mutableStateOf("(blank)")
    var lastNowPlayingArtist by mutableStateOf("(blank)")
    var lastSentToVrchatAtMs by mutableStateOf(0L)

    private var nowPlayingDurationMs by mutableStateOf(0L)
    private var nowPlayingPositionMs by mutableStateOf(0L)
    private var nowPlayingPositionUpdateTimeMs by mutableStateOf(0L)
    private var nowPlayingPlaybackSpeed by mutableStateOf(1f)
    var nowPlayingIsPlaying by mutableStateOf(false)
        private set

    private var nowPlayingJob: Job? = null
    private var nowPlayingRunning by mutableStateOf(false)

    init {
        viewModelScope.launch {
            afkMessage = userPreferencesRepository.afkMessage.first()
            cycleMessages = userPreferencesRepository.cycleMessages.first()
            cycleIntervalSeconds = userPreferencesRepository.cycleInterval.first().coerceAtLeast(2)
            cycleEnabled = userPreferencesRepository.cycleEnabled.first()
            refreshPresetPreviews()
        }

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

                if (spotifyEnabled && nowPlayingRunning) requestCombinedSend()
            }
        }
    }

    private suspend fun refreshPresetPreviews() {
        for (i in 1..3) {
            val t = userPreferencesRepository.getAfkPresetOnce(i)
            afkPresetPreview[i - 1] = t
        }

        for (i in 1..5) {
            val (msgs, interval) = userPreferencesRepository.getCyclePresetOnce(i)
            val first = msgs.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() } ?: "(empty)"
            val compact = if (first.length > 24) first.take(23) + "…" else first
            cyclePresetPreview[i - 1] = "$compact  (${interval.coerceAtLeast(2)}s)"
        }
    }

    fun notificationAccessIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun setSpotifyEnabledFlag(enabled: Boolean) {
        spotifyEnabled = enabled
        if (!enabled) stopNowPlayingSender() else requestCombinedSend(immediate = true)
    }

    fun setSpotifyDemoFlag(enabled: Boolean) {
        spotifyDemoEnabled = enabled
        requestCombinedSend(immediate = true)
    }

    fun updateSpotifyPreset(preset: Int) {
        spotifyPreset = preset.coerceIn(1, 5)
        requestCombinedSend(immediate = true)
    }

    fun updateMusicRefreshSeconds(seconds: Int) {
        musicRefreshSeconds = seconds.coerceAtLeast(2)
    }

    fun startNowPlayingSender(local: Boolean = false) {
        if (!spotifyEnabled) return
        if (nowPlayingRunning) return

        nowPlayingRunning = true
        nowPlayingJob?.cancel()
        nowPlayingJob = viewModelScope.launch {
            requestCombinedSend(immediate = true)
            while (nowPlayingRunning && spotifyEnabled) {
                requestCombinedSend()
                delay(musicRefreshSeconds.coerceAtLeast(2).toLong() * 1000L)
            }
        }
    }

    fun stopNowPlayingSender() {
        nowPlayingRunning = false
        nowPlayingJob?.cancel()
        nowPlayingJob = null
        debugLastMusicOsc = ""
        requestCombinedSend(immediate = true)
    }

    fun sendNowPlayingOnce(local: Boolean = false) {
        if (!spotifyEnabled) return
        requestCombinedSend(immediate = true)
    }

    fun stopAll() {
        stopCycle()
        stopNowPlayingSender()
        stopAfkSender()
    }

    // =========================================================
    // COMBINED OUTPUT
    // =========================================================
    private fun computeEffectivePositionMs(): Long {
        val base = nowPlayingPositionMs
        val dur = nowPlayingDurationMs
        if (dur <= 0) return max(0L, base)

        return if (nowPlayingIsPlaying && nowPlayingPositionUpdateTimeMs > 0L) {
            val elapsed = SystemClock.elapsedRealtime() - nowPlayingPositionUpdateTimeMs
            val advanced = base + (elapsed * nowPlayingPlaybackSpeed).toLong()
            advanced.coerceIn(0L, dur)
        } else {
            base.coerceIn(0L, dur)
        }
    }

    private fun buildAfkLine(): String {
        if (!afkEnabled || !afkRunning) return ""
        val t = afkMessage.trim()
        return if (t.isBlank()) "AFK" else t
    }

    private fun buildCycleLine(): String {
        if (!cycleEnabled || !cycleRunning) return ""
        return cycleCurrentLine.trim()
    }

    private fun buildNowPlayingLines(): List<String> {
        if (!spotifyEnabled || !nowPlayingRunning) return emptyList()

        val detected = nowPlayingDetected
        val title = if (spotifyDemoEnabled && !detected) "Pretty Girl" else lastNowPlayingTitle
        val artist = if (spotifyDemoEnabled && !detected) "Clairo" else lastNowPlayingArtist

        if (!spotifyDemoEnabled && !detected) return emptyList()

        val safeTitle = title.takeIf { it != "(blank)" } ?: ""
        val safeArtist = artist.takeIf { it != "(blank)" } ?: ""

        val maxLine = 42
        val base = if (safeArtist.isNotBlank()) "$safeArtist — $safeTitle" else safeTitle
        val statusSuffix = if (!nowPlayingIsPlaying) " (Paused)" else ""
        val combined = (base + statusSuffix).trim()

        val line1 = when {
            combined.length <= maxLine -> combined
            (safeTitle + statusSuffix).length <= maxLine -> (safeTitle + statusSuffix).trim()
            else -> combined.take(maxLine - 1) + "…"
        }

        val dur = if (spotifyDemoEnabled && !detected) 80_000L else nowPlayingDurationMs
        val pos = if (spotifyDemoEnabled && !detected) 58_000L else computeEffectivePositionMs()

        val bar = renderProgressBar(spotifyPreset, pos, max(1L, dur))
        val time = "${fmtTime(pos)} / ${fmtTime(max(1L, dur))}"
        val line2 = "$bar $time".trim()

        debugLastMusicOsc = listOf(line1, line2).joinToString("\n").trim()
        return listOf(line1, line2).filter { it.isNotBlank() }
    }

    private fun buildCombinedText(): String {
        val lines = mutableListOf<String>()
        val afk = buildAfkLine()
        val cycle = buildCycleLine()
        val music = buildNowPlayingLines()

        if (afk.isNotBlank()) lines += afk
        if (cycle.isNotBlank()) lines += cycle
        lines.addAll(music)

        debugLastAfkOsc = afk
        debugLastCycleOsc = cycle
        debugLastCombinedOsc = lines.joinToString("\n")

        return joinWithLimit(lines, 144)
    }

    private fun requestCombinedSend(immediate: Boolean = false) {
        sendRequested.set(true)
        if (sendCoordinatorJob == null) {
            sendCoordinatorJob = viewModelScope.launch {
                while (true) {
                    while (!sendRequested.get()) delay(50)
                    sendRequested.set(false)

                    val now = System.currentTimeMillis()
                    val nextAllowed = lastSendAtMs + MIN_SEND_COOLDOWN_MS
                    if (now < nextAllowed) delay(nextAllowed - now)

                    val text = buildCombinedText()
                    sendCombinedToVrchat(text)
                }
            }
        }
    }

    private fun sendCombinedToVrchat(text: String, local: Boolean = false) {
        val payload = if (text.isBlank()) " " else text
        sendToVrchatRaw(payload, local, addToConversation = false)
        lastSendAtMs = System.currentTimeMillis()
    }

    // =========================================================
    // ✅ PROGRESS BAR PRESETS (RESTORED TO ORIGINAL LOOKS)
    // =========================================================
    private fun renderProgressBar(preset: Int, posMs: Long, durMs: Long): String {
        val duration = max(1L, durMs)
        val p = min(1f, max(0f, posMs.toFloat() / duration.toFloat()))

        fun idx(slots: Int): Int = (p * (slots - 1)).toInt().coerceIn(0, slots - 1)

        return when (preset.coerceIn(1, 5)) {
            // 1) Love: ♡━━━◉━━━━♡  (8 slots between hearts)
            1 -> {
                val slots = 8
                val i = idx(slots)
                val arr = CharArray(slots) { '━' }
                arr[i] = '◉'
                "♡" + arr.concatToString() + "♡"
            }

            // 2) Minimal: ━━◉────────── (13 total slots)
            2 -> {
                val slots = 13
                val i = idx(slots)
                val arr = CharArray(slots) { '─' }
                // keep the “━” leading vibe like the original
                if (slots >= 2) {
                    arr[0] = '━'
                    arr[1] = '━'
                }
                arr[i] = '◉'
                arr.concatToString()
            }

            // 3) Crystal: ⟡⟡⟡◉⟡⟡⟡⟡⟡ (9 slots)
            3 -> {
                val slots = 9
                val i = idx(slots)
                val arr = CharArray(slots) { '⟡' }
                arr[i] = '◉'
                arr.concatToString()
            }

            // 4) Soundwave: ▁▂▃▄▅●▅▄▃▂▁ (11 slots)
            4 -> {
                val base = charArrayOf('▁','▂','▃','▄','▅','▅','▄','▃','▂','▁','▁')
                // ^ using 11 slots: [▁▂▃▄▅▅▄▃▂▁▁] still looks wavey
                // but original is ▁▂▃▄▅●▅▄▃▂▁ (also 11)
                // We'll use the original exact pattern instead:
                val original = charArrayOf('▁','▂','▃','▄','▅','▅','▅','▄','▃','▂','▁')
                val slots = original.size
                val i = idx(slots)
                val out = original.copyOf()
                out[i] = '●'
                out.concatToString()
            }

            // 5) Geometry: ▣▣▣◉▢▢▢▢▢▢▢ (11 slots)
            else -> {
                val slots = 11
                val i = idx(slots)
                val arr = CharArray(slots) { '▢' }
                // first 3 are filled blocks like the original
                if (slots >= 3) {
                    arr[0] = '▣'
                    arr[1] = '▣'
                    arr[2] = '▣'
                }
                arr[i] = '◉'
                arr.concatToString()
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
