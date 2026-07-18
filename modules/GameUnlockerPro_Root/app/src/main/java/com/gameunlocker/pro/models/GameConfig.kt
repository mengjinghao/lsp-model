package com.gameunlocker.pro.models

import com.google.gson.annotations.SerializedName

/**
 * 单个游戏的独立配置
 * 每个游戏基于包名存储一份独立配置，互不干扰
 */
data class GameConfig(
    @SerializedName("packageName") val packageName: String,          // 游戏包名
    @SerializedName("gameName") val gameName: String = "",           // 游戏显示名

    /** 机型伪装配置 */
    @SerializedName("deviceSpoofEnabled") var deviceSpoofEnabled: Boolean = true,  // 是否开启机型伪装
    @SerializedName("selectedDeviceProfileId") var selectedDeviceProfileId: String = "xiaomi15", // 选择的机型ID
    @SerializedName("customDeviceProfile") var customDeviceProfile: DeviceProfile? = null,      // 自定义机型

    /** 帧率解锁配置 */
    @SerializedName("frameRateUnlockEnabled") var frameRateUnlockEnabled: Boolean = true,  // 是否开启帧率解锁
    @SerializedName("targetFps") var targetFps: Int = 120,  // 目标帧率: 60/90/120/144/160/-1(自动)

    /** 温控屏蔽配置 */
    @SerializedName("thermalBypassEnabled") var thermalBypassEnabled: Boolean = true,      // 是否屏蔽温控
    @SerializedName("customThermalThreshold") var customThermalThreshold: Int = 50,         // 自定义温控阈值(℃)

    /** 环境隐藏配置 */
    @SerializedName("detectionHideEnabled") var detectionHideEnabled: Boolean = true,       // 是否开启环境隐藏
    @SerializedName("hideShizuku") var hideShizuku: Boolean = true,                         // 是否隐藏Shizuku
    @SerializedName("hideXposed") var hideXposed: Boolean = true,                           // 是否隐藏Xposed痕迹
    @SerializedName("hideLspatch") var hideLspatch: Boolean = true,                         // 是否隐藏LSPatch

    /** 分辨率伪装配置 */
    @SerializedName("resolutionSpoofEnabled") var resolutionSpoofEnabled: Boolean = false,  // 是否伪装分辨率
    @SerializedName("spoofWidth") var spoofWidth: Int = 2560,                               // 伪装宽
    @SerializedName("spoofHeight") var spoofHeight: Int = 1440,                             // 伪装高
    @SerializedName("spoofDpi") var spoofDpi: Int = 560,                                    // 伪装DPI

    /** GPU调度优化 */
    @SerializedName("gpuOptimizeEnabled") var gpuOptimizeEnabled: Boolean = true,           // 是否优化GPU

    /** Shizuku联动 */
    @SerializedName("shizukuBridgeEnabled") var shizukuBridgeEnabled: Boolean = true,       // 是否联动Shizuku

    /** 其他 */
    @SerializedName("lastModified") var lastModified: Long = System.currentTimeMillis(),    // 最后修改时间
    @SerializedName("configVersion") var configVersion: Int = 1                             // 配置版本号
)
