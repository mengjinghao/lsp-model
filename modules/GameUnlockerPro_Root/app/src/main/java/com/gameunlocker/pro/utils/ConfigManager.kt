package com.gameunlocker.pro.utils

import android.content.Context
import android.content.SharedPreferences
import com.gameunlocker.pro.models.DeviceProfile
import com.gameunlocker.pro.models.GameConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/**
 * 配置管理器
 * 负责每个游戏的独立配置读写、导入导出
 * 存储介质：SharedPreferences（简单配置）+ JSON文件（复杂机型配置）
 */
object ConfigManager {

    private const val PREFS_NAME = "game_unlocker_configs"
    private const val KEY_ALL_CONFIGS = "all_game_configs"
    private const val KEY_GLOBAL_CONFIG = "global_config"

    private val gson = Gson()
    private lateinit var prefs: SharedPreferences
    private var isInitialized = false

    /**
     * 初始化配置管理器（模块入口调用）
     * 注意：LSPatch本地模式下context可能来自目标游戏进程
     */
    fun init(context: Context) {
        if (isInitialized) return
        // 使用设备保护存储，确保LSPatch环境下可读写
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isInitialized = true
        LogX.i("ConfigManager 初始化完成")
    }

    /** 获取所有游戏配置Map */
    fun getAllConfigs(): MutableMap<String, GameConfig> {
        val json = prefs.getString(KEY_ALL_CONFIGS, null)
        if (json.isNullOrEmpty()) {
            return mutableMapOf()
        }
        return try {
            val type = object : TypeToken<MutableMap<String, GameConfig>>() {}.type
            gson.fromJson(json, type) ?: mutableMapOf()
        } catch (e: Exception) {
            LogX.e("解析配置JSON失败", e)
            mutableMapOf()
        }
    }

    /** 保存所有游戏配置Map */
    private fun saveAllConfigs(configs: MutableMap<String, GameConfig>) {
        try {
            val json = gson.toJson(configs)
            prefs.edit().putString(KEY_ALL_CONFIGS, json).apply()
        } catch (e: Exception) {
            LogX.e("保存配置失败", e)
        }
    }

    /** 获取指定游戏的配置（不存在则返回默认配置） */
    fun getGameConfig(packageName: String): GameConfig {
        val configs = getAllConfigs()
        return configs[packageName] ?: createDefaultConfig(packageName)
    }

    /** 保存指定游戏的配置 */
    fun saveGameConfig(config: GameConfig) {
        val configs = getAllConfigs()
        config.lastModified = System.currentTimeMillis()
        configs[config.packageName] = config
        saveAllConfigs(configs)
        LogX.d("已保存配置: ${config.packageName}")
    }

    /** 删除指定游戏的配置 */
    fun deleteGameConfig(packageName: String) {
        val configs = getAllConfigs()
        configs.remove(packageName)
        saveAllConfigs(configs)
    }

    /** 创建默认配置 */
    fun createDefaultConfig(packageName: String): GameConfig {
        return GameConfig(
            packageName = packageName,
            deviceSpoofEnabled = true,
            selectedDeviceProfileId = "xiaomi15",
            frameRateUnlockEnabled = true,
            targetFps = 120,
            thermalBypassEnabled = true,
            detectionHideEnabled = true,
            hideShizuku = true,
            hideXposed = true,
            hideLspatch = true,
            resolutionSpoofEnabled = false,
            gpuOptimizeEnabled = true,
            shizukuBridgeEnabled = true
        )
    }

    /**
     * 导出所有配置到JSON文件
     * @param outputDir 输出目录
     * @return 导出文件路径，失败返回null
     */
    fun exportConfigs(outputDir: File): String? {
        return try {
            val configs = getAllConfigs()
            val json = gson.toJson(configs)
            val file = File(outputDir, "GameUnlockerPro_Configs_${System.currentTimeMillis()}.json")
            FileWriter(file).use { it.write(json) }
            LogX.i("配置已导出: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            LogX.e("导出配置失败", e)
            null
        }
    }

    /**
     * 从JSON文件导入配置
     * @param file 配置文件
     * @param merge 是否合并（true=合并，false=覆盖）
     * @return 导入的配置数量
     */
    fun importConfigs(file: File, merge: Boolean = true): Int {
        return try {
            val json = FileReader(file).readText()
            val type = object : TypeToken<MutableMap<String, GameConfig>>() {}.type
            val imported: MutableMap<String, GameConfig> = gson.fromJson(json, type)
                ?: return 0
            val currentConfigs = if (merge) getAllConfigs() else mutableMapOf()
            currentConfigs.putAll(imported)
            saveAllConfigs(currentConfigs)
            LogX.i("配置已导入: ${imported.size} 条")
            imported.size
        } catch (e: Exception) {
            LogX.e("导入配置失败", e)
            0
        }
    }

    /** 重置所有配置 */
    fun resetAllConfigs() {
        prefs.edit().clear().apply()
        LogX.i("所有配置已重置")
    }
}
