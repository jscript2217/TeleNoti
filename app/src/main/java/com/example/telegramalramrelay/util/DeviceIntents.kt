package com.example.telegramalramrelay.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Attempts to open the best available battery-optimization / ignore-optimizations screen.
 * Returns true if an activity was started, false otherwise.
 */
fun openBatteryOptimizationSettings(context: Context): Boolean {
    try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = context.packageName

        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Exception) {}
        } else {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Exception) {}
        }

        if (openAppDetailsSettings(context)) return true
    } catch (_: Exception) {}
    return false
}

fun openNotificationListenerSettings(context: Context): Boolean {
    return try {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            true
        } else {
            openAppDetailsSettings(context)
        }
    } catch (_: Exception) {
        false
    }
}

fun openAppDetailsSettings(context: Context): Boolean {
    return try {
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        if (details.resolveActivity(context.packageManager) != null) {
            context.startActivity(details)
            true
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }
}

fun openNotificationChannelSettings(context: Context, channelId: String) {
    try {
        val pm = context.packageManager
        val intent = Intent().apply {
            action = Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (intent.resolveActivity(pm) != null) {
            context.startActivity(intent)
            return
        }

        val fallback = Intent().apply {
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (fallback.resolveActivity(pm) != null) {
            context.startActivity(fallback)
            return
        }

        openAppDetailsSettings(context)
    } catch (_: Exception) {}
}
