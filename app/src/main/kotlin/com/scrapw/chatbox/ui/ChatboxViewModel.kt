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
    }

    override fun onCleared() {
        stopAll()
        super.onCleared()
    }

    // ----------------------------
    // Conversation / UI state
    // ----------------------------
    val conversationUiState = ConversationUiState()

    private val storedIpState: StateFlow<String> =
        userPreferencesRepository.ipAddress
            .map { it }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = "127.0.0.1"
            )

    private val userInputIpState = MutableStateFlow("")
    var ipAddressLocked by mutableStateOf(false)

    private val ipFlow = listOf(storedIpState, userInputIpState).asFlow().flattenMerge()

    // KEEP: used all over the app
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
        viewModelScope.launch {
            userPreferencesRepository.saveIpAddress(address)
        }
    }

    fun portApply(port: Int) {
        remoteChatboxOSC.port = port
        viewModelScope.launch {
            userPreferencesRepository.savePort(port)
        }
    }

    val isAddressResolvable = mutableStateOf(true)

    // KEEP signature: overlay expects (TextFieldValue, Boolean)
    fun onMessageTextChange(message: TextFieldValue, local: Boolean = false) {
        val osc = if (!local) remoteChatboxOSC else localChatboxOSC
        messageText.value = message

        if (messengerUiState.value.isRealtimeMsg) {
            osc.sendRealtimeMessage(message.text)
        } else {
            if (messengerUiState.value.isTypingIndicator) {
                osc.typing = message.text.isNotEmpty()
            }
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
            Message(messageText.value.text, false, Instant.now())
        )

        messageText.value = TextFieldValue("", TextRange.Zero)
    }

    // KEEP signature: overlay uses stashMessage(local=true/false)
    fun stashMessage(local: Boolean = false) {
        val osc = if (!local) remoteChatboxOSC else localChatboxOSC
        osc.typing = false

        conversationUiState.addMessage(
            Message(messageText.value.text, true, Instant.now())
        )

        messageText.value = TextFieldValue("", TextRange.Zero)
    }

    fun onRealtimeMsgChanged(isChecked: Boolean) {
        viewModelScope.launch { userPreferencesRepository.saveIsRealtimeMsg(isChecked) }
    }

    fun onTriggerSfxChanged(isChecked: Boolean) {
        viewModelScope.launch { userPreferencesRepository.saveIsTriggerSFX(isChecked) }
    }

    fun onTypingIndicatorChanged(isChecked: Boolean) {
        viewModelScope.launch { userPreferencesRepository.saveTypingIndicator(isChecked) }
    }

    fun onSendImmediatelyChanged(isChecked: Boolean) {
        viewModelScope.launch { userPreferencesRepository.saveIsSendImmediately(isChecked) }
    }

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
    // Cycle (rotating lines)
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
                val out = buildOutgoingMessage(cycleLine = msgs[i])
                messageText.value = TextFieldValue(out, TextRange(out.length))
                sendMessage(local)
                i = (i + 1) % msgs.size
                delay(cycleIntervalSeconds.coerceAtLeast(1).toLong() * 1000L)
            }
        }
    }

    private fun stopCycle() {
        cycleJob?.cancel()
        cycleJob = null
    }

    // ============================
    // Now Playing (notification listener feeds NowPlayingState)
    // ============================
    var spotifyEnabled by mutableStateOf(false)
        private set

    var spotifyDemoEnabled by mutableStateOf(false)
        private set

    var spotifyPreset by mutableStateOf(1)
        private set

    var musicRefreshSeconds by mutableStateOf(2)

    // Debug fields used by your Debug tab
    var nowPlayingDetected by mutableStateOf(false)
        private set
    var lastNowPlayingArtist by mutableStateOf("")
        private set
    var lastNowPlayingTitle by mutableStateOf("")
        private set
    var lastSentToVrchatAtMs by mutableStateOf(0L)
        private set

    private var nowPlayingSenderJob: Job? = null

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

    fun notificationAccessIntent(): Intent {
        // Opens the system page where user enables Notification Access for the app
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun startNowPlayingSender(local: Boolean = false) {
        if (!spotifyEnabled) return

        nowPlayingSenderJob?.cancel()
        nowPlayingSenderJob = viewModelScope.launch {
            while (spotifyEnabled) {
                val out = buildOutgoingMessage(cycleLine = null)
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
        nowPlayingSenderJob?.cancel()
        nowPlayingSenderJob = null
    }

    fun sendNowPlayingOnce(local: Boolean = false) {
        if (!spotifyEnabled) return
        val out = buildOutgoingMessage(cycleLine = null)
        messageText.value = TextFieldValue(out, TextRange(out.length))
        sendMessage(local)
        lastSentToVrchatAtMs = System.currentTimeMillis()
    }

    fun stopAll() {
        stopCycle()
        stopNowPlayingSender()
    }

    // Build outgoing message:
    // - cycle line (if provided) on top
    // - now playing block always under it (if enabled)
    private fun buildOutgoingMessage(cycleLine: String?): String {
        val sb = StringBuilder()

        val cycle = cycleLine?.trim().orEmpty()
        if (cycle.isNotEmpty()) {
            sb.append(cycle)
        }

        val np = buildNowPlayingBlock()
        if (np.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append(np)
        }

        return sb.toString().trim()
    }

    private fun buildNowPlayingBlock(): String {
        if (!spotifyEnabled) return ""

        // Demo fallback so you can test UI even if detection fails
        val snapshot = if (spotifyDemoEnabled) {
            NowPlayingState.state.value.copy(
                title = "pretty song",
                artist = "soft artist",
                isPlaying = true,
                positionMs = (58_000L),
                durationMs = (80_000L)
            )
        } else {
            NowPlayingState.state.value
        }

        val title = snapshot.title.trim()
        val artist = snapshot.artist.trim()
        if (title.isEmpty() && artist.isEmpty()) return ""

        // Preset bars (fixed to same width so they don't overflow inconsistently)
        val bar = renderPresetBar(
            preset = spotifyPreset,
            positionMs = snapshot.positionMs,
            durationMs = snapshot.durationMs
        )

        val time = "${fmtTime(snapshot.positionMs)} / ${fmtTime(snapshot.durationMs)}"

        // line 1: artist / title (artist can be dropped by your later overflow rules)
        // line 2: bar + time (ONE LINE)
        val line1 = if (artist.isNotEmpty()) "$artist — $title" else title
        val line2 = "$bar $time"

        return "$line1\n$line2"
    }

    private fun renderPresetBar(preset: Int, positionMs: Long, durationMs: Long): String {
        val dur = durationMs.coerceAtLeast(1L)
        val p = (positionMs.toDouble() / dur.toDouble()).coerceIn(0.0, 1.0)

        // Use a consistent symbol count across all presets to reduce overflow.
        // Total "slots" includes the moving marker.
        val slots = 11
        val idx = (p * (slots - 1)).roundToInt().coerceIn(0, slots - 1)

        fun build(prefix: String, fillChar: Char, marker: String, suffix: String): String {
            val sb = StringBuilder()
            sb.append(prefix)
            for (i in 0 until slots) {
                sb.append(if (i == idx) marker else fillChar)
            }
            sb.append(suffix)
            return sb.toString()
        }

        return when (preset.coerceIn(1, 5)) {
            // (love) ♡━━━◉━━━━♡ (shortened to fixed width but same vibe)
            1 -> build(prefix = "♡", fillChar = '━', marker = "◉", suffix = "♡")
            // (minimal) ━━◉──────────  (fixed width)
            2 -> build(prefix = "", fillChar = '─', marker = "◉", suffix = "")
            // (crystal) ⟡⟡⟡◉⟡⟡⟡⟡⟡
            3 -> build(prefix = "", fillChar = '⟡', marker = "◉", suffix = "")
            // (soundwave) ▁▂▃▄▅●▅▄▃▂▁ (fixed pattern with moving marker)
            4 -> {
                val wave = charArrayOf('▁','▂','▃','▄','▅','▅','▄','▃','▂','▁','▁')
                val sb = StringBuilder()
                for (i in 0 until slots) {
                    sb.append(if (i == idx) '●' else wave[i % wave.size])
                }
                sb.toString()
            }
            // (geometry) ▣▣▣◉▢▢▢▢▢▢▢ (fixed width)
            else -> {
                val leftCount = 3
                val sb = StringBuilder()
                for (i in 0 until slots) {
                    val ch = if (i < leftCount) '▣' else '▢'
                    sb.append(if (i == idx) "◉" else ch.toString())
                }
                sb.toString()
            }
        }
    }

    private fun fmtTime(ms: Long): String {
        val totalSec = (ms.coerceAtLeast(0L) / 1000L).toInt()
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }

    // ============================
    // Hook NowPlayingState into debug fields
    // ============================
    init {
        viewModelScope.launch {
            NowPlayingState.state.collect { s ->
                lastNowPlayingArtist = s.artist
                lastNowPlayingTitle = s.title
                nowPlayingDetected = s.title.isNotBlank() || s.artist.isNotBlank()
            }
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

