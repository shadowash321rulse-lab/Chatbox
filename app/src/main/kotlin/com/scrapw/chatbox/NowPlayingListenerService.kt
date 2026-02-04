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

    // Optional: only accept these apps (keeps it clean).
    // If you want ANY media app, set this to emptySet().
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
    // Movement-based play detect (stable)
    // -------------------------
    private var lastTrackKey: String = ""
    private var lastPosMs: Long = 0L
    private var lastElapsedMs: Long = 0L

    // Debounce: require multiple "not moving" reads before we call it paused
    private var notMovingStreak: Int = 0

    // When track changes / seeking happens, some apps briefly report paused.
    // This grace window prevents incorrect "Paused" in OSC.
    private var forcePlayingUntilElapsedMs: Long = 0L

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

            // If we didn't detect anything meaningful, don't overwrite good state with blanks.
            if (!detected) return

            val rawPos = pb?.position ?: 0L
            val lastUpdate = pb?.lastPositionUpdateTime ?: 0L
            val speed = pb?.playbackSpeed ?: 1f

            // lastPositionUpdateTime is elapsedRealtime-based.
            val snapshotUpdateTime = if (lastUpdate > 0L) lastUpdate else SystemClock.elapsedRealtime()
            val nowElapsed = SystemClock.elapsedRealtime()

            // Track identity key (duration included so same title doesn't stick across different tracks)
            val trackKey = buildString {
                append(pkg)
                append('|')
                append(title.trim())
                append('|')
                append(artist.trim())
                append('|')
                append(duration)
            }

            // Detect track change / large seek reset and apply a short grace window.
            val trackChanged = trackKey != lastTrackKey
            val bigBackwardJump = (lastTrackKey.isNotEmpty() && trackKey == lastTrackKey && rawPos > 0L && lastPosMs > 0L && (rawPos + 2000L) < lastPosMs)
            val durationChangedALot = (trackKey == lastTrackKey && duration > 0L && abs(duration - extractDurationFromKey(lastTrackKey)) > 2000L)

            if (trackChanged || bigBackwardJump || durationChangedALot) {
                // ~1.5s grace is enough for most apps to settle after skip/back/seek
                forcePlayingUntilElapsedMs = nowElapsed + 1500L
                notMovingStreak = 0
                // Reset movement baseline to avoid mis-detect on first update
                lastPosMs = rawPos
                lastElapsedMs = nowElapsed
                lastTrackKey = trackKey
            }

            // Movement check (ignore pb.state)
            val dt = nowElapsed - lastElapsedMs
            val dp = rawPos - lastPosMs

            // Only evaluate movement if enough time has passed; otherwise keep last result.
            var moving = false
            if (dt >= 700L) {
                // "Moving" if position advances a meaningful amount
                if (dp >= 250L) {
                    moving = true
                    notMovingStreak = 0
                } else {
                    // treat as "not moving" only if it basically didn't advance
                    if (dp <= 50L) {
                        notMovingStreak += 1
                    } else {
                        // tiny/uncertain movement: don't instantly call paused
                        notMovingStreak = 0
                    }
                    moving = notMovingStreak < 2
                }

                lastElapsedMs = nowElapsed
                lastPosMs = rawPos
                lastTrackKey = trackKey
            } else {
                // Not enough time to judge; keep whatever we had (but don't force paused)
                moving = notMovingStreak < 2
            }

            val isPlayingByMovement = moving
            val isPlaying = (nowElapsed < forcePlayingUntilElapsedMs) || isPlayingByMovement

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
            // If MediaController fails, do nothing.
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        NowPlayingState.clearIfActivePackage(sbn.packageName ?: "")
    }

    private fun extractDurationFromKey(key: String): Long {
        // key format: pkg|title|artist|duration
        val lastBar = key.lastIndexOf('|')
        if (lastBar <= 0 || lastBar == key.length - 1) return 0L
        return key.substring(lastBar + 1).toLongOrNull() ?: 0L
    }
}
