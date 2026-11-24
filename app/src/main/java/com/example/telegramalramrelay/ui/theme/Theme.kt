package com.example.telegramalramrelay.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = TeleBlueLight,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = TeleBlueContainerDark,
    onPrimaryContainer = TeleBlueOnContainerDark,
    secondary = TelePurpleLight,
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = TelePurpleContainerDark,
    onSecondaryContainer = TelePurpleOnContainerDark,
    tertiary = TeleGreenLight,
    onTertiary = Color(0xFF064E3B),
    tertiaryContainer = TeleGreenContainerDark,
    onTertiaryContainer = TeleGreenOnContainerDark,
    background = Color(0xFF0B1120),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B),
    error = TeleErrorDark,
    onError = Color(0xFF450A0A),
    errorContainer = TeleErrorContainerDark,
    onErrorContainer = TeleErrorOnContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = TeleBlue,
    onPrimary = Color.White,
    primaryContainer = TeleBlueContainerLight,
    onPrimaryContainer = TeleBlueOnContainerLight,
    secondary = TelePurple,
    onSecondary = Color.White,
    secondaryContainer = TelePurpleContainerLight,
    onSecondaryContainer = TelePurpleOnContainerLight,
    tertiary = TeleGreen,
    onTertiary = Color.White,
    tertiaryContainer = TeleGreenContainerLight,
    onTertiaryContainer = TeleGreenOnContainerLight,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFF1F5F9),
    error = TeleErrorLight,
    onError = Color.White,
    errorContainer = TeleErrorContainerLight,
    onErrorContainer = TeleErrorOnContainerLight
)

@Composable
fun TelegramalramrelayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
