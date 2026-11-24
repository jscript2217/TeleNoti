package com.example.telegramalramrelay.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.util.Log
import com.example.telegramalramrelay.AppKeepAliveService
import com.example.telegramalramrelay.NotificationListenerServiceImpl
import com.example.telegramalramrelay.data.SettingsRepository
import com.example.telegramalramrelay.util.DailyResetScheduler
import com.example.telegramalramrelay.worker.RetryDeliveryWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val appCtx = context.applicationContext
        val repo = SettingsRepository.getInstance(appCtx)
        repo.checkAndApplyDailyResetIfDue()
        DailyResetScheduler.scheduleNextReset(appCtx)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                NotificationListenerService.requestRebind(
                    ComponentName(appCtx, NotificationListenerServiceImpl::class.java)
                )
            } catch (_: Exception) {}
        }

        if (repo.getPendingRetryItems().isNotEmpty()) {
            try {
                RetryDeliveryWorker.enqueue(appCtx)
            } catch (_: Exception) {}
        }

        if (repo.currentSettings().backgroundEnabled) {
            try {
                AppKeepAliveService.start(appCtx)
            } catch (e: Exception) {
                Log.w("BootReceiver", "Could not start foreground service on boot/update: ${e.message}")
            }
        }
    }
}
