package com.batteryopt.pro.utils

import android.content.Context
import android.content.SharedPreferences
import com.batteryopt.pro.models.BatteryConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 配置管理器（Root 版）
 *
 * 存储每款 APP 的独立省电配置。
 * LSPatch 本地模式下使用目标 APP 进程的 SharedPreferences，
 * 模块自身 MainActivity 也使用同一份管理逻辑。
 */
object ConfigManager {

    private const val PREFS_NAME = "battery_optimizer_pro_configs"
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
            LogX.e("解析配置 JSON 失败", e)
            mutableMapOf()
        }
    }

    private fun saveAll(configs: MutableMap<String, BatteryConfig>) {
        try {
            prefs?.edit()?.putString(KEY_ALL, gson.toJson(configs))?.apply()
        } catch (e: Exception) {
            LogX.e("保存配置失败", e)
        }
    }

    fun getAppConfig(pkg: String): BatteryConfig {
        return getAllConfigs()[pkg] ?: createDefault(pkg)
    }

    fun saveAppConfig(cfg: BatteryConfig) {
        val all = getAllConfigs()
        cfg.lastModified = System.currentTimeMillis()
        all[cfg.packageName] = cfg
        saveAll(all)
        LogX.d("已保存配置: ${cfg.packageName}")
    }

    fun deleteAppConfig(pkg: String) {
        val all = getAllConfigs()
        all.remove(pkg)
        saveAll(all)
    }

    fun createDefault(pkg: String) = BatteryConfig(
        packageName = pkg,
        // 应用层默认全开
        wakeLockOptEnabled = true,
        alarmOptEnabled = true,
        syncOptEnabled = true,
        jobOptEnabled = true,
        locationOptEnabled = true,
        animationOptEnabled = false,
        sensorOptEnabled = true,
        // 系统级默认关闭（避免无 Shizuku 时误触发）
        dozeEnabled = false,
        freezeEnabled = false,
        cpuGovernorEnabled = false,
        greenifyEnabled = false
    )

    fun resetAll() { prefs?.edit()?.clear()?.apply() }
}
