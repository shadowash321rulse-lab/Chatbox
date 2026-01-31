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

    // =========================
    // Conversation (old UI compatibility)
    // =========================
    val conversationUiState = ConversationUiState()

    // =========================
    // Existing settings flow (keep)
    // =========================
    private val storedIpState: StateFlow<String> =
        userPreferencesRepository.ipAddress.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ""
        )

    private val userInputIpState = kotlinx.coroutines.flow.MutableStateFlow("")
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

    // Old settings toggles used elsewhere
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

    // =========================
    // Update checker (keep)
    // =========================
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
    // AFK
    // =========================
    var afkEnabled by mutableStateOf(false)

    var afkMessage by mutableStateOf("")
        private set

    // forced interval (stable). You asked for no user interval picker.
    private val afkForcedIntervalSeconds = 6

    private var afkJob: Job? = null

    // Preset caches (for previews)
    private var afkPresetCache = arrayOf("", "", "")

    // =========================
    // Cycle
    // =========================
    var cycleEnabled by mutableStateOf(false)

    var cycleMessages by mutableStateOf("")
        private set

    var cycleIntervalSeconds by mutableStateOf(3)

    private var cycleJob: Job? = null
    private var cycleIndex = 0

    // Preset caches (slot 1..5)
    private var cyclePresetCache = Array(5) { "" }

    // =========================
    // Now Playing (UI still calls this “spotify”)
    // =========================
    var spotifyEnabled by mutableStateOf(false)
    var spotifyDemoEnabled by mutableStateOf(false)
    var spotifyPreset by mutableStateOf(1)

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
    private var nowPlayingPlaybackSpeed by mutableStateOf(1f)
    var nowPlayingIsPlaying by mutableStateOf(false)
        private set

    private var nowPlayingJob: Job? = null
    private var positionTickerJob: Job? = null

    // =========================
    // Debug: what each module generates
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
    // Central merged sender (prevents canceling)
    // =========================
    private var senderJob: Job? = null
    private var lastSentText: String = ""
    private var dirty = true

    // Safe cooldown to avoid VRChat cutting out
    private val minCooldownMs = 2000L // 2 seconds

    // Each module “publishes” a current line/block here
    private var currentAfkLine: String = ""
    private var currentCycleLine: String = ""
    private var currentMusicBlock: List<String> = emptyList()

    init {
        // Load persisted texts + presets, and bind NowPlayingState
        viewModelScope.launch {
            // Current texts
            afkMessage = userPreferencesRepository.afkMessage.first()
            cycleMessages = userPreferencesRepository.cycleMessages.first()
            cycleIntervalSeconds = userPreferencesRepository.cycleInterval.first().coerceAtLeast(2)

            // AFK presets
            afkPresetCache[0] = userPreferencesRepository.afkPreset1.first()
            afkPresetCache[1] = userPreferencesRepository.afkPreset2.first()
            afkPresetCache[2] = userPreferencesRepository.afkPreset3.first()

            // Cycle presets (messages only; interval is saved too, but preview focuses on messages)
            cyclePresetCache[0] = userPreferencesRepository.cyclePreset1Messages.first()
            cyclePresetCache[1] = userPreferencesRepository.cyclePreset2Messages.first()
            cyclePresetCache[2] = userPreferencesRepository.cyclePreset3Messages.first()
            cyclePresetCache[3] = userPreferencesRepository.cyclePreset4Messages.first()
            cyclePresetCache[4] = userPreferencesRepository.cyclePreset5Messages.first()
        }

        // Bind to listener state
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

                // music block may need refresh
                markDirty()
            }
        }

        // Start central sender loop
        ensureSenderRunning()

        // Live position ticker (UI + music block staying “alive”)
        ensurePositionTickerRunning()
    }

    // =========================
    // Permissions intents
    // =========================
    fun notificationAccessIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // =========================
    // AFK setters / presets
    // =========================
    fun updateAfkMessage(text: String) {
        afkMessage = text
        viewModelScope.launch { userPreferencesRepository.saveAfkMessage(text) }
        // If AFK is running, the line changes immediately
        if (afkEnabled) {
            currentAfkLine = buildAfkLine()
            debugLastAfkOsc = currentAfkLine
            markDirty()
        }
    }

    fun getAfkPresetPreview(slot: Int): String {
        val idx = (slot - 1).coerceIn(0, 2)
        val v = afkPresetCache[idx].trim()
        return if (v.isBlank()) "(empty)" else v
    }

    fun saveAfkPreset(slot: Int, text: String) {
        val idx = (slot - 1).coerceIn(0, 2)
        afkPresetCache[idx] = text
        viewModelScope.launch { userPreferencesRepository.saveAfkPreset(slot, text) }
    }

    fun loadAfkPreset(slot: Int) {
        val idx = (slot - 1).coerceIn(0, 2)
        val v = afkPresetCache[idx]
        updateAfkMessage(v)
    }

    fun startAfkSender(local: Boolean = false) {
        if (!afkEnabled) return
        afkJob?.cancel()
        currentAfkLine = buildAfkLine()
        debugLastAfkOsc = currentAfkLine
        markDirty()

        afkJob = viewModelScope.launch {
            while (afkEnabled) {
                // AFK is top line; we don’t need to change content unless you want flashing,
                // but we DO resend on a stable interval to keep it present.
                markDirty()
                delay(afkForcedIntervalSeconds.toLong() * 1000L)
            }
        }
    }

    fun stopAfkSender() {
        afkJob?.cancel()
        afkJob = null
        currentAfkLine = ""
        debugLastAfkOsc = ""
        markDirty(clearIfNothingElse = true)
    }

    fun sendAfkNow() {
        if (!afkEnabled) return
        currentAfkLine = buildAfkLine()
        debugLastAfkOsc = currentAfkLine
        markDirty()
    }

    private fun buildAfkLine(): String {
        val t = afkMessage.trim()
        if (t.isBlank()) return "(AFK)"
        // If user didn’t include (AFK), we add it cleanly
        return if (t.contains("AFK", ignoreCase = true)) t else "(AFK) $t"
    }

    // =========================
    // Cycle setters / presets
    // =========================
    fun updateCycleMessages(text: String) {
        // limit to 10 lines max (your request)
        val limited = text.lines()
            .take(10)
            .joinToString("\n") { it.trimEnd() }

        cycleMessages = limited
        viewModelScope.launch { userPreferencesRepository.saveCycleMessages(limited) }

        if (cycleEnabled) {
            // if running, keep current line (don’t reset index unless empty)
            if (limited.lines().all { it.trim().isBlank() }) {
                currentCycleLine = ""
            }
            debugLastCycleOsc = currentCycleLine
            markDirty()
        }
    }

    fun setCycleEnabledFlag(enabled: Boolean) {
        cycleEnabled = enabled
        viewModelScope.launch { userPreferencesRepository.saveCycleEnabled(enabled) }

        if (!enabled) {
            stopCycle()
        }
    }

    fun setCycleIntervalSecondsFlag(seconds: Int) {
        cycleIntervalSeconds = seconds.coerceAtLeast(2)
        viewModelScope.launch { userPreferencesRepository.saveCycleInterval(cycleIntervalSeconds) }
    }

    fun getCyclePresetPreview(slot: Int): String {
        val idx = (slot - 1).coerceIn(0, 4)
        val v = cyclePresetCache[idx].trim()
        if (v.isBlank()) return "(empty)"
        val firstLine = v.lines().firstOrNull()?.trim().orEmpty()
        return if (firstLine.isBlank()) "(empty)" else firstLine
    }

    fun saveCyclePreset(slot: Int, messages: String) {
        val idx = (slot - 1).coerceIn(0, 4)
        val limited = messages.lines().take(10).joinToString("\n") { it.trimEnd() }
        cyclePresetCache[idx] = limited

        // store interval with it (current interval)
        viewModelScope.launch {
            userPreferencesRepository.saveCyclePreset(slot, limited, cycleIntervalSeconds.coerceAtLeast(2))
        }
    }

    fun loadCyclePreset(slot: Int) {
        val idx = (slot - 1).coerceIn(0, 4)
        val v = cyclePresetCache[idx]
        updateCycleMessages(v)

        // Load interval too (from datastore)
        viewModelScope.launch {
            val interval = when (slot) {
                1 -> userPreferencesRepository.cyclePreset1Interval.first()
                2 -> userPreferencesRepository.cyclePreset2Interval.first()
                3 -> userPreferencesRepository.cyclePreset3Interval.first()
                4 -> userPreferencesRepository.cyclePreset4Interval.first()
                else -> userPreferencesRepository.cyclePreset5Interval.first()
            }
            setCycleIntervalSecondsFlag(interval.coerceAtLeast(2))
        }
    }

    fun startCycle(local: Boolean = false) {
        val msgs = cycleMessages.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(10)
        if (!cycleEnabled || msgs.isEmpty()) return

        cycleJob?.cancel()
        cycleJob = viewModelScope.launch {
            cycleIndex = 0
            while (cycleEnabled) {
                val line = msgs[cycleIndex % msgs.size]
                currentCycleLine = line
                debugLastCycleOsc = currentCycleLine
                markDirty()

                cycleIndex = (cycleIndex + 1) % msgs.size
                delay(cycleIntervalSeconds.coerceAtLeast(2).toLong() * 1000L)
            }
        }
    }

    fun stopCycle() {
        cycleJob?.cancel()
        cycleJob = null
        currentCycleLine = ""
        debugLastCycleOsc = ""
        markDirty(clearIfNothingElse = true)
    }

    // =========================
    // Now Playing controls
    // =========================
    fun setSpotifyEnabledFlag(enabled: Boolean) {
        spotifyEnabled = enabled
        if (!enabled) stopNowPlayingSender()
        markDirty(clearIfNothingElse = true)
    }

    fun setSpotifyDemoFlag(enabled: Boolean) {
        spotifyDemoEnabled = enabled
        markDirty()
    }

    fun updateSpotifyPreset(preset: Int) {
        spotifyPreset = preset.coerceIn(1, 5)
        markDirty()
    }

    fun updateMusicRefreshSeconds(seconds: Int) {
        musicRefreshSeconds = seconds.coerceAtLeast(2)
    }

    fun startNowPlayingSender(local: Boolean = false) {
        if (!spotifyEnabled) return

        nowPlayingJob?.cancel()
        nowPlayingJob = viewModelScope.launch {
            while (spotifyEnabled) {
                currentMusicBlock = buildNowPlayingLines()
                debugLastMusicOsc = currentMusicBlock.joinToString("\n")
                markDirty()
                delay(musicRefreshSeconds.coerceAtLeast(2).toLong() * 1000L)
            }
        }
    }

    fun stopNowPlayingSender() {
        nowPlayingJob?.cancel()
        nowPlayingJob = null
        currentMusicBlock = emptyList()
        debugLastMusicOsc = ""
        markDirty(clearIfNothingElse = true)
    }

    fun sendNowPlayingOnce(local: Boolean = false) {
        if (!spotifyEnabled) return
        currentMusicBlock = buildNowPlayingLines()
        debugLastMusicOsc = currentMusicBlock.joinToString("\n")
        markDirty()
    }

    // Live position again (independent of notification updates)
    private fun ensurePositionTickerRunning() {
        positionTickerJob?.cancel()
        positionTickerJob = viewModelScope.launch {
            while (true) {
                // This updates the internal “effective position” only for building lines.
                // It does NOT spam-send; sender respects cooldown + dirty state.
                if (spotifyEnabled && nowPlayingIsPlaying) {
                    markDirty()
                }
                delay(1000L)
            }
        }
    }

    // Computes effective position from snapshot + elapsed realtime
    private fun effectiveNowPlayingPositionMs(): Long {
        val basePos = nowPlayingPositionMs
        val baseTime = nowPlayingPositionUpdateTimeMs
        if (!nowPlayingIsPlaying) return basePos
        if (baseTime <= 0L) return basePos

        val now = SystemClock.elapsedRealtime()
        val delta = (now - baseTime).coerceAtLeast(0L)
        val advanced = (delta.toFloat() * nowPlayingPlaybackSpeed).toLong()
        return (basePos + advanced).coerceAtLeast(0L)
    }

    private fun buildNowPlayingLines(): List<String> {
        if (!spotifyEnabled) return emptyList()

        // Demo mode if no real detection
        val title = if (spotifyDemoEnabled && !nowPlayingDetected) "Pretty Girl" else lastNowPlayingTitle
        val artist = if (spotifyDemoEnabled && !nowPlayingDetected) "Clairo" else lastNowPlayingArtist

        // If truly nothing detected and demo off → show nothing
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
        val pos = if (spotifyDemoEnabled && !nowPlayingDetected) 58_000L else effectiveNowPlayingPositionMs()

        val bar = renderProgressBar(spotifyPreset, pos, dur)
        val time = "${fmtTime(pos)} / ${fmtTime(if (dur > 0) dur else max(pos, 1L))}"

        val status = if (nowPlayingIsPlaying) "" else " (Paused)"
        val line2 = "$bar $time$status".trim()

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

    // =========================
    // Central sender implementation (prevents canceling)
    // =========================
    private fun ensureSenderRunning() {
        if (senderJob != null) return
        senderJob = viewModelScope.launch {
            while (true) {
                if (dirty) {
                    trySendMerged()
                }
                delay(200L) // cheap tick; cooldown prevents spam
            }
        }
    }

    private fun markDirty(clearIfNothingElse: Boolean = false) {
        dirty = true
        if (clearIfNothingElse) {
            // if everything is off, we want the merged sender to push a blank once
            // (VRChat clears the chatbox line)
        }
    }

    private fun trySendMerged() {
        val mergedLines = mutableListOf<String>()

        if (afkEnabled && currentAfkLine.isNotBlank()) mergedLines += currentAfkLine
        if (cycleEnabled && currentCycleLine.isNotBlank()) mergedLines += currentCycleLine
        if (spotifyEnabled) mergedLines.addAll(currentMusicBlock)

        val merged = joinWithLimit(mergedLines, 144)

        debugLastCombinedOsc = merged

        // Safe cooldown
        val now = System.currentTimeMillis()
        if (now - lastSentToVrchatAtMs < minCooldownMs) {
            return
        }

        // Only send when changed OR when we need to clear
        val shouldSend = (merged != lastSentText) || (merged.isBlank() && lastSentText.isNotBlank())
        if (!shouldSend) {
            dirty = false
            return
        }

        // Send merged
        sendToVrchatRaw(merged, local = false, addToConversation = false)
        lastSentText = merged
        lastSentToVrchatAtMs = now

        dirty = false
    }

    private fun joinWithLimit(lines: List<String>, limit: Int): String {
        if (lines.isEmpty()) return ""
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return ""

        // Keep bottom content (music) if trimming, but preserve AFK at top when possible.
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

        // Remove sending “sound effect” by default:
        // force triggerSFX false at send-level (you asked to remove it)
        osc.sendMessage(
            text,
            messengerUiState.value.isSendImmediately,
            false
        )
        osc.typing = false

        if (addToConversation) {
            conversationUiState.addMessage(Message(text, false, Instant.now()))
        }
    }

    fun stopAll() {
        stopAfkSender()
        stopCycle()
        stopNowPlayingSender()
    }
}

data class MessengerUiState(
    val ipAddress: String = "127.0.0.1",
    val isRealtimeMsg: Boolean = false,
    val isTriggerSFX: Boolean = true,
    val isTypingIndicator: Boolean = true,
    val isSendImmediately: Boolean = true
)
