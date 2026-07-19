package com.adblockerx.noroot.utils

import android.content.Context
import android.content.SharedPreferences
import com.adblockerx.noroot.models.AdBlockConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 配置管理器
 *
 * 双通道读取：
 *  1. UI 侧（模块进程）：通过 SharedPreferences 读写
 *  2. Hook 侧（目标APP进程）：通过 XSharedPreferences 读取模块 prefs（LSPosed模式）
 *     或通过 Context.getSharedPreferences 读取（LSPatch本地模式，同进程）
 *
 * LSPosed 兼容：prefs 使用 MODE_WORLD_READABLE（LSPosed 拦截并放行），失败回退 MODE_PRIVATE。
 */
object ConfigManager {

    const val PREFS_NAME = "adblockerx_noroot_prefs"
    private const val KEY_GLOBAL = "global_config"
    private const val KEY_BLOCKED_COUNT = "blocked_count"

    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs != null) return
        prefs = try {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)
        } catch (_: Throwable) {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun isInitialized(): Boolean = prefs != null

    fun getGlobalConfig(): AdBlockConfig {
        val def = AdBlockConfig()
        if (!isInitialized()) return def
        val json = prefs?.getString(KEY_GLOBAL, null) ?: return def
        return try { gson.fromJson(json, AdBlockConfig::class.java) ?: def } catch (_: Throwable) { def }
    }

    fun saveGlobalConfig(cfg: AdBlockConfig) {
        if (!isInitialized()) return
        cfg.lastModified = System.currentTimeMillis()
        prefs?.edit()?.putString(KEY_GLOBAL, gson.toJson(cfg))?.apply()
    }

    fun getBlockedCount(): Long = prefs?.getLong(KEY_BLOCKED_COUNT, 0L) ?: 0L

    fun incrementBlockedCount(delta: Long = 1L) {
        try {
            prefs?.edit()?.putLong(KEY_BLOCKED_COUNT, getBlockedCount() + delta)?.apply()
        } catch (_: Exception) {}
    }

    fun resetBlockedCount() {
        prefs?.edit()?.putLong(KEY_BLOCKED_COUNT, 0L)?.apply()
    }

    fun resetAll() {
        prefs?.edit()?.clear()?.apply()
    }
}
