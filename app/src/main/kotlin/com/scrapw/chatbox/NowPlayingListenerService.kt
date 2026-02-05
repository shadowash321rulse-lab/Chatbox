package com.scrapw.chatbox

import android.app.Notification
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NowPlayingListenerService : NotificationListenerService() {

    // Optional: only accept these apps (keeps it clean).
    private val allowedPackages = setOf(
        "com.spotify.music",
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.apple.android.music",
        "deezer.android.app",
        "com.soundcloud.android",
        "com.amazon.mp3",
        "com.bandcamp.android"
    )

    override fun onListenerConnected() {
        super.onListenerConnected()
        NowPlayingState.setConnected(true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NowPlayingState.setConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        // If you want to accept ANY media app, comment out this allowlist block.
        if (allowedPackages.isNotEmpty() && pkg !in allowedPackages) return

        // HARD FILTER: only accept real media notifications (must have a MediaSession token)
        val token = getMediaSessionToken(extras) ?: return

        try {
            val controller = MediaController(this, token)
            val metadata = controller.metadata
            val pb = controller.playbackState

            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

            val rawPos = pb?.position ?: 0L
            val lastUpdate = pb?.lastPositionUpdateTime ?: 0L
            val speed = pb?.playbackSpeed ?: 1f

            // Raw PlaybackState flag (VM can infer paused from movement if needed)
            val isPlaying = pb?.state == PlaybackState.STATE_PLAYING

            // lastPositionUpdateTime is based on elapsedRealtime.
            val snapshotUpdateTime = if (lastUpdate > 0L) lastUpdate else SystemClock.elapsedRealtime()

            val detected = title.isNotBlank() || artist.isNotBlank()

            NowPlayingState.update(
                NowPlayingSnapshot(
                    listenerConnected = true,
                    activePackage = pkg,
                    detected = detected,
                    title = title,
                    artist = artist,
                    durationMs = duration,
                    positionMs = rawPos,
                    positionUpdateTimeMs = snapshotUpdateTime,
                    playbackSpeed = speed,
                    isPlaying = isPlaying
                )
            )
        } catch (_: Throwable) {
            // If MediaController fails, do nothing (don’t fall back to non-media notifications).
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        NowPlayingState.clearIfActivePackage(sbn.packageName ?: "")
    }

    private fun getMediaSessionToken(extras: android.os.Bundle): MediaSession.Token? {
        return if (Build.VERSION.SDK_INT >= 33) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        }
    }
}
