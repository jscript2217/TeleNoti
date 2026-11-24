package com.example.telegramalramrelay.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.telegramalramrelay.data.SettingsRepository

class ResetAtNoonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        try {
            val appCtx = context.applicationContext
            SettingsRepository.getInstance(appCtx).resetDailyStats()
            DailyResetScheduler.scheduleNextReset(appCtx)
        } catch (_: Exception) {}
    }
}
