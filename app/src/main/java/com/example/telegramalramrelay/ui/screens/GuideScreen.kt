package com.example.telegramalramrelay.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.telegramalramrelay.domain.MessageFormatter
import com.example.telegramalramrelay.ui.MainViewModel
import com.example.telegramalramrelay.ui.components.PermissionWizardDialog
import com.example.telegramalramrelay.util.openAppDetailsSettings
import com.example.telegramalramrelay.util.openNotificationListenerSettings

@Composable
fun GuideScreen(
    viewModel: MainViewModel,
    onRequestNotificationPermission: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onRequestCallPermissions: () -> Unit
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
    val listenerConnected by viewModel.listenerConnected.collectAsStateWithLifecycle()
    val serviceRunning by viewModel.serviceRunning.collectAsStateWithLifecycle()

    var showWizardDialog by remember { mutableStateOf(false) }

    val appVersion = remember {
        try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            pi.versionName ?: "2.5.0"
        } catch (_: Exception) {
            "2.5.0"
        }
    }

    if (showWizardDialog) {
        PermissionWizardDialog(
            viewModel = viewModel,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onRequestSmsPermission = onRequestSmsPermission,
            onRequestCallPermissions = onRequestCallPermissions,
            onDismiss = { showWizardDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                    .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Help,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = "텔레노티 사용 가이드",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "초기 설정부터 One UI 7 최적화·긴 문자(LMS/MMS) 해결까지",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Section 1: 4-step quick start
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "1. 처음 시작하기 (4단계)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                GuideItem(
                    step = "① 텔레그램 봇 만들기 (@BotFather)",
                    body = "텔레그램에서 @BotFather를 찾아 /newbot을 입력합니다. 봇 표시 이름을 정한 뒤, 아이디(username)는 반드시 'bot'으로 끝나게 입력하세요 (예: my_alarm_bot). 발급된 토큰(숫자:영문문자열)을 복사해 설정 탭에 붙여넣습니다."
                )
                GuideItem(
                    step = "② 내 Chat ID 자동 입력하기",
                    body = "@BotFather가 알려준 내 봇 링크(t.me/내봇아이디)로 들어가 '시작(/start)' 버튼을 한 번 누릅니다. 그다음 텔레노티 설정 탭에서 'Chat ID 자동 가져오기'를 누르면 Chat ID가 자동으로 채워집니다."
                )
                GuideItem(
                    step = "③ 그룹방 / 채널로 받고 싶을 때",
                    body = "원하는 그룹방이나 채널에 봇을 초대(채널은 관리자 추가)한 뒤 메시지를 한 번 보내세요. 이후 'Chat ID 자동 가져오기'를 누르면 '-100...' 형태의 그룹/채널 ID를 선택할 수 있습니다."
                )
                GuideItem(
                    step = "④ 알림 받을 앱 선택 (숨겨진 앱 찾기)",
                    body = "'앱 선택' 탭에서 원하는 앱을 고르세요. 기본 화면에 안 보이면 검색창에 이름을 입력하거나 '시스템 앱 포함'·'최근 알림 온 앱' 칩을 누르세요. 우측 상단 '+ 직접 추가'로 패키지명 입력도 가능합니다."
                )
            }
        }

        // Section 2: Galaxy S21 / One UI 7 (Android 15) Complete Guide + Action Shortcuts
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "2. 갤럭시 S21 · One UI 7 (Android 15) 필수 설정",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Text(
                    text = "• '제한된 설정'으로 알림 접근 스위치가 회색으로 잠겼을 때\n" +
                        "  APK 파일로 직접 설치한 앱은 Android 13~15 보안 정책으로 처음에 차단됩니다.\n" +
                        "  → 아래 '앱 정보 열기' → 우측 상단 ⋮ 메뉴 → [제한된 설정 허용] 클릭 후 다시 알림 접근을 켜세요.",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Text(
                    text = "• 삼성 절전 기능으로 화면이 꺼지면 멈출 때\n" +
                        "  1) 앱 정보 → 배터리 → '제한 없음' 선택\n" +
                        "  2) 설정 → 디바이스 케어 → 배터리 → 백그라운드 사용 제한 → '절전 예외 앱'에 텔레노티 추가\n" +
                        "  3) 최근 앱(멀티태스킹) 화면에서 텔레노티 아이콘 터치 → '이 앱 열어두기'",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Text(
                    text = "• 상단 퀵 패널(빠른 설정창)에 '텔레노티' 버튼 추가\n" +
                        "  One UI 7 빠른 설정창을 내리고 연필(편집) 아이콘을 누르면 '텔레노티' 빠른 설정 타일을 추가할 수 있습니다. 앱을 열지 않고도 전송 켜기/끄기와 작동 여부를 바로 볼 수 있습니다.",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!openAppDetailsSettings(context)) {
                                Toast.makeText(context, "앱 정보를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text("앱 정보 열기", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { openNotificationListenerSettings(context) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text("알림 접근 설정", fontSize = 12.sp)
                    }
                }

                Button(
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
                    Text("권한 설정 도우미 열기", fontSize = 13.sp)
                }
            }
        }

        // Section 3: LMS / MMS Guide
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "3. 긴 문자(LMS / MMS) 및 이미지 전송 안내",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "한국 통신사 장문 문자(LMS)는 내부적으로 MMS 형식으로 수신되어, 알림창에는 '멀티미디어 메시지', '다운로드 중' 또는 앞부분만 '...'으로 줄여져 오는 경우가 많습니다.\n\n" +
                        "해결 및 사진 전송 방법:\n" +
                        "1) 설정 탭 → 필터 및 스케줄 → '긴 문자(LMS/MMS) 원문 보강'과 '이미지(사진) 함께 전송'이 켜져 있는지 확인하고 SMS 읽기 권한을 허용해 주세요.\n" +
                        "2) 카카오톡 사진 미리보기·CCTV·배달 알림 등의 큰 이미지(BigPicture)와 MMS 첨부 사진은 텔레그램 전송 규격에 맞게 자동 압축되어 사진으로 전송됩니다.\n" +
                        "3) 본문이 1,024자를 초과하는 긴 메시지는 사진 전송 직후 전체 텍스트가 이어서 전송되어 내용이 잘리지 않습니다.\n" +
                        "4) 삼성 메시지 앱 → 우측 상단 ⋮ → 설정 → 알림 → '알림 상세 내용 표시'가 켜져 있는지도 확인해 주세요.",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!readSmsGranted) {
                    OutlinedButton(
                        onClick = onRequestSmsPermission,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("지금 SMS 읽기 권한 허용하기", fontSize = 12.sp)
                    }
                }
            }
        }

        // Section 4: Collapsible FAQ + Format Variables
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "4. 자주 묻는 질문 (FAQ)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                ExpandableFaqItem(
                    question = "전화가 올 때도 바로 텔레그램으로 알림을 받을 수 있나요?",
                    answer = "네! 설정 탭의 '수신 전화 즉시 알림'을 켜고 전화·통화 기록 권한을 허용하면 벨이 울리는 즉시 발신번호와 함께 텔레그램으로 알려드립니다. 연락처 권한까지 허용하면 저장된 발신자 이름도 함께 표시됩니다.\n" +
                        "※ One UI 7에서 권한 요청이 차단되면 '앱 정보 열기' → 우측 상단 ⋮ 메뉴 → '제한된 설정 허용'을 먼저 눌러 주세요. (Android 보안 정책상 전화 대신 받기/통화 음성 중계는 지원되지 않으며 수신 알림만 즉시 전달됩니다.)"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ExpandableFaqItem(
                    question = "텔레그램에서 카톡이나 문자로 바로 답장할 수 있나요?",
                    answer = "네! 설정 탭의 '텔레그램에서 바로 답장 (카톡·문자)'이 켜져 있고 알림에 '↩️ 답장 가능'이 지원되는 앱(카카오톡·삼성 메시지·구글 메시지 등)이면, 텔레그램에서 받은 알림 메시지를 길게 누르거나 스와이프하여 '답장(Reply)'으로 전송하면 휴대폰에서 자동으로 회신한 뒤 '✅ 답장 전송 완료' 알림을 보내드립니다.\n" +
                        "※ 카카오톡 알림 내용이 '이름+메시지'로 설정되어 있어야 답장 버튼이 포함되며, '상주 서비스'를 켜두면 화면이 꺼져도 안정적으로 답장 명령을 받을 수 있습니다. 앱을 재시작하기 전 수신한 알림만 답장이 가능합니다."
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ExpandableFaqItem(
                    question = "지금 포워딩이 켜져 있는지 밖에서도 바로 알 수 있나요?",
                    answer = "네. 홈 화면의 '상단바 작동 표시(상주 서비스)'를 켜두면 상단 상태바에 '텔레노티 · 포워딩 작동 중 (오늘 N건)' 알림이 고정 표시되며, 알림 버튼으로 일시중지/재개도 할 수 있습니다. 또한 One UI 7 빠른 설정창 타일에서도 확인할 수 있습니다."
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ExpandableFaqItem(
                    question = "일시적으로 인터넷이 끊기면 알림이 사라지나요?",
                    answer = "아니요. 네트워크 오류나 전송 한도 초과(429) 시 '대기' 상태로 기록된 뒤 인터넷이 복구되면 백그라운드에서 최대 5회 자동 재시도합니다. 홈 화면에서 '실패 재전송'으로 직접 다시 보낼 수도 있습니다."
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ExpandableFaqItem(
                    question = "텔레그램 앱 자체 알림을 선택할 수 없어요.",
                    answer = "텔레노티가 보낸 텔레그램 알림을 다시 텔레그램으로 전달하는 무한 루프를 방지하기 위해 텔레노티 자신과 텔레그램 계열 앱은 선택 목록에서 자동 제외됩니다."
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ExpandableFaqItem(
                    question = "특정 앱만 다른 사람에게 보내거나 2명 이상에게 동시에 보낼 수 있나요?",
                    answer = "네! 설정 탭의 '수신자 프로필 관리'에서 가족·동료·그룹방을 이름으로 저장해 두면 편하게 관리할 수 있습니다.\n" +
                        "• 2명 동시 전송(전체): 기본 Chat ID에 쉼표로 여러 ID를 입력하거나, 저장한 프로필 카드에서 '기본 동시전송' 칩을 켜세요.\n" +
                        "• 앱별 다른 사람/여러 명 지정: '앱 선택' 탭에서 체크된 앱 우측의 슬라이더(맞춤 설정) 아이콘을 눌러 원하는 프로필을 1명 또는 여러 명 선택하면 됩니다."
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ExpandableFaqItem(
                    question = "이전 버전에서 업데이트했는데 기존 토큰이 유지되나요?",
                    answer = "네. 기존 봇 토큰·Chat ID·선택 앱 목록·포맷은 앱 실행 시 자동으로 암호화 저장소로 안전하게 이관됩니다."
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = "메시지 포맷 변수 안내 (기본 형식 추천)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "{app} 앱 이름 · {title} 알림 제목 · {text} 알림 본문\n" +
                        "{time} 수신 시간 · {package} 패키지명",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = MessageFormatter.DEFAULT_FORMAT,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Section 5: Current Device & Permission Diagnostics Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "5. 현재 앱 상태 진단",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                DiagnosticRow("앱 버전", "v$appVersion")
                DiagnosticRow("기기 OS", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                DiagnosticRow("알림 표시 권한", if (postNotifGranted) "허용됨" else "미허용")
                DiagnosticRow("알림 접근 권한", if (notifAccessEnabled) "허용됨" else "미허용")
                DiagnosticRow("배터리 예외 등록", if (ignoringBattery) "예외 등록됨" else "미등록")
                DiagnosticRow("SMS 원문 보강 권한", if (readSmsGranted) "허용됨" else "미허용 (선택)")
                DiagnosticRow(
                    "수신 전화 권한",
                    when {
                        readPhoneStateGranted && readCallLogGranted && readContactsGranted -> "전체 허용됨 (이름·번호)"
                        readPhoneStateGranted && readCallLogGranted -> "전화·번호 허용됨 (연락처 선택)"
                        readPhoneStateGranted -> "전화만 허용됨 (번호 미표시)"
                        else -> "미허용 (선택)"
                    }
                )
                DiagnosticRow("실시간 리스너", if (listenerConnected) "연결됨" else "대기 중")
                DiagnosticRow(
                    "상주 작동 표시",
                    if (serviceRunning || settings.backgroundEnabled) "켜짐" else "꺼짐"
                )
            }
        }
    }
}

@Composable
private fun GuideItem(step: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = step,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = body,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun ExpandableFaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Q.",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = question,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        if (expanded) {
            Text(
                text = "A. $answer",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp)
            )
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
