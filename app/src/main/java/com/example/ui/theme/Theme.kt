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
    primary = LaboCyanPrimary,
    onPrimary = Color(0xFF00363D),
    primaryContainer = LaboCyanContainer,
    onPrimaryContainer = LaboOnCyanContainer,
    secondary = LaboIndigoPrimary,
    onSecondary = Color.White,
    secondaryContainer = LaboIndigoContainer,
    onSecondaryContainer = LaboOnIndigoContainer,
    tertiary = Color(0xFF64B5F6),
    background = LaboDarkBackground,
    onBackground = LaboDarkOnBackground,
    surface = LaboDarkSurface,
    onSurface = LaboDarkOnSurface,
    surfaceVariant = LaboDarkSurfaceVariant,
    onSurfaceVariant = LaboDarkOnSurfaceVariant,
    outline = LaboDarkBorder,
    error = LaboDangerRed
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF007A87),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBCEBFF),
    onPrimaryContainer = Color(0xFF001F24),
    secondary = Color(0xFF304FFE),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE1FF),
    onSecondaryContainer = Color(0xFF00105C),
    tertiary = Color(0xFF0288D1),
    background = LaboLightBackground,
    onBackground = LaboLightOnBackground,
    surface = LaboLightSurface,
    onSurface = LaboLightOnSurface,
    surfaceVariant = LaboLightSurfaceVariant,
    onSurfaceVariant = LaboLightOnSurfaceVariant,
    outline = LaboLightBorder,
    error = LaboDangerRed
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
