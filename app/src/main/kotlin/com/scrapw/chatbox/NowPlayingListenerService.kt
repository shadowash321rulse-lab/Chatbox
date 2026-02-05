package com.scrapw.chatbox

import android.app.Notification
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NowPlayingListenerService : NotificationListenerService() {

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
        if (allowedPackages.isNotEmpty() && pkg !in allowedPackages) return

        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        val token = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
        } else null

        if (token == null) return

        try {
            val controller = MediaController(this, token)
            val metadata = controller.metadata
            val pb = controller.playbackState

            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

            val rawPos = pb?.position ?: 0L
            val lastUpdate = pb?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime()
            val speed = pb?.playbackSpeed ?: 1f

            NowPlayingState.update(
                NowPlayingSnapshot(
                    listenerConnected = true,
                    activePackage = pkg,
                    detected = title.isNotBlank() || artist.isNotBlank(),
                    title = title,
                    artist = artist,
                    durationMs = duration,
                    positionMs = rawPos,
                    positionUpdateTimeMs = lastUpdate,
                    playbackSpeed = speed,
                    isPlaying = false // ← DO NOT TRUST THIS
                )
            )
        } catch (_: Throwable) {
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        NowPlayingState.clearIfActivePackage(sbn.packageName ?: "")
    }
}
