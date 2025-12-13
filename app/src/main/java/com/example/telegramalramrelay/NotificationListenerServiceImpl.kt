package com.example.telegramalramrelay

import android.app.Notification
import android.app.Notification.EXTRA_TEXT
import android.app.Notification.EXTRA_TITLE
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import android.content.Intent
import com.example.telegramalramrelay.network.TelegramApi

class NotificationListenerServiceImpl : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        // No diagnostic logging or dedupe here per user's request
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val pkg = sbn.packageName ?: return

            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val forwarding = prefs.getBoolean("forwardingEnabled", true)
            if (!forwarding) return

            val selectedJson = prefs.getString("selected_apps", null)
            if (!selectedJson.isNullOrEmpty()) {
                val arr = JSONArray(selectedJson)
                var selected = false
                for (i in 0 until arr.length()) if (arr.getString(i) == pkg) { selected = true; break }
                if (!selected) return
            } else {
                // no selected apps -> nothing to forward
                return
            }

            val botToken = prefs.getString("botToken", null) ?: return
            val chatId = prefs.getString("chatId", null) ?: return

            val extras = sbn.notification.extras
            val titleCs = extras.getCharSequence(EXTRA_TITLE)
            val title = titleCs?.toString()?.takeIf { it.isNotBlank() } ?: sbn.packageName
            val textCs = extras.getCharSequence(EXTRA_TEXT)
            val text = textCs?.toString()?.takeIf { it.isNotBlank() } ?: "(no text)"

            // Ignore noisy fallback notifications that only carry package name and no text
            if (title == pkg && (text == "(no text)" || text.isBlank())) return

            val fmt = prefs.getString("message_format", "📱 {app}\n📌 {title}\n💬 {text}\n🕐 {time}") ?: "📱 {app}\n📌 {title}\n💬 {text}\n🕐 {time}"

            val appName = try {
                val ai = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(ai).toString()
            } catch (t: Throwable) {
                pkg
            }

            val timeStr = try {
                val sdf = java.text.SimpleDateFormat("yy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                sdf.format(java.util.Date(sbn.postTime))
            } catch (t: Throwable) {
                "${sbn.postTime}"
            }

            val message = fmt
                .replace("{app}", appName)
                .replace("{title}", title)
                .replace("{text}", text)
                .replace("{time}", timeStr)
                .replace("{package}", pkg)

            scope.launch {
                try {
                    TelegramApi.sendTestMessage(botToken, chatId, message)
                } catch (e: Exception) {
                    // intentionally silent
                }
            }

            // Append to recent_notifications (keep max 20)
            try {
                val recentJson = prefs.getString("recent_notifications", "[]") ?: "[]"
                val arr = JSONArray(recentJson)
                val obj = JSONObject()
                obj.put("emoji", "🔔")
                obj.put("app", appName)
                obj.put("message", message.replace("\n", " "))
                obj.put("time", timeStr)
                obj.put("package", pkg)
                // prepend
                val newArr = JSONArray()
                newArr.put(obj)
                for (i in 0 until arr.length()) {
                    if (newArr.length() >= 20) break
                    newArr.put(arr.get(i))
                }
                prefs.edit().putString("recent_notifications", newArr.toString()).apply()
                // increment today's sent counter
                try {
                    val today = prefs.getInt("sent_notifications_today", 0)
                    prefs.edit().putInt("sent_notifications_today", today + 1).apply()
                } catch (_: Exception) {}

                // notify UI to refresh recent/stats
                try {
                    val ctx = applicationContext
                    ctx.sendBroadcast(Intent("com.example.telegramalramrelay.ACTION_RECENT_UPDATED"))
                    ctx.sendBroadcast(Intent("com.example.telegramalramrelay.ACTION_STATS_UPDATED"))
                } catch (_: Exception) {}
            } catch (_: Exception) {
                // ignore persistence errors
            }
        } catch (e: Exception) {
            // intentionally silent on errors
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // no-op
    }
}
