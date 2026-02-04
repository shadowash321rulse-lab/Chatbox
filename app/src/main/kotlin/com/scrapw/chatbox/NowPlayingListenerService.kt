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
import kotlin.math.abs

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

    // -------------------------
    // Movement + state fusion
    // -------------------------
    private var lastTrackKey: String = ""
    private var lastPosMs: Long = 0L
    private var lastElapsedMs: Long = 0L
    private var notMovingStreak: Int = 0

    // Ignore pb.state briefly after track change / seek / skip-back
    private var ignorePbStateUntilElapsedMs: Long = 0L

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
        val extras = notif.extras

        val token: MediaSession.Token? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
        } else null

        if (token == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return

        try {
            val controller = MediaController(this, token)
            val metadata = controller.metadata
            val pb = controller.playbackState

            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

            val detected = title.isNotBlank() || artist.isNotBlank()
            if (!detected) return

            val rawPos = pb?.position ?: 0L
            val lastUpdate = pb?.lastPositionUpdateTime ?: 0L
            val speed = pb?.playbackSpeed ?: 1f
            val pbState = pb?.state ?: PlaybackState.STATE_NONE

            val snapshotUpdateTime = if (lastUpdate > 0L) lastUpdate else SystemClock.elapsedRealtime()
            val nowElapsed = SystemClock.elapsedRealtime()

            val trackKey = buildString {
                append(pkg)
                append('|')
                append(title.trim())
                append('|')
                append(artist.trim())
                append('|')
                append(duration)
            }

            // Detect track changes / skip-back / big seek
            val trackChanged = trackKey != lastTrackKey
            val bigBackwardJump =
                (lastTrackKey.isNotEmpty() && trackKey == lastTrackKey && rawPos > 0L && lastPosMs > 0L && (rawPos + 2000L) < lastPosMs)
            val durationChangedALot =
                (trackKey == lastTrackKey && duration > 0L && abs(duration - extractDurationFromKey(lastTrackKey)) > 2000L)

            if (trackChanged || bigBackwardJump || durationChangedALot) {
                // During this window, ignore "paused" from pb.state (skip/back glitch).
                ignorePbStateUntilElapsedMs = nowElapsed + 1500L
                notMovingStreak = 0
                lastTrackKey = trackKey
                lastPosMs = rawPos
                lastElapsedMs = nowElapsed
            }

            // -------------------------
            // Movement detection
            // -------------------------
            val dt = nowElapsed - lastElapsedMs
            val dp = rawPos - lastPosMs

            // Update baseline whenever we have *any* meaningful time gap.
            // (Old code waited too long and could get stuck in "moving".)
            if (dt >= 350L) {
                val movedEnough = dp >= 250L
                val basicallyStill = dp <= 50L

                if (movedEnough) {
                    notMovingStreak = 0
                } else if (basicallyStill) {
                    notMovingStreak += 1
                } else {
                    // ambiguous small move: don't immediately count as stopped
                    notMovingStreak = 0
                }

                lastElapsedMs = nowElapsed
                lastPosMs = rawPos
                lastTrackKey = trackKey
            }

            val moving = notMovingStreak == 0

            // -------------------------
            // Final play/pause decision (fusion)
            // Priority:
            // 1) If pb.state says PLAYING -> playing
            // 2) If we're in ignore window -> use movement only (avoids skip/back "Paused")
            // 3) If pb.state says PAUSED/STOPPED -> paused (even if tiny position changes)
            // 4) Otherwise fallback to movement
            // -------------------------
            val inIgnoreWindow = nowElapsed < ignorePbStateUntilElapsedMs

            val isPlaying = when {
                pbState == PlaybackState.STATE_PLAYING -> true
                inIgnoreWindow -> moving
                pbState == PlaybackState.STATE_PAUSED -> false
                pbState == PlaybackState.STATE_STOPPED -> false
                pbState == PlaybackState.STATE_NONE -> moving
                else -> moving
            }

            NowPlayingState.update(
                NowPlayingSnapshot(
                    listenerConnected = true,
                    activePackage = pkg,
                    detected = true,
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
            // do nothing
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        NowPlayingState.clearIfActivePackage(sbn.packageName ?: "")
    }

    private fun extractDurationFromKey(key: String): Long {
        val lastBar = key.lastIndexOf('|')
        if (lastBar <= 0 || lastBar == key.length - 1) return 0L
        return key.substring(lastBar + 1).toLongOrNull() ?: 0L
    }
}
