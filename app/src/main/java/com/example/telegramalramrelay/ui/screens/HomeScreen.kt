package com.example.telegramalramrelay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // One-time legacy cleanup: only run once to avoid accidentally clearing real data
    LaunchedEffect(Unit) {
        try {
            val didCleanup = prefs.getBoolean("legacy_cleanup_done", false)
            if (!didCleanup) {
                val existing = prefs.getString("recent_notifications", null)
                if (!existing.isNullOrEmpty()) {
                    val lowered = existing
                    if (lowered.contains("카카오톡") || lowered.contains("Gmail") || lowered.contains("Instagram") || lowered.contains("새 메시지가 도착했습니다")) {
                        prefs.edit()
                            .putString("recent_notifications", "[]")
                            .putInt("connected_apps", 0)
                            .putInt("sent_notifications_today", 0)
                            .putBoolean("legacy_cleanup_done", true)
                            .apply()
                    } else {
                        prefs.edit().putBoolean("legacy_cleanup_done", true).apply()
                    }
                } else {
                    prefs.edit().putBoolean("legacy_cleanup_done", true).apply()
                }
            }
        } catch (_: Exception) {}
    }

    // Do not seed sample notifications on startup — show empty state when none exist

    var recentJson by remember { mutableStateOf(prefs.getString("recent_notifications", "[]") ?: "[]") }
    val recentList = remember(recentJson) { parseRecentNotifications(recentJson) }
    val connectedApps = prefs.getInt("connected_apps", 0)
    var sentToday by remember { mutableStateOf(prefs.getInt("sent_notifications_today", 0)) }

    // Always refresh recent notifications and today's counter when the screen is entered
    LaunchedEffect(Unit) {
        try {
            recentJson = prefs.getString("recent_notifications", "[]") ?: "[]"
            sentToday = prefs.getInt("sent_notifications_today", 0)
        } catch (_: Exception) {}
    }
    // Schedule daily reset at noon
    LaunchedEffect(Unit) {
        try {
            com.example.telegramalramrelay.util.DailyResetScheduler.scheduleDailyNoonReset(context)
        } catch (_: Exception) {}
    }

    // Receiver to update recent list and stats in real-time
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                try {
                    when (intent?.action) {
                        "com.example.telegramalramrelay.ACTION_RECENT_UPDATED" -> {
                            recentJson = prefs.getString("recent_notifications", "[]") ?: "[]"
                        }
                        "com.example.telegramalramrelay.ACTION_STATS_UPDATED" -> {
                            sentToday = prefs.getInt("sent_notifications_today", 0)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction("com.example.telegramalramrelay.ACTION_RECENT_UPDATED")
            addAction("com.example.telegramalramrelay.ACTION_STATS_UPDATED")
        }
        context.registerReceiver(receiver, filter)
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF2563EB)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            Text(
                text = "알림 → 텔레그램",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )
            Text(
                text = "스마트폰 알림을 텔레그램으로 받아보세요",
                fontSize = 14.sp,
                color = Color(0xFF6B7280)
            )
        }

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // forwardingEnabled governs whether notifications are forwarded (not process lifecycle)
                var forwardingEnabled by remember { mutableStateOf(prefs.getBoolean("forwardingEnabled", true)) }

                // Combined status + toggle row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "연결 상태",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF111827)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                    val statusColor = if (forwardingEnabled) Color(0xFF10B981) else Color(0xFF9CA3AF)
                                    // show last notification's app icon in the status area when available
                                    val lastPackage = recentList.firstOrNull()?.packageName ?: ""
                                    val statusIconBitmap = remember(lastPackage) {
                                        try {
                                            if (lastPackage.isBlank()) null
                                            else {
                                                val pm = context.packageManager
                                                val drawable = pm.getApplicationIcon(lastPackage)
                                                val width = (drawable.intrinsicWidth.takeIf { it > 0 } ?: 24)
                                                val height = (drawable.intrinsicHeight.takeIf { it > 0 } ?: 24)
                                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                                val canvas = Canvas(bitmap)
                                                drawable.setBounds(0, 0, canvas.width, canvas.height)
                                                drawable.draw(canvas)
                                                bitmap.asImageBitmap()
                                            }
                                        } catch (e: Exception) { null }
                                    }
                                    if (statusIconBitmap != null) {
                                        Image(bitmap = statusIconBitmap, contentDescription = null, modifier = Modifier.size(20.dp))
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(statusColor)
                                        )
                                    }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (forwardingEnabled) "알림 전송 중" else "알림 전송 중지",
                                fontSize = 14.sp,
                                color = statusColor
                            )
                        }
                    }

                    // Switch on the right controls forwardingEnabled; persisted to prefs
                    Switch(checked = forwardingEnabled, onCheckedChange = { on ->
                        forwardingEnabled = on
                        try { prefs.edit().putBoolean("forwardingEnabled", on).apply() } catch (_: Exception) {}
                    }, colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF2563EB),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFD1D5DB)
                    ))
                }

                Divider(color = Color(0xFFF3F4F6))

                StatusRow("연결된 앱", "${connectedApps}개")
                StatusRow("오늘 전송된 알림", "${sentToday}개")
            }
        }

        

        // Recent Notifications
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "최근 알림",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF111827)
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column {
                    if (recentList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text(text = "최근 알림이 없습니다", color = Color(0xFF6B7280))
                        }
                    } else {
                        recentList.forEachIndexed { idx, item ->
                            RecentNotificationItem(
                                emoji = item.emoji,
                                app = item.app,
                                message = item.message,
                                time = item.time,
                                packageName = item.packageName
                            )
                            if (idx < recentList.size - 1) Divider(color = Color(0xFFF3F4F6))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFF6B7280)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2563EB)
        )
    }
}

@Composable
fun QuickActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        onClick = { }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF111827)
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280)
                )
            }
        }
    }
}

@Composable
fun RecentNotificationItem(
    emoji: String,
    app: String,
    message: String,
    time: String,
    packageName: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val context = LocalContext.current
        val iconBitmap = remember(packageName) {
            try {
                if (packageName.isBlank()) null
                else {
                    val pm = context.packageManager
                    val drawable = pm.getApplicationIcon(packageName)
                    val width = (drawable.intrinsicWidth.takeIf { it > 0 } ?: 48)
                    val height = (drawable.intrinsicHeight.takeIf { it > 0 } ?: 48)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bitmap.asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        }

        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = emoji,
                fontSize = 28.sp
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = app,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF111827)
            )
            Text(
                text = message,
                fontSize = 12.sp,
                color = Color(0xFF6B7280),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = time,
            fontSize = 12.sp,
            color = Color(0xFF9CA3AF)
        )
    }
}

private data class RecentItem(val emoji: String, val app: String, val message: String, val time: String, val packageName: String = "")

private fun parseRecentNotifications(json: String): List<RecentItem> {
    return try {
        val arr = JSONArray(json)
        val out = mutableListOf<RecentItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += RecentItem(
                emoji = o.optString("emoji", "🔔"),
                app = o.optString("app", "알림"),
                message = o.optString("message", ""),
                time = o.optString("time", ""),
                packageName = o.optString("package", "")
            )
        }
        out
    } catch (e: Exception) {
        emptyList()
    }
}
