package com.adblockerx.pro.ui.theme

import androidx.compose.ui.graphics.Color

val RedPrimary = Color(0xFFC62828)
val RedLight = Color(0xFFE53935)
val RedContainer = Color(0xFFFFCDD2)
val OnRedContainer = Color(0xFF410002)
val DarkRedPrimary = Color(0xFFFFB4A9)
val DarkRedContainer = Color(0xFF930006)
val AmberExp = Color(0xFFFF8F00)
val AmberExpContainer = Color(0xFFFFE0B2)
val White = Color.White
val Black = Color.Black

// 主题预设
enum class ThemeMode { LIGHT, DARK, SYSTEM }

data class ThemeColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val background: Color,
    val surface: Color,
    val error: Color,
    val name: String
)

val ThemePresets = listOf(
    ThemeColors(RedPrimary, White, RedContainer, OnRedContainer, AmberExp, AmberExpContainer, Color(0xFFF5F5F5), White, Color(0xFFD32F2F), "默认"),
    ThemeColors(Color(0xFF1565C0), Color.White, Color(0xFFBBDEFB), Color(0xFF0D47A1), Color(0xFF42A5F5), Color(0xFFE3F2FD), Color(0xFFF5F5F5), Color.White, Color(0xFFD32F2F), "海洋蓝"),
    ThemeColors(Color(0xFF2E7D32), Color.White, Color(0xFFC8E6C9), Color(0xFF1B5E20), Color(0xFF66BB6A), Color(0xFFE8F5E9), Color(0xFFFAFAFA), Color.White, Color(0xFFD32F2F), "森林绿"),
    ThemeColors(Color(0xFFEF6C00), Color.White, Color(0xFFFFE0B2), Color(0xFFE65100), Color(0xFFFF7043), Color(0xFFFFF3E0), Color(0xFFF5F5F5), Color.White, Color(0xFFD32F2F), "日落橙"),
    ThemeColors(Color(0xFF7B1FA2), Color.White, Color(0xFFE1BEE7), Color(0xFF4A148C), Color(0xFFAB47BC), Color(0xFFF3E5F5), Color(0xFFFAFAFA), Color.White, Color(0xFFD32F2F), "皇家紫"),
    ThemeColors(Color(0xFF212121), Color.White, Color(0xFF424242), Color(0xFFBDBDBD), Color(0xFF616161), Color(0xFF424242), Color(0xFF121212), Color(0xFF1E1E1E), Color(0xFFCF6679), "午夜"),
    ThemeColors(Color(0xFF6750A4), Color.White, Color(0xFFE8DEF8), Color(0xFF1D192B), Color(0xFF625B71), Color(0xFFE8DEF8), Color(0xFFFFFBFE), Color.White, Color(0xFFB3261E), "Material You")
)
