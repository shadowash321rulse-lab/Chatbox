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

    // Last known snapshot pieces (for skip detection)
    private var lastTitle: String = ""
    private var lastArtist: String = ""
    private var lastDuration: Long = 0L
    private var lastPosition: Long = 0L

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
        val extras = notif.extras

        if (allowedPackages.isNotEmpty() && pkg !in allowedPackages) return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return

        val token: android.media.session.MediaSession.Token? =
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)

        if (token == null) return

        try {
            val controller = MediaController(this, token)
            val metadata = controller.metadata
            val pb = controller.playbackState

            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

            val isPlaying = pb?.state == PlaybackState.STATE_PLAYING

            val position = pb?.position ?: 0L
            val lastUpdate =
                pb?.lastPositionUpdateTime?.takeIf { it > 0 }
                    ?: SystemClock.elapsedRealtime()

            val speed = pb?.playbackSpeed ?: 1f

            // ===== SKIP DETECTION =====
            val titleChanged = title.isNotBlank() && title != lastTitle
            val artistChanged = artist.isNotBlank() && artist != lastArtist
            val durationChanged = duration > 0 && duration != lastDuration

            // Position jumped backwards significantly → likely new track
            val positionReset =
                position < lastPosition &&
                        (lastPosition - position) > 5_000L

            val detected = titleChanged || artistChanged || durationChanged || positionReset

            // Update memory
            lastTitle = title
            lastArtist = artist
            lastDuration = duration
            lastPosition = position

            NowPlayingState.update(
                NowPlayingSnapshot(
                    listenerConnected = true,
                    activePackage = pkg,
                    detected = detected || title.isNotBlank() || artist.isNotBlank(),
                    title = title,
                    artist = artist,
                    durationMs = duration,
                    positionMs = position,
                    positionUpdateTimeMs = lastUpdate,
                    playbackSpeed = speed,
                    isPlaying = isPlaying
                )
            )
        } catch (_: Throwable) {
            // ignore bad controllers
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        NowPlayingState.clearIfActivePackage(sbn.packageName ?: "")
    }
}
