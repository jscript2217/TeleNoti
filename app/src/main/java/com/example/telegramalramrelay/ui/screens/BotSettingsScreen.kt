package com.example.telegramalramrelay.ui.screens

import android.app.NotificationManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.telegramalramrelay.AppKeepAliveService
import com.example.telegramalramrelay.data.DetectedChat
import com.example.telegramalramrelay.data.KeywordFilterConfig
import com.example.telegramalramrelay.data.ThemeMode
import com.example.telegramalramrelay.ui.MainViewModel
import com.example.telegramalramrelay.ui.components.PermissionWizardDialog
import com.example.telegramalramrelay.util.openBatteryOptimizationSettings
import com.example.telegramalramrelay.util.openNotificationChannelSettings
import com.example.telegramalramrelay.util.openNotificationListenerSettings

private enum class ActiveTimePickerTarget {
    QUIET_START,
    QUIET_END,
    DAILY_RESET
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BotSettingsScreen(
    viewModel: MainViewModel,
    onRequestNotificationPermission: () -> Unit,
    onRequestSmsPermission: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val notifAccessEnabled by viewModel.notificationAccessEnabled.collectAsStateWithLifecycle()
    val ignoringBattery by viewModel.ignoringBatteryOptimizations.collectAsStateWithLifecycle()
    val readSmsGranted by viewModel.readSmsGranted.collectAsStateWithLifecycle()

    var botToken by remember { mutableStateOf(settings.botToken) }
    var chatId by remember { mutableStateOf(settings.chatId) }
    var showToken by remember { mutableStateOf(false) }
    var isSendingTest by remember { mutableStateOf(false) }
    var isFetchingChats by remember { mutableStateOf(false) }
    var detectedChatsDialog by remember { mutableStateOf<List<DetectedChat>?>(null) }

    var includeKeywordsInput by remember { mutableStateOf(settings.keywordFilter.includeKeywordsRaw) }
    var excludeKeywordsInput by remember { mutableStateOf(settings.keywordFilter.excludeKeywordsRaw) }

    var timePickerTarget by remember { mutableStateOf<ActiveTimePickerTarget?>(null) }
    var showCrashDialog by remember { mutableStateOf(false) }
    var showWizardDialog by remember { mutableStateOf(false) }

    if (showWizardDialog) {
        PermissionWizardDialog(
            viewModel = viewModel,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onRequestSmsPermission = onRequestSmsPermission,
            onDismiss = { showWizardDialog = false }
        )
    }

    // Check notification channel disabled dialog
    val notifManager = context.getSystemService(NotificationManager::class.java)
    val channel = remember {
        try {
            notifManager?.getNotificationChannel(AppKeepAliveService.CHANNEL_ID)
        } catch (_: Exception) {
            null
        }
    }
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
                TextButton(
                    onClick = {
                        showChannelDialog = false
                        openNotificationChannelSettings(context, AppKeepAliveService.CHANNEL_ID)
                    }
                ) { Text("설정 열기") }
            },
            dismissButton = {
                TextButton(onClick = { showChannelDialog = false }) { Text("취소") }
            }
        )
    }

    detectedChatsDialog?.let { chats ->
        AlertDialog(
            onDismissRequest = { detectedChatsDialog = null },
            title = { Text("감지된 채팅방 선택") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "최근 봇에게 메시지를 보낸 채팅방입니다. 사용할 항목을 누르세요.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    chats.forEach { chat ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    chatId = chat.chatId
                                    detectedChatsDialog = null
                                    Toast.makeText(
                                        context,
                                        "Chat ID 입력됨: ${chat.title} (저장 버튼을 눌러 확정하세요)",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = chat.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "ID: ${chat.chatId} · ${chat.type}" +
                                        if (chat.username.isNotBlank()) " (@${chat.username})" else "",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detectedChatsDialog = null }) { Text("닫기") }
            }
        )
    }

    timePickerTarget?.let { target ->
        val (initHour, initMinute, titleText) = when (target) {
            ActiveTimePickerTarget.QUIET_START -> Triple(
                settings.quietHours.startHour,
                settings.quietHours.startMinute,
                "방해금지 시작 시각"
            )
            ActiveTimePickerTarget.QUIET_END -> Triple(
                settings.quietHours.endHour,
                settings.quietHours.endMinute,
                "방해금지 종료 시각"
            )
            ActiveTimePickerTarget.DAILY_RESET -> Triple(
                settings.dailyReset.hour,
                settings.dailyReset.minute,
                "일일 통계 초기화 시각"
            )
        }
        val pickerState = rememberTimePickerState(
            initialHour = initHour,
            initialMinute = initMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { timePickerTarget = null },
            title = { Text(titleText) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (target) {
                            ActiveTimePickerTarget.QUIET_START -> viewModel.setQuietHours(
                                settings.quietHours.copy(
                                    startHour = pickerState.hour,
                                    startMinute = pickerState.minute
                                )
                            )
                            ActiveTimePickerTarget.QUIET_END -> viewModel.setQuietHours(
                                settings.quietHours.copy(
                                    endHour = pickerState.hour,
                                    endMinute = pickerState.minute
                                )
                            )
                            ActiveTimePickerTarget.DAILY_RESET -> viewModel.setDailyResetConfig(
                                settings.dailyReset.copy(
                                    hour = pickerState.hour,
                                    minute = pickerState.minute
                                )
                            )
                        }
                        timePickerTarget = null
                    }
                ) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { timePickerTarget = null }) { Text("취소") }
            }
        )
    }

    if (showCrashDialog && !settings.lastCrash.isNullOrBlank()) {
        val crashText = settings.lastCrash ?: ""
        AlertDialog(
            onDismissRequest = { showCrashDialog = false },
            title = { Text("마지막 크래시 로그") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp)
                ) {
                    Text(
                        text = crashText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(crashText))
                        Toast.makeText(context, "크래시 로그가 클립보드에 복사되었습니다.", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("복사") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel.clearLastCrash()
                            showCrashDialog = false
                            Toast.makeText(context, "크래시 로그를 삭제했습니다.", Toast.LENGTH_SHORT).show()
                        }
                    ) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { showCrashDialog = false }) { Text("닫기") }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
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
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = "텔레그램 봇 & 동작 설정",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "봇 연결, 필터, 권한 및 화면 테마를 관리하세요",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Credentials Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "봇 연결 정보",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = botToken,
                    onValueChange = { botToken = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Bot Token") },
                    placeholder = { Text("예: 1234567890:ABCdefGHIjklMNO...", fontSize = 13.sp) },
                    visualTransformation = if (showToken) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "토큰 표시 전환",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboard.getText()?.let { botToken = it.text.trim() }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "붙여넣기",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = chatId,
                    onValueChange = { chatId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Chat ID") },
                    placeholder = { Text("예: 123456789 또는 -100...", fontSize = 13.sp) },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                clipboard.getText()?.let { chatId = it.text.trim() }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "붙여넣기",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    singleLine = true
                )

                OutlinedButton(
                    onClick = {
                        if (isFetchingChats) return@OutlinedButton
                        isFetchingChats = true
                        viewModel.fetchChats(botToken) { chats, err ->
                            isFetchingChats = false
                            when {
                                err != null -> Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                chats.isEmpty() -> Toast.makeText(
                                    context,
                                    "감지된 채팅방이 없습니다. 봇에게 /start를 먼저 보낸 뒤 다시 눌러주세요.",
                                    Toast.LENGTH_LONG
                                ).show()
                                chats.size == 1 -> {
                                    val first = chats.first()
                                    chatId = first.chatId
                                    Toast.makeText(
                                        context,
                                        "Chat ID 자동 입력: ${first.title} (${first.chatId})",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                else -> detectedChatsDialog = chats
                            }
                        }
                    },
                    enabled = !isFetchingChats,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isFetchingChats) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("채팅방 조회 중...", fontSize = 13.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chat ID 자동 가져오기 (/start 후 클릭)", fontSize = 13.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.saveCredentials(botToken, chatId)
                            Toast.makeText(context, "봇 정보가 암호화되어 저장되었습니다.", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 13.dp)
                    ) {
                        Text(text = "저장", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }

                    Button(
                        onClick = {
                            if (isSendingTest) return@Button
                            if (botToken.isBlank() || chatId.isBlank()) {
                                Toast.makeText(context, "봇 토큰과 Chat ID를 입력하세요", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSendingTest = true
                            viewModel.sendTestMessage(botToken, chatId) { ok, msg ->
                                isSendingTest = false
                                Toast.makeText(
                                    context,
                                    if (ok) msg else "실패: $msg",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        enabled = !isSendingTest,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 13.dp)
                    ) {
                        if (isSendingTest) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(text = "테스트 메시지", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // How to Get Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📚 봇 정보 얻는 방법",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                InstructionStep("1.", "텔레그램에서 @BotFather 검색 후 /newbot 명령어로 새 봇 생성")
                InstructionStep("2.", "발급받은 Bot Token을 복사해 위에 입력")
                InstructionStep("3.", "생성한 봇 채팅방에 들어가 /start 메시지 전송")
                InstructionStep("4.", "위 'Chat ID 자동 가져오기' 버튼을 눌러 자동 입력 후 저장")
            }
        }

        // Filter & Schedule Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "필터 및 스케줄",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // LMS / MMS full-text enrichment
                SwitchSettingRow(
                    title = "긴 문자(LMS/MMS) 원문 보강",
                    subtitle = when {
                        settings.lmsEnrichmentEnabled && readSmsGranted -> {
                            "활성화됨 · 잘린 장문/MMS도 문자함에서 전체 원문 조회"
                        }
                        settings.lmsEnrichmentEnabled && !readSmsGranted -> {
                            "SMS 읽기 권한 필요 (아래 버튼을 눌러 허용하세요)"
                        }
                        else -> "비활성화 (알림창 요약 텍스트만 전송)"
                    },
                    checked = settings.lmsEnrichmentEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.setLmsEnrichmentEnabled(enabled)
                        if (enabled && !readSmsGranted) {
                            onRequestSmsPermission()
                        }
                    }
                )
                if (settings.lmsEnrichmentEnabled && !readSmsGranted) {
                    OutlinedButton(
                        onClick = onRequestSmsPermission,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SMS 읽기 권한 허용하기 (LMS/MMS 원문 읽기)", fontSize = 12.sp)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Image forwarding toggle
                SwitchSettingRow(
                    title = "이미지(사진) 함께 전송",
                    subtitle = "알림 미리보기 사진(카톡·CCTV 등)과 MMS 첨부 이미지를 텔레그램으로 전송합니다",
                    checked = settings.imageForwardingEnabled,
                    onCheckedChange = { viewModel.setImageForwardingEnabled(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Telegram two-way reply toggle
                SwitchSettingRow(
                    title = "텔레그램에서 바로 답장 (카톡·문자)",
                    subtitle = "텔레그램에서 받은 카톡·문자 알림에 스와이프 답장하면 휴대폰에서 자동 회신합니다 · 상주 서비스 ON 권장",
                    checked = settings.telegramReplyEnabled,
                    onCheckedChange = { viewModel.setTelegramReplyEnabled(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Keyword filter toggle
                SwitchSettingRow(
                    title = "키워드 필터",
                    subtitle = if (settings.keywordFilter.enabled) "포함/제외 키워드 적용 중" else "모든 알림 내용 허용",
                    checked = settings.keywordFilter.enabled,
                    onCheckedChange = { enabled ->
                        viewModel.setKeywordFilter(
                            KeywordFilterConfig(
                                enabled = enabled,
                                includeKeywordsRaw = includeKeywordsInput,
                                excludeKeywordsRaw = excludeKeywordsInput
                            )
                        )
                    }
                )

                if (settings.keywordFilter.enabled) {
                    OutlinedTextField(
                        value = includeKeywordsInput,
                        onValueChange = { includeKeywordsInput = it },
                        label = { Text("포함 키워드 (쉼표로 구분, 비우면 모두 허용)") },
                        placeholder = { Text("예: 입금, 인증번호, 택배") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = excludeKeywordsInput,
                        onValueChange = { excludeKeywordsInput = it },
                        label = { Text("제외 키워드 (쉼표로 구분)") },
                        placeholder = { Text("예: 광고, 이벤트, 홍보") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            viewModel.setKeywordFilter(
                                KeywordFilterConfig(
                                    enabled = true,
                                    includeKeywordsRaw = includeKeywordsInput,
                                    excludeKeywordsRaw = excludeKeywordsInput
                                )
                            )
                            Toast.makeText(context, "키워드 필터가 저장되었습니다.", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("키워드 필터 저장", fontSize = 13.sp)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Quiet hours
                SwitchSettingRow(
                    title = "방해금지 시간대",
                    subtitle = if (settings.quietHours.enabled) {
                        "${formatTime(settings.quietHours.startHour, settings.quietHours.startMinute)} ~ " +
                            formatTime(settings.quietHours.endHour, settings.quietHours.endMinute)
                    } else {
                        "비활성화"
                    },
                    checked = settings.quietHours.enabled,
                    onCheckedChange = { on ->
                        viewModel.setQuietHours(settings.quietHours.copy(enabled = on))
                    }
                )
                if (settings.quietHours.enabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { timePickerTarget = ActiveTimePickerTarget.QUIET_START },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "시작: ${formatTime(settings.quietHours.startHour, settings.quietHours.startMinute)}",
                                fontSize = 13.sp
                            )
                        }
                        OutlinedButton(
                            onClick = { timePickerTarget = ActiveTimePickerTarget.QUIET_END },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "종료: ${formatTime(settings.quietHours.endHour, settings.quietHours.endMinute)}",
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Ignore ongoing
                SwitchSettingRow(
                    title = "진행 중(상단바 고정) 알림 제외",
                    subtitle = "음악 재생, 타이머, 파일 다운로드 등 지속 알림 무시",
                    checked = settings.ignoreOngoing,
                    onCheckedChange = { viewModel.setIgnoreOngoing(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Dedupe window
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "중복 알림 방지",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "같은 앱·제목·내용의 연속 알림을 지정한 시간 동안 중복 전송하지 않습니다",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val dedupeOptions = listOf(0 to "끄기", 3 to "3초", 5 to "5초", 10 to "10초", 30 to "30초")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        dedupeOptions.forEach { (sec, label) ->
                            FilterChip(
                                selected = settings.dedupeWindowSeconds == sec,
                                onClick = { viewModel.setDedupeWindowSeconds(sec) },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Daily reset schedule
                SwitchSettingRow(
                    title = "일일 통계 자동 초기화",
                    subtitle = if (settings.dailyReset.enabled) {
                        "매일 ${formatTime(settings.dailyReset.hour, settings.dailyReset.minute)}에 오늘 전송 카운트 초기화"
                    } else {
                        "비활성화"
                    },
                    checked = settings.dailyReset.enabled,
                    onCheckedChange = { enabled ->
                        viewModel.setDailyResetConfig(settings.dailyReset.copy(enabled = enabled))
                    }
                )
                if (settings.dailyReset.enabled) {
                    OutlinedButton(
                        onClick = { timePickerTarget = ActiveTimePickerTarget.DAILY_RESET },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "초기화 시각 변경: ${formatTime(settings.dailyReset.hour, settings.dailyReset.minute)}",
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Permissions, Service & Theme Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "권한, 서비스 및 화면",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                FilledTonalButton(
                    onClick = { showWizardDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("권한 설정 도우미 열기 (단계별 안내)", fontSize = 13.sp)
                }

                SwitchSettingRow(
                    title = "알림 접근 권한",
                    subtitle = if (notifAccessEnabled) "허용됨" else "미허용 (터치하여 시스템 설정 열기)",
                    checked = notifAccessEnabled,
                    onCheckedChange = {
                        openNotificationListenerSettings(context)
                    }
                )

                SwitchSettingRow(
                    title = "배터리 예외 등록",
                    subtitle = if (ignoringBattery) "예외 등록됨" else "예외 등록 필요 (터치하여 설정 열기)",
                    checked = ignoringBattery,
                    onCheckedChange = {
                        val opened = openBatteryOptimizationSettings(context)
                        if (!opened) {
                            Toast.makeText(context, "해당 설정 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                SwitchSettingRow(
                    title = "백그라운드 상주 서비스",
                    subtitle = if (settings.backgroundEnabled) "항상 실행 (상단바 작동 상태 표시)" else "비활성화",
                    checked = settings.backgroundEnabled,
                    onCheckedChange = { on ->
                        val res = viewModel.setBackgroundEnabled(on)
                        if (res.isSuccess) {
                            Toast.makeText(
                                context,
                                if (on) "백그라운드 서비스를 시작했습니다." else "백그라운드 서비스를 중지했습니다.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "서비스 제어 실패: ${res.exceptionOrNull()?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "화면 테마",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val themes = listOf(
                        ThemeMode.SYSTEM to "시스템 설정",
                        ThemeMode.LIGHT to "라이트",
                        ThemeMode.DARK to "다크"
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        themes.forEach { (mode, label) ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                if (!settings.lastCrash.isNullOrBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Button(
                        onClick = { showCrashDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text(text = "마지막 크래시 로그 보기")
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
fun InstructionStep(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = number,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            lineHeight = 18.sp
        )
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    return "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
}
