package com.scrapw.chatbox

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class NowPlayingSnapshot(
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 1L,

    // NEW debug signals
    val listenerConnected: Boolean = false,
    val activeControllerPackage: String = ""
)

object NowPlayingState {
    private val _state = MutableStateFlow(NowPlayingSnapshot())
    val state: StateFlow<NowPlayingSnapshot> = _state

    fun setConnected(connected: Boolean) {
        _state.value = _state.value.copy(listenerConnected = connected)
    }

    fun update(value: NowPlayingSnapshot) {
        _state.value = value
    }

    fun clearKeepConnected() {
        // Keep connected flag, wipe content
        val c = _state.value.listenerConnected
        _state.value = NowPlayingSnapshot(listenerConnected = c)
    }
}
