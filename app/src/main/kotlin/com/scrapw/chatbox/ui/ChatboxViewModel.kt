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

    // ============================================================
    // AFK (toggle) — adds an AFK line ABOVE cycle/music when enabled
    // ============================================================
    var afkEnabled by mutableStateOf(false)
    var afkMessage by mutableStateOf("AFK 🌙 back soon")

    // =========================
    // Cycle + Now Playing (single sender, two independent timers)
    // =========================

    // Cycle
    var cycleEnabled by mutableStateOf(false)
    var cycleMessages by mutableStateOf("")
    var cycleIntervalSeconds by mutableStateOf(3)

    // Now Playing (phone music) – UI still calls these as “spotify”
    var spotifyEnabled by mutableStateOf(false)
    var spotifyDemoEnabled by mutableStateOf(false)

    // 1..5 (your bars)
    var spotifyPreset by mutableStateOf(1)

    // refresh seconds for progress updates (separate from cycle speed)
    var musicRefreshSeconds by mutableStateOf(2)

    // Debug fields shown in UI
    var listenerConnected by mutableStateOf(false)
    var activePackage by mutableStateOf("(none)")
    var nowPlayingDetected by mutableStateOf(false)
    var lastNowPlayingTitle by mutableStateOf("(blank)")
    var lastNowPlayingArtist by mutableStateOf("(blank)")
    var lastSentToVrchatAtMs by mutableStateOf(0L)

    // internal now playing snapshots
    private var nowPlayingDurationMs by mutableStateOf(0L)
    private var nowPlayingPositionMs by mutableStateOf(0L)
    private var nowPlayingPositionUpdateTimeMs by mutableStateOf(0L)
    private var nowPlayingPlaybackSpeed by mutableStateOf(1f)
    var nowPlayingIsPlaying by mutableStateOf(false)
        private set

    // master sender
    private var senderJob: Job? = null
    private var lastCycleTickMs = 0L
    private var lastMusicTickMs = 0L
    private var cycleIndex = 0
    private var lastSentText: String = ""

    init {
        // Bind to listener state (keeps Debug screen from staying false/blank/blank forever)
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
    }

    fun notificationAccessIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

    // -------------------------
    // FIX: ONE sender only
    // -------------------------
    fun startSender(local: Boolean = false) {
        if (!cycleEnabled && !spotifyEnabled && !afkEnabled) return

        senderJob?.cancel()
        senderJob = viewModelScope.launch {
            // initialize ticks so both timers begin independently (no “sync”)
            val now0 = System.currentTimeMillis()
            lastCycleTickMs = now0
            lastMusicTickMs = now0

            while (cycleEnabled || spotifyEnabled || afkEnabled) {
                val now = System.currentTimeMillis()
                var changed = false

                // Advance cycle line only on its own timer
                if (cycleEnabled && now - lastCycleTickMs >= cycleIntervalSeconds.coerceAtLeast(1) * 1000L) {
                    cycleIndex++
                    lastCycleTickMs = now
                    changed = true
                }

                // Advance music progress only on its own timer
                if (spotifyEnabled && now - lastMusicTickMs >= musicRefreshSeconds.coerceAtLeast(1) * 1000L) {
                    lastMusicTickMs = now
                    changed = true
                }

                // If either clock ticked (or we haven't sent anything yet), rebuild output
                if (changed || lastSentText.isBlank()) {
                    val cycleLine = currentCycleLineOrNull()
                    val outgoing = buildOutgoingText(cycleLine)

                    // only send if text actually changed (prevents spam + prevents “sync”)
                    if (outgoing.isNotBlank() && outgoing != lastSentText) {
                        sendToVrchatRaw(outgoing, local)
                        lastSentText = outgoing
                    }
                }

                delay(200) // internal tick only; user speeds are the timers above
            }
        }
    }

    fun stopAll() {
        senderJob?.cancel()
        senderJob = null
        lastSentText = ""
    }

    private fun currentCycleLineOrNull(): String? {
        if (!cycleEnabled) return null
        val msgs = cycleMessages.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (msgs.isEmpty()) return null
        return msgs[cycleIndex % msgs.size]
    }

    // =========================
    // Compose outgoing text
    // Order:
    // AFK (optional)
    // Cycle line (optional)
    // Now Playing block (optional) ALWAYS under cycle
    // =========================
    private fun buildOutgoingText(cycleLine: String?): String {
        val lines = mutableListOf<String>()

        if (afkEnabled) {
            val afk = afkMessage.trim().ifEmpty { "AFK" }
            lines += afk
        }

        val cycle = cycleLine?.trim().orEmpty()
        if (cycle.isNotEmpty()) lines += cycle

        lines.addAll(buildNowPlayingLines())

        return joinWithLimit(lines, 144)
    }

    private fun buildNowPlayingLines(): List<String> {
        if (!spotifyEnabled) return emptyList()

        // Demo values if demo ON and nothing detected
        val title = if (spotifyDemoEnabled && !nowPlayingDetected) "Pretty Girl" else lastNowPlayingTitle
        val artist = if (spotifyDemoEnabled && !nowPlayingDetected) "Clairo" else lastNowPlayingArtist

        // If truly nothing detected and demo off → show nothing
        if (!spotifyDemoEnabled && !nowPlayingDetected) return emptyList()

        val safeTitle = title.takeIf { it != "(blank)" } ?: ""
        val safeArtist = artist.takeIf { it != "(blank)" } ?: ""

        // line 1: artist — title (drop artist if overflow)
        val maxLine = 42
        val combined = if (safeArtist.isNotBlank()) "$safeArtist — $safeTitle" else safeTitle
        val line1 = when {
            combined.length <= maxLine -> combined
            safeTitle.length <= maxLine -> safeTitle // drop artist when overflow
            else -> safeTitle.take(maxLine - 1) + "…"
        }.trim()

        // Effective position that updates smoothly even if notification doesn’t refresh:
        val dur = if (spotifyDemoEnabled && !nowPlayingDetected) 80_000L else nowPlayingDurationMs
        val pos = if (spotifyDemoEnabled && !nowPlayingDetected) 58_000L else effectivePositionMs()

        // If paused, show paused line (and bar freezes)
        if (!nowPlayingIsPlaying && !spotifyDemoEnabled) {
            // Still show title line; show paused line below it
            val pausedLine = "⏸ Paused"
            return listOf(line1, pausedLine).filter { it.isNotBlank() }
        }

        val bar = renderProgressBar(spotifyPreset, pos, dur)
        val time = "${fmtTime(pos)} / ${fmtTime(if (dur > 0) dur else max(pos, 1L))}"
        val line2 = "$bar $time".trim()

        return listOf(line1, line2).filter { it.isNotBlank() }
    }

    private fun effectivePositionMs(): Long {
        val base = nowPlayingPositionMs
        val updateAt = nowPlayingPositionUpdateTimeMs
        if (!nowPlayingIsPlaying) return base

        // positionUpdateTimeMs is assumed “elapsedRealtime-like” in your NowPlayingState
        // We use currentTimeMillis for simplicity; still works as long as both times are same source.
        val now = System.currentTimeMillis()
        val delta = max(0L, now - updateAt)
        val advanced = (delta.toFloat() * nowPlayingPlaybackSpeed).toLong()
        return max(0L, base + advanced)
    }

    // =========================
    // Your 5 preset bars (short and consistent)
    // =========================
    private fun renderProgressBar(preset: Int, posMs: Long, durMs: Long): String {
        val duration = max(1L, durMs)
        val p = min(1f, max(0f, posMs.toFloat() / duration.toFloat()))

        return when (preset.coerceIn(1, 5)) {
            // (love) ♡━━━━━━━━♡ with moving ◉ inside
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

        // Build from bottom up so Now Playing block is preserved first
        val out = ArrayList<String>()
        var total = 0

        for (i in clean.indices.reversed()) {
            val line = clean[i]
            val add = if (out.isEmpty()) line.length else (1 + line.length)
            if (total + add > limit) {
                if (i == 0 && limit - total > 2) {
                    val remain = limit - total - (if (out.isEmpty()) 0 else 1)
                    val cut = if (remain >= 2) (line.take(remain - 1) + "…") else ""
                    if (cut.isNotBlank()) out.add(0, cut)
                    total = limit
                }
                continue
            }
            out.add(0, line)
            total += add
        }

        return out.joinToString("\n")
    }

    private fun sendToVrchatRaw(text: String, local: Boolean) {
        val osc = if (!local) remoteChatboxOSC else localChatboxOSC
        osc.sendMessage(
            text,
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
