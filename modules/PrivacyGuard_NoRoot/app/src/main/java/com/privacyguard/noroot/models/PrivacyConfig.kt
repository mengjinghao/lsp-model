package com.privacyguard.noroot.models

import com.google.gson.annotations.SerializedName

/**
 * PrivacyGuard 隐私保护配置（NoRoot 版）
 *
 * 仅包含应用层 Hook 的开关字段，不涉及任何系统级修改。
 * 每个 APP 独立保存一份配置，互不干扰。
 *
 * 硬性限制：
 *  - 所有 Hook 仅在目标 APP 进程内生效，无法影响系统全局
 *  - 不修改系统属性、不写 /system 或 /sys 文件
 *  - 不进行全局权限拦截、不调用 Shizuku 系统级操作
 */
data class PrivacyConfig(
    @SerializedName("packageName") val packageName: String,

    // ===== 设备ID伪造 =====
    @SerializedName("deviceIdSpoofEnabled") var deviceIdSpoofEnabled: Boolean = true,

    // ===== 剪贴板保护 =====
    @SerializedName("clipboardGuardEnabled") var clipboardGuardEnabled: Boolean = true,
    @SerializedName("clipboardBlockRead") var clipboardBlockRead: Boolean = false,

    // ===== 权限欺骗（仅欺骗 APP 自身检查，不影响系统授权） =====
    @SerializedName("permissionSpoofEnabled") var permissionSpoofEnabled: Boolean = false,
    @SerializedName("deniedPermissions") var deniedPermissions: MutableList<String> = mutableListOf(
        "android.permission.READ_CONTACTS",
        "android.permission.READ_CALL_LOG",
        "android.permission.READ_PHONE_STATE"
    ),

    // ===== 位置伪造 =====
    @SerializedName("locationSpoofEnabled") var locationSpoofEnabled: Boolean = false,
    @SerializedName("spoofLatitude") var spoofLatitude: Double = 31.230416,   // 默认上海
    @SerializedName("spoofLongitude") var spoofLongitude: Double = 121.473701,

    // ===== 传感器伪造 =====
    @SerializedName("sensorFakerEnabled") var sensorFakerEnabled: Boolean = false,
    @SerializedName("sensorNoiseMode") var sensorNoiseMode: Int = 0, // 0=静态, 1=加噪

    // ===== 广告ID屏蔽 =====
    @SerializedName("advertisingIdBlockEnabled") var advertisingIdBlockEnabled: Boolean = true,

    @SerializedName("lastModified") var lastModified: Long = System.currentTimeMillis()
)
