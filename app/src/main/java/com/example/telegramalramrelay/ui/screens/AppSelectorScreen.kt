package com.example.telegramalramrelay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class AppInfo(
    val id: String,
    val name: String,
    val emoji: String,
    val packageName: String,
    var enabled: Boolean
)

@Composable
fun AppSelectorScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var apps by remember {
        mutableStateOf(listOf<AppInfo>())
    }

    // load installed apps once
    LaunchedEffect(Unit) {
        try {
            val pm = context.packageManager
            val selectedJson = prefs.getString("selected_apps", null)
            val selectedSet = mutableSetOf<String>()
            if (!selectedJson.isNullOrEmpty()) {
                val arr = JSONArray(selectedJson)
                for (i in 0 until arr.length()) selectedSet.add(arr.getString(i))
            }

            // Query for launchable activities (recommended way to list apps with LAUNCHER icons)
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val resolveInfos = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
            val packageNames = resolveInfos.map { it.activityInfo.packageName }.distinct()

            val installed = packageNames.mapNotNull { pkg ->
                try {
                    val ai = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
                    AppInfo(
                        id = pkg,
                        name = pm.getApplicationLabel(ai).toString(),
                        emoji = "",
                        packageName = pkg,
                        enabled = selectedSet.contains(pkg)
                    )
                } catch (e: Exception) {
                    null
                }
            }.sortedWith(compareByDescending<AppInfo> { it.enabled }.thenBy { it.name })

            apps = installed
            // ensure connected_apps count saved
            prefs.edit().putInt("connected_apps", apps.count { it.enabled }).apply()
        } catch (e: Exception) {
            apps = listOf()
        }
    }
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = apps.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
    }

    val enabledCount = apps.count { it.enabled }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "알림 받을 앱 선택",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Text(
                    text = "${enabledCount}개 앱 활성화됨",
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280)
                )
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("앱 검색...", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(20.dp)
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2563EB),
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color(0xFFF3F4F6),
                    unfocusedContainerColor = Color(0xFFF3F4F6)
                ),
                singleLine = true
            )

            // Quick Filters
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterButton(
                    label = "전체 선택",
                    onClick = {
                        apps = apps.map { it.copy(enabled = true) }
                        // persist
                        val enabledPkgs = apps.map { it.packageName }
                        prefs.edit().putString("selected_apps", JSONArray(enabledPkgs).toString()).putInt("connected_apps", enabledPkgs.size).apply()
                    }
                )
                FilterButton(
                    label = "전체 해제",
                    onClick = {
                        apps = apps.map { it.copy(enabled = false) }
                        prefs.edit().putString("selected_apps", JSONArray().toString()).putInt("connected_apps", 0).apply()
                    }
                )
            }
        }

        Divider(color = Color(0xFFE5E7EB))

        // App List
        if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "검색 결과가 없습니다",
                    color = Color(0xFF9CA3AF),
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn {
                items(filteredApps) { app ->
                    AppItem(
                        app = app,
                        onToggle = {
                            apps = apps.map {
                                if (it.id == app.id) it.copy(enabled = !it.enabled)
                                else it
                            }
                            // persist selected package names
                            val enabledPkgs = apps.filter { it.enabled }.map { it.packageName }
                            prefs.edit().putString("selected_apps", JSONArray(enabledPkgs).toString()).putInt("connected_apps", enabledPkgs.size).apply()
                        }
                    )
                    Divider(color = Color(0xFFF3F4F6))
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
            containerColor = Color(0xFFF3F4F6),
            contentColor = Color(0xFF374151)
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp
        )
    }
}

@Composable
fun AppItem(app: AppInfo, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Icon
        val context = LocalContext.current
        val pm = context.packageManager
        val drawable = remember(app.packageName) {
            try { pm.getApplicationIcon(app.packageName) } catch (e: Exception) { null }
        }
        if (drawable != null) {
            val bitmap: Bitmap = remember(drawable) {
                val b = Bitmap.createBitmap(drawable.intrinsicWidth.coerceAtLeast(1), drawable.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                val canvas = Canvas(b)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                b
            }
            Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF3F4F6)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = app.emoji.ifEmpty { "🔔" },
                    fontSize = 24.sp
                )
            }
        }

        // App Info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = app.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF111827)
            )
            Text(
                text = app.packageName,
                fontSize = 12.sp,
                color = Color(0xFF9CA3AF),
                maxLines = 1
            )
        }

        // Checkbox
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (app.enabled) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (app.enabled) Color(0xFF2563EB) else Color(0xFFD1D5DB),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
