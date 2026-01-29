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
            val list = controllers?.toList() ?: emptyList()
            setActiveController(pickBestController(list))
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(tag, "onListenerConnected()")
        NowPlayingState.setConnected(true)

        try {
            // In NotificationListenerService context, null is correct.
            msm.addOnActiveSessionsChangedListener(sessionsListener, null)

            val initial = msm.getActiveSessions(null)?.toList() ?: emptyList()
            setActiveController(pickBestController(initial))
        } catch (t: Throwable) {
            Log.e(tag, "Failed to connect sessions listener", t)
            NowPlayingState.setConnected(true) // connected but failed to read sessions
            NowPlayingState.clearKeepConnected()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(tag, "onListenerDisconnected()")

        try {
            msm.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Throwable) {}

        setActiveController(null)
        NowPlayingState.setConnected(false)
        NowPlayingState.clearKeepConnected()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "onDestroy()")

        try {
            msm.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Throwable) {}

        setActiveController(null)
        NowPlayingState.setConnected(false)
        NowPlayingState.clearKeepConnected()
    }

    private fun pickBestController(list: List<MediaController>): MediaController? {
        if (list.isEmpty()) return null

        // 1) Prefer actively playing
        val playing = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        if (playing != null) return playing

        // 2) Else prefer paused (Spotify often pauses but still has metadata)
        val paused = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
        if (paused != null) return paused

        // 3) Else any controller that has metadata
        val hasMeta = list.firstOrNull { it.metadata != null }
        if (hasMeta != null) return hasMeta

        return list.first()
    }

    private fun setActiveController(controller: MediaController?) {
        if (active == controller) {
            publish(active)
            return
        }

        try { active?.unregisterCallback(controllerCallback) } catch (_: Throwable) {}

        active = controller

        try { active?.registerCallback(controllerCallback) } catch (_: Throwable) {}

        publish(active)
    }

    private fun publish(controller: MediaController?) {
        if (controller == null) {
            NowPlayingState.clearKeepConnected()
            return
        }

        val md = controller.metadata
        val st = controller.playbackState

        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()

        val duration = (md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 1L).coerceAtLeast(1L)

        val isPlaying = st?.state == PlaybackState.STATE_PLAYING
        val position = (st?.position ?: 0L).coerceIn(0L, duration)

        val pkg = try { controller.packageName ?: "" } catch (_: Throwable) { "" }

        NowPlayingState.update(
            NowPlayingSnapshot(
                title = title,
                artist = artist,
                isPlaying = isPlaying,
                positionMs = position,
                durationMs = duration,
                listenerConnected = true,
                activeControllerPackage = pkg
            )
        )
    }
}
