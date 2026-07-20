package com.batteryopt.pro.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun BatteryOptimizerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    themeIndex: Int = 0,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val preset = ThemePresets.getOrNull(themeIndex) ?: ThemePresets.first()

    val colorScheme = when {
        dynamicColor && themeIndex == ThemePresets.lastIndex && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = preset.primary,
            onPrimary = preset.onPrimary,
            primaryContainer = preset.primaryContainer,
            onPrimaryContainer = preset.onPrimaryContainer,
            secondary = preset.secondary,
            secondaryContainer = preset.secondaryContainer,
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            error = preset.error
        )
        else -> lightColorScheme(
            primary = preset.primary,
            onPrimary = preset.onPrimary,
            primaryContainer = preset.primaryContainer,
            onPrimaryContainer = preset.onPrimaryContainer,
            secondary = preset.secondary,
            secondaryContainer = preset.secondaryContainer,
            background = preset.background,
            surface = preset.surface,
            error = preset.error
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
