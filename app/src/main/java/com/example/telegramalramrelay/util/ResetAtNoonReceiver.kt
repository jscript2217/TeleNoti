package com.example.telegramalramrelay.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class ResetAtNoonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        try {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("recent_notifications", "[]")
                .putInt("sent_notifications_today", 0)
                .apply()

            // Broadcast updates so UI refreshes
            try {
                val recentIntent = Intent("com.example.telegramalramrelay.ACTION_RECENT_UPDATED")
                context.sendBroadcast(recentIntent)
                val statsIntent = Intent("com.example.telegramalramrelay.ACTION_STATS_UPDATED")
                context.sendBroadcast(statsIntent)
            } catch (_: Exception) {}

            // Reschedule next day's alarm
            DailyResetScheduler.scheduleDailyNoonReset(context)

            // Optional user-facing toast to indicate reset occurred
            try {
                Toast.makeText(context, "오늘 전송된 알림을 초기화했습니다.", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }
}
