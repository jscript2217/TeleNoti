package com.example.telegramalramrelay.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

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
                // Opening IGNORE_BATTERY_OPTIMIZATION_SETTINGS
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
                // Requesting REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Exception) {}
        }

        // Fallback: open application details
        try {
            val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            if (details.resolveActivity(context.packageManager) != null) {
                context.startActivity(details)
                return true
            }
        } catch (_: Exception) {}
    } catch (_: Exception) {}
    return false
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

        // Fallback: app notification settings
        val fallback = Intent().apply {
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (fallback.resolveActivity(pm) != null) { context.startActivity(fallback); return }

        // Final fallback: app details
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (details.resolveActivity(pm) != null) context.startActivity(details)
    } catch (_: Exception) {}
}
