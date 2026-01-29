package com.scrapw.chatbox.ui

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
        fun getInstance(): ChatboxViewModel = instance
        fun isInstanceInitialized(): Boolean = ::instance.isInitialized
    }

    override fun onCleared() {
        stopCycle()
        super.onCleared()
    }

    val conversationUiState = ConversationUiState()

    private val storedIpState =
        userPreferencesRepository.ipAddress.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ""
        )

    private val userInputIpState = MutableStateFlow("")
    var ipAddressLocked by mutableStateOf(false)

    private val ipFlow = merge(storedIpState, userInputIpState)

    val messengerUiState = combine(
        ipFlow,
        userPreferencesRepository.isRealtimeMsg,
        userPreferencesRepository.isTriggerSfx,
        userPreferencesRepository.isTypingIndicator,
        userPreferencesRepository.isSendImmediately
    ) { ip, r, s, t, i ->
        MessengerUiState(ip, r, s, t, i)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MessengerUiState()
    )

    private val remoteChatboxOSC = ChatboxOSC(
        runBlocking { userPreferencesRepository.ipAddress.first() },
        9000
    )

    private val localChatboxOSC = ChatboxOSC("localhost", 9000)

    val messageText = mutableStateOf(TextFieldValue(""))
    val isAddressResolvable = mutableStateOf(true)

    fun onIpAddressChange(ip: String) {
        userInputIpState.value = ip
    }

    fun ipAddressApply(address: String) {
        viewModelScope.launch {
            remoteChatboxOSC.ipAddress = address
            isAddressResolvable.value = remoteChatboxOSC.addressResolvable
            userPreferencesRepository.saveIpAddress(address)
        }
    }

    fun portApply(port: Int) {
        remoteChatboxOSC.port = port
        viewModelScope.launch { userPreferencesRepository.savePort(port) }
    }

    fun onMessageTextChange(message: TextFieldValue, local: Boolean = false) {
        val osc = if (local) localChatboxOSC else remoteChatboxOSC
        messageText.value = message
        if (messengerUiState.value.isRealtimeMsg) {
            osc.sendRealtimeMessage(message.text)
        }
    }

    private fun sendText(text: String, local: Boolean) {
        val osc = if (local) localChatboxOSC else remoteChatboxOSC
        osc.sendMessage(
            text,
            messengerUiState.value.isSendImmediately,
            messengerUiState.value.isTriggerSFX
        )
        conversationUiState.addMessage(
            Message(text, false, Instant.now())
        )
    }

    fun sendMessage(local: Boolean = false) {
        sendText(messageText.value.text, local)
        messageText.value = TextFieldValue("", TextRange.Zero)
    }

    fun stashMessage(local: Boolean = false) {
        conversationUiState.addMessage(
            Message(messageText.value.text, true, Instant.now())
        )
        messageText.value = TextFieldValue("", TextRange.Zero)
    }

    fun onRealtimeMsgChanged(b: Boolean) =
        viewModelScope.launch { userPreferencesRepository.saveIsRealtimeMsg(b) }

    fun onTriggerSfxChanged(b: Boolean) =
        viewModelScope.launch { userPreferencesRepository.saveIsTriggerSFX(b) }

    fun onTypingIndicatorChanged(b: Boolean) =
        viewModelScope.launch { userPreferencesRepository.saveTypingIndicator(b) }

    fun onSendImmediatelyChanged(b: Boolean) =
        viewModelScope.launch { userPreferencesRepository.saveIsSendImmediately(b) }

    // ===== Cycle feature =====
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
                sendText(msgs[i], local)
                i = (i + 1) % msgs.size
                delay(cycleIntervalSeconds * 1000L)
            }
        }
    }

    fun stopCycle() {
        cycleJob?.cancel()
        cycleJob = null
    }

    private var updateChecked = false
    var updateInfo by mutableStateOf(UpdateInfo(UpdateStatus.NOT_CHECKED))
    fun checkUpdate() {
        if (updateChecked) return
        updateChecked = true
        viewModelScope.launch {
            updateInfo = checkUpdate("ScrapW", "Chatbox")
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
