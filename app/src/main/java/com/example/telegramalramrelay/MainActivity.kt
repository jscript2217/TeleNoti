package com.example.telegramalramrelay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telegramalramrelay.ui.screens.*
import com.example.telegramalramrelay.ui.theme.TelegramalramrelayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Request POST_NOTIFICATIONS permission on Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (!granted) {
                        Toast.makeText(this, "알림 권한이 필요합니다 (앱 설정에서 허용하세요)", Toast.LENGTH_LONG).show()
                    }
                }
                launcher.launch(permission)
            }
        }
        enableEdgeToEdge()
        setContent {
            TelegramalramrelayTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        },
        containerColor = Color(0xFFF9FAFB)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> HomeScreen()
                1 -> AppSelectorScreen()
                2 -> BotSettingsScreen()
                3 -> FormatSettingsScreen()
                4 -> GuideScreen()
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        val items = listOf(
            BottomNavItem("홈", Icons.Default.Home),
            BottomNavItem("앱 선택", Icons.Default.Notifications),
            BottomNavItem("설정", Icons.Default.Settings),
            BottomNavItem("포맷", Icons.Default.Description),
            BottomNavItem("가이드", Icons.Default.Info)
        )

        items.forEachIndexed { index, item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        fontSize = 10.sp
                    )
                },
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF2563EB),
                    selectedTextColor = Color(0xFF2563EB),
                    unselectedIconColor = Color(0xFF6B7280),
                    unselectedTextColor = Color(0xFF6B7280),
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

data class BottomNavItem(
    val label: String,
    val icon: ImageVector
)