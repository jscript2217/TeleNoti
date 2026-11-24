package com.example.telegramalramrelay.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.telegramalramrelay.MainActivity
import com.example.telegramalramrelay.data.SettingsRepository

class TeleNotiTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        if (!hasNotificationAccess()) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pending = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        val repo = SettingsRepository.getInstance(applicationContext)
        repo.setForwardingEnabled(!repo.currentSettings().forwardingEnabled)
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val settings = SettingsRepository.getInstance(applicationContext).currentSettings()
        val hasAccess = hasNotificationAccess()

        tile.label = "텔레노티"
        when {
            !hasAccess -> {
                tile.state = Tile.STATE_UNAVAILABLE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "권한 필요"
                }
            }
            settings.forwardingEnabled -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "작동 중 · ${settings.sentToday}건"
                }
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "일시 중지"
                }
            }
        }
        tile.updateTile()
    }

    private fun hasNotificationAccess(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(
                contentResolver,
                "enabled_notification_listeners"
            ) ?: ""
            enabled.contains(packageName)
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            try {
                requestListeningState(
                    context.applicationContext,
                    ComponentName(context.applicationContext, TeleNotiTileService::class.java)
                )
            } catch (_: Exception) {}
        }
    }
}
