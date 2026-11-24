package com.example.telegramalramrelay.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.telegramalramrelay.data.AppCustomRule
import com.example.telegramalramrelay.domain.MessageFormatter
import com.example.telegramalramrelay.ui.InstalledAppEntry
import com.example.telegramalramrelay.ui.MainViewModel
import com.example.telegramalramrelay.util.AppIconCache

private enum class AppFilterTab(val label: String) {
    NORMAL("일반 앱"),
    ALL("시스템 앱 포함"),
    OBSERVED("최근 알림 온 앱")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppSelectorScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isLoadingApps by viewModel.isLoadingApps.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var filterTab by remember { mutableStateOf(AppFilterTab.NORMAL) }
    var editingApp by remember { mutableStateOf<InstalledAppEntry?>(null) }
    var showAddPackageDialog by remember { mutableStateOf(false) }

    val filteredApps = remember(
        installedApps,
        searchQuery,
        filterTab,
        settings.selectedPackages,
        settings.observedPackages
    ) {
        val query = searchQuery.trim()
        installedApps
            .filter { entry ->
                val isSelected = entry.packageName in settings.selectedPackages
                val isObserved = entry.packageName in settings.observedPackages
                if (query.isNotEmpty()) {
                    // When searching, always search across ALL installed apps including system apps
                    entry.name.contains(query, ignoreCase = true) ||
                        entry.packageName.contains(query, ignoreCase = true)
                } else {
                    when (filterTab) {
                        AppFilterTab.NORMAL -> {
                            entry.hasLauncherIcon || !entry.isSystemApp || isSelected || isObserved
                        }
                        AppFilterTab.ALL -> true
                        AppFilterTab.OBSERVED -> isObserved || isSelected
                    }
                }
            }
            .sortedWith(
                compareByDescending<InstalledAppEntry> { it.packageName in settings.selectedPackages }
                    .thenBy { it.isBlockedLoop }
                    .thenBy { it.name.lowercase() }
            )
    }

    editingApp?.let { target ->
        val existingRule = settings.appCustomRules[target.packageName]
        AppCustomRuleDialog(
            appName = target.name,
            packageName = target.packageName,
            initialRule = existingRule ?: AppCustomRule(target.packageName),
            onDismiss = { editingApp = null },
            onSave = { rule ->
                viewModel.setAppCustomRule(rule)
                editingApp = null
                Toast.makeText(context, "${target.name} 맞춤 설정이 저장되었습니다.", Toast.LENGTH_SHORT).show()
            },
            onReset = {
                viewModel.removeAppCustomRule(target.packageName)
                editingApp = null
                Toast.makeText(context, "${target.name} 맞춤 설정이 초기화되었습니다.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showAddPackageDialog) {
        ManualPackageDialog(
            onDismiss = { showAddPackageDialog = false },
            onAdd = { pkgInput ->
                val added = viewModel.addManualPackage(pkgInput)
                if (added) {
                    showAddPackageDialog = false
                    Toast.makeText(context, "$pkgInput 패키지가 추가되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        context,
                        "유효하지 않거나 루프 방지 대상 패키지입니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "알림 받을 앱 선택",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${settings.selectedPackages.size}개 활성화 · 전체 ${installedApps.size}개 앱 감지됨",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = { viewModel.refreshInstalledApps() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "앱 목록 새로고침",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { showAddPackageDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("직접 추가", fontSize = 12.sp)
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("앱 이름 또는 패키지명 검색 (시스템 앱 포함)...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "검색어 지우기",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                singleLine = true
            )

            if (searchQuery.isNotBlank()) {
                Text(
                    text = "검색어 입력 시 시스템·숨겨진 앱까지 전체 설치 앱에서 검색합니다.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AppFilterTab.entries.forEach { tab ->
                        FilterChip(
                            selected = filterTab == tab,
                            onClick = { filterTab = tab },
                            label = { Text(tab.label, fontSize = 12.sp) }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterButton(
                    label = "목록 전체 선택",
                    onClick = {
                        val visibleSafe = filteredApps
                            .filter { !it.isBlockedLoop }
                            .map { it.packageName }
                            .toSet()
                        viewModel.selectPackages(visibleSafe)
                    }
                )
                FilterButton(
                    label = "전체 해제",
                    onClick = { viewModel.clearAllSelectedApps() }
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (isLoadingApps && installedApps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "설치된 전체 앱 목록을 불러오는 중...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (filterTab == AppFilterTab.OBSERVED && searchQuery.isBlank()) {
                            "아직 감지된 알림 앱이 없습니다.\n알림이 한 번이라도 오면 여기에 자동 기록됩니다."
                        } else {
                            "검색 결과가 없습니다"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    if (searchQuery.isNotBlank()) {
                        TextButton(onClick = { showAddPackageDialog = true }) {
                            Text("패키지명 직접 추가하기")
                        }
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val enabled = app.packageName in settings.selectedPackages
                    val isObserved = app.packageName in settings.observedPackages
                    val customRule = settings.appCustomRules[app.packageName]
                    AppItem(
                        app = app,
                        enabled = enabled,
                        isObserved = isObserved,
                        hasCustomRule = customRule?.hasCustomizations == true,
                        onToggle = { viewModel.togglePackage(app.packageName) },
                        onOpenCustomRule = { editingApp = app }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
fun FilterButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(text = label, fontSize = 12.sp)
    }
}

@Composable
private fun AppItem(
    app: InstalledAppEntry,
    enabled: Boolean,
    isObserved: Boolean,
    hasCustomRule: Boolean,
    onToggle: () -> Unit,
    onOpenCustomRule: () -> Unit
) {
    val context = LocalContext.current
    val iconBitmap by produceState<ImageBitmap?>(
        initialValue = AppIconCache.getCached(app.packageName),
        key1 = app.packageName
    ) {
        if (value == null) {
            value = AppIconCache.getOrLoad(context, app.packageName)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !app.isBlockedLoop, onClick = onToggle)
            .background(MaterialTheme.colorScheme.surface)
            .alpha(if (app.isBlockedLoop) 0.55f else 1f)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap!!,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🔔", fontSize = 20.sp)
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = app.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (app.isSystemApp) {
                    SmallTagBadge(
                        text = "시스템",
                        bgColor = MaterialTheme.colorScheme.surfaceVariant,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isObserved) {
                    SmallTagBadge(
                        text = "최근 알림",
                        bgColor = MaterialTheme.colorScheme.tertiaryContainer,
                        textColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                if (hasCustomRule) {
                    SmallTagBadge(
                        text = "맞춤",
                        bgColor = MaterialTheme.colorScheme.primaryContainer,
                        textColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Text(
                text = if (app.isBlockedLoop) {
                    "무한 알림 루프 방지를 위해 선택 불가"
                } else {
                    app.packageName
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (enabled && !app.isBlockedLoop) {
            IconButton(onClick = onOpenCustomRule) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "앱별 맞춤 설정",
                    tint = if (hasCustomRule) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        IconButton(
            onClick = onToggle,
            enabled = !app.isBlockedLoop
        ) {
            Icon(
                imageVector = if (enabled) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun SmallTagBadge(text: String, bgColor: Color, textColor: Color) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = bgColor
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun ManualPackageDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var packageInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("패키지명 직접 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "목록에 보이지 않는 앱이나 특수 모듈의 패키지명을 직접 입력해 등록할 수 있습니다.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = packageInput,
                    onValueChange = { packageInput = it },
                    label = { Text("패키지명") },
                    placeholder = { Text("예: com.samsung.android.messaging") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(packageInput) }) {
                Text("추가")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun AppCustomRuleDialog(
    appName: String,
    packageName: String,
    initialRule: AppCustomRule,
    onDismiss: () -> Unit,
    onSave: (AppCustomRule) -> Unit,
    onReset: () -> Unit
) {
    var customChatId by remember { mutableStateOf(initialRule.customChatId) }
    var customFormat by remember { mutableStateOf(initialRule.customFormat) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = "$appName 맞춤 설정", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = packageName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "비워두면 전역 기본 설정(기본 Chat ID 및 포맷)을 따릅니다.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = customChatId,
                    onValueChange = { customChatId = it },
                    label = { Text("앱 전용 Chat ID") },
                    placeholder = { Text("예: -1001234567890") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = customFormat,
                    onValueChange = { customFormat = it },
                    label = { Text("앱 전용 메시지 포맷") },
                    placeholder = { Text(MessageFormatter.DEFAULT_FORMAT) },
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                )
                TextButton(
                    onClick = { customFormat = MessageFormatter.DEFAULT_FORMAT },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("기본 포맷 불러오기", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        AppCustomRule(
                            packageName = packageName,
                            customChatId = customChatId.trim(),
                            customFormat = customFormat
                        )
                    )
                }
            ) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (initialRule.hasCustomizations) {
                    TextButton(onClick = onReset) {
                        Text("초기화", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        }
    )
}
