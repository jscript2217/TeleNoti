package com.example.telegramalramrelay.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.telegramalramrelay.ui.MainViewModel
import com.example.telegramalramrelay.util.openAppDetailsSettings
import com.example.telegramalramrelay.util.openBatteryOptimizationSettings
import com.example.telegramalramrelay.util.openNotificationListenerSettings

@Composable
fun PermissionWizardDialog(
    viewModel: MainViewModel,
    onRequestNotificationPermission: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onRequestCallPermissions: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val postNotifGranted by viewModel.postNotificationsGranted.collectAsStateWithLifecycle()
    val notifAccessEnabled by viewModel.notificationAccessEnabled.collectAsStateWithLifecycle()
    val ignoringBattery by viewModel.ignoringBatteryOptimizations.collectAsStateWithLifecycle()
    val readSmsGranted by viewModel.readSmsGranted.collectAsStateWithLifecycle()
    val readPhoneStateGranted by viewModel.readPhoneStateGranted.collectAsStateWithLifecycle()
    val readCallLogGranted by viewModel.readCallLogGranted.collectAsStateWithLifecycle()
    val readContactsGranted by viewModel.readContactsGranted.collectAsStateWithLifecycle()
    val serviceRunning by viewModel.serviceRunning.collectAsStateWithLifecycle()

    val requiredStepsDone = listOf(
        postNotifGranted,
        notifAccessEnabled,
        ignoringBattery
    ).count { it }
    val progress = requiredStepsDone / 3f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 660.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "권한 설정 도우미",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "필수·권장 설정 완료: $requiredStepsDone / 3 (허용 후 돌아오면 자동 반영됩니다)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Step 1: POST_NOTIFICATIONS
                    WizardStepCard(
                        stepTitle = "1. 알림 표시 권한",
                        badge = if (postNotifGranted) "완료" else "필수",
                        done = postNotifGranted,
                        description = "상단바에서 포워딩 작동 여부를 실시간으로 확인하려면 알림 표시 권한이 필요합니다."
                    ) {
                        if (!postNotifGranted) {
                            Button(
                                onClick = { onRequestNotificationPermission() },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("권한 허용 요청", fontSize = 12.sp)
                            }
                        }
                    }

                    // Step 2: Notification Listener + One UI 7 Restricted Settings helper
                    WizardStepCard(
                        stepTitle = "2. 알림 접근 권한",
                        badge = if (notifAccessEnabled) "완료" else "핵심 필수",
                        done = notifAccessEnabled,
                        description = "다른 앱의 알림을 읽어 텔레그램으로 전달하는 가장 중요한 권한입니다. 목록에서 '텔레노티'를 찾아 켜 주세요."
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (!openNotificationListenerSettings(context)) {
                                        Toast.makeText(context, "알림 접근 설정을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text("알림 접근 열기", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    if (!openAppDetailsSettings(context)) {
                                        Toast.makeText(context, "앱 정보를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text("앱 정보 열기", fontSize = 12.sp)
                            }
                        }

                        if (!notifAccessEnabled) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "💡 스위치가 회색으로 안 눌리나요? (One UI 7 제한된 설정)\n" +
                                        "위 '앱 정보 열기' → 우측 상단 ⋮ 메뉴 → '제한된 설정 허용'을 먼저 누른 뒤 '알림 접근 열기'로 돌아가세요.",
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }

                    // Step 3: Battery optimization
                    WizardStepCard(
                        stepTitle = "3. 배터리 최적화 예외",
                        badge = if (ignoringBattery) "완료" else "권장",
                        done = ignoringBattery,
                        description = "화면이 꺼진 뒤에도 포워딩이 멈추지 않게 합니다. (삼성 기기는 앱 정보 > 배터리 > '제한 없음' 권장)"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (!openBatteryOptimizationSettings(context)) {
                                        Toast.makeText(context, "배터리 설정을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text("배터리 예외 요청", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { openAppDetailsSettings(context) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text("앱 정보(제한 없음)", fontSize = 12.sp)
                            }
                        }
                    }

                    // Step 4: Foreground service keep-alive indicator
                    val isKeepAliveOn = serviceRunning || settings.backgroundEnabled
                    WizardStepCard(
                        stepTitle = "4. 상단바 작동 표시 (상주 서비스)",
                        badge = if (isKeepAliveOn) "실행 중" else "추천",
                        done = isKeepAliveOn,
                        description = "상단바에 '텔레노티 · 포워딩 작동 중' 알림을 띄워 현재 동작 여부를 한눈에 확인하고 일시중지도 바로 할 수 있습니다."
                    ) {
                        Button(
                            onClick = {
                                val next = !isKeepAliveOn
                                val res = viewModel.setBackgroundEnabled(next)
                                if (res.isFailure) {
                                    Toast.makeText(
                                        context,
                                        "서비스 시작 실패: ${res.exceptionOrNull()?.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(if (isKeepAliveOn) "끄기" else "바로 켜기", fontSize = 12.sp)
                        }
                    }

                    // Step 5: Optional READ_SMS for LMS/MMS full text
                    WizardStepCard(
                        stepTitle = "5. 긴 문자(LMS/MMS) 원문 보강",
                        badge = if (readSmsGranted) "완료" else "선택",
                        done = readSmsGranted,
                        description = "긴 문자(LMS)나 MMS가 알림창에 '...' 또는 '멀티미디어 메시지'로 줄여져 올 때 문자함에서 전체 원문을 읽어옵니다."
                    ) {
                        if (!readSmsGranted) {
                            Button(
                                onClick = { onRequestSmsPermission() },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("SMS 읽기 권한 허용", fontSize = 12.sp)
                            }
                        }
                    }

                    // Step 6: Optional Incoming Call instant alert permissions
                    val callDone = readPhoneStateGranted && readCallLogGranted
                    val callBadge = when {
                        readPhoneStateGranted && readCallLogGranted && readContactsGranted -> "완료"
                        readPhoneStateGranted && readCallLogGranted -> "번호 완료"
                        readPhoneStateGranted -> "일부"
                        else -> "선택"
                    }
                    val callDescription = when {
                        readPhoneStateGranted && readCallLogGranted && readContactsGranted ->
                            "전화가 오면 발신자 이름과 전화번호를 즉시 텔레그램으로 알려드립니다."
                        readPhoneStateGranted && readCallLogGranted ->
                            "전화가 오면 발신번호를 즉시 알려드립니다. 연락처 권한을 추가하면 저장된 이름도 함께 표시됩니다."
                        readPhoneStateGranted ->
                            "전화 울림은 감지되지만 통화 기록 권한이 없어 번호가 보이지 않습니다. 발신번호를 보려면 허용해 주세요."
                        else ->
                            "벨이 울리는 즉시 텔레그램으로 알림을 보내려면 전화·통화 기록 권한이 필요합니다. (연락처 권한 허용 시 이름도 표시 · One UI 7에서 차단 시 위 '앱 정보 열기'에서 제한된 설정을 먼저 허용하세요)"
                    }
                    WizardStepCard(
                        stepTitle = "6. 수신 전화 즉시 알림",
                        badge = callBadge,
                        done = callDone,
                        description = callDescription
                    ) {
                        if (!readPhoneStateGranted || !readCallLogGranted || !readContactsGranted) {
                            Button(
                                onClick = { onRequestCallPermissions() },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (callDone) "연락처 이름 표시도 허용" else "전화·발신번호 권한 허용",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            viewModel.refreshSystemStatus()
                            Toast.makeText(context, "권한 상태를 다시 확인했습니다.", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("상태 새로고침")
                    }
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("닫기")
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardStepCard(
    stepTitle: String,
    badge: String,
    done: Boolean,
    description: String,
    actions: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (done) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (done) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stepTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (done) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Text(
                        text = badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (done) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            actions()
        }
    }
}
