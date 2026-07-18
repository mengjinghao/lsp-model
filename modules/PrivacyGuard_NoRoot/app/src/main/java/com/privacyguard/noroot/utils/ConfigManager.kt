package com.privacyguard.noroot.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.privacyguard.noroot.models.PrivacyConfig

/**
 * 配置管理器
 * LSPatch本地模式下使用目标APP进程的SharedPreferences存储配置。
 * 每个 APP 独立一份 PrivacyConfig，互不干扰。
 */
object ConfigManager {

    private const val PREFS_NAME = "privacy_guard_configs"
    private const val KEY_ALL = "all_app_configs"
    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs == null) {
            prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /** 检查是否已初始化（用于Application.onCreate前调用判断） */
    fun isInitialized(): Boolean = prefs != null

    fun getAllConfigs(): MutableMap<String, PrivacyConfig> {
        val json = prefs?.getString(KEY_ALL, null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, PrivacyConfig>>() {}.type
            gson.fromJson(json, type) ?: mutableMapOf()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun saveAll(configs: MutableMap<String, PrivacyConfig>) {
        prefs?.edit()?.putString(KEY_ALL, gson.toJson(configs))?.apply()
    }

    fun getConfig(pkg: String): PrivacyConfig {
        return getAllConfigs()[pkg] ?: createDefault(pkg)
    }

    fun saveConfig(cfg: PrivacyConfig) {
        val all = getAllConfigs()
        cfg.lastModified = System.currentTimeMillis()
        all[cfg.packageName] = cfg
        saveAll(all)
    }

    fun deleteConfig(pkg: String) {
        val all = getAllConfigs()
        all.remove(pkg)
        saveAll(all)
    }

    fun createDefault(pkg: String) = PrivacyConfig(
        packageName = pkg,
        deviceIdSpoofEnabled = true,
        clipboardGuardEnabled = true,
        clipboardBlockRead = false,
        permissionSpoofEnabled = false,
        locationSpoofEnabled = false,
        sensorFakerEnabled = false,
        advertisingIdBlockEnabled = true
    )

    fun resetAll() { prefs?.edit()?.clear()?.apply() }
}
