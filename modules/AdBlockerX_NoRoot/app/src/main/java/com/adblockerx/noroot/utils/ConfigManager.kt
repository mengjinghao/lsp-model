package com.adblockerx.noroot.utils

import android.content.Context
import android.content.SharedPreferences
import com.adblockerx.noroot.models.AdBlockConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 配置管理器
 *
 * LSPatch本地模式下使用目标 APP 进程的 SharedPreferences 存储配置。
 * 仅保存本 APP 进程内的拦截策略，绝不影响系统或其他 APP。
 */
object ConfigManager {

    private const val PREFS_NAME = "adblockerx_noroot_configs"
    private const val KEY_CONFIG = "ad_block_config"
    private const val KEY_BLOCKED_COUNT = "blocked_count"
    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs == null) {
            prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun isInitialized(): Boolean = prefs != null

    fun getConfig(): AdBlockConfig {
        val json = prefs?.getString(KEY_CONFIG, null) ?: return defaultConfig()
        return try {
            gson.fromJson(json, AdBlockConfig::class.java) ?: defaultConfig()
        } catch (e: Exception) {
            defaultConfig()
        }
    }

    fun saveConfig(cfg: AdBlockConfig) {
        cfg.lastModified = System.currentTimeMillis()
        prefs?.edit()?.putString(KEY_CONFIG, gson.toJson(cfg))?.apply()
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

    /** 自定义黑名单（行分隔） */
    fun getCustomBlocklistRaw(): String {
        return try {
            val cfg = getConfig()
            cfg.customBlocklist.joinToString("\n")
        } catch (_: Exception) { "" }
    }

    fun saveCustomBlocklistRaw(text: String) {
        try {
            val cfg = getConfig()
            cfg.customBlocklist = text.split("\n")
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
            saveConfig(cfg)
        } catch (_: Exception) {}
    }

    fun defaultConfig(): AdBlockConfig {
        return AdBlockConfig(
            webViewBlockEnabled = true,
            okHttpBlockEnabled = true,
            urlConnectionBlockEnabled = true,
            hostsFilterEnabled = true,
            adViewHideEnabled = true,
            injectJsEnabled = false,
            builtinBlocklistEnabled = true,
            customBlocklist = emptyList(),
            logEnabled = true,
            blockedCount = 0L
        )
    }
}
