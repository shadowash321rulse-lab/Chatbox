package com.scrapw.chatbox.nowplaying

import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log

class NowPlayingListenerService : NotificationListenerService() {

    private var msm: MediaSessionManager? = null
    private var activeController: MediaController? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        pickBestController(controllers)
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            pushCurrent()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            pushCurrent()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            msm = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            msm?.addOnActiveSessionsChangedListener(sessionsListener, null)

            val controllers = msm?.getActiveSessions(null).orEmpty()
            pickBestController(controllers)
            pushCurrent()
        } catch (t: Throwable) {
            Log.e("NowPlayingListener", "Failed to connect", t)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        try {
            msm?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Throwable) { }
        detachController()
        NowPlayingState.clear()
    }

    private fun pickBestController(controllers: List<MediaController>) {
        // Prefer a playing controller; otherwise first one
        val best = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()

        if (best?.sessionToken == activeController?.sessionToken) return

        detachController()
        activeController = best
        activeController?.registerCallback(controllerCallback)
        pushCurrent()
    }

    private fun detachController() {
        try {
            activeController?.unregisterCallback(controllerCallback)
        } catch (_: Throwable) { }
        activeController = null
    }

    private fun pushCurrent() {
        val c = activeController ?: run {
            NowPlayingState.clear()
            return
        }

        val md = c.metadata
        val st = c.playbackState

        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 1L

        val pos = st?.position ?: 0L
        val isPlaying = st?.state == PlaybackState.STATE_PLAYING

        NowPlayingState.update(
            NowPlaying(
                isPlaying = isPlaying,
                artist = artist,
                title = title,
                positionMs = pos.coerceAtLeast(0L),
                durationMs = duration.coerceAtLeast(1L)
            )
        )
    }
}

