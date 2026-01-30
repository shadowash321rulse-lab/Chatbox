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
import com.scrapw.chatbox.checkUpdate
import com.scrapw.chatbox.UpdateInfo
import com.scrapw.chatbox.UpdateStatus
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

    // ✅ RESTORED: Conversation state used by overlay + old screens
    val conversationUiState = ConversationUiState()

    // ----------------------------
    // Existing settings flow
    // ----------------------------
    private val storedIpState: StateFlow<String> =
        userPreferencesRepository.ipAddress.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ""
        )

    private val storedPortState: StateFlow<Int> =
        userPreferencesRepository.port.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 9000
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
        port = runBlocking { userPreferencesRepository.port.first() }
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

    // ✅ RESTORED: used by old UI / overlay
    fun onRealtimeMsgChanged(isChecked: Boolean) =
        viewModelScope.launch { userPreferencesRepository.saveIsRealtimeMsg(isChecked) }

    fun onTriggerSfxChanged(isChecked: Boolean) =
        viewModelScope.launch { userPreferencesRepository.saveIsTriggerSFX(isChecked) }

    fun onTypingIndicatorChanged(isChecked: Boolean) =
        viewModelScope.launch { userPreferencesRepository.saveTypingIndicator(isChecked) }

    fun onSendImmediatelyChanged(isChecked: Boolean) =
        viewModelScope.launch { userPreferencesRepository.saveIsSendImmediately(isChecked) }

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

        // add to conversation log
        conversationUiState.addMessage(
            Message(messageText.value.text, false, Instant.now())
        )

        messageText.value = TextFieldValue("", TextRange.Zero)
    }

    // ✅ RESTORED: overlay expects stashMessage()
    fun stashMessage(local: Boolean = false) {
        val osc = if (!local) remoteChatboxOSC else localChatboxOSC
        osc.typing = false

        conversationUiState.addMessage(
            Message(messageText.value.text, true, Instant.now())
        )

        messageText.value = TextFieldValue("", TextRange.Zero)
    }

    // ----------------------------
    // Update checker (kept)
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
    // Debug: what each module is generating
    // =========================
    var debugLastAfkOsc by mutableStateOf("")
        private set
    var debugLastCycleOsc by mutableStateOf("")
        private set
    var debugLastMusicOsc by mutableStateOf("")
        private set
    var debugLastCombinedOsc by mutableStateOf("")
        private set

    // =========================
    // AFK
    // =========================
    var afkEnabled by mutableStateOf(false)

    var afkMessage by mutableStateOf("AFK 🌙 back soon")
        private set

    private val afkForcedIntervalSeconds = 5 // forced (no UI selector)
    private var afkJob: Job? = null

    fun updateAfkMessage(text: String) {
        afkMessage = text
        viewModelScope.launch { userPreferencesRepository.saveAfkMessage(text) }
    }

    fun saveAfkPreset(slot: Int, text: String) {
        viewModelScope.launch { userPreferencesRepository.saveAfkPreset(slot.coerceIn(1, 3), text) }
    }

    fun loadAfkPreset(slot: Int) {
        val s = slot.coerceIn(1, 3)
        viewModelScope.launch {
            val loaded = when (s) {
                1 -> userPreferencesRepository.afkPreset1.first()
                2 -> userPreferencesRepository.afkPreset2.first()
                else -> userPreferencesRepository.afkPreset3.first()
            }
            if (loaded.isNotBlank()) updateAfkMessage(loaded)
        }
    }

    fun getAfkPresetPreview(slot: Int): String = when (slot.coerceIn(1, 3)) {
        1 -> afkPresetPreview1
        2 -> afkPresetPreview2
        else -> afkPresetPreview3
    }.oneLinePreview(48)

    private var afkPresetPreview1 by mutableStateOf("")
    private var afkPresetPreview2 by mutableStateOf("")
    private var afkPresetPreview3 by mutableStateOf("")

    fun startAfkSender(local: Boolean = false) {
        if (!afkEnabled) return

        afkJob?.cancel()
        afkJob = viewModelScope.launch {
            while (afkEnabled) {
                val outgoing = buildOutgoingText(cycleLine = null)
                if (outgoing.isNotBlank()) {
                    sendToVrchatRaw(outgoing, local, addToConversation = false)
                }
                delay(afkForcedIntervalSeconds * 1000L)
            }
        }
    }

    fun stopAfkSender(local: Boolean = false) {
        afkJob?.cancel()
        afkJob = null
        // blank message to clear VRChat chatbox immediately
        sendToVrchatRaw("", local, addToConversation = false)
    }

    fun sendAfkNow(local: Boolean = false) {
        if (!afkEnabled) return
        val outgoing = buildOutgoingText(cycleLine = null)
        sendToVrchatRaw(outgoing, local, addToConversation = false)
    }

    // =========================
    // Cycle
    // =========================
    var cycleEnabled by mutableStateOf(false)
        private set

    var cycleMessages by mutableStateOf("")
        private set

    var cycleIntervalSeconds by mutableStateOf(3)
        private set

    private var cycleJob: Job? = null
    private var cycleIndex = 0

    fun setCycleEnabledFlag(enabled: Boolean) {
        cycleEnabled = enabled
        viewModelScope.launch { userPreferencesRepository.saveCycleEnabled(enabled) }
        if (!enabled) stopCycle()
    }

    fun updateCycleMessages(text: String) {
        cycleMessages = text
        viewModelScope.launch { userPreferencesRepository.saveCycleMessages(text) }
    }

    fun setCycleIntervalSecondsFlag(seconds: Int) {
        val clamped = seconds.coerceAtLeast(2)
        cycleIntervalSeconds = clamped
        viewModelScope.launch { userPreferencesRepository.saveCycleInterval(clamped) }
    }

    private var cyclePresetPreview1 by mutableStateOf("")
    private var cyclePresetPreview2 by mutableStateOf("")
    private var cyclePresetPreview3 by mutableStateOf("")
    private var cyclePresetPreview4 by mutableStateOf("")
    private var cyclePresetPreview5 by mutableStateOf("")

    fun getCyclePresetPreview(slot: Int): String = when (slot.coerceIn(1, 5)) {
        1 -> cyclePresetPreview1
        2 -> cyclePresetPreview2
        3 -> cyclePresetPreview3
        4 -> cyclePresetPreview4
        else -> cyclePresetPreview5
    }.oneLinePreview(48)

    fun saveCyclePreset(slot: Int, messages: String) {
        val s = slot.coerceIn(1, 5)
        val interval = cycleIntervalSeconds
        viewModelScope.launch {
            when (s) {
                1 -> userPreferencesRepository.saveCyclePreset1(messages, interval)
                2 -> userPreferencesRepository.saveCyclePreset2(messages, interval)
                3 -> userPreferencesRepository.saveCyclePreset3(messages, interval)
                4 -> userPreferencesRepository.saveCyclePreset4(messages, interval)
                else -> userPreferencesRepository.saveCyclePreset5(messages, interval)
            }
        }
    }

    fun loadCyclePreset(slot: Int) {
        val s = slot.coerceIn(1, 5)
        viewModelScope.launch {
            val msgs: String
            val interval: Int
            when (s) {
                1 -> { msgs = userPreferencesRepository.cyclePreset1Messages.first(); interval = userPreferencesRepository.cyclePreset1Interval.first() }
                2 -> { msgs = userPreferencesRepository.cyclePreset2Messages.first(); interval = userPreferencesRepository.cyclePreset2Interval.first() }
                3 -> { msgs = userPreferencesRepository.cyclePreset3Messages.first(); interval = userPreferencesRepository.cyclePreset3Interval.first() }
                4 -> { msgs = userPreferencesRepository.cyclePreset4Messages.first(); interval = userPreferencesRepository.cyclePreset4Interval.first() }
                else -> { msgs = userPreferencesRepository.cyclePreset5Messages.first(); interval = userPreferencesRepository.cyclePreset5Interval.first() }
            }
            if (msgs.isNotBlank()) updateCycleMessages(msgs)
            setCycleIntervalSecondsFlag(interval)
        }
    }

    fun startCycle(local: Boolean = false) {
        val msgs = cycleMessages.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (!cycleEnabled || msgs.isEmpty()) return

        cycleJob?.cancel()
        cycleJob = viewModelScope.launch {
            cycleIndex = 0
            while (cycleEnabled) {
                val line = msgs[cycleIndex % msgs.size]
                val outgoing = buildOutgoingText(cycleLine = line)
                sendToVrchatRaw(outgoing, local, addToConversation = false)

                cycleIndex = (cycleIndex + 1) % msgs.size
                delay(cycleIntervalSeconds.coerceAtLeast(2).toLong() * 1000L)
            }
        }
    }

    fun stopCycle(local: Boolean = false) {
        cycleJob?.cancel()
        cycleJob = null
        // blank message to clear
        sendToVrchatRaw("", local, addToConversation = false)
    }

    // =========================
    // Now Playing (Notification access)
    // =========================
    var spotifyEnabled by mutableStateOf(false)
    var spotifyDemoEnabled by mutableStateOf(false)
    var spotifyPreset by mutableStateOf(1)
    var musicRefreshSeconds by mutableStateOf(2)

    var listenerConnected by mutableStateOf(false)
    var activePackage by mutableStateOf("(none)")
    var nowPlayingDetected by mutableStateOf(false)
    var lastNowPlayingTitle by mutableStateOf("(blank)")
    var lastNowPlayingArtist by mutableStateOf("(blank)")
    var nowPlayingIsPlaying by mutableStateOf(false)

    var lastSentToVrchatAtMs by mutableStateOf(0L)

    private var nowPlayingDurationMs by mutableStateOf(0L)
    private var nowPlayingPositionMs by mutableStateOf(0L)
    private var nowPlayingPositionUpdateTimeMs by mutableStateOf(0L)
    private var nowPlayingPlaybackSpeed by mutableStateOf(1f)

    private var nowPlayingJob: Job? = null

    init {
        // restore persisted base values
        viewModelScope.launch { userPreferencesRepository.afkMessage.collect { afkMessage = it } }
        viewModelScope.launch { userPreferencesRepository.cycleEnabled.collect { cycleEnabled = it } }
        viewModelScope.launch { userPreferencesRepository.cycleMessages.collect { cycleMessages = it } }
        viewModelScope.launch { userPreferencesRepository.cycleInterval.collect { cycleIntervalSeconds = it.coerceAtLeast(2) } }

        // presets (AFK 3)
        viewModelScope.launch { userPreferencesRepository.afkPreset1.collect { afkPresetPreview1 = it } }
        viewModelScope.launch { userPreferencesRepository.afkPreset2.collect { afkPresetPreview2 = it } }
        viewModelScope.launch { userPreferencesRepository.afkPreset3.collect { afkPresetPreview3 = it } }

        // presets (Cycle 5)
        viewModelScope.launch { userPreferencesRepository.cyclePreset1Messages.collect { cyclePresetPreview1 = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset2Messages.collect { cyclePresetPreview2 = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset3Messages.collect { cyclePresetPreview3 = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset4Messages.collect { cyclePresetPreview4 = it } }
        viewModelScope.launch { userPreferencesRepository.cyclePreset5Messages.collect { cyclePresetPreview5 = it } }

        // NowPlaying state -> VM fields
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
                val outgoing = buildOutgoingText(cycleLine = null)
                if (outgoing.isNotBlank()) {
                    sendToVrchatRaw(outgoing, local, addToConversation = false)
                }
                delay(musicRefreshSeconds.coerceAtLeast(2).toLong() * 1000L)
            }
        }
    }

    fun stopNowPlayingSender(local: Boolean = false) {
        nowPlayingJob?.cancel()
        nowPlayingJob = null
        // blank to clear
        sendToVrchatRaw("", local, addToConversation = false)
    }

    fun sendNowPlayingOnce(local: Boolean = false) {
        val outgoing = buildOutgoingText(cycleLine = null)
        if (outgoing.isNotBlank()) {
            sendToVrchatRaw(outgoing, local, addToConversation = false)
        }
    }

    fun stopAll(local: Boolean = false) {
        stopCycle(local)
        stopNowPlayingSender(local)
        stopAfkSender(local)
    }

    // =========================
    // Compose outgoing text
    // AFK on top, cycle under it, NowPlaying at bottom
    // =========================
    private fun buildOutgoingText(cycleLine: String?): String {
        val lines = mutableListOf<String>()

        val afkLine = if (afkEnabled) afkMessage.trim() else ""
        if (afkLine.isNotBlank()) lines += afkLine

        val cycle = cycleLine?.trim().orEmpty()
        if (cycle.isNotEmpty()) lines += cycle

        val np = buildNowPlayingLines()
        lines.addAll(np)

        val combined = joinWithLimit(lines, 144)

        debugLastAfkOsc = afkLine
        debugLastCycleOsc = cycle
        debugLastMusicOsc = np.joinToString("\n")
        debugLastCombinedOsc = combined

        return combined
    }

    private fun buildNowPlayingLines(): List<String> {
        if (!spotifyEnabled) return emptyList()

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
        val basePos = if (spotifyDemoEnabled && !nowPlayingDetected) 58_000L else nowPlayingPositionMs

        // live progress even if notifications aren't constantly updating
        val pos = if (nowPlayingIsPlaying && nowPlayingPositionUpdateTimeMs > 0L) {
            val delta = android.os.SystemClock.elapsedRealtime() - nowPlayingPositionUpdateTimeMs
            (basePos + (delta * nowPlayingPlaybackSpeed).toLong()).coerceAtLeast(0L)
        } else basePos.coerceAtLeast(0L)

        val bar = renderProgressBar(spotifyPreset, pos, dur)
        val pausedSuffix = if (nowPlayingIsPlaying) "" else " (Paused)"
        val time = "${fmtTime(pos)} / ${fmtTime(if (dur > 0) dur else max(pos, 1L))}$pausedSuffix"
        val line2 = "$bar $time".trim()

        return listOf(line1, line2).filter { it.isNotBlank() }
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
            if (total + add > limit) continue
            out.add(0, line)
            total += add
        }
        return out.joinToString("\n")
    }

    private fun String.oneLinePreview(maxChars: Int): String {
        val flat = this.lines().joinToString(" | ") { it.trim() }.trim()
        if (flat.isBlank()) return ""
        return if (flat.length <= maxChars) flat else flat.take(maxChars - 1) + "…"
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
