package com.vipunlock.pro.utils

import com.google.gson.Gson
import com.vipunlock.pro.models.VipConfig

/**
 * Hook 侧配置读取器
 *
 * 优先使用 XSharedPreferences 直接读取模块 prefs 文件（LSPosed 模式，跨进程）。
 * 失败时回退到 Context-based ConfigManager（LSPatch 本地模式，同进程）。
 */
object HookConfigReader {

    private const val MODULE_PKG = "com.vipunlock.pro"
    private val gson = Gson()

    fun readGlobal(): VipConfig? {
        return try {
            val xsp = de.robv.android.xposed.XSharedPreferences(MODULE_PKG, ConfigManager.PREFS_NAME)
            xsp.makeWorldReadable()
            val json = xsp.getString("global_config", null) ?: return null
            gson.fromJson(json, VipConfig::class.java)
        } catch (_: Throwable) { null }
    }
}
