package com.scrapw.chatbox

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlayingSnapshot(
    val listenerConnected: Boolean = false,
    val activePackage: String = "",
    val detected: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val durationMs: Long = 0L,

    // Raw position snapshot from PlaybackState
    val positionMs: Long = 0L,

    // For live progress without notification updates:
    // elapsedRealtime at the moment positionMs was measured
    val positionUpdateTimeMs: Long = 0L,
    val playbackSpeed: Float = 1f,

    val isPlaying: Boolean = false
)

object NowPlayingState {
    private val _state = MutableStateFlow(NowPlayingSnapshot())
    val state: StateFlow<NowPlayingSnapshot> = _state.asStateFlow()

    fun update(snapshot: NowPlayingSnapshot) {
        _state.value = snapshot
    }

    fun setConnected(connected: Boolean) {
        _state.value = _state.value.copy(listenerConnected = connected)
    }

    fun clearIfActivePackage(pkg: String) {
        val cur = _state.value
        if (cur.activePackage == pkg) {
            _state.value = cur.copy(
                detected = false,
                title = "",
                artist = "",
                durationMs = 0L,
                positionMs = 0L,
                positionUpdateTimeMs = 0L,
                playbackSpeed = 1f,
                isPlaying = false
            )
        }
    }
}
