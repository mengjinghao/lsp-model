package com.batteryopt.pro.utils

import android.content.Context
import android.content.SharedPreferences
import com.batteryopt.pro.models.BatteryConfig
import com.google.gson.Gson

/**
 * 配置管理器（Root 版）
 *
 * 双通道读取：
 *  1. UI 侧（模块进程）：通过 SharedPreferences 读写
 *  2. Hook 侧（目标APP进程）：通过 XSharedPreferences 读取模块 prefs（LSPosed模式）
 *     或通过 Context.getSharedPreferences 读取（LSPatch本地模式，同进程）
 *
 * LSPosed 兼容：prefs 使用 MODE_WORLD_READABLE（LSPosed 拦截并放行），失败回退 MODE_PRIVATE。
 */
object ConfigManager {

    const val PREFS_NAME = "battery_optimizer_pro_prefs"
    private const val KEY_GLOBAL = "global_config"

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

    fun getGlobalConfig(): BatteryConfig {
        val def = BatteryConfig(packageName = "global")
        if (!isInitialized()) return def
        val json = prefs?.getString(KEY_GLOBAL, null) ?: return def
        return try { gson.fromJson(json, BatteryConfig::class.java) ?: def } catch (_: Throwable) { def }
    }

    fun saveGlobalConfig(cfg: BatteryConfig) {
        if (!isInitialized()) return
        cfg.lastModified = System.currentTimeMillis()
        prefs?.edit()?.putString(KEY_GLOBAL, gson.toJson(cfg))?.apply()
    }

    fun resetAll() { prefs?.edit()?.clear()?.apply() }
}
