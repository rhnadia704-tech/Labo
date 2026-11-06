package com.example.ui.theme

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
import com.example.core.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = IosBlueDarkAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0F325C),
    onPrimaryContainer = Color(0xFFCCE4FF),
    secondary = IosTealAccent,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF133B4D),
    onSecondaryContainer = Color(0xFFD4F2FF),
    tertiary = IosPurpleAccent,
    background = IosDarkBackground,
    onBackground = IosDarkOnBackground,
    surface = IosDarkSurface,
    onSurface = IosDarkOnSurface,
    surfaceVariant = IosDarkSurfaceElevated,
    onSurfaceVariant = IosDarkOnSurfaceVariant,
    outline = IosDarkBorder,
    error = IosRedAccent
)

private val LightColorScheme = lightColorScheme(
    primary = IosBlueAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E9FF),
    onPrimaryContainer = Color(0xFF002959),
    secondary = Color(0xFF0288D1),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1F5FE),
    onSecondaryContainer = Color(0xFF01579B),
    tertiary = Color(0xFF8E24AA),
    background = IosLightBackground,
    onBackground = IosLightOnBackground,
    surface = IosLightSurface,
    onSurface = IosLightOnSurface,
    surfaceVariant = IosLightSurfaceElevated,
    onSurfaceVariant = IosLightOnSurfaceVariant,
    outline = IosLightBorder,
    error = Color(0xFFD32F2F)
)

@Composable
fun LaboTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
