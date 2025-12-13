package com.example.telegramalramrelay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val DEFAULT_FORMAT = "📱 {app}\n📌 {title}\n💬 {text}\n🕐 {time}"

private val PRESET_FORMATS = listOf(
    "기본" to "📱 {app}\n📌 {title}\n💬 {text}\n🕐 {time}",
    "간단" to "[{app}] {title}\n{text}",
    "상세" to "━━━━━━━━━━━━\n📱 앱: {app}\n📌 제목: {title}\n💬 내용: {text}\n🕐 시간: {time}\n━━━━━━━━━━━━",
    "최소" to "{app}: {text}"
)

private val VARIABLES = listOf(
    "{app}" to "앱 이름",
    "{title}" to "알림 제목",
    "{text}" to "알림 내용",
    "{time}" to "시간",
    "{package}" to "패키지명"
)

private val SAMPLE_DATA = mapOf(
    "app" to "카카오톡",
    "title" to "친구 이름",
    "text" to "안녕하세요! 오늘 저녁 약속 괜찮으신가요?",
    "time" to "오후 2:30",
    "package" to "com.kakao.talk"
)

@Composable
fun FormatSettingsScreen() {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var format by remember { mutableStateOf(prefs.getString("message_format", DEFAULT_FORMAT) ?: DEFAULT_FORMAT) }

    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(scroll)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFF2563EB), shape = RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "알림 포맷 설정", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(text = "텔레그램으로 전송될 메시지 형식 설정", color = Color(0xFF6B7280), fontSize = 12.sp)
        }

        // Presets
        Column {
            Text(text = "프리셋 템플릿", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            PRESET_FORMATS.chunked(2).forEach { pair ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { (name, preset) ->
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { format = preset },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = name, color = Color(0xFF111827))
                                Text(text = preset.split('\n')[0], color = Color(0xFF9CA3AF), fontSize = 12.sp)
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Editor
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "메시지 포맷", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    TextButton(onClick = { format = DEFAULT_FORMAT }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "초기화", fontSize = 12.sp)
                    }
                }

                OutlinedTextField(
                    value = format,
                    onValueChange = { format = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    placeholder = { Text("메시지 포맷을 입력하세요...") },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    singleLine = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF9FAFB),
                        unfocusedContainerColor = Color(0xFFF9FAFB),
                        focusedBorderColor = Color(0xFF2563EB),
                        unfocusedBorderColor = Color(0xFFE5E7EB)
                    )
                )
                Text(text = "변수를 사용하여 동적으로 정보를 표시할 수 있습니다", color = Color(0xFF9CA3AF), fontSize = 12.sp)
            }
        }

        // Variables
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "사용 가능한 변수", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                VARIABLES.forEach { (key, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { format += key }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = key, color = Color(0xFF7C3AED), fontFamily = FontFamily.Monospace)
                            Text(text = desc, color = Color(0xFF6B7280), fontSize = 12.sp)
                        }
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF9CA3AF))
                    }
                    Divider()
                }
            }
        }

        // Preview removed: 메시지 포맷 카드에서 포맷 편집으로 대체됨

        // Tips
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5E6FF)), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "💡 팁", color = Color(0xFF6D28D9), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(text = "이모지를 사용하여 메시지를 더 알아보기 쉽게 만들 수 있습니다", color = Color(0xFF6D28D9), fontSize = 12.sp)
                Text(text = "줄바꿈을 사용하여 정보를 구분하세요", color = Color(0xFF6D28D9), fontSize = 12.sp)
                Text(text = "변수를 클릭하면 자동으로 에디터에 추가됩니다", color = Color(0xFF6D28D9), fontSize = 12.sp)
                Text(text = "미리보기에서 실제 메시지 형태를 확인하세요", color = Color(0xFF6D28D9), fontSize = 12.sp)
            }
        }

        // Save
        Button(
            onClick = {
                prefs.edit().putString("message_format", format).apply()
                Toast.makeText(ctx, "포맷이 저장되었습니다.", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "포맷 저장", color = Color.White, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        // Preview
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "미리보기", fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = getPreview(format), fontFamily = FontFamily.Monospace)
            }
        }
    }
}

private fun getPreview(format: String): String {
    var preview = format
    preview = preview.replace("{app}", SAMPLE_DATA["app"].orEmpty())
    preview = preview.replace("{title}", SAMPLE_DATA["title"].orEmpty())
    preview = preview.replace("{text}", SAMPLE_DATA["text"].orEmpty())
    preview = preview.replace("{time}", SAMPLE_DATA["time"].orEmpty())
    preview = preview.replace("{package}", SAMPLE_DATA["package"].orEmpty())
    return preview
}
