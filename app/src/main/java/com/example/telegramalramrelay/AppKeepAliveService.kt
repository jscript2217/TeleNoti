package com.example.telegramalramrelay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.telegramalramrelay.data.SettingsRepository
import com.example.telegramalramrelay.data.TeleNotiSettings
import com.example.telegramalramrelay.network.TelegramReplyPoller
import com.example.telegramalramrelay.service.TeleNotiTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar

class AppKeepAliveService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val TAG = "AppKeepAliveService"
        private const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "telegramalramrelay_service"
        const val ACTION_STOP = "com.example.telegramalramrelay.ACTION_STOP"
        const val ACTION_UPDATE_PIN = "com.example.telegramalramrelay.ACTION_UPDATE_PIN"
        const val ACTION_TOGGLE_FORWARDING = "com.example.telegramalramrelay.ACTION_TOGGLE_FORWARDING"

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

        var isRunning: Boolean
            get() = _isRunningFlow.value
            set(value) {
                _isRunningFlow.value = value
            }

        fun start(context: Context) {
            val intent = Intent(context, AppKeepAliveService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            val stopIntent = Intent(appCtx, AppKeepAliveService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                appCtx.startService(stopIntent)
            } catch (_: Exception) {
                try {
                    appCtx.stopService(Intent(appCtx, AppKeepAliveService::class.java))
                } catch (_: Exception) {}
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        val repo = SettingsRepository.getInstance(applicationContext)
        promoteToForeground(repo.currentSettings(), NotificationListenerServiceImpl.isConnected.value)
        TelegramReplyPoller.syncState(applicationContext)

        serviceScope.launch {
            combine(
                repo.settings,
                NotificationListenerServiceImpl.isConnected
            ) { settings, connected ->
                settings to connected
            }.collect { (settings, connected) ->
                updatePostedNotification(settings, connected)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repo = SettingsRepository.getInstance(applicationContext)
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "Received ACTION_STOP, stopping service")
                isRunning = false
                try {
                    repo.setBackgroundEnabled(false)
                } catch (_: Exception) {}
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_TOGGLE_FORWARDING -> {
                val next = !repo.currentSettings().forwardingEnabled
                repo.setForwardingEnabled(next)
                TeleNotiTileService.requestUpdate(applicationContext)
                promoteToForeground(repo.currentSettings(), NotificationListenerServiceImpl.isConnected.value)
                return START_STICKY
            }

            ACTION_UPDATE_PIN -> {
                promoteToForeground(repo.currentSettings(), NotificationListenerServiceImpl.isConnected.value)
                return START_STICKY
            }
        }

        isRunning = true
        promoteToForeground(repo.currentSettings(), NotificationListenerServiceImpl.isConnected.value)
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        if (!NotificationListenerServiceImpl.isConnected.value) {
            TelegramReplyPoller.stop()
        }
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteToForeground(settings: TeleNotiSettings, connected: Boolean) {
        try {
            val notification = createNotification(settings, connected)
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed: ${e.message}")
        }
    }

    private fun updatePostedNotification(settings: TeleNotiSettings, connected: Boolean) {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, createNotification(settings, connected))
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID,
                "텔레노티 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "텔레노티의 실시간 알림 포워딩 상태 표시 채널"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(chan)
        }
    }

    private fun createNotification(
        settings: TeleNotiSettings,
        connected: Boolean
    ): android.app.Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPending = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleIntent = Intent(this, AppKeepAliveService::class.java).apply {
            action = ACTION_TOGGLE_FORWARDING
        }
        val togglePending = PendingIntent.getService(
            this,
            1,
            toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, AppKeepAliveService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val hasNotifAccess = try {
            val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
            enabled.contains(packageName)
        } catch (_: Exception) {
            false
        }

        val cal = Calendar.getInstance()
        val inQuietNow = settings.quietHours.isQuietAt(
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )

        val title = when {
            !hasNotifAccess -> "텔레노티 · 권한 확인 필요"
            !settings.forwardingEnabled -> "텔레노티 · 일시 중지됨"
            inQuietNow -> "텔레노티 · 방해금지 중 (오늘 ${settings.sentToday}건)"
            connected -> "텔레노티 · 포워딩 작동 중 (오늘 ${settings.sentToday}건)"
            else -> "텔레노티 · 연결 대기 중"
        }

        val lastItem = settings.recentNotifications.firstOrNull()
        val body = buildString {
            append("감시 앱 ${settings.selectedPackages.size}개")
            if (lastItem != null) {
                append(" · 최근: ${lastItem.appName} (${lastItem.timeDisplay})")
            }
        }

        val toggleIcon = if (settings.forwardingEnabled) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val toggleLabel = if (settings.forwardingEnabled) "일시 중지" else "전송 재개"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_stat_telenoti)
            .setContentIntent(mainPending)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(toggleIcon, toggleLabel, togglePending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "서비스 끄기", stopPending)
            .build()
    }
}
