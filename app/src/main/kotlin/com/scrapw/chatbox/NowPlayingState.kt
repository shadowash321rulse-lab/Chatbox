package com.scrapw.chatbox

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class NowPlaying(
    val isPlaying: Boolean = false,
    val artist: String = "",
    val title: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 1L
)

object NowPlayingState {
    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state

    fun update(np: NowPlaying) {
        _state.value = np
    }

    fun clear() {
        _state.value = NowPlaying()
    }
}
