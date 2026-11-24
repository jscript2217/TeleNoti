package com.example.telegramalramrelay.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.telegramalramrelay.data.SettingsRepository
import java.util.Calendar

object DailyResetScheduler {
    fun scheduleDailyNoonReset(context: Context) {
        scheduleNextReset(context)
    }

    fun scheduleNextReset(context: Context) {
        try {
            val appCtx = context.applicationContext
            val config = SettingsRepository.getInstance(appCtx).currentSettings().dailyReset
            if (!config.enabled) {
                cancelScheduledReset(appCtx)
                return
            }

            val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(appCtx, ResetAtNoonReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                appCtx,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val now = Calendar.getInstance()
            val next = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, config.hour.coerceIn(0, 23))
                set(Calendar.MINUTE, config.minute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now.timeInMillis) add(Calendar.DATE, 1)
            }

            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (canExact) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        next.timeInMillis,
                        pending
                    )
                } catch (_: SecurityException) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        next.timeInMillis,
                        pending
                    )
                }
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    next.timeInMillis,
                    pending
                )
            }
        } catch (_: Exception) {}
    }

    fun cancelScheduledReset(context: Context) {
        try {
            val appCtx = context.applicationContext
            val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(appCtx, ResetAtNoonReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                appCtx,
                0,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pending != null) {
                alarmManager.cancel(pending)
                pending.cancel()
            }
        } catch (_: Exception) {}
    }
}
