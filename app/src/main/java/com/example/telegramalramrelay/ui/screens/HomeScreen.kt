package com.example.telegramalramrelay.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.telegramalramrelay.data.DeliveryStatus
import com.example.telegramalramrelay.data.NotificationLogItem
import com.example.telegramalramrelay.data.ThemeMode
import com.example.telegramalramrelay.ui.MainViewModel
import com.example.telegramalramrelay.ui.components.PermissionWizardDialog
import com.example.telegramalramrelay.ui.theme.TeleAmberContainerDark
import com.example.telegramalramrelay.ui.theme.TeleAmberContainerLight
import com.example.telegramalramrelay.ui.theme.TeleAmberDark
import com.example.telegramalramrelay.ui.theme.TeleAmberLight
import com.example.telegramalramrelay.ui.theme.TeleGreen
import com.example.telegramalramrelay.ui.theme.TeleGreenLight
import com.example.telegramalramrelay.util.AppIconCache
import com.example.telegramalramrelay.worker.RetryDeliveryWorker
import java.util.Calendar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToApps: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToGuide: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onRequestCallPermissions: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val listenerConnected by viewModel.listenerConnected.collectAsStateWithLifecycle()
    val serviceRunning by viewModel.serviceRunning.collectAsStateWithLifecycle()
    val replyPollingActive by viewModel.replyPollingActive.collectAsStateWithLifecycle()
    val postNotifGranted by viewModel.postNotificationsGranted.collectAsStateWithLifecycle()
    val notifAccessEnabled by viewModel.notificationAccessEnabled.collectAsStateWithLifecycle()
    val ignoringBattery by viewModel.ignoringBatteryOptimizations.collectAsStateWithLifecycle()
    val readSmsGranted by viewModel.readSmsGranted.collectAsStateWithLifecycle()
    val readPhoneStateGranted by viewModel.readPhoneStateGranted.collectAsStateWithLifecycle()
    val retryingIds by viewModel.retryingIds.collectAsStateWithLifecycle()

    val isDark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    var showClearDialog by remember { mutableStateOf(false) }
    var showWizardDialog by remember { mutableStateOf(false) }
    var isSendingTest by remember { mutableStateOf(false) }

    if (showWizardDialog) {
        PermissionWizardDialog(
            viewModel = viewModel,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onRequestSmsPermission = onRequestSmsPermission,
            onRequestCallPermissions = onRequestCallPermissions,
            onDismiss = { showWizardDialog = false }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("최근 알림 기록 비우기") },
            text = { Text("저장된 최근 알림 기록을 모두 삭제할까요? (오늘 전송 카운트는 유지됩니다)") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearRecentNotifications()
                        showClearDialog = false
                    }
                ) { Text("비우기") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("취소") }
            }
        )
    }

    val pendingOrFailedCount = remember(settings.recentNotifications) {
        settings.recentNotifications.count {
            it.status == DeliveryStatus.PENDING_RETRY || it.status == DeliveryStatus.FAILED
        }
    }

    val requiredDone = listOf(postNotifGranted, notifAccessEnabled, ignoringBattery).count { it }
    val allCorePermissionsReady = requiredDone == 3
    val botConfigured = settings.botToken.isNotBlank() && settings.chatId.isNotBlank()
    val keepAliveOn = serviceRunning || settings.backgroundEnabled

    val cal = Calendar.getInstance()
    val inQuietNow = settings.quietHours.isQuietAt(
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }
            Text(
                text = "텔레노티",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "스마트폰 알림을 텔레그램으로 실시간 전달합니다",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Permission wizard shortcut banner if any core permission is incomplete
        if (!allCorePermissionsReady) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (!notifAccessEnabled) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        if (isDark) TeleAmberContainerDark else TeleAmberContainerLight
                    }
                )
            ) {
                val contentColor = if (!notifAccessEnabled) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    if (isDark) TeleAmberDark else TeleAmberLight
                }
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "권한 설정 도우미 (필수 완료 $requiredDone / 3)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                    }
                    Text(
                        text = if (!notifAccessEnabled) {
                            "알림 접근 권한이 꺼져 있습니다. One UI 7 제한된 설정 해결까지 도우미에서 한 번에 진행하세요."
                        } else {
                            "알림 표시 또는 배터리 예외 설정이 아직 남아 있습니다."
                        },
                        fontSize = 12.sp,
                        color = contentColor
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showWizardDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = contentColor,
                                contentColor = if (!notifAccessEnabled) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("권한 도우미 열기", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (!notifAccessEnabled) {
                            TextButton(onClick = onNavigateToGuide) {
                                Text("One UI 7 안내", fontSize = 12.sp, color = contentColor)
                            }
                        }
                    }
                }
            }
        } else if (!botConfigured) {
            SetupBannerCard(
                title = "텔레그램 봇 정보가 비어 있습니다",
                description = "설정 탭에서 봇 토큰과 Chat ID를 입력하고 저장해 주세요.",
                actionLabel = "봇 설정하기",
                containerColor = if (isDark) TeleAmberContainerDark else TeleAmberContainerLight,
                contentColor = if (isDark) TeleAmberDark else TeleAmberLight,
                onAction = onNavigateToSettings
            )
        } else if (settings.selectedPackages.isEmpty()) {
            SetupBannerCard(
                title = "선택된 앱이 없습니다",
                description = "앱 선택 탭에서 텔레그램으로 전달할 앱을 고르세요.",
                actionLabel = "앱 선택하기",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onAction = onNavigateToApps
            )
        }

        // Real-time Status Dashboard Hero Card
        val statusColor: Color
        val statusContainerColor: Color
        val statusTitle: String
        val statusSubtitle: String
        when {
            !notifAccessEnabled -> {
                statusColor = MaterialTheme.colorScheme.error
                statusContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                statusTitle = "권한 필요 · 포워딩 중단됨"
                statusSubtitle = "알림 접근 권한을 허용해야 동작합니다"
            }
            !botConfigured -> {
                statusColor = if (isDark) TeleAmberDark else TeleAmberLight
                statusContainerColor = if (isDark) TeleAmberContainerDark.copy(alpha = 0.55f) else TeleAmberContainerLight
                statusTitle = "봇 미설정 · 대기 중"
                statusSubtitle = "봇 토큰과 Chat ID를 설정하세요"
            }
            !settings.forwardingEnabled -> {
                statusColor = MaterialTheme.colorScheme.onSurfaceVariant
                statusContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                statusTitle = "포워딩 일시 중지됨"
                statusSubtitle = "우측 스위치를 켜면 즉시 알림을 전달합니다"
            }
            inQuietNow -> {
                statusColor = if (isDark) TeleAmberDark else TeleAmberLight
                statusContainerColor = if (isDark) TeleAmberContainerDark.copy(alpha = 0.55f) else TeleAmberContainerLight
                statusTitle = "방해금지 시간대 적용 중"
                statusSubtitle = "설정한 종료 시각 이후 자동으로 재개됩니다"
            }
            listenerConnected -> {
                statusColor = if (isDark) TeleGreenLight else TeleGreen
                statusContainerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                statusTitle = "실시간 포워딩 작동 중"
                statusSubtitle = "선택한 앱의 새 알림을 즉시 감시하고 있습니다"
            }
            else -> {
                statusColor = if (isDark) TeleAmberDark else TeleAmberLight
                statusContainerColor = if (isDark) TeleAmberContainerDark.copy(alpha = 0.55f) else TeleAmberContainerLight
                statusTitle = "리스너 연결 대기 중"
                statusSubtitle = "아래 '재연결'을 누르거나 잠시 기다려 주세요"
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Hero Status Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(statusContainerColor)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusTitle,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                        Text(
                            text = statusSubtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = settings.forwardingEnabled,
                        onCheckedChange = { viewModel.setForwardingEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 3 Live Diagnostic Rows
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "알림 리스너 서비스",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (listenerConnected) "시스템과 실시간 연결됨" else "연결 대기 중",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (listenerConnected) {
                                    if (isDark) TeleGreenLight else TeleGreen
                                } else {
                                    if (isDark) TeleAmberDark else TeleAmberLight
                                }
                            )
                        }
                        TextButton(
                            onClick = {
                                viewModel.rebindNotificationListener()
                                Toast.makeText(context, "알림 리스너 재연결을 요청했습니다.", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("재연결", fontSize = 12.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "상단바 작동 표시 (상주 서비스)",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (keepAliveOn) {
                                    "켜짐 · 상태바에서 실시간 확인 가능"
                                } else {
                                    "꺼짐 · 켜면 상단바에 작동 상태가 표시됩니다"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (keepAliveOn) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Switch(
                            checked = keepAliveOn,
                            onCheckedChange = { on ->
                                val res = viewModel.setBackgroundEnabled(on)
                                if (res.isFailure) {
                                    Toast.makeText(
                                        context,
                                        "서비스 제어 실패: ${res.exceptionOrNull()?.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    StatusRow("연결된 앱", "${settings.selectedPackages.size}개")
                    StatusRow("오늘 전송된 알림", "${settings.sentToday}개")

                    val badges = buildList {
                        if (settings.telegramReplyEnabled && replyPollingActive) add("양방향 답장")
                        if (settings.incomingCallAlertEnabled && readPhoneStateGranted) add("수신 전화 알림")
                        if (settings.imageForwardingEnabled) add("이미지 전송")
                        if (settings.lmsEnrichmentEnabled && readSmsGranted) add("긴 문자(LMS/MMS) 보강")
                        if (settings.keywordFilter.enabled) add("키워드 필터 ON")
                        if (settings.quietHours.enabled) {
                            add(if (inQuietNow) "방해금지 적용 중" else "방해금지 예약됨")
                        }
                        if (settings.dedupeWindowSeconds > 0) add("중복방지 ${settings.dedupeWindowSeconds}초")
                    }
                    if (badges.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            badges.forEach { badge ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = badge,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Instant one-tap test send from Home
                    OutlinedButton(
                        onClick = {
                            if (isSendingTest) return@OutlinedButton
                            if (!botConfigured) {
                                Toast.makeText(
                                    context,
                                    "먼저 설정 탭에서 봇 토큰과 Chat ID를 저장해 주세요.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onNavigateToSettings()
                                return@OutlinedButton
                            }
                            isSendingTest = true
                            viewModel.sendTestMessage(settings.botToken, settings.chatId) { ok, msg ->
                                isSendingTest = false
                                Toast.makeText(
                                    context,
                                    if (ok) "텔레그램 테스트 전송 성공!" else "전송 실패: $msg",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        enabled = !isSendingTest,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSendingTest) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("테스트 메시지 전송 중...", fontSize = 13.sp)
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("지금 텔레그램 테스트 전송", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Recent Notifications
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "최근 알림 (${settings.recentNotifications.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (pendingOrFailedCount > 0) {
                        TextButton(
                            onClick = {
                                viewModel.retryAllPendingOrFailed { ok, fail ->
                                    Toast.makeText(
                                        context,
                                        "재전송 완료: 성공 ${ok}건, 실패 ${fail}건",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("실패 재전송 ($pendingOrFailedCount)", fontSize = 12.sp)
                        }
                    }
                    if (settings.recentNotifications.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "비우기",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column {
                    if (settings.recentNotifications.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "최근 알림이 없습니다",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        settings.recentNotifications.forEachIndexed { idx, item ->
                            RecentNotificationItem(
                                item = item,
                                isDark = isDark,
                                isRetrying = item.id in retryingIds,
                                onRetry = {
                                    viewModel.retryItem(item.id) { _, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            if (idx < settings.recentNotifications.size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupBannerCard(
    title: String,
    description: String,
    actionLabel: String,
    containerColor: Color,
    contentColor: Color,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            }
            Text(
                text = description,
                fontSize = 12.sp,
                color = contentColor
            )
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = contentColor,
                    contentColor = containerColor
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(text = actionLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun RecentNotificationItem(
    item: NotificationLogItem,
    isDark: Boolean,
    isRetrying: Boolean,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val iconBitmap by produceState<ImageBitmap?>(
        initialValue = AppIconCache.getCached(item.packageName),
        key1 = item.packageName
    ) {
        if (value == null && item.packageName.isNotBlank()) {
            value = AppIconCache.getOrLoad(context, item.packageName)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap!!,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(text = item.emoji, fontSize = 18.sp)
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.appName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (item.canReply) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "↩️ 답장 가능",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (item.hasImage) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "🖼️ 사진",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                DeliveryStatusBadge(item = item, isDark = isDark)
            }

            Text(
                text = item.formattedMessage.replace("\n", " "),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (!item.errorMessage.isNullOrBlank()) {
                Text(
                    text = item.errorMessage,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = item.timeDisplay,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }

        if (item.status != DeliveryStatus.SENT) {
            if (isRetrying) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .padding(2.dp),
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "재전송",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeliveryStatusBadge(item: NotificationLogItem, isDark: Boolean) {
    val label: String
    val bgColor: Color
    val textColor: Color

    when (item.status) {
        DeliveryStatus.SENT -> {
            label = "전송됨"
            bgColor = MaterialTheme.colorScheme.tertiaryContainer
            textColor = MaterialTheme.colorScheme.onTertiaryContainer
        }
        DeliveryStatus.PENDING_RETRY -> {
            label = "대기 (${item.retryCount}/${RetryDeliveryWorker.MAX_RETRY_COUNT})"
            bgColor = if (isDark) TeleAmberContainerDark else TeleAmberContainerLight
            textColor = if (isDark) TeleAmberDark else TeleAmberLight
        }
        DeliveryStatus.FAILED -> {
            label = "실패"
            bgColor = MaterialTheme.colorScheme.errorContainer
            textColor = MaterialTheme.colorScheme.onErrorContainer
        }
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
