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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import com.example.telegramalramrelay.network.TelegramApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.PowerManager
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.app.NotificationManager
import com.example.telegramalramrelay.util.openBatteryOptimizationSettings
import com.example.telegramalramrelay.util.openNotificationChannelSettings
import androidx.compose.ui.unit.sp




@Composable
fun BotSettingsScreen() {
    var botToken by remember { mutableStateOf("1234567890:ABCdefGHIjklMNOpqrsTUVwxyz") }
    var chatId by remember { mutableStateOf("123456789") }
    val clipboard = LocalClipboardManager.current
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (saved) {
            kotlinx.coroutines.delay(2000)
            saved = false
        }
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
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2563EB)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = "텔레그램 봇 설정",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )
            Text(
                text = "봇 정보를 입력하고 관리하세요",
                fontSize = 14.sp,
                color = Color(0xFF6B7280)
            )
        }

        // Credentials (Bot Token + Chat ID) in one card
        val ctx = LocalContext.current
        val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

        LaunchedEffect(Unit) {
            botToken = prefs.getString("botToken", botToken) ?: botToken
            chatId = prefs.getString("chatId", chatId) ?: chatId
            // load service state if saved
            val svc = prefs.getBoolean("serviceRunning", com.example.telegramalramrelay.AppKeepAliveService.isRunning)
            com.example.telegramalramrelay.AppKeepAliveService.isRunning = svc
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = botToken,
                    onValueChange = { botToken = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("1234567890:ABCdefGHIjklMNOpqrsTUVwxyz", fontSize = 14.sp) },
                    visualTransformation = VisualTransformation.None,
                    trailingIcon = {
                        IconButton(onClick = { clipboard.getText()?.let { botToken = it.text } }) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "붙여넣기",
                                tint = Color(0xFF6B7280),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2563EB),
                        unfocusedBorderColor = Color(0xFFE5E7EB),
                        focusedContainerColor = Color(0xFFF9FAFB),
                        unfocusedContainerColor = Color(0xFFF9FAFB)
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = chatId,
                    onValueChange = { chatId = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("123456789", fontSize = 14.sp) },
                    trailingIcon = {
                        IconButton(onClick = { clipboard.getText()?.let { chatId = it.text } }) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "붙여넣기",
                                tint = Color(0xFF6B7280),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2563EB),
                        unfocusedBorderColor = Color(0xFFE5E7EB),
                        focusedContainerColor = Color(0xFFF9FAFB),
                        unfocusedContainerColor = Color(0xFFF9FAFB)
                    ),
                    singleLine = true
                )
                Text(
                    text = "@BotFather에서 받은 봇 토큰과 Chat ID를 입력하세요",
                    fontSize = 12.sp,
                    color = Color(0xFF9CA3AF)
                )
            }
        }

        // Credentials actions: Save (bot token/chat id) + Test send side-by-side
        val coroutineScope = rememberCoroutineScope()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    try {
                        prefs.edit().putString("botToken", botToken.trim()).putString("chatId", chatId.trim()).apply()
                        saved = true
                        Toast.makeText(ctx, "봇 정보가 저장되었습니다.", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Text(text = "저장", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
            }

            Button(
                onClick = {
                    // validate
                    if (botToken.isBlank() || chatId.isBlank()) {
                        Toast.makeText(ctx, "봇 토큰과 Chat ID를 입력하세요", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    coroutineScope.launch {
                        val toastMsg = withContext(Dispatchers.IO) {
                            val (ok, msg) = TelegramApi.sendTestMessage(botToken.trim(), chatId.trim(), "[테스트 메시지] 이 메시지는 텔레노티에서 보냈습니다.")
                            if (ok) msg else "실패: $msg"
                        }
                        Toast.makeText(ctx, toastMsg, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Text(text = "테스트 메시지", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
            }
        }

        // How to Get Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "📚 정보를 얻는 방법",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E3A8A)
                )
                InstructionStep("1.", "텔레그램에서 @BotFather를 검색하고 /newbot 명령어로 새 봇 생성")
                InstructionStep("2.", "봇 토큰을 복사하여 위 필드에 입력")
                InstructionStep("3.", "@userinfobot에게 메시지를 보내 Chat ID 확인")
                InstructionStep("4.", "받은 Chat ID를 위 필드에 입력")
            }
        }

        // Permissions & Service Card
        val context = LocalContext.current
        var backgroundEnabled by remember { mutableStateOf(prefs.getBoolean("backgroundEnabled", false)) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "권한 및 서비스", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))

                // Notification access row as switch
                val notifEnabled = remember { mutableStateOf(isNotificationAccessEnabled(context)) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(text = "알림 접근 권한", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(text = if (notifEnabled.value) "허용됨" else "미허용", fontSize = 12.sp, color = Color(0xFF6B7280))
                    }
                    Switch(checked = notifEnabled.value, onCheckedChange = { enabled ->
                        try {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                        } catch (_: Exception) {}
                        notifEnabled.value = enabled
                    }, colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF2563EB),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFD1D5DB)
                    ))
                }

                

                // Check notification channel importance and show dialog if channel disabled
                val notifManager = context.getSystemService(NotificationManager::class.java)
                val channel = try { notifManager?.getNotificationChannel("telegramalramrelay_service") } catch (_: Exception) { null }
                var showChannelDialog by remember { mutableStateOf(false) }
                LaunchedEffect(channel) {
                    if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                        showChannelDialog = true
                    }
                }

                if (showChannelDialog) {
                    AlertDialog(
                        onDismissRequest = { showChannelDialog = false },
                        title = { Text("알림 채널이 비활성화되었습니다") },
                        text = { Text("이 앱의 서비스 알림이 차단되어 상단바에 표시되지 않을 수 있습니다. 알림 채널 설정을 열어 허용해 주세요.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showChannelDialog = false
                                openNotificationChannelSettings(context, "telegramalramrelay_service")
                            }) { Text("설정 열기") }
                        },
                        dismissButton = { TextButton(onClick = { showChannelDialog = false }) { Text("취소") } }
                    )
                }

                // Battery optimization
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val ignoring = remember { mutableStateOf<Boolean>(try { powerManager.isIgnoringBatteryOptimizations(context.packageName) } catch (_: Exception) { false }) }
                // Re-check battery-exemption status when the screen (activity) resumes
                val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            try {
                                ignoring.value = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                            } catch (_: Exception) {}

                            // Delayed re-check to catch user action in settings UI
                            try {
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(1000)
                                    try {
                                        val newVal = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                                        if (newVal != ignoring.value) {
                                            ignoring.value = newVal
                                            if (newVal) {
                                                withContext(Dispatchers.Main) {
                                                    try { Toast.makeText(context, "배터리 예외가 등록되었습니다.", Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
                                                }
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                var showBatteryDialog by remember { mutableStateOf(false) }

                if (showBatteryDialog) {
                    AlertDialog(
                        onDismissRequest = { showBatteryDialog = false },
                        title = { Text(text = "배터리 예외 처리") },
                        text = { Text(text = "앱이 백그라운드에서 정상 동작하도록 배터리 최적화 예외로 등록하시겠습니까?") },
                        confirmButton = {
                            TextButton(onClick = {
                                showBatteryDialog = false
                                try {
                                    val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    // If the device can handle the explicit request intent, use it; otherwise open the general battery optimization settings
                                    val pm = context.packageManager
                                    if (requestIntent.resolveActivity(pm) != null) {
                                        context.startActivity(requestIntent)
                                    } else {
                                        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                        if (fallback.resolveActivity(pm) != null) context.startActivity(fallback) else Toast.makeText(context, "해당 설정을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "예외 요청을 열 수 없습니다: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                                try { ignoring.value = powerManager.isIgnoringBatteryOptimizations(context.packageName) } catch (_: Exception) {}
                            }) { Text("예") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBatteryDialog = false }) { Text("아니요") }
                        }
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(text = "배터리 예외 등록", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(text = if (ignoring.value) "예외 등록됨" else "예외 등록 필요", fontSize = 12.sp, color = Color(0xFF6B7280))
                    }
                    Switch(checked = ignoring.value, onCheckedChange = { on ->
                        try {
                            // User toggled battery exemption switch
                            // Directly open the best available battery exception/settings screen
                            val opened = openBatteryOptimizationSettings(context)
                            // Re-evaluate current exemption state (immediate check may still be false)
                            try { ignoring.value = powerManager.isIgnoringBatteryOptimizations(context.packageName) } catch (_: Exception) {}
                            if (!opened) Toast.makeText(context, "해당 설정 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "설정 열기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }, colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF2563EB),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFD1D5DB)
                    ))
                }

                // Background enable/disable option
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(text = "백그라운드 실행", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(text = if (backgroundEnabled) "항상 실행" else "비활성화", fontSize = 12.sp, color = Color(0xFF6B7280))
                    }
                    Switch(checked = backgroundEnabled, onCheckedChange = { on ->
                        backgroundEnabled = on
                        try {
                            prefs.edit().putBoolean("backgroundEnabled", on).putBoolean("pinNotification", on).apply()
                        } catch (e: Exception) {
                            Toast.makeText(context, "설정 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                        }

                        if (on) {
                            try {
                                ContextCompat.startForegroundService(context, Intent(context, com.example.telegramalramrelay.AppKeepAliveService::class.java))
                                com.example.telegramalramrelay.AppKeepAliveService.isRunning = true
                                prefs.edit().putBoolean("serviceRunning", true).apply()
                                Toast.makeText(context, "백그라운드 서비스 시작 시도됨", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "서비스 시작 실패: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            try {
                                // Prefer sending ACTION_STOP so the service can stop itself safely
                                val stopIntent = Intent(context, com.example.telegramalramrelay.AppKeepAliveService::class.java).apply { action = "com.example.telegramalramrelay.ACTION_STOP" }
                                try { context.startService(stopIntent) } catch (_: Exception) {}
                                // Also request stopService as a fallback
                                val stopped = try { context.stopService(Intent(context, com.example.telegramalramrelay.AppKeepAliveService::class.java)) } catch (_: Exception) { false }
                                com.example.telegramalramrelay.AppKeepAliveService.isRunning = false
                                prefs.edit().putBoolean("serviceRunning", false).apply()
                                if (stopped) Toast.makeText(context, "서비스 중지됨", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "서비스 중지 실패: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }, colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF2563EB),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFD1D5DB)
                    ))
                }

                // '상단바 알림 고정' 설정은 백그라운드 실행과 함께 제어됩니다.

                // Last crash viewer for debugging (shows saved stacktrace if the app previously crashed)
                val lastCrash = remember { prefs.getString("lastCrash", null) }
                if (!lastCrash.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            try {
                                Toast.makeText(ctx, "마지막 크래시 로그가 복사되었습니다.", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text(text = "마지막 크래시 보기", color = Color.White)
                    }
                }

            }
        }
    }

}

@Composable
fun InstructionStep(number: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = number,
            fontSize = 12.sp,
            color = Color(0xFF1E40AF)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            color = Color(0xFF1E40AF),
            lineHeight = 18.sp
        )
    }
}

@Composable
fun ToggleSetting(label: String, description: String) {
    var enabled by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
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
        Switch(
            checked = enabled,
            onCheckedChange = { enabled = it },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF2563EB),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFD1D5DB)
            )
        )
    }
}

private fun isNotificationAccessEnabled(context: Context): Boolean {
    return try {
        val enabled = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
        enabled.contains(context.packageName)
    } catch (e: Exception) {
        false
    }
}
                