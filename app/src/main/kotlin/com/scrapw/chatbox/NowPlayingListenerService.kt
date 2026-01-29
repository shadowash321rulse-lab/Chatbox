package com.scrapw.chatbox

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification

class NowPlayingListenerService : NotificationListenerService() {

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

        val pkg = sbn.packageName ?: ""
        val notif = sbn.notification ?: return
        val extras = notif.extras

        // 1) Best path: MediaSession token -> MediaController (gives progress + duration)
        val token: MediaSession.Token? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
        } else {
            null
        }

        if (token != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val controller = MediaController(this, token)
                val metadata = controller.metadata
                val pb = controller.playbackState

                val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
                val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
                val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

                val position = pb?.position ?: 0L
                val isPlaying = pb?.state == PlaybackState.STATE_PLAYING

                val detected = title.isNotBlank() || artist.isNotBlank()

                NowPlayingState.update(
                    NowPlayingSnapshot(
                        listenerConnected = true,
                        activePackage = pkg,
                        detected = detected,
                        title = title,
                        artist = artist,
                        durationMs = duration,
                        positionMs = position,
                        isPlaying = isPlaying
                    )
                )
                return
            } catch (_: Throwable) {
                // Fall through to extras parsing
            }
        }

        // 2) Fallback: parse notification text fields (no reliable duration/progress)
        val titleFallback = (extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()).orEmpty()
        val textFallback = (extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()).orEmpty()

        val detectedFallback = titleFallback.isNotBlank() || textFallback.isNotBlank()

        // Heuristic: title = song, artist = text if it looks short enough.
        val title = titleFallback
        val artist = if (textFallback.length <= 60) textFallback else ""

        NowPlayingState.update(
            NowPlayingSnapshot(
                listenerConnected = true,
                activePackage = pkg,
                detected = detectedFallback,
                title = title,
                artist = artist,
                durationMs = 0L,
                positionMs = 0L,
                isPlaying = true // unknown; assume playing if notification is active
            )
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        NowPlayingState.clearIfActivePackage(sbn.packageName ?: "")
    }
}
