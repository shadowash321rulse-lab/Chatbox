package com.scrapw.chatbox.ui

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
import com.scrapw.chatbox.UpdateInfo
import com.scrapw.chatbox.UpdateStatus
import com.scrapw.chatbox.checkUpdate
import com.scrapw.chatbox.data.UserPreferencesRepository
import com.scrapw.chatbox.osc.ChatboxOSC
import com.scrapw.chatbox.ui.mainScreen.ConversationUiState
import com.scrapw.chatbox.ui.mainScreen.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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

        const val VRCHAT_LIMIT = 144
        private const val SPOTIFY_TOKEN_TEXT = "{SPOTIFY}"
    }

    override fun onCleared() {
        stopCycle()
        stopSpotifyPolling()
        super.onCleared()
    }

    // ----------------------------
    // Core app state
    // ----------------------------
    val conversationUiState = ConversationUiState()

    private val storedIpState: StateFlow<String> =
        userPreferencesRepository.ipAddress
            .map { it }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ""
            )

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
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.IO) {
                remoteChatboxOSC.ipAddress = address
                isAddressResolvable.value = remoteChatboxOSC.addressResolvable
                if (!isAddressResolvable.value) ipAddressLocked = false
            }
        }
        viewModelScope.launch { userPreferencesRepository.saveIpAddress(address) }
    }

    fun portApply(port: Int) {
        remoteChatboxOSC.port = port
        viewModelScope.launch { userPreferencesRepository.savePort(port) }
    }

    val isAddressResolvable = mutableStateOf(true)

    // KEEP signature: overlay expects (TextFieldValue, Boolean)
    fun onMessageTextChange(message: TextFieldValue, local: Boolean = false) {
        val osc = if (!local) remoteChatboxOSC else localChatboxOSC

        messageText.value = message
        if (messengerUiState.value.isRealtimeMsg) {
            osc.sendRealtimeMessage(message.text)
        } else if (messengerUiState.value.isTypingIndicator) {
            osc.typing = message.text.isNotEmpty()
        }
    }

    // KEEP signature: overlay calls sendMessage(local=true/false)
    fun sendMessage(local: Boolean = false) {
        val osc = if (!local) remoteChatboxOSC else localChatboxOSC

        osc.sendMessage(
            messageText.value.text,
            messengerUiState.value.isSendImmediately,
            messengerUiState.value.isTriggerSFX
        )
        osc.typing = false

        conversationUiState.addMessage(
            Message(
                messageText.value.text,
                false,
                Instant.now()
            )
        )

        messageText.value = TextFieldValue("", TextRange.Zero)
    }

    // KEEP signature: overlay uses stashMessage(local=true/false)
    fun stashMessage(local: Boolean = false) {
        val osc = if (!local) remoteChatboxOSC else localChatboxOSC
        osc.typing = false

        conversationUiState.addMessage(
            Message(
                messageText.value.text,
                true,
                Instant.now()
            )
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

    // ============================
    // Cycle
    // ============================
    var cycleEnabled by mutableStateOf(false)
    var cycleMessages by mutableStateOf("")
    var cycleIntervalSeconds by mutableStateOf(3)
    private var cycleJob: Job? = null

    fun startCycle(local: Boolean = false) {
        val msgs = cycleMessages.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (!cycleEnabled || msgs.isEmpty()) return

        cycleJob?.cancel()
        cycleJob = viewModelScope.launch {
            var i = 0
            while (cycleEnabled) {
                val outgoing = buildOutgoingMessageAlwaysBottom(msgs[i])
                messageText.value = TextFieldValue(outgoing, TextRange(outgoing.length))
                sendMessage(local)
                i = (i + 1) % msgs.size
                delay(cycleIntervalSeconds.coerceAtLeast(1).toLong() * 1000L)
            }
        }
    }

    fun stopCycle() {
        cycleJob?.cancel()
        cycleJob = null
    }

    private fun buildOutgoingMessageAlwaysBottom(cycleLine: String): String {
        val cleanedCycle = cycleLine.replace(SPOTIFY_TOKEN_TEXT, "").trim()
        val spotifyBlock = buildSpotifyBlockOrEmpty().trim()

        val result = when {
            spotifyBlock.isBlank() -> cleanedCycle
            cleanedCycle.isBlank() -> spotifyBlock
            else -> "$cleanedCycle\n$spotifyBlock"
        }

        return clampVrchat(result)
    }

    private fun clampVrchat(text: String): String {
        val t = text.trimEnd()
        return if (t.length <= VRCHAT_LIMIT) t else t.take(VRCHAT_LIMIT)
    }

    // ============================
    // Spotify (DEMO WIRED) – real auth later
    // ============================
    var spotifyEnabled by mutableStateOf(false)
    var spotifyPreset by mutableStateOf(1) // 1..5
    var spotifyDemoEnabled by mutableStateOf(true)

    data class SpotifyNowPlaying(
        val isPlaying: Boolean,
        val artist: String,
        val track: String,
        val progressMs: Long,
        val durationMs: Long
    )

    var spotifyNowPlaying by mutableStateOf<SpotifyNowPlaying?>(null)
        private set

    private var spotifyPollJob: Job? = null

    fun updateSpotifyPreset(preset: Int) {
        spotifyPreset = preset.coerceIn(1, 5)
    }

    // ✅ Renamed to avoid JVM setter clashes
    fun setSpotifyEnabledFlag(enabled: Boolean) {
        spotifyEnabled = enabled
        if (!enabled) stopSpotifyPolling() else startSpotifyPolling()
    }

    // ✅ Renamed to avoid JVM setter clashes
    fun setSpotifyDemoFlag(enabled: Boolean) {
        spotifyDemoEnabled = enabled
        if (spotifyEnabled) startSpotifyPolling()
    }

    fun startSpotifyPolling() {
        spotifyPollJob?.cancel()
        spotifyPollJob = viewModelScope.launch {
            while (spotifyEnabled) {
                if (spotifyDemoEnabled) {
                    val duration = 80_000L // 1:20
                    val prog = (System.currentTimeMillis() % duration).coerceIn(0L, duration)
                    spotifyNowPlaying = SpotifyNowPlaying(
                        isPlaying = true,
                        artist = "Demo Artist",
                        track = "Demo Song",
                        progressMs = prog,
                        durationMs = duration
                    )
                } else {
                    spotifyNowPlaying = null
                }
                delay(800L)
            }
        }
    }

    fun stopSpotifyPolling() {
        spotifyPollJob?.cancel()
        spotifyPollJob = null
        spotifyNowPlaying = null
    }

    fun buildSpotifyBlockOrEmpty(): String {
        if (!spotifyEnabled) return ""
        val np = spotifyNowPlaying ?: return ""

        val progressLine = formatProgressLine(np.progressMs, np.durationMs, spotifyPreset)

        val titleMax = (VRCHAT_LIMIT - progressLine.length - 1).coerceAtLeast(0)
        val title = clampTitleToBudget(np.artist, np.track, titleMax)

        val combined = "$title\n$progressLine"
        return if (combined.length <= VRCHAT_LIMIT) combined else combined.take(VRCHAT_LIMIT)
    }

    private fun clampTitleToBudget(artist: String, track: String, budget: Int): String {
        if (budget <= 0) return ""
        val full = "$artist — $track"
        if (full.length <= budget) return full
        if (track.length <= budget) return track
        return ellipsize(track, budget)
    }

    private fun ellipsize(text: String, maxLen: Int): String {
        if (maxLen <= 0) return ""
        if (text.length <= maxLen) return text
        if (maxLen == 1) return "…"
        return text.take(maxLen - 1) + "…"
    }

    private fun formatProgressLine(progressMs: Long, durationMs: Long, preset: Int): String {
        val dur = durationMs.coerceAtLeast(1L)
        val prog = progressMs.coerceIn(0L, dur)
        val left = formatTime(prog)
        val right = formatTime(dur)

        return when (preset.coerceIn(1, 5)) {
            1 -> {
                val inner = movingBar(width = 8, progress = prog.toFloat() / dur.toFloat(), filled = "━", empty = "━", marker = "◉")
                "♡$inner♡ $left / $right"
            }
            2 -> {
                val bar = movingBar(width = 13, progress = prog.toFloat() / dur.toFloat(), filled = "━", empty = "─", marker = "◉")
                "$bar $left/$right"
            }
            3 -> {
                val bar = movingBar(width = 9, progress = prog.toFloat() / dur.toFloat(), filled = "⟡", empty = "⟡", marker = "◉")
                "$bar $left / $right"
            }
            4 -> {
                val wave = movingWave(progress = prog.toFloat() / dur.toFloat())
                "$wave $left / $right"
            }
            else -> {
                val bar = movingBar(width = 11, progress = prog.toFloat() / dur.toFloat(), filled = "▣", empty = "▢", marker = "◉")
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

    private fun movingWave(progress: Float): String {
        val wave = listOf("▁", "▂", "▃", "▄", "▅", "▅", "▄", "▃", "▂", "▁", "▁")
        val idx = ((progress.coerceIn(0f, 1f)) * (wave.size - 1)).roundToInt()
        val sb = StringBuilder()
        for (i in wave.indices) sb.append(if (i == idx) "●" else wave[i])
        return sb.toString()
    }

    private fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val m = totalSec / 60L
        val s = totalSec % 60L
        return if (s < 10) "${m}:0${s}" else "${m}:${s}"
    }

    // ============================
    // Persistence wiring (cycle only)
    // ============================
    init {
        viewModelScope.launch {
            cycleEnabled = userPreferencesRepository.cycleEnabled.first()
            cycleMessages = userPreferencesRepository.cycleMessages.first()
            cycleIntervalSeconds = userPreferencesRepository.cycleInterval.first()
        }

        viewModelScope.launch { snapshotFlow { cycleEnabled }.collect { userPreferencesRepository.saveCycleEnabled(it) } }
        viewModelScope.launch { snapshotFlow { cycleMessages }.collect { userPreferencesRepository.saveCycleMessages(it) } }
        viewModelScope.launch { snapshotFlow { cycleIntervalSeconds }.collect { userPreferencesRepository.saveCycleInterval(it) } }
    }
}

data class MessengerUiState(
    val ipAddress: String = "127.0.0.1",
    val isRealtimeMsg: Boolean = false,
    val isTriggerSFX: Boolean = true,
    val isTypingIndicator: Boolean = true,
    val isSendImmediately: Boolean = true
)
