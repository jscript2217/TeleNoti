package com.example.telegramalramrelay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.telegramalramrelay.R

class AppKeepAliveService : Service() {
    companion object {
        var isRunning = false
    }

    private val CHANNEL_ID = "telegramalramrelay_service"
    private val ACTION_STOP = "com.example.telegramalramrelay.ACTION_STOP"
    private val ACTION_UPDATE_PIN = "com.example.telegramalramrelay.ACTION_UPDATE_PIN"

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()

        try {
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            val pin = prefs.getBoolean("pinNotification", false)
            val notification = createNotification(pin)
            // Always start foreground with ongoing notification so it cannot be swiped away while service runs
            startForeground(1, notification)
            Log.d("AppKeepAliveService", "startForeground called, pin=$pin")
        } catch (e: Exception) {
            try { Log.w("AppKeepAliveService", "startForeground failed: ${e.message}") } catch (_: Exception) {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure the service remains running; request restart if killed
        isRunning = true
        // Handle stop action from notification
        val action = intent?.action
        if (action == ACTION_STOP) {
            Log.d("AppKeepAliveService", "Received ACTION_STOP, stopping service")
            try { stopForeground(true) } catch (_: Exception) {}
            stopSelf()
            isRunning = false
            return START_NOT_STICKY
        } else if (action == ACTION_UPDATE_PIN) {
            try {
                val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                val pin = prefs.getBoolean("pinNotification", false)
                val notification = createNotification(pin)
                // Update posted notification and adjust foreground/dismissible state
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(1, notification)
                try {
                    startForeground(1, notification)
                } catch (_: Exception) {}
                Log.d("AppKeepAliveService", "ACTION_UPDATE_PIN applied, pin=$pin")
            } catch (e: Exception) {
                Log.w("AppKeepAliveService", "ACTION_UPDATE_PIN failed: ${e.message}")
            }
            return START_STICKY
        }

        // If for some reason foreground wasn't started in onCreate (rare), ensure notification
        try {
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            val pin = prefs.getBoolean("pinNotification", false)
            val notification = createNotification(pin)
            startForeground(1, notification)
            Log.d("AppKeepAliveService", "onStartCommand startForeground called, pin=$pin")
        } catch (_: Exception) {}

        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        try { stopForeground(true) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(CHANNEL_ID, "텔레노티 서비스", NotificationManager.IMPORTANCE_LOW)
            chan.description = "텔레노티의 백그라운드 서비스 알림 채널"
            chan.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(chan)
            Log.d("AppKeepAliveService", "notification channel created: ${chan.id} importance=${chan.importance}")
        }
    }

    private fun createNotification(pin: Boolean): android.app.Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPending = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val stopIntent = Intent(this, AppKeepAliveService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this,
            0,
            stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("텔레노티 서비스")
            .setContentText("알림 포워딩 서비스중")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(mainPending)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Make notification ongoing/un-dismissible while service runs
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "중지", stopPending)
            .build()
    }
}
