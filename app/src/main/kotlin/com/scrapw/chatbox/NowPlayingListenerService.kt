package com.scrapw.chatbox

import android.app.Notification
import android.media.session.PlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.media.app.NotificationCompat
import android.media.session.MediaSession
import android.media.session.MediaController

/**
 * Listens to media notifications and pushes a parsed "Now Playing" snapshot into NowPlayingState.
 *
 * IMPORTANT:
 * - This file must NOT contain any Compose UI.
 * - Ensure the service is declared in AndroidManifest with BIND_NOTIFICATION_LISTENER_SERVICE.
 */
class NowPlayingListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
        NowPlayingState.update { it.copy(listenerConnected = true) }
        // Try to immediately detect anything already posted
        tryScanActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")
        NowPlayingState.update {
            it.copy(
                listenerConnected = false,
                activePackage = "(none)",
                detected = false,
                title = "",
                artist = "",
                durationMs = 0L,
                positionMs = 0L,
                isPlaying = false
            )
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        // Only care about notifications that look like media
        val n = sbn.notification ?: return
        val isMedia = looksLikeMediaNotification(n)
        if (!isMedia) return

        val parsed = parseNowPlaying(sbn.packageName, n)
        if (parsed != null) {
            NowPlayingState.update { parsed.copy(listenerConnected = true) }
        } else {
            // Mark active package anyway so you can debug
            NowPlayingState.update {
                it.copy(
                    listenerConnected = true,
                    activePackage = sbn.packageName
                )
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)

        // If the active package notification was removed, rescan others
        val current = NowPlayingState.current()
        if (current.activePackage == sbn.packageName) {
            tryScanActiveNotifications()
        }
    }

    private fun tryScanActiveNotifications() {
        val list = activeNotifications ?: return
        var best: NowPlayingSnapshot? = null

        for (sbn in list) {
            val n = sbn.notification ?: continue
            if (!looksLikeMediaNotification(n)) continue
            val parsed = parseNowPlaying(sbn.packageName, n) ?: continue
            // Prefer "playing" over paused
            if (best == null || (parsed.isPlaying && !best!!.isPlaying)) {
                best = parsed
            }
        }

        if (best != null) {
            NowPlayingState.update { best!!.copy(listenerConnected = true) }
        } else {
            NowPlayingState.update {
                it.copy(
                    listenerConnected = true,
                    activePackage = "(none)",
                    detected = false,
                    title = "",
                    artist = "",
                    durationMs = 0L,
                    positionMs = 0L,
                    isPlaying = false
                )
            }
        }
    }

    private fun looksLikeMediaNotification(n: Notification): Boolean {
        // Many players set category=TRANSPORT or have a MediaStyle
        val catOk = n.category == Notification.CATEGORY_TRANSPORT
        val styleOk = NotificationCompat.MediaStyle::class.java.name == n.extras?.getString("android.template")
        val hasMediaSession = getMediaSessionToken(n) != null
        return catOk || styleOk || hasMediaSession
    }

    private fun parseNowPlaying(pkg: String, n: Notification): NowPlayingSnapshot? {
        // First try media session token (best quality)
        val token = getMediaSessionToken(n)
        if (token != null) {
            val snap = parseFromMediaSession(pkg, token)
            if (snap != null) return snap
        }

        // Fallback: read title/text from extras (works for many apps)
        val extras = n.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()

        // Heuristic split:
        // Sometimes title=Song, text=Artist ; sometimes opposite. We'll do best effort.
        val song = title.ifBlank { text }
        val artist = if (song == title) text else title

        if (song.isBlank() && artist.isBlank() && sub.isBlank()) return null

        return NowPlayingSnapshot(
            listenerConnected = true,
            activePackage = pkg,
            detected = true,
            title = song.ifBlank { title },
            artist = artist.ifBlank { sub },
            durationMs = 0L,
            positionMs = 0L,
            isPlaying = true // unknown; assume playing if notification is present
        )
    }

    private fun parseFromMediaSession(pkg: String, token: Any): NowPlayingSnapshot? {
        return try {
            val controller = when (token) {
                is MediaSession.Token -> MediaController(this, token)
                else -> null
            } ?: return null

            val meta = controller.metadata
            val state = controller.playbackState

            val title = meta?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
            val artist = meta?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)?.trim().orEmpty()
            val dur = meta?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 0L

            val isPlaying = state?.state == PlaybackState.STATE_PLAYING
            val pos = state?.position ?: 0L

            if (title.isBlank() && artist.isBlank()) return null

            NowPlayingSnapshot(
                listenerConnected = true,
                activePackage = pkg,
                detected = true,
                title = title,
                artist = artist,
                durationMs = dur,
                positionMs = pos,
                isPlaying = isPlaying
            )
        } catch (t: Throwable) {
            Log.w(TAG, "parseFromMediaSession failed: ${t.message}")
            null
        }
    }

    private fun getMediaSessionToken(n: Notification): Any? {
        return try {
            // AndroidX media style attaches a token under this key:
            n.extras?.getParcelable("android.mediaSession")
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        private const val TAG = "NowPlayingListener"
    }
}
