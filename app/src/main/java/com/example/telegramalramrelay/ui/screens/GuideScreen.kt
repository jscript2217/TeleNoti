package com.example.telegramalramrelay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GuideScreen() {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.padding(16.dp),
		verticalArrangement = Arrangement.Top
	) {
		// Header like other screens
		Column(
			modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Box(
				modifier = Modifier
					.height(64.dp)
					.width(64.dp)
					.background(Color(0xFF2563EB), shape = RoundedCornerShape(16.dp)),
				contentAlignment = Alignment.Center
			) {
				Icon(imageVector = Icons.Default.Help, contentDescription = null, tint = Color.White)
			}
			Text(
				text = "가이드",
				style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp)
			)
			Text(text = "앱 사용 방법과 자주 묻는 질문", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF6B7280))
		}

		Spacer(modifier = Modifier.height(8.dp))

		// Use same highlighted card background as settings '정보를 얻는 방법'
		Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))) {
			Column(modifier = Modifier.padding(16.dp)) {
				Text(text = "빠른 설정 가이드", style = MaterialTheme.typography.titleMedium)
				Spacer(modifier = Modifier.height(8.dp))

				InstructionStep("1.", "봇 토큰과 챗 ID 설정")
				Text(text = "앱의 '설정' 탭에서 Telegram Bot의 토큰과 전달할 챗 ID를 입력하세요. '저장'을 누르면 값이 앱에 저장됩니다.", style = MaterialTheme.typography.bodyMedium)
				Spacer(modifier = Modifier.height(8.dp))

				InstructionStep("2.", "알림을 전송할 앱 선택")
				Text(text = "'앱 선택' 탭에서 전달할 앱을 선택하세요. 선택한 앱의 알림만 Telegram으로 전달됩니다.", style = MaterialTheme.typography.bodyMedium)
				Spacer(modifier = Modifier.height(8.dp))

				InstructionStep("3.", "권한 허용")
				Text(text = "알림 접근 권한과 배터리 최적화 예외가 필요합니다. '설정'의 권한 카드에서 시스템 설정 화면으로 이동해 허용하세요.", style = MaterialTheme.typography.bodyMedium)
				Spacer(modifier = Modifier.height(8.dp))

				InstructionStep("4.", "서비스 제어")
				Text(text = "권한을 허용한 뒤 홈 화면의 '서비스' 토글을 켜면 서비스가 실행되어 알림 전달을 유지합니다.", style = MaterialTheme.typography.bodyMedium)
			}
		}

		Spacer(modifier = Modifier.height(12.dp))

		Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))) {
			Column(modifier = Modifier.padding(16.dp)) {
				Text(text = "자주 묻는 질문 (FAQ)", style = MaterialTheme.typography.titleMedium)
				Spacer(modifier = Modifier.height(8.dp))

				Row(verticalAlignment = Alignment.Top) {
					Text(text = "Q:", fontWeight = FontWeight.Bold)
					Spacer(modifier = Modifier.width(6.dp))
					Text(text = "알림이 전달되지 않아요.", style = MaterialTheme.typography.bodyMedium)
				}
				Spacer(modifier = Modifier.height(4.dp))
				Text(text = "A: 알림 접근 권한과 앱 선택 상태를 확인하세요. 일부 제조사에서는 배터리 최적화도 해제해야 합니다.", style = MaterialTheme.typography.bodyMedium)

				Spacer(modifier = Modifier.height(12.dp))
				Divider(color = Color(0xFFE0E0E0))
				Spacer(modifier = Modifier.height(12.dp))

				Text(text = "메시지 포맷 예시", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
				Spacer(modifier = Modifier.height(6.dp))
				Text(text = "{{app}} | {{title}}\n{{text}}", style = MaterialTheme.typography.bodyMedium)
			}
		}
	}
}
