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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import kotlin.math.roundToInt

class ChatboxViewModel(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    companion object {
        private lateinit var instance: ChatboxViewModel

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as ChatboxApplication)
                instance = ChatboxViewModel(application.userPreferencesRepository)
                instance
            }
        }

        @MainThread
        fun getInstance(): ChatboxViewModel {
            if (!isInstanceInitialized()) throw Exception("ChatboxViewModel is not initialized!")
            return instance
        }

        fun isInstanceInitialized(): Boolean = ::instance.isInitialized

        const val VRCHAT_LIMIT = 144
    }

    override fun onCleared() {
        stopCycle()
        stopNowPlayingSender()
        super.onCleared()
    }

    val conversationUiState = ConversationUiState()

    private val storedIpState: StateFlow<String> =
        userPreferencesRepository.ipAddress
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val storedPortState: StateFlow<Int> =
        userPreferencesRepository.port
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 9000)

    private val userInputIpState = MutableStateFlow("")
    var ipAddressLocked by mutableStateOf(false)

    private val ipFlow = listOf(storedIpState, userInputIpState).asFlow().flattenMerge()

    val messengerUiState: StateFlow<MessengerUiState> = combine(
        ipFlow,
        userPreferencesRepository.isRealtimeMsg,
        userPreferencesRepository.isTriggerSfx,
        userPreferencesRepository.isTypingIndicator,
        userPreferencesRepository.isSendImmediately
    ) { ipAddress, isRealtimeMsg, isTriggerSFX, isTypingIndicator, isSendImmediately ->
        MessengerUiState(
            ipAddress = ipAddress,
            isRealtimeMsg = isRealtimeMsg,
            isTriggerSFX = isTriggerSFX,
            isTypingIndicator = isTypingIndicator,
            isSendImmediately = isSendImmediately
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MessengerUiState())

    private val remoteChatboxOSC = ChatboxOSC(
        ipAddress = runBlocking { userPreferencesRepository.ipAddress.first() },
        port = runBlocking { userPreferencesRepository.port.first() }
    )

    private val localChatboxOSC = ChatboxOSC("localhost", 9000)

    val messageText = mutableStateOf(TextFieldValue(""))
    val isAddressResolvable = mutableStateOf(true)

    fun onIpAddressChange(ip: String) {
        userInputIpState.value = ip
    }

    fun ipAddressApply(address: String) {
        CoroutineScope(Dispatchers.IO).launch {
            remoteChatboxOSC.ipAddress = address
            isAddressResolvable.value = remoteChatboxOSC.addressResolvable
            if (!isAddressResolvable.value) ipAddressLocked = false
        }
        viewModelScope.launch { userPreferencesRepository.saveIpAddress(address) }
    }

    fun portApply(port: Int) {
        remoteChatboxOSC.port = port
        viewModelScope.launch { userPreferencesRepository.savePort(port) }
    }

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

    // Update checker (kept)
    private var updateChecked = false
    var updateInfo by mutableStateOf(UpdateInfo(UpdateStatus.NOT_CHECKED))
    fun checkUpdate() {
        if (updateChecked) return
        updateChecked = true
        viewModelScope.launch(Dispatchers.Main) {
            updateInfo = checkUpdate("ScrapW", "Chatbox")
        }
    }

    // ============================
    // Cycle + separate Now Playing refresh speed
    // ============================
    var cycleEnabled by mutableStateOf(false)
    var cycleMessages by mutableStateOf("")
    var cycleIntervalSeconds by mutableStateOf(3)       // switches to next cycle line
    var musicRefreshSeconds by mutableStateOf(1)        // re-send same line with updated progress bar

    // Now Playing settings (kept spotify naming)
    var spotifyEnabled by mutableStateOf(false)
    var spotifyPreset by mutableStateOf(1)              // 1..5
    var spotifyDemoEnabled by mutableStateOf(false)

    fun updateSpotifyPreset(preset: Int) { spotifyPreset = preset.coerceIn(1, 5) }
    fun setSpotifyEnabledFlag(enabled: Boolean) { spotifyEnabled = enabled }
    fun setSpotifyDemoFlag(enabled: Boolean) { spotifyDemoEnabled = enabled }

    fun notificationAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // Debug indicators you asked for
    var lastNowPlayingTitle by mutableStateOf("")
    var lastNowPlayingArtist by mutableStateOf("")
    var nowPlayingDetected by mutableStateOf(false)
    var lastSentToVrchatAtMs by mutableStateOf(0L)

    // Internal “current cycle line”
    private var cycleIndex by mutableStateOf(0)
    private var cachedCycleLines: List<String> = emptyList()
    private fun rebuildCycleLines() {
        cachedCycleLines = cycleMessages.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (cachedCycleLines.isEmpty()) cycleIndex = 0
        else cycleIndex %= cachedCycleLines.size
    }

    private var cycleJob: Job? = null
    private var nowPlayingSendJob: Job? = null

    fun startCycle(local: Boolean = false) {
        if (!cycleEnabled) return
        rebuildCycleLines()
        if (cachedCycleLines.isEmpty()) return

        cycleJob?.cancel()
        cycleJob = viewModelScope.launch {
            while (cycleEnabled) {
                delay(cycleIntervalSeconds.coerceAtLeast(1).toLong() * 1000L)
                if (!cycleEnabled) break
                rebuildCycleLines()
                if (cachedCycleLines.isEmpty()) break
                cycleIndex = (cycleIndex + 1) % cachedCycleLines.size
            }
        }

        // If music refresh is enabled, start it too (so progress bar moves smoothly)
        startNowPlayingSender(local)
    }

    fun stopCycle() {
        cycleJob?.cancel()
        cycleJob = null
    }

    fun startNowPlayingSender(local: Boolean = false) {
        nowPlayingSendJob?.cancel()
        nowPlayingSendJob = viewModelScope.launch {
            while (true) {
                val out = buildOutgoingMessage()
                if (out.isNotBlank()) {
                    messageText.value = TextFieldValue(out, TextRange(out.length))
                    sendMessage(local)
                    lastSentToVrchatAtMs = System.currentTimeMillis()
                }
                delay(musicRefreshSeconds.coerceAtLeast(1).toLong() * 1000L)
            }
        }
    }

    fun stopNowPlayingSender() {
        nowPlayingSendJob?.cancel()
        nowPlayingSendJob = null
    }

    fun stopAll(local: Boolean = false) {
        stopCycle()
        stopNowPlayingSender()
    }

    fun sendNowPlayingOnce(local: Boolean = false) {
        val out = buildOutgoingMessage()
        if (out.isNotBlank()) {
            messageText.value = TextFieldValue(out, TextRange(out.length))
            sendMessage(local)
            lastSentToVrchatAtMs = System.currentTimeMillis()
        }
    }

    // ============================
    // Persistence wiring
    // ============================
    init {
        viewModelScope.launch {
            cycleEnabled = userPreferencesRepository.cycleEnabled.first()
            cycleMessages = userPreferencesRepository.cycleMessages.first()
            cycleIntervalSeconds = userPreferencesRepository.cycleInterval.first()

            spotifyEnabled = userPreferencesRepository.spotifyEnabled.first()
            spotifyPreset = userPreferencesRepository.spotifyPreset.first()
            spotifyDemoEnabled = userPreferencesRepository.spotifyDemo.first()

            musicRefreshSeconds = userPreferencesRepository.musicRefreshInterval.first()
            rebuildCycleLines()
        }

        viewModelScope.launch { snapshotFlow { cycleEnabled }.collect { userPreferencesRepository.saveCycleEnabled(it) } }
        viewModelScope.launch { snapshotFlow { cycleMessages }.collect { userPreferencesRepository.saveCycleMessages(it); rebuildCycleLines() } }
        viewModelScope.launch { snapshotFlow { cycleIntervalSeconds }.collect { userPreferencesRepository.saveCycleInterval(it.coerceAtLeast(1)) } }

        viewModelScope.launch { snapshotFlow { spotifyEnabled }.collect { userPreferencesRepository.saveSpotifyEnabled(it) } }
        viewModelScope.launch { snapshotFlow { spotifyPreset }.collect { userPreferencesRepository.saveSpotifyPreset(it.coerceIn(1, 5)) } }
        viewModelScope.launch { snapshotFlow { spotifyDemoEnabled }.collect { userPreferencesRepository.saveSpotifyDemo(it) } }

        viewModelScope.launch { snapshotFlow { musicRefreshSeconds }.collect { userPreferencesRepository.saveMusicRefreshInterval(it.coerceAtLeast(1)) } }

        // Listen to NowPlayingState
        viewModelScope.launch {
            NowPlayingState.state.collect { np ->
                lastNowPlayingTitle = np.title
                lastNowPlayingArtist = np.artist
                nowPlayingDetected = np.title.isNotBlank()
            }
        }
    }

    // ============================
    // VRChat message building
    // ============================
    private fun clampVrchat(text: String): String =
        if (text.length <= VRCHAT_LIMIT) text else text.take(VRCHAT_LIMIT)

    private fun currentCycleLine(): String {
        rebuildCycleLines()
        if (cachedCycleLines.isEmpty()) return ""
        return cachedCycleLines[cycleIndex].take(VRCHAT_LIMIT)
    }

    private fun buildOutgoingMessage(): String {
        // If neither cycle nor spotify enabled, nothing to send
        if (!cycleEnabled && !spotifyEnabled) return ""

        val cycle = currentCycleLine().trim()
        val remaining = (VRCHAT_LIMIT - cycle.length - if (cycle.isNotBlank()) 1 else 0).coerceAtLeast(0)
        val npBlock = buildNowPlayingBlockWithBudget(remaining).trim()

        val out = when {
            cycle.isNotBlank() && npBlock.isNotBlank() -> "$cycle\n$npBlock"
            cycle.isNotBlank() -> cycle
            npBlock.isNotBlank() -> npBlock
            else -> ""
        }
        return clampVrchat(out)
    }

    private data class NowPlayingUi(
        val isPlaying: Boolean,
        val artist: String,
        val title: String,
        val positionMs: Long,
        val durationMs: Long
    )

    private fun buildNowPlayingBlockWithBudget(budget: Int): String {
        if (!spotifyEnabled || budget <= 0) return ""

        val np = if (spotifyDemoEnabled) {
            val duration = 200_000L
            val pos = (System.currentTimeMillis() % duration).coerceIn(0L, duration)
            NowPlayingUi(true, "Demo Artist", "Demo Song", pos, duration)
        } else {
            val s = NowPlayingState.state.value
            NowPlayingUi(s.isPlaying, s.artist, s.title, s.positionMs, s.durationMs)
        }

        if (np.title.isBlank()) return "" // nothing detected

        val progressLine = formatProgressLine(np.positionMs, np.durationMs, spotifyPreset)
        if (progressLine.length > budget) return ""

        val titleBudget = (budget - progressLine.length - 1).coerceAtLeast(0)
        val titleLine = clampTitleToBudget(np.artist, np.title, titleBudget)

        return if (titleLine.isBlank()) {
            progressLine.take(budget)
        } else {
            ("$titleLine\n$progressLine").take(budget)
        }
    }

    // show artist unless it overflows; if too long, drop artist first
    private fun clampTitleToBudget(artist: String, title: String, budget: Int): String {
        if (budget <= 0) return ""
        val a = artist.trim()
        val t = title.trim()
        val full = if (a.isNotBlank()) "$a — $t" else t

        if (full.length <= budget) return full
        if (t.length <= budget) return t
        return ellipsize(t, budget)
    }

    private fun ellipsize(text: String, maxLen: Int): String {
        if (maxLen <= 0) return ""
        if (text.length <= maxLen) return text
        if (maxLen == 1) return "…"
        return text.take(maxLen - 1) + "…"
    }

    // Your 5 presets, uniform short width to avoid overflow
    private fun formatProgressLine(progressMs: Long, durationMs: Long, preset: Int): String {
        val dur = durationMs.coerceAtLeast(1L)
        val prog = progressMs.coerceIn(0L, dur)
        val left = formatTime(prog)
        val right = formatTime(dur)
        val barWidth = 8

        return when (preset.coerceIn(1, 5)) {
            1 -> {
                val inner = movingBar(barWidth, prog.toFloat() / dur.toFloat(), "━", "━", "◉")
                "♡$inner♡ $left / $right"
            }
            2 -> {
                val bar = movingBar(barWidth, prog.toFloat() / dur.toFloat(), "━", "─", "◉")
                "$bar $left/$right"
            }
            3 -> {
                val bar = movingBar(barWidth, prog.toFloat() / dur.toFloat(), "⟡", "⟡", "◉")
                "$bar $left / $right"
            }
            4 -> {
                val wave = movingWaveShort(prog.toFloat() / dur.toFloat())
                "$wave $left / $right"
            }
            else -> {
                val bar = movingBar(barWidth, prog.toFloat() / dur.toFloat(), "▣", "▢", "◉")
                "$bar $left / $right"
            }
        }
    }

    private fun movingBar(width: Int, progress: Float, filled: String, empty: String, marker: String): String {
        val w = width.coerceAtLeast(2)
        val idx = ((progress.coerceIn(0f, 1f)) * (w - 1)).roundToInt()
        val sb = StringBuilder()
        for (i in 0 until w) {
            sb.append(
                when {
                    i == idx -> marker
                    i < idx -> filled
                    else -> empty
                }
            )
        }
        return sb.toString()
    }

    private fun movingWaveShort(progress: Float): String {
        val wave = listOf("▁", "▂", "▃", "▄", "▅", "▄", "▃", "▂")
        val idx = ((progress.coerceIn(0f, 1f)) * (wave.size - 1)).roundToInt()
        val sb = StringBuilder()
        for (i in wave.indices) sb.append(if (i == idx) "●" else wave[i])
        return sb.toString()
    }

    private fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        return if (h > 0) {
            val ss = if (s < 10) "0$s" else "$s"
            val mm = if (m < 10) "0$m" else "$m"
            "$h:$mm:$ss"
        } else {
            val ss = if (s < 10) "0$s" else "$s"
            "$m:$ss"
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
