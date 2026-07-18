package com.gameunlocker.noroot.utils

import android.content.Context
import android.content.SharedPreferences
import com.gameunlocker.noroot.models.GameConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 配置管理器
 * LSPatch本地模式下使用目标APP进程的SharedPreferences存储配置。
 * 注意：重启LSPatch或重新修补APK后配置仍保留（存储在目标APP data目录）。
 */
object ConfigManager {

    private const val PREFS_NAME = "game_unlocker_configs"
    private const val KEY_ALL = "all_game_configs"
    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs == null) {
            prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun getAllConfigs(): MutableMap<String, GameConfig> {
        val json = prefs?.getString(KEY_ALL, null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, GameConfig>>() {}.type
            gson.fromJson(json, type) ?: mutableMapOf()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun saveAll(configs: MutableMap<String, GameConfig>) {
        prefs?.edit()?.putString(KEY_ALL, gson.toJson(configs))?.apply()
    }

    fun getGameConfig(pkg: String): GameConfig {
        return getAllConfigs()[pkg] ?: createDefault(pkg)
    }

    fun saveGameConfig(cfg: GameConfig) {
        val all = getAllConfigs()
        cfg.lastModified = System.currentTimeMillis()
        all[cfg.packageName] = cfg
        saveAll(all)
    }

    fun deleteGameConfig(pkg: String) {
        val all = getAllConfigs()
        all.remove(pkg)
        saveAll(all)
    }

    fun createDefault(pkg: String) = GameConfig(
        packageName = pkg,
        deviceSpoofEnabled = true,
        selectedDeviceProfileId = "xiaomi15",
        frameRateUnlockEnabled = true,
        targetFps = 120,
        detectionHideEnabled = true,
        processOptimizeEnabled = true
    )

    fun resetAll() { prefs?.edit()?.clear()?.apply() }
}
