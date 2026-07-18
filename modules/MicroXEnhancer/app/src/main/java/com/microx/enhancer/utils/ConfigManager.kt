package com.microx.enhancer.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * 配置管理器（单例）
 * 基于SharedPreferences管理所有功能开关
 * 支持导入/导出配置到JSON文件
 */
object ConfigManager {

    private const val PREFS_NAME = "microx_enhancer_config"
    private lateinit var prefs: SharedPreferences
    private var isInitialized = false

    // ===== 功能开关Key常量 =====
    const val KEY_MASTER_SWITCH = "master_switch"

    // 广告净化
    const val KEY_AD_SPLASH = "ad_splash"
    const val KEY_AD_MOMENTS = "ad_moments"
    const val KEY_AD_OFFICIAL_ACCOUNT = "ad_official_account"
    const val KEY_AD_MINI_PROGRAM = "ad_mini_program"
    const val KEY_AD_CHAT_CARD = "ad_chat_card"

    // 消息增强
    const val KEY_ANTI_RECALL = "anti_recall"
    const val KEY_ANTI_DELETE_MOMENT = "anti_delete_moment"
    const val KEY_MESSAGE_BACKUP = "message_backup"
    const val KEY_AUTO_REPLY = "auto_reply"

    // 界面美化
    const val KEY_CUSTOM_BUBBLE = "custom_bubble"
    const val KEY_CUSTOM_FONT = "custom_font"
    const val KEY_HIDE_RED_DOT = "hide_red_dot"
    const val KEY_REMOVE_TAB = "remove_tab"
    const val KEY_SIMPLIFY_MOMENTS = "simplify_moments"
    const val KEY_CHAT_BACKGROUND = "chat_background"

    // 隐私增强
    const val KEY_HIDE_TYPING = "hide_typing"
    const val KEY_HIDE_READ_STATUS = "hide_read_status"
    const val KEY_UNLIMITED_FORWARD = "unlimited_forward"
    const val KEY_FORCE_ORIGINAL = "force_original"
    const val KEY_NO_WATERMARK_SAVE = "no_watermark_save"

    // 批量管理
    const val KEY_ONE_KEY_CLEAN = "one_key_clean"
    const val KEY_EXPORT_FRIENDS = "export_friends"
    const val KEY_ZOMBIE_CHECK = "zombie_check"
    const val KEY_GROUP_MANAGE = "group_manage"

    // 安全适配
    const val KEY_BYPASS_DETECTION = "bypass_detection"

    // ===== 初始化（在MainHook中调用）=====
    fun init(context: Context) {
        if (isInitialized) return
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // 首次运行时开启所有默认值
        if (!prefs.contains(KEY_MASTER_SWITCH)) {
            setDefaults()
        }
        isInitialized = true
    }

    /** 设置所有功能的默认开关值 */
    private fun setDefaults() {
        prefs.edit().apply {
            putBoolean(KEY_MASTER_SWITCH, true)
            // 广告净化：默认全部开启
            putBoolean(KEY_AD_SPLASH, true)
            putBoolean(KEY_AD_MOMENTS, true)
            putBoolean(KEY_AD_OFFICIAL_ACCOUNT, true)
            putBoolean(KEY_AD_MINI_PROGRAM, true)
            putBoolean(KEY_AD_CHAT_CARD, true)
            // 消息增强
            putBoolean(KEY_ANTI_RECALL, true)
            putBoolean(KEY_ANTI_DELETE_MOMENT, true)
            putBoolean(KEY_MESSAGE_BACKUP, false)     // 备份功能默认关闭（I/O密集）
            putBoolean(KEY_AUTO_REPLY, false)
            // 界面美化
            putBoolean(KEY_CUSTOM_BUBBLE, false)
            putBoolean(KEY_CUSTOM_FONT, false)
            putBoolean(KEY_HIDE_RED_DOT, true)
            putBoolean(KEY_REMOVE_TAB, true)
            putBoolean(KEY_SIMPLIFY_MOMENTS, true)
            putBoolean(KEY_CHAT_BACKGROUND, false)
            // 隐私增强
            putBoolean(KEY_HIDE_TYPING, true)
            putBoolean(KEY_HIDE_READ_STATUS, true)
            putBoolean(KEY_UNLIMITED_FORWARD, false)
            putBoolean(KEY_FORCE_ORIGINAL, false)
            putBoolean(KEY_NO_WATERMARK_SAVE, false)
            // 批量管理：全部默认关闭
            putBoolean(KEY_ONE_KEY_CLEAN, false)
            putBoolean(KEY_EXPORT_FRIENDS, false)
            putBoolean(KEY_ZOMBIE_CHECK, false)
            putBoolean(KEY_GROUP_MANAGE, false)
            // 安全适配：默认开启
            putBoolean(KEY_BYPASS_DETECTION, true)
        }.apply()
    }

    // ===== 读取函数 =====
    fun isMasterEnabled(): Boolean = prefs.getBoolean(KEY_MASTER_SWITCH, true)

    fun isEnabled(key: String): Boolean {
        if (!isMasterEnabled()) return false
        return prefs.getBoolean(key, true)
    }

    fun setEnabled(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun setMasterEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER_SWITCH, value).apply()
    }

    /** 获取所有配置的Map（用于导出） */
    fun getAllConfig(): Map<String, Boolean> {
        return prefs.all.mapValues { it.value as Boolean }
    }

    /** 从Map批量导入配置 */
    fun importConfig(configMap: Map<String, Boolean>) {
        prefs.edit().apply {
            configMap.forEach { (key, value) ->
                putBoolean(key, value)
            }
        }.apply()
    }

    /** 重置所有配置为默认值 */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
        setDefaults()
    }
}
