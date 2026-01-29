package com.scrapw.chatbox

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log

class NowPlayingListenerService : NotificationListenerService() {

    private val tag = "NowPlayingListener"

    private val msm: MediaSessionManager by lazy {
        getSystemService(MediaSessionManager::class.java)
    }

    private var active: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publish(active)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publish(active)
        }
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            val list = controllers ?: emptyList()
            // Prefer the one that's playing
            val playing = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            setActiveController(playing ?: list.firstOrNull())
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(tag, "onListenerConnected()")

        try {
            // In a NotificationListenerService, passing null is valid and commonly used.
            msm.addOnActiveSessionsChangedListener(sessionsListener, null)

            val initial = msm.getActiveSessions(null) ?: emptyList()
            val playing = initial.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            setActiveController(playing ?: initial.firstOrNull())
        } catch (t: Throwable) {
            Log.e(tag, "Failed to connect sessions listener", t)
            NowPlayingState.clear()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(tag, "onListenerDisconnected()")

        try {
            msm.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Throwable) {}

        setActiveController(null)
        NowPlayingState.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "onDestroy()")

        try {
            msm.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Throwable) {}

        setActiveController(null)
    }

    private fun setActiveController(controller: MediaController?) {
        if (active == controller) {
            publish(active)
            return
        }

        try {
            active?.unregisterCallback(controllerCallback)
        } catch (_: Throwable) {}

        active = controller

        try {
            active?.registerCallback(controllerCallback)
        } catch (_: Throwable) {}

        publish(active)
    }

    private fun publish(controller: MediaController?) {
        if (controller == null) {
            NowPlayingState.clear()
            return
        }

        val md = controller.metadata
        val st = controller.playbackState

        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()

        val duration = (md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 1L).coerceAtLeast(1L)

        val isPlaying = st?.state == PlaybackState.STATE_PLAYING
        val position = (st?.position ?: 0L).coerceIn(0L, duration)

        NowPlayingState.update(
            NowPlayingSnapshot(
                title = title,
                artist = artist,
                isPlaying = isPlaying,
                positionMs = position,
                durationMs = duration
            )
        )
    }
}
