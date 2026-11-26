package com.example.telegramalramrelay.ui.screens

import android.app.NotificationManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.telegramalramrelay.AppKeepAliveService
import com.example.telegramalramrelay.data.ChatProfile
import com.example.telegramalramrelay.data.DetectedChat
import com.example.telegramalramrelay.data.KeywordFilterConfig
import com.example.telegramalramrelay.data.ThemeMode
import com.example.telegramalramrelay.domain.ChatTargetResolver
import com.example.telegramalramrelay.ui.MainViewModel
import com.example.telegramalramrelay.ui.components.PermissionWizardDialog
import com.example.telegramalramrelay.util.openAppDetailsSettings
import com.example.telegramalramrelay.util.openBatteryOptimizationSettings
import com.example.telegramalramrelay.util.openNotificationChannelSettings
import com.example.telegramalramrelay.util.openNotificationListenerSettings

private enum class ActiveTimePickerTarget {
    QUIET_START,
    QUIET_END,
    DAILY_RESET
}

private enum class SettingsTab(val label: String) {
    BOT_RECIPIENTS("봇·수신자"),
    FEATURES("전달 기능"),
    FILTERS_SCHEDULE("필터·시간"),
    PERMISSIONS_SYSTEM("권한·시스템")
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BotSettingsScreen(
    viewModel: MainViewModel,
    onRequestNotificationPermission: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onRequestCallPermissions: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val postNotifGranted by viewModel.postNotificationsGranted.collectAsStateWithLifecycle()
    val notifAccessEnabled by viewModel.notificationAccessEnabled.collectAsStateWithLifecycle()
    val ignoringBattery by viewModel.ignoringBatteryOptimizations.collectAsStateWithLifecycle()
    val readSmsGranted by viewModel.readSmsGranted.collectAsStateWithLifecycle()
    val readPhoneStateGranted by viewModel.readPhoneStateGranted.collectAsStateWithLifecycle()
    val readCallLogGranted by viewModel.readCallLogGranted.collectAsStateWithLifecycle()
    val readContactsGranted by viewModel.readContactsGranted.collectAsStateWithLifecycle()

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedTab = SettingsTab.entries[selectedTabIndex.coerceIn(0, SettingsTab.entries.lastIndex)]
    val requiredPermsDone = listOf(postNotifGranted, notifAccessEnabled, ignoringBattery).count { it }
    val requiredPermsMissing = requiredPermsDone < 3

    var botToken by remember { mutableStateOf(settings.botToken) }
    var chatId by remember { mutableStateOf(settings.chatId) }
    var showToken by remember { mutableStateOf(false) }
    var showBotGuide by rememberSaveable { mutableStateOf(settings.botToken.isBlank()) }
    var isSendingTest by remember { mutableStateOf(false) }
    var isFetchingChats by remember { mutableStateOf(false) }
    var detectedChatsDialog by remember { mutableStateOf<List<DetectedChat>?>(null) }
    var editingProfile by remember { mutableStateOf<ChatProfile?>(null) }
    var profilePendingDelete by remember { mutableStateOf<ChatProfile?>(null) }

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
            onRequestCallPermissions = onRequestCallPermissions,
            onDismiss = { showWizardDialog = false }
        )
    }

    // Check notification channel disabled dialog (API 26+)
    val channel = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.getSystemService(NotificationManager::class.java)
                    ?.getNotificationChannel(AppKeepAliveService.CHANNEL_ID)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }
    var showChannelDialog by remember { mutableStateOf(false) }
    LaunchedEffect(channel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            channel != null &&
            channel.importance == NotificationManager.IMPORTANCE_NONE
        ) {
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
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "최근 봇에게 메시지를 보낸 채팅방입니다. 기본 수신 ID로 넣거나 프로필로 바로 저장할 수 있습니다.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    chats.forEach { chat ->
                        val existingProfile = settings.chatProfiles.firstOrNull {
                            it.chatId.trim() == chat.chatId.trim()
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            chatId = chat.chatId
                                            detectedChatsDialog = null
                                            Toast.makeText(
                                                context,
                                                "Chat ID 입력됨: ${chat.title} (저장 버튼을 눌러 확정하세요)",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text("기본 ID 입력", fontSize = 11.sp)
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            viewModel.saveChatProfile(
                                                id = existingProfile?.id,
                                                name = existingProfile?.name ?: chat.title,
                                                chatId = chat.chatId
                                            )
                                            Toast.makeText(
                                                context,
                                                if (existingProfile != null) {
                                                    "'${existingProfile.name}' 프로필이 이미 저장되어 있습니다."
                                                } else {
                                                    "'${chat.title}' 프로필을 저장했습니다."
                                                },
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            if (existingProfile != null) "프로필 저장됨" else "프로필로 저장",
                                            fontSize = 11.sp
                                        )
                                    }
                                }
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

    editingProfile?.let { target ->
        ChatProfileEditorDialog(
            initialProfile = target,
            botToken = botToken.ifBlank { settings.botToken },
            viewModel = viewModel,
            onDismiss = { editingProfile = null },
            onSave = { nameInput, chatIdInput ->
                val ok = viewModel.saveChatProfile(
                    id = target.id.takeIf { it.isNotBlank() },
                    name = nameInput,
                    chatId = chatIdInput
                )
                if (ok) {
                    editingProfile = null
                    Toast.makeText(context, "수신자 프로필이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Chat ID를 입력하세요.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    profilePendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { profilePendingDelete = null },
            title = { Text("프로필 삭제") },
            text = {
                Text("'${target.name}' (${target.chatId}) 프로필을 삭제할까요?\n기본 동시 전송 및 앱별 맞춤 지정에서도 함께 해제됩니다.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteChatProfile(target.id)
                        profilePendingDelete = null
                        Toast.makeText(context, "'${target.name}' 프로필을 삭제했습니다.", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { profilePendingDelete = null }) { Text("취소") }
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

    val scrollState = rememberScrollState()
    LaunchedEffect(selectedTab) {
        scrollState.scrollTo(0)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "설정",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "텔레그램 연결·기능·필터·권한을 카테고리별로 관리합니다",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        PrimaryTabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SettingsTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTabIndex = tab.ordinal },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = tab.label,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (tab == SettingsTab.PERMISSIONS_SYSTEM && requiredPermsMissing) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error)
                                )
                            }
                        }
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedTab) {
                SettingsTab.BOT_RECIPIENTS -> {
                    val defaultRecipients = remember(
                        settings.chatId,
                        settings.chatProfiles,
                        settings.defaultProfileIds
                    ) {
                        ChatTargetResolver.resolveDefaultRecipients(settings)
                    }
                    val primaryChatIds = remember(settings.chatId) {
                        ChatTargetResolver.parseChatIds(settings.chatId).toSet()
                    }
                    BotRecipientsTabContent(
                        botToken = botToken,
                        onBotTokenChange = { botToken = it },
                        chatId = chatId,
                        onChatIdChange = { chatId = it },
                        showToken = showToken,
                        onToggleShowToken = { showToken = !showToken },
                        showBotGuide = showBotGuide,
                        onToggleBotGuide = { showBotGuide = !showBotGuide },
                        isSendingTest = isSendingTest,
                        isFetchingChats = isFetchingChats,
                        savedBotToken = settings.botToken,
                        chatProfiles = settings.chatProfiles,
                        defaultProfileIds = settings.defaultProfileIds,
                        defaultRecipients = defaultRecipients,
                        primaryChatIds = primaryChatIds,
                        onPasteToken = { clipboard.getText()?.let { botToken = it.text.trim() } },
                        onPasteChatId = { clipboard.getText()?.let { chatId = it.text.trim() } },
                        onFetchChats = {
                            if (!isFetchingChats) {
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
                            }
                        },
                        onSaveCredentials = {
                            viewModel.saveCredentials(botToken, chatId)
                            Toast.makeText(context, "봇 정보가 암호화되어 저장되었습니다.", Toast.LENGTH_SHORT).show()
                        },
                        onSendTest = {
                            if (!isSendingTest) {
                                if (botToken.isBlank() || chatId.isBlank()) {
                                    Toast.makeText(context, "봇 토큰과 Chat ID를 입력하세요", Toast.LENGTH_SHORT).show()
                                } else {
                                    isSendingTest = true
                                    viewModel.sendTestMessage(botToken, chatId) { ok, msg ->
                                        isSendingTest = false
                                        Toast.makeText(
                                            context,
                                            if (ok) msg else "실패: $msg",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        },
                        onAddProfile = { editingProfile = ChatProfile("", "", "") },
                        onTestProfile = { profile ->
                            val activeToken = botToken.ifBlank { settings.botToken }.trim()
                            if (activeToken.isBlank()) {
                                Toast.makeText(context, "먼저 봇 토큰을 입력하세요.", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.sendTestMessage(activeToken, profile.chatId) { ok, msg ->
                                    Toast.makeText(
                                        context,
                                        if (ok) "'${profile.name}'에게 테스트 메시지 전송 성공" else "전송 실패: $msg",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onEditProfile = { editingProfile = it },
                        onDeleteProfile = { profilePendingDelete = it },
                        onToggleDefaultProfile = { viewModel.toggleDefaultProfile(it) }
                    )
                }

                SettingsTab.FEATURES -> FeaturesTabContent(
                    incomingCallAlertEnabled = settings.incomingCallAlertEnabled,
                    lmsEnrichmentEnabled = settings.lmsEnrichmentEnabled,
                    imageForwardingEnabled = settings.imageForwardingEnabled,
                    telegramReplyEnabled = settings.telegramReplyEnabled,
                    readPhoneStateGranted = readPhoneStateGranted,
                    readCallLogGranted = readCallLogGranted,
                    readContactsGranted = readContactsGranted,
                    readSmsGranted = readSmsGranted,
                    onToggleIncomingCallAlert = { enabled ->
                        viewModel.setIncomingCallAlertEnabled(enabled)
                        if (enabled && (!readPhoneStateGranted || !readCallLogGranted)) {
                            onRequestCallPermissions()
                        }
                    },
                    onRequestCallPermissions = onRequestCallPermissions,
                    onToggleLmsEnrichment = { enabled ->
                        viewModel.setLmsEnrichmentEnabled(enabled)
                        if (enabled && !readSmsGranted) {
                            onRequestSmsPermission()
                        }
                    },
                    onRequestSmsPermission = onRequestSmsPermission,
                    onToggleImageForwarding = { viewModel.setImageForwardingEnabled(it) },
                    onToggleTelegramReply = { viewModel.setTelegramReplyEnabled(it) }
                )

                SettingsTab.FILTERS_SCHEDULE -> FiltersScheduleTabContent(
                    keywordFilterEnabled = settings.keywordFilter.enabled,
                    includeKeywordsInput = includeKeywordsInput,
                    onIncludeKeywordsChange = { includeKeywordsInput = it },
                    excludeKeywordsInput = excludeKeywordsInput,
                    onExcludeKeywordsChange = { excludeKeywordsInput = it },
                    ignoreOngoing = settings.ignoreOngoing,
                    dedupeWindowSeconds = settings.dedupeWindowSeconds,
                    quietHoursEnabled = settings.quietHours.enabled,
                    quietStartHour = settings.quietHours.startHour,
                    quietStartMinute = settings.quietHours.startMinute,
                    quietEndHour = settings.quietHours.endHour,
                    quietEndMinute = settings.quietHours.endMinute,
                    dailyResetEnabled = settings.dailyReset.enabled,
                    dailyResetHour = settings.dailyReset.hour,
                    dailyResetMinute = settings.dailyReset.minute,
                    onToggleKeywordFilter = { enabled ->
                        viewModel.setKeywordFilter(
                            KeywordFilterConfig(
                                enabled = enabled,
                                includeKeywordsRaw = includeKeywordsInput,
                                excludeKeywordsRaw = excludeKeywordsInput
                            )
                        )
                    },
                    onSaveKeywordFilter = {
                        viewModel.setKeywordFilter(
                            KeywordFilterConfig(
                                enabled = true,
                                includeKeywordsRaw = includeKeywordsInput,
                                excludeKeywordsRaw = excludeKeywordsInput
                            )
                        )
                        Toast.makeText(context, "키워드 필터가 저장되었습니다.", Toast.LENGTH_SHORT).show()
                    },
                    onToggleIgnoreOngoing = { viewModel.setIgnoreOngoing(it) },
                    onSelectDedupeWindow = { viewModel.setDedupeWindowSeconds(it) },
                    onToggleQuietHours = { on ->
                        viewModel.setQuietHours(settings.quietHours.copy(enabled = on))
                    },
                    onPickQuietStart = { timePickerTarget = ActiveTimePickerTarget.QUIET_START },
                    onPickQuietEnd = { timePickerTarget = ActiveTimePickerTarget.QUIET_END },
                    onToggleDailyReset = { enabled ->
                        viewModel.setDailyResetConfig(settings.dailyReset.copy(enabled = enabled))
                    },
                    onPickDailyResetTime = { timePickerTarget = ActiveTimePickerTarget.DAILY_RESET }
                )

                SettingsTab.PERMISSIONS_SYSTEM -> PermissionsSystemTabContent(
                    requiredPermsDone = requiredPermsDone,
                    requiredPermsMissing = requiredPermsMissing,
                    postNotifGranted = postNotifGranted,
                    notifAccessEnabled = notifAccessEnabled,
                    ignoringBattery = ignoringBattery,
                    readPhoneStateGranted = readPhoneStateGranted,
                    readCallLogGranted = readCallLogGranted,
                    readContactsGranted = readContactsGranted,
                    readSmsGranted = readSmsGranted,
                    backgroundEnabled = settings.backgroundEnabled,
                    themeMode = settings.themeMode,
                    hasCrashLog = !settings.lastCrash.isNullOrBlank(),
                    onOpenWizard = { showWizardDialog = true },
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenNotificationListenerSettings = {
                        if (!openNotificationListenerSettings(context)) {
                            Toast.makeText(context, "알림 접근 설정을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenBatterySettings = {
                        if (!openBatteryOptimizationSettings(context)) {
                            Toast.makeText(context, "해당 설정 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRequestCallPermissions = onRequestCallPermissions,
                    onRequestSmsPermission = onRequestSmsPermission,
                    onOpenAppDetails = {
                        if (!openAppDetailsSettings(context)) {
                            Toast.makeText(context, "앱 정보를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onToggleBackground = { on ->
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
                    },
                    onSelectTheme = { viewModel.setThemeMode(it) },
                    onShowCrashDialog = { showCrashDialog = true }
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    spacing: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BotRecipientsTabContent(
    botToken: String,
    onBotTokenChange: (String) -> Unit,
    chatId: String,
    onChatIdChange: (String) -> Unit,
    showToken: Boolean,
    onToggleShowToken: () -> Unit,
    showBotGuide: Boolean,
    onToggleBotGuide: () -> Unit,
    isSendingTest: Boolean,
    isFetchingChats: Boolean,
    savedBotToken: String,
    chatProfiles: List<ChatProfile>,
    defaultProfileIds: Set<String>,
    defaultRecipients: List<com.example.telegramalramrelay.domain.ResolvedRecipient>,
    primaryChatIds: Set<String>,
    onPasteToken: () -> Unit,
    onPasteChatId: () -> Unit,
    onFetchChats: () -> Unit,
    onSaveCredentials: () -> Unit,
    onSendTest: () -> Unit,
    onAddProfile: () -> Unit,
    onTestProfile: (ChatProfile) -> Unit,
    onEditProfile: (ChatProfile) -> Unit,
    onDeleteProfile: (ChatProfile) -> Unit,
    onToggleDefaultProfile: (String) -> Unit
) {
    // Card 1: Bot Connection
    SettingsSectionCard(spacing = 12.dp) {
        Text(
            text = "텔레그램 봇 연결",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        OutlinedTextField(
            value = botToken,
            onValueChange = onBotTokenChange,
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
                    IconButton(onClick = onToggleShowToken) {
                        Icon(
                            imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "토큰 표시 전환",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onPasteToken) {
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
            onValueChange = onChatIdChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("기본 Chat ID (쉼표로 여러 명 가능)") },
            placeholder = { Text("예: 123456789 또는 123456789, -100...", fontSize = 13.sp) },
            trailingIcon = {
                IconButton(onClick = onPasteChatId) {
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
            onClick = onFetchChats,
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
                onClick = onSaveCredentials,
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
                onClick = onSendTest,
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onToggleBotGuide() }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "봇 토큰·Chat ID 얻는 방법",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = if (showBotGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        if (showBotGuide) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InstructionStep("1.", "텔레그램에서 @BotFather 검색 후 /newbot 명령어로 새 봇 생성")
                    InstructionStep("2.", "발급받은 Bot Token을 복사해 위에 입력")
                    InstructionStep("3.", "생성한 봇 채팅방에 들어가 /start 메시지 전송")
                    InstructionStep("4.", "위 'Chat ID 자동 가져오기' 버튼을 눌러 자동 입력 후 저장")
                }
            }
        }
    }

    // Card 2: Chat ID Profiles & Multi-Recipient Management
    SettingsSectionCard(spacing = 12.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "수신자 프로필 (사람·채팅방별)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "자주 보내는 사람·방 이름을 저장해 앱별 전송이나 2명+ 동시 전송에 사용합니다",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onAddProfile,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("프로필 추가", fontSize = 12.sp)
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ) {
            Text(
                text = when {
                    defaultRecipients.isEmpty() ->
                        "현재 기본 수신자: 미설정 (위 기본 Chat ID를 입력하거나 프로필에서 '기본 동시전송'을 켜세요)"
                    defaultRecipients.size == 1 ->
                        "현재 기본 수신자: ${defaultRecipients[0].label} (${defaultRecipients[0].chatId})"
                    else ->
                        "현재 기본 수신자 (${defaultRecipients.size}명 동시 전송): " +
                            defaultRecipients.joinToString(", ") { it.label }
                },
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(10.dp)
            )
        }

        if (chatProfiles.isEmpty()) {
            Text(
                text = "저장된 프로필이 없습니다. '+ 프로필 추가'를 누르거나 위 'Chat ID 자동 가져오기 -> 프로필로 저장'을 이용해 보세요.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val hasActiveToken = botToken.ifBlank { savedBotToken }.isNotBlank()
            chatProfiles.forEachIndexed { idx, profile ->
                val isInPrimary = profile.chatId.trim() in primaryChatIds
                val isDefaultExtra = profile.id in defaultProfileIds

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = profile.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = profile.chatId,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onTestProfile(profile) },
                                enabled = hasActiveToken,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "프로필 테스트 전송",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            IconButton(
                                onClick = { onEditProfile(profile) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "프로필 수정",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDeleteProfile(profile) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "프로필 삭제",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isInPrimary) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "기본 Chat ID에 포함됨",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            FilterChip(
                                selected = isDefaultExtra,
                                onClick = { onToggleDefaultProfile(profile.id) },
                                label = {
                                    Text(
                                        text = if (isDefaultExtra) "기본 동시전송 ON" else "기본 동시전송",
                                        fontSize = 11.sp
                                    )
                                }
                            )
                        }
                    }
                }

                if (idx < chatProfiles.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun FeaturesTabContent(
    incomingCallAlertEnabled: Boolean,
    lmsEnrichmentEnabled: Boolean,
    imageForwardingEnabled: Boolean,
    telegramReplyEnabled: Boolean,
    readPhoneStateGranted: Boolean,
    readCallLogGranted: Boolean,
    readContactsGranted: Boolean,
    readSmsGranted: Boolean,
    onToggleIncomingCallAlert: (Boolean) -> Unit,
    onRequestCallPermissions: () -> Unit,
    onToggleLmsEnrichment: (Boolean) -> Unit,
    onRequestSmsPermission: () -> Unit,
    onToggleImageForwarding: (Boolean) -> Unit,
    onToggleTelegramReply: (Boolean) -> Unit
) {
    SettingsSectionCard(spacing = 14.dp) {
        Text(
            text = "알림·전화 전달 기능",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        SwitchSettingRow(
            title = "수신 전화 즉시 알림",
            subtitle = when {
                incomingCallAlertEnabled && readPhoneStateGranted && readCallLogGranted && readContactsGranted ->
                    "활성화됨 · 발신자 이름·전화번호를 즉시 텔레그램으로 전송"
                incomingCallAlertEnabled && readPhoneStateGranted && readCallLogGranted ->
                    "활성화됨 · 발신번호 즉시 전송 (연락처 권한 허용 시 이름도 표시)"
                incomingCallAlertEnabled && readPhoneStateGranted ->
                    "전화 감지 활성 · 통화 기록 권한 허용 시 발신번호 표시"
                incomingCallAlertEnabled ->
                    "전화·통화 기록 권한 필요 (아래 버튼을 눌러 허용하세요)"
                else -> "비활성화 (수신 전화는 텔레그램으로 알리지 않음)"
            },
            checked = incomingCallAlertEnabled,
            onCheckedChange = onToggleIncomingCallAlert
        )
        if (incomingCallAlertEnabled && (!readPhoneStateGranted || !readCallLogGranted || !readContactsGranted)) {
            OutlinedButton(
                onClick = onRequestCallPermissions,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (readPhoneStateGranted && readCallLogGranted) {
                        "연락처 이름 표시 권한도 허용하기"
                    } else {
                        "전화·발신번호 권한 허용하기"
                    },
                    fontSize = 12.sp
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SwitchSettingRow(
            title = "긴 문자(LMS/MMS) 원문 보강",
            subtitle = when {
                lmsEnrichmentEnabled && readSmsGranted ->
                    "활성화됨 · 잘린 장문/MMS도 문자함에서 전체 원문 조회"
                lmsEnrichmentEnabled && !readSmsGranted ->
                    "SMS 읽기 권한 필요 (아래 버튼을 눌러 허용하세요)"
                else -> "비활성화 (알림창 요약 텍스트만 전송)"
            },
            checked = lmsEnrichmentEnabled,
            onCheckedChange = onToggleLmsEnrichment
        )
        if (lmsEnrichmentEnabled && !readSmsGranted) {
            OutlinedButton(
                onClick = onRequestSmsPermission,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("SMS 읽기 권한 허용하기 (LMS/MMS 원문 읽기)", fontSize = 12.sp)
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SwitchSettingRow(
            title = "이미지(사진) 함께 전송",
            subtitle = "알림 미리보기 사진(카톡·CCTV 등)과 MMS 첨부 이미지를 텔레그램으로 전송합니다",
            checked = imageForwardingEnabled,
            onCheckedChange = onToggleImageForwarding
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SwitchSettingRow(
            title = "텔레그램에서 바로 답장 (카톡·문자)",
            subtitle = "텔레그램에서 받은 카톡·문자 알림에 스와이프 답장하면 휴대폰에서 자동 회신합니다 · 상주 서비스 ON 권장",
            checked = telegramReplyEnabled,
            onCheckedChange = onToggleTelegramReply
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Text(
            text = "앱별 맞춤 수신자·포맷 설정은 하단 '앱 선택' 탭에서 앱 우측 ⚙ 버튼으로 변경할 수 있습니다.",
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FiltersScheduleTabContent(
    keywordFilterEnabled: Boolean,
    includeKeywordsInput: String,
    onIncludeKeywordsChange: (String) -> Unit,
    excludeKeywordsInput: String,
    onExcludeKeywordsChange: (String) -> Unit,
    ignoreOngoing: Boolean,
    dedupeWindowSeconds: Int,
    quietHoursEnabled: Boolean,
    quietStartHour: Int,
    quietStartMinute: Int,
    quietEndHour: Int,
    quietEndMinute: Int,
    dailyResetEnabled: Boolean,
    dailyResetHour: Int,
    dailyResetMinute: Int,
    onToggleKeywordFilter: (Boolean) -> Unit,
    onSaveKeywordFilter: () -> Unit,
    onToggleIgnoreOngoing: (Boolean) -> Unit,
    onSelectDedupeWindow: (Int) -> Unit,
    onToggleQuietHours: (Boolean) -> Unit,
    onPickQuietStart: () -> Unit,
    onPickQuietEnd: () -> Unit,
    onToggleDailyReset: (Boolean) -> Unit,
    onPickDailyResetTime: () -> Unit
) {
    // Card 1: Notification Filters
    SettingsSectionCard(spacing = 14.dp) {
        Text(
            text = "알림 필터",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        SwitchSettingRow(
            title = "키워드 필터",
            subtitle = if (keywordFilterEnabled) "포함/제외 키워드 적용 중" else "모든 알림 내용 허용",
            checked = keywordFilterEnabled,
            onCheckedChange = onToggleKeywordFilter
        )

        if (keywordFilterEnabled) {
            OutlinedTextField(
                value = includeKeywordsInput,
                onValueChange = onIncludeKeywordsChange,
                label = { Text("포함 키워드 (쉼표로 구분, 비우면 모두 허용)") },
                placeholder = { Text("예: 입금, 인증번호, 택배") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = excludeKeywordsInput,
                onValueChange = onExcludeKeywordsChange,
                label = { Text("제외 키워드 (쉼표로 구분)") },
                placeholder = { Text("예: 광고, 이벤트, 홍보") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = onSaveKeywordFilter,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("키워드 필터 저장", fontSize = 13.sp)
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SwitchSettingRow(
            title = "진행 중(상단바 고정) 알림 제외",
            subtitle = "음악 재생, 타이머, 파일 다운로드 등 지속 알림 무시",
            checked = ignoreOngoing,
            onCheckedChange = onToggleIgnoreOngoing
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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
                        selected = dedupeWindowSeconds == sec,
                        onClick = { onSelectDedupeWindow(sec) },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }
        }
    }

    // Card 2: Time & Schedule
    SettingsSectionCard(spacing = 14.dp) {
        Text(
            text = "시간·스케줄",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        SwitchSettingRow(
            title = "방해금지 시간대",
            subtitle = if (quietHoursEnabled) {
                "${formatTime(quietStartHour, quietStartMinute)} ~ ${formatTime(quietEndHour, quietEndMinute)}"
            } else {
                "비활성화"
            },
            checked = quietHoursEnabled,
            onCheckedChange = onToggleQuietHours
        )
        if (quietHoursEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onPickQuietStart,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "시작: ${formatTime(quietStartHour, quietStartMinute)}",
                        fontSize = 13.sp
                    )
                }
                OutlinedButton(
                    onClick = onPickQuietEnd,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "종료: ${formatTime(quietEndHour, quietEndMinute)}",
                        fontSize = 13.sp
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SwitchSettingRow(
            title = "일일 통계 자동 초기화",
            subtitle = if (dailyResetEnabled) {
                "매일 ${formatTime(dailyResetHour, dailyResetMinute)}에 오늘 전송 카운트 초기화"
            } else {
                "비활성화"
            },
            checked = dailyResetEnabled,
            onCheckedChange = onToggleDailyReset
        )
        if (dailyResetEnabled) {
            OutlinedButton(
                onClick = onPickDailyResetTime,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "초기화 시각 변경: ${formatTime(dailyResetHour, dailyResetMinute)}",
                    fontSize = 13.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionsSystemTabContent(
    requiredPermsDone: Int,
    requiredPermsMissing: Boolean,
    postNotifGranted: Boolean,
    notifAccessEnabled: Boolean,
    ignoringBattery: Boolean,
    readPhoneStateGranted: Boolean,
    readCallLogGranted: Boolean,
    readContactsGranted: Boolean,
    readSmsGranted: Boolean,
    backgroundEnabled: Boolean,
    themeMode: ThemeMode,
    hasCrashLog: Boolean,
    onOpenWizard: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onRequestCallPermissions: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onToggleBackground: (Boolean) -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onShowCrashDialog: () -> Unit
) {
    // Card 1: Unified Permissions Management
    SettingsSectionCard(spacing = 14.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "권한 관리",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (requiredPermsMissing) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                }
            ) {
                Text(
                    text = "필수 $requiredPermsDone/3 완료",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (requiredPermsMissing) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        FilledTonalButton(
            onClick = onOpenWizard,
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SwitchSettingRow(
            title = "알림 표시 권한",
            subtitle = when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> "허용됨 (자동 허용 OS)"
                postNotifGranted -> "허용됨 (상단바 상태 표시 가능)"
                else -> "필수 · 미허용 (터치하여 허용 요청)"
            },
            checked = postNotifGranted,
            onCheckedChange = {
                if (!postNotifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    onRequestNotificationPermission()
                } else {
                    onOpenAppDetails()
                }
            }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SwitchSettingRow(
            title = "알림 접근 권한",
            subtitle = if (notifAccessEnabled) {
                "핵심 필수 · 허용됨"
            } else {
                "핵심 필수 · 미허용 (터치하여 시스템 설정 열기)"
            },
            checked = notifAccessEnabled,
            onCheckedChange = { onOpenNotificationListenerSettings() }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SwitchSettingRow(
            title = "배터리 예외 등록",
            subtitle = if (ignoringBattery) {
                "권장 · 예외 등록됨"
            } else {
                "권장 · 예외 등록 필요 (터치하여 설정 열기)"
            },
            checked = ignoringBattery,
            onCheckedChange = { onOpenBatterySettings() }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SwitchSettingRow(
            title = "수신 전화 권한 (전화·발신번호·연락처)",
            subtitle = when {
                readPhoneStateGranted && readCallLogGranted && readContactsGranted ->
                    "선택 · 전체 허용됨 (발신자 이름·전화번호 표시)"
                readPhoneStateGranted && readCallLogGranted ->
                    "선택 · 발신번호 허용됨 (터치하여 연락처 이름 표시도 허용)"
                readPhoneStateGranted ->
                    "선택 · 전화만 허용됨 (터치하여 통화 기록·발신번호 허용)"
                else -> "선택 · 미허용 (터치하여 전화·발신번호·연락처 허용)"
            },
            checked = readPhoneStateGranted && readCallLogGranted,
            onCheckedChange = {
                if (readPhoneStateGranted && readCallLogGranted && readContactsGranted) {
                    onOpenAppDetails()
                } else {
                    onRequestCallPermissions()
                }
            }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SwitchSettingRow(
            title = "SMS 읽기 권한 (긴 문자 원문)",
            subtitle = if (readSmsGranted) {
                "선택 · 허용됨 (긴 문자·MMS 원문 조회)"
            } else {
                "선택 · 미허용 (터치하여 SMS 읽기 권한 허용)"
            },
            checked = readSmsGranted,
            onCheckedChange = {
                if (!readSmsGranted) {
                    onRequestSmsPermission()
                } else {
                    onOpenAppDetails()
                }
            }
        )

        OutlinedButton(
            onClick = onOpenAppDetails,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("앱 정보 열기 (권한·제한된 설정)", fontSize = 12.sp)
        }
    }

    // Card 2: Service & Theme
    SettingsSectionCard(spacing = 14.dp) {
        Text(
            text = "서비스 및 화면",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        SwitchSettingRow(
            title = "백그라운드 상주 서비스",
            subtitle = if (backgroundEnabled) "항상 실행 (상단바 작동 상태 표시)" else "비활성화",
            checked = backgroundEnabled,
            onCheckedChange = onToggleBackground
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
                        selected = themeMode == mode,
                        onClick = { onSelectTheme(mode) },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }
        }

        if (hasCrashLog) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Button(
                onClick = onShowCrashDialog,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatProfileEditorDialog(
    initialProfile: ChatProfile,
    botToken: String,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSave: (name: String, chatId: String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var nameInput by remember { mutableStateOf(initialProfile.name) }
    var chatIdInput by remember { mutableStateOf(initialProfile.chatId) }
    var isFetching by remember { mutableStateOf(false) }
    var fetchedChats by remember { mutableStateOf<List<DetectedChat>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialProfile.id.isBlank()) "수신자 프로필 추가" else "수신자 프로필 수정",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "프로필 이름을 지정해 두면 앱 선택 화면에서 쉽게 골라 보낼 수 있습니다.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("프로필 이름 (별칭)") },
                    placeholder = { Text("예: 아내, 나, 회사 단톡방") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = chatIdInput,
                    onValueChange = { chatIdInput = it },
                    label = { Text("텔레그램 Chat ID") },
                    placeholder = { Text("예: 123456789 또는 -100...") },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                clipboard.getText()?.let { chatIdInput = it.text.trim() }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "붙여넣기",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        if (isFetching) return@OutlinedButton
                        val cleanToken = botToken.trim()
                        if (cleanToken.isBlank()) {
                            Toast.makeText(context, "먼저 봇 토큰을 입력하세요.", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        isFetching = true
                        viewModel.fetchChats(cleanToken) { chats, err ->
                            isFetching = false
                            when {
                                err != null -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                chats.isEmpty() -> Toast.makeText(
                                    context,
                                    "감지된 채팅방이 없습니다. 봇 방에서 메시지를 먼저 보내 보세요.",
                                    Toast.LENGTH_LONG
                                ).show()
                                else -> fetchedChats = chats
                            }
                        }
                    },
                    enabled = !isFetching,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isFetching) {
                        CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("최근 채팅방 조회 중...", fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("최근 봇 대화방에서 불러오기", fontSize = 12.sp)
                    }
                }

                if (fetchedChats.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        fetchedChats.forEach { chat ->
                            FilterChip(
                                selected = chatIdInput.trim() == chat.chatId.trim(),
                                onClick = {
                                    chatIdInput = chat.chatId
                                    if (nameInput.isBlank()) {
                                        nameInput = chat.title
                                    }
                                },
                                label = { Text("${chat.title} (${chat.chatId})", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(nameInput, chatIdInput) }) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
