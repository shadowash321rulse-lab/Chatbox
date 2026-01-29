package com.scrapw.chatbox

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * NotificationListenerService that reads "Now Playing" from active media sessions.
 * IMPORTANT: This file MUST NOT contain any Compose UI code.
 */
class NowPlayingListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NowPlayingListener"

        data class NowPlayingState(
            val detected: Boolean = false,
            val activePackage: String = "",
            val artist: String = "",
            val title: String = "",
            val durationMs: Long = 0L,
            val positionMs: Long = 0L,
            val isPlaying: Boolean = false
        )

        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected

        private val _state = MutableStateFlow(NowPlayingState())
        val state: StateFlow<NowPlayingState> = _state

        fun clear() {
            _state.value = NowPlayingState()
        }
    }

    private var msm: MediaSessionManager? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        _connected.value = true
        msm = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        refreshFromSessions()
        Log.d(TAG, "Listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _connected.value = false
        clear()
        Log.d(TAG, "Listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        // Any notification could mean session state changed; refresh.
        refreshFromSessions()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        refreshFromSessions()
    }

    private fun refreshFromSessions() {
        try {
            val mgr = msm ?: return
            val controllers: List<MediaController> =
                mgr.getActiveSessions(ComponentName(this, NowPlayingListenerService::class.java)) ?: emptyList()

            // Prefer an actively playing session
            val best = controllers.firstOrNull { c ->
                val st = c.playbackState
                st != null && st.state == PlaybackState.STATE_PLAYING
            } ?: controllers.firstOrNull()

            if (best == null) {
                _state.value = NowPlayingState(detected = false, activePackage = "")
                return
            }

            val md = best.metadata
            val st = best.playbackState

            val title = md?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            val artist = md?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            val duration = md?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 0L

            val isPlaying = st?.state == PlaybackState.STATE_PLAYING
            val pos = st?.position ?: 0L

            _state.value = NowPlayingState(
                detected = title.isNotBlank() || artist.isNotBlank(),
                activePackage = best.packageName ?: "",
                artist = artist,
                title = title,
                durationMs = duration,
                positionMs = pos,
                isPlaying = isPlaying
            )
        } catch (t: Throwable) {
            Log.e(TAG, "refreshFromSessions failed", t)
            _state.value = NowPlayingState(detected = false, activePackage = "")
        }
    }
}
