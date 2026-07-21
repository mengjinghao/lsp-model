package com.stepmod.pro.utils

import com.google.gson.Gson
import com.stepmod.pro.models.StepConfig

/**
 * Hook 侧配置读取器
 *
 * 优先使用 XSharedPreferences 直接读取模块 prefs 文件（LSPosed 模式，跨进程）。
 * 失败时回退到 Context-based ConfigManager（LSPatch 本地模式，同进程）。
 */
object HookConfigReader {

    private const val MODULE_PKG = "com.stepmod.pro"
    private val gson = Gson()

    fun readGlobal(): StepConfig? {
        return try {
            val xsp = de.robv.android.xposed.XSharedPreferences(MODULE_PKG, ConfigManager.PREFS_NAME)
            xsp.makeWorldReadable()
            val json = xsp.getString("global_config", null) ?: return null
            gson.fromJson(json, StepConfig::class.java)
        } catch (_: Throwable) { null }
    }
}
