package com.batteryopt.noroot.utils

import android.content.Context
import android.content.SharedPreferences
import com.batteryopt.noroot.models.BatteryConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 配置管理器
 *
 * LSPatch本地模式下使用目标 APP 进程的 SharedPreferences 存储配置。
 * 注意：每款 APP 的配置相互隔离，保存在各自进程的 data 目录下。
 * 模块自身的 MainActivity（在模块 APK 进程内）也使用同一份管理逻辑。
 */
object ConfigManager {

    private const val PREFS_NAME = "battery_optimizer_configs"
    private const val KEY_ALL = "all_app_configs"
    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs == null) {
            prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun getAllConfigs(): MutableMap<String, BatteryConfig> {
        val json = prefs?.getString(KEY_ALL, null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, BatteryConfig>>() {}.type
            gson.fromJson(json, type) ?: mutableMapOf()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun saveAll(configs: MutableMap<String, BatteryConfig>) {
        prefs?.edit()?.putString(KEY_ALL, gson.toJson(configs))?.apply()
    }

    fun getAppConfig(pkg: String): BatteryConfig {
        return getAllConfigs()[pkg] ?: createDefault(pkg)
    }

    fun saveAppConfig(cfg: BatteryConfig) {
        val all = getAllConfigs()
        cfg.lastModified = System.currentTimeMillis()
        all[cfg.packageName] = cfg
        saveAll(all)
    }

    fun deleteAppConfig(pkg: String) {
        val all = getAllConfigs()
        all.remove(pkg)
        saveAll(all)
    }

    fun createDefault(pkg: String) = BatteryConfig(
        packageName = pkg,
        wakeLockOptEnabled = true,
        alarmOptEnabled = true,
        syncOptEnabled = true,
        jobOptEnabled = true,
        locationOptEnabled = true,
        animationOptEnabled = false,
        sensorOptEnabled = true
    )

    fun resetAll() { prefs?.edit()?.clear()?.apply() }
}
