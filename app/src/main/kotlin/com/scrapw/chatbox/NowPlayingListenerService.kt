package com.scrapw.chatbox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    private val mainHandler = Handler(Looper.getMainLooper())

    // If the media notification disappears briefly (screen off / OEM / skip spam),
    // don't instantly clear the state — wait a bit.
    private val clearDelayMs = 6_000L
    private var pendingClearPkg: String? = null
    private val clearRunnable = Runnable {
        val pkg = pendingClearPkg ?: return@Runnable
        NowPlayingState.clearIfActivePackage(pkg)
        pendingClearPkg = null
    }

    private val channelId = "vrca_now_playing_keepalive"
    private val fgNotifId = 10042

    override fun onListenerConnected() {
        super.onListenerConnected()
        NowPlayingState.setConnected(true)
        startKeepAliveForeground()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NowPlayingState.setConnected(false)
        stopKeepAliveForeground()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        // If you want to accept ANY media app, comment out this allowlist block.
        if (allowedPackages.isNotEmpty() && pkg !in allowedPackages) return

        // HARD FILTER: only accept real media notifications (must have a MediaSession token)
        val token: MediaSession.Token = getMediaSessionToken(extras) ?: return

        // If we were about to clear this package, cancel it — we got a fresh media notif.
        if (pendingClearPkg == pkg) {
            mainHandler.removeCallbacks(clearRunnable)
            pendingClearPkg = null
        }

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

            // Keepalive notification stays up while listener is connected; refresh it lightly.
            // (Some OEMs are picky — keeping it "active" helps.)
            startKeepAliveForeground()

        } catch (_: Throwable) {
            // If MediaController fails, do nothing (don’t fall back to non-media notifications).
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return

        // Don’t instantly wipe. Some devices remove media notifs when screen turns off.
        // Schedule a clear, and cancel if we get a new post.
        pendingClearPkg = pkg
        mainHandler.removeCallbacks(clearRunnable)
        mainHandler.postDelayed(clearRunnable, clearDelayMs)
    }

    private fun getMediaSessionToken(extras: Bundle): MediaSession.Token? {
        return if (Build.VERSION.SDK_INT >= 33) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        }
    }

    private fun startKeepAliveForeground() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(
                    channelId,
                    "VRC-A Keep Alive",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps Now Playing active when the screen is off."
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
                nm.createNotificationChannel(ch)
            }

            val launchIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val piFlags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                else
                    PendingIntent.FLAG_UPDATE_CURRENT

            val pi = PendingIntent.getActivity(this, 0, launchIntent, piFlags)

            val builder =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Notification.Builder(this, channelId)
                } else {
                    @Suppress("DEPRECATION")
                    Notification.Builder(this)
                }

            val n = builder
                .setContentTitle("VRC-A running")
                .setContentText("Keeping Now Playing active (screen-off safe).")
                .setSmallIcon(R.mipmap.ic_launcher) // uses your launcher icon
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .build()

            // Start/refresh foreground
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(fgNotifId, n, ServiceInfoCompat.mediaPlaybackType())
            } else {
                startForeground(fgNotifId, n)
            }
        } catch (_: Throwable) {
            // If anything fails, don't crash the listener.
        }
    }

    private fun stopKeepAliveForeground() {
        try {
            stopForeground(true)
        } catch (_: Throwable) {
        }
    }
}

/**
 * Small helper to avoid hard-crashing on API differences for foreground types.
 * Kept inside the same file to match your “full replacement” workflow.
 */
private object ServiceInfoCompat {
    fun mediaPlaybackType(): Int {
        return if (Build.VERSION.SDK_INT >= 29) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else 0
    }
}
