package com.scrapw.chatbox.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.scrapw.chatbox.R

class ChatboxKeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID = "chatbox_keep_alive"
        private const val NOTIF_ID = 4242

        const val ACTION_START = "com.scrapw.chatbox.service.ACTION_START_KEEP_ALIVE"
        const val ACTION_STOP = "com.scrapw.chatbox.service.ACTION_STOP_KEEP_ALIVE"
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startAsForeground()
                acquireWakeLock()
                return START_STICKY
            }
            else -> return START_STICKY
        }
    }

    private fun startAsForeground() {
        createChannelIfNeeded()

        val n: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Chatbox running")
            .setContentText("Keeping OSC updates active while screen is off")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIF_ID, n)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val ch = NotificationChannel(
            CHANNEL_ID,
            "Chatbox Keep Alive",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps OSC sending active while the screen is off"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        nm.createNotificationChannel(ch)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Chatbox:KeepAlive"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Throwable) {
        } finally {
            wakeLock = null
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
