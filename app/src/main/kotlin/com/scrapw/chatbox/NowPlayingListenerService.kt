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

    private data class ControllerEntry(
        val controller: MediaController,
        val callback: MediaController.Callback
    )

    private val controllersByPackage = HashMap<String, ControllerEntry>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        NowPlayingState.setConnected(true)

        // Prime state from currently active notifications so the UI works immediately
        try {
            activeNotifications?.forEach { sbn ->
                onNotificationPosted(sbn)
            }
        } catch (_: Throwable) {
            // ignore
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NowPlayingState.setConnected(false)
        teardownAllControllers()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        if (allowedPackages.isNotEmpty() && pkg !in allowedPackages) return

        val token = getMediaSessionToken(extras) ?: return

        // Ensure we are listening to this session via MediaController callbacks.
        // This is the key fix: skips/back/pauses often do NOT cause a fresh "posted" notification.
        ensureControllerForPackage(pkg, token)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return

        teardownController(pkg)
        NowPlayingState.clearIfActivePackage(pkg)
    }

    // ---- MediaController wiring ----

    private fun ensureControllerForPackage(pkg: String, token: MediaSession.Token) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return

        // Always recreate on every post to avoid stale tokens/sessions.
        teardownController(pkg)

        try {
            val controller = MediaController(this, token)

            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    pushSnapshot(pkg, controller.metadata, state)
                }

                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    pushSnapshot(pkg, metadata, controller.playbackState)
                }

                override fun onSessionDestroyed() {
                    teardownController(pkg)
                    NowPlayingState.clearIfActivePackage(pkg)
                }
            }

            controller.registerCallback(cb)
            controllersByPackage[pkg] = ControllerEntry(controller, cb)

            // Push an immediate snapshot so UI/OSC updates right away.
            pushSnapshot(pkg, controller.metadata, controller.playbackState)
        } catch (_: Throwable) {
            // If MediaController fails, do nothing (don’t fall back to non-media notifications).
        }
    }

    private fun teardownController(pkg: String) {
        val entry = controllersByPackage.remove(pkg) ?: return
        try {
            entry.controller.unregisterCallback(entry.callback)
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun teardownAllControllers() {
        val keys = controllersByPackage.keys.toList()
        keys.forEach { teardownController(it) }
        controllersByPackage.clear()
    }

    private fun pushSnapshot(
        pkg: String,
        metadata: MediaMetadata?,
        pb: PlaybackState?
    ) {
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

        val rawPos = pb?.position ?: 0L
        val lastUpdate = pb?.lastPositionUpdateTime ?: 0L
        val speed = pb?.playbackSpeed ?: 1f

        val isPlaying = pb?.state == PlaybackState.STATE_PLAYING

        // lastPositionUpdateTime is based on elapsedRealtime.
        val snapshotUpdateTime =
            if (lastUpdate > 0L) lastUpdate else SystemClock.elapsedRealtime()

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
