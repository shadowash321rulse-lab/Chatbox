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
    // Existing settings flow
    // ----------------------------
    private val storedIpState: StateFlow<String> =
        userPreferencesRepository.ipAddress.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ""
        )

    private val userInputIpState = MutableStateFlow("")
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
    // Update checker
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
    // DEBUG: show exactly what each component sent to OSC
    // =========================
    var debugLastCycleOsc by mutableStateOf("(none)")
    var debugLastMusicOsc by mutableStateOf("(none)")
    var debugLastAfkOsc by mutableStateOf("(none)")
    var debugLastRawOsc by mutableStateOf("(none)")

    // =========================
    // AFK (toggle NOT persisted, text IS persisted, forced interval)
    // =========================
    private companion object {
        const val AFK_FORCED_INTERVAL_SECONDS = 20
    }

    var afkEnabled by mutableStateOf(false) // always defaults OFF on reopen
    var afkMessage by mutableStateOf("AFK 🌙 back soon")

    private var afkJob: Job? = null

    fun startAfkSender(local: Boolean = false) {
        if (!afkEnabled) return
        afkJob?.cancel()
        afkJob = viewModelScope.launch {
            while (afkEnabled) {
                val text = afkMessage.trim().ifEmpty { "AFK" }
                debugLastAfkOsc = text
                sendToVrchatRaw(text, local, addToConversation = false)
                delay(AFK_FORCED_INTERVAL_SECONDS * 1000L)
            }
        }
    }

    fun stopAfkSender() {
        afkJob?.cancel()
        afkJob = null
    }

    fun sendAfkOnce(local: Boolean = false) {
        if (!afkEnabled) return
        val text = afkMessage.trim().ifEmpty { "AFK" }
        debugLastAfkOsc = text
        sendToVrchatRaw(text, local, addToConversation = false)
    }

    // =========================
    // Cycle (persisted) + 3 presets
    // =========================
    var cycleEnabled by mutableStateOf(false)
    var cycleMessages by mutableStateOf("")
    var cycleIntervalSeconds by mutableStateOf(3)
    private var cycleJob: Job? = null
    private var cycleIndex = 0

    private var preset1Messages by mutableStateOf("")
    private var preset1Interval by mutableStateOf(3)

    private var preset2Messages by mutableStateOf("")
    private var preset2Interval by mutableStateOf(3)

    private var preset3Messages by mutableStateOf("")
    private var preset3Interval by mutableStateOf(3)

    fun cyclePreset1Save() {
        viewModelScope.launch {
            userPreferencesRepository.saveCyclePreset1(cycleMessages, cycleIntervalSeconds.coerceAtLeast(1))
            preset1Messages = cycleMessages
            preset1Interval = cycleIntervalSeconds.coerceAtLeast(1)
        }
    }

    fun cyclePreset2Save() {
        viewModelScope.launch {
            userPreferencesRepository.saveCyclePreset2(cycleMessages, cycleIntervalSeconds.coerceAtLeast(1))
            preset2Messages = cycleMessages
            preset2Interval = cycleIntervalSeconds.coerceAtLeast(1)
        }
    }

    fun cyclePreset3Save() {
        viewModelScope.launch {
            userPreferencesRepository.saveCyclePreset3(cycleMessages, cycleIntervalSeconds.coerceAtLeast(1))
            preset3Messages = cycleMessages
            preset3Interval = cycleIntervalSeconds.coerceAtLeast(1)
        }
    }

    fun cyclePreset1Load() {
        cycleMessages = preset1Messages
        cycleIntervalSeconds = preset1Interval.coerceAtLeast(1)
    }

    fun cyclePreset2Load() {
        cycleMessages = preset2Messages
        cycleIntervalSeconds = preset2Interval.coerceAtLeast(1)
    }

    fun cyclePreset3Load() {
        cycleMessages = preset3Messages
        cycleIntervalSeconds = preset3Interval.coerceAtLeast(1)
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

                debugLastCycleOsc = outgoing
                if (outgoing.isNotBlank()) sendToVrchatRaw(outgoing, local, addToConversation = false)

                cycleIndex = (cycleIndex + 1) % msgs.size
                delay(cycleIntervalSeconds.coerceAtLeast(1).toLong() * 1000L)
            }
        }
    }

    fun stopCycle() {
        cycleJob?.cancel()
        cycleJob = null
    }

    // =========================
    // Now Playing (phone music)
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
    var lastSentToVrchatAtMs by mutableStateOf(0L)

    private var nowPlayingDurationMs by mutableStateOf(0L)
    private var nowPlayingPositionMs by mutableStateOf(0L)
    private var nowPlayingIsPlaying by mutableStateOf(false)

    private var nowPlayingJob: Job? = null

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

    fun startNowPlayingSender(local: Boolean = false) {
        if (!spotifyEnabled) return

        nowPlayingJob?.cancel()
        nowPlayingJob = viewModelScope.launch {
            while (spotifyEnabled) {
                val outgoing = buildOutgoingText(cycleLine = null)

                debugLastMusicOsc = outgoing
                if (outgoing.isNotBlank()) sendToVrchatRaw(outgoing, local, addToConversation = false)

                delay(musicRefreshSeconds.coerceAtLeast(1).toLong() * 1000L)
            }
        }
    }

    fun stopNowPlayingSender() {
        nowPlayingJob?.cancel()
        nowPlayingJob = null
    }

    fun sendNowPlayingOnce(local: Boolean = false) {
        val outgoing = buildOutgoingText(cycleLine = null)
        debugLastMusicOsc = outgoing
        if (outgoing.isNotBlank()) sendToVrchatRaw(outgoing, local, addToConversation = false)
    }

    fun stopAll() {
        stopAfkSender()
        stopCycle()
        stopNowPlayingSender()
    }

    // =========================
    // Init: load persisted values + autosave + wire NowPlayingState
    // =========================
    init {
        viewModelScope.launch {
            cycleEnabled = userPreferencesRepository.cycleEnabled.first()
            cycleMessages = userPreferencesRepository.cycleMessages.first()
            cycleIntervalSeconds = userPreferencesRepository.cycleInterval.first().coerceAtLeast(1)

            // AFK TEXT ONLY
            afkMessage = userPreferencesRepository.afkMessage.first()

            preset1Messages = userPreferencesRepository.cyclePreset1Messages.first()
            preset1Interval = userPreferencesRepository.cyclePreset1Interval.first().coerceAtLeast(1)

            preset2Messages = userPreferencesRepository.cyclePreset2Messages.first()
            preset2Interval = userPreferencesRepository.cyclePreset2Interval.first().coerceAtLeast(1)

            preset3Messages = userPreferencesRepository.cyclePreset3Messages.first()
            preset3Interval = userPreferencesRepository.cyclePreset3Interval.first().coerceAtLeast(1)
        }

        // Cycle autosave
        viewModelScope.launch { snapshotFlow { cycleEnabled }.collect { userPreferencesRepository.saveCycleEnabled(it) } }
        viewModelScope.launch { snapshotFlow { cycleMessages }.collect { userPreferencesRepository.saveCycleMessages(it) } }
        viewModelScope.launch {
            snapshotFlow { cycleIntervalSeconds }
                .collect { userPreferencesRepository.saveCycleInterval(it.coerceAtLeast(1)) }
        }

        // AFK text autosave (toggle NOT saved)
        viewModelScope.launch {
            snapshotFlow { afkMessage }
                .collect { userPreferencesRepository.saveAfkMessage(it) }
        }

        // Now playing wiring
        viewModelScope.launch {
            NowPlayingState.state.collect { s ->
                listenerConnected = s.listenerConnected
                activePackage = if (s.activePackage.isBlank()) "(none)" else s.activePackage
                nowPlayingDetected = s.detected
                lastNowPlayingTitle = if (s.title.isBlank()) "(blank)" else s.title
                lastNowPlayingArtist = if (s.artist.isBlank()) "(blank)" else s.artist
                nowPlayingDurationMs = s.durationMs
                nowPlayingPositionMs = s.positionMs
                nowPlayingIsPlaying = s.isPlaying
            }
        }
    }

    // =========================
    // Compose outgoing text (AFK on top if enabled, then Cycle, NowPlaying bottom)
    // =========================
    private fun buildOutgoingText(cycleLine: String?): String {
        val lines = mutableListOf<String>()

        if (afkEnabled) {
            val af
