package com.privacyguard.pro.models

import com.google.gson.annotations.SerializedName

/**
 * PrivacyGuard 隐私保护配置（Root 版）
 *
 * 包含 NoRoot 版全部字段，并额外增加系统级 Hook 配置：
 *  - SystemPropSpoofHook: 修改 ro.* 系统属性
 *  - GlobalPermissionHook: pm revoke/grant 全局权限控制
 *  - NetworkIdentifierHook: 修改网卡 MAC / ip link
 *  - ShizukuBridgeHook: settings put、pm clear 等系统命令
 *
 * 硬性限制：
 *  - 系统级 Hook 必须先检查 ShizukuHelper.isShizukuAvailable()
 *  - setprop 修改非持久化，重启后消失
 *  - 写 /sys/* 节点需要 root 级别 Shizuku 授权
 */
data class PrivacyConfig(
    @SerializedName("packageName") val packageName: String,

    // ===== 设备ID伪造（应用层） =====
    @SerializedName("deviceIdSpoofEnabled") var deviceIdSpoofEnabled: Boolean = true,

    // ===== 剪贴板保护（应用层） =====
    @SerializedName("clipboardGuardEnabled") var clipboardGuardEnabled: Boolean = true,
    @SerializedName("clipboardBlockRead") var clipboardBlockRead: Boolean = false,

    // ===== 权限欺骗（应用层，仅欺骗APP自身检查） =====
    @SerializedName("permissionSpoofEnabled") var permissionSpoofEnabled: Boolean = false,
    @SerializedName("deniedPermissions") var deniedPermissions: MutableList<String> = mutableListOf(
        "android.permission.READ_CONTACTS",
        "android.permission.READ_CALL_LOG",
        "android.permission.READ_PHONE_STATE"
    ),

    // ===== 位置伪造（应用层） =====
    @SerializedName("locationSpoofEnabled") var locationSpoofEnabled: Boolean = false,
    @SerializedName("spoofLatitude") var spoofLatitude: Double = 31.230416,   // 默认上海
    @SerializedName("spoofLongitude") var spoofLongitude: Double = 121.473701,

    // ===== 传感器伪造（应用层） =====
    @SerializedName("sensorFakerEnabled") var sensorFakerEnabled: Boolean = false,
    @SerializedName("sensorNoiseMode") var sensorNoiseMode: Int = 0, // 0=静态, 1=加噪

    // ===== 广告ID屏蔽（应用层） =====
    @SerializedName("advertisingIdBlockEnabled") var advertisingIdBlockEnabled: Boolean = true,

    // ===== 系统属性伪造（Root 专属，Shizuku setprop） =====
    @SerializedName("systemPropSpoofEnabled") var systemPropSpoofEnabled: Boolean = false,
    @SerializedName("spoofSerial") var spoofSerial: String = "PG2024XYZ001",
    @SerializedName("spoofProductModel") var spoofProductModel: String = "Pixel 8 Pro",
    @SerializedName("spoofProductBrand") var spoofProductBrand: String = "google",
    @SerializedName("spoofProductManufacturer") var spoofProductManufacturer: String = "Google",

    // ===== 全局权限控制（Root 专属，Shizuku pm revoke/grant） =====
    @SerializedName("globalPermissionControlEnabled") var globalPermissionControlEnabled: Boolean = false,
    @SerializedName("revokedPermissions") var revokedPermissions: MutableList<String> = mutableListOf(
        "android.permission.READ_CONTACTS",
        "android.permission.READ_CALL_LOG"
    ),
    @SerializedName("hiddenIntents") var hiddenIntents: MutableList<String> = mutableListOf(),

    // ===== 网络标识伪造（Root 专属，Shizuku ip link） =====
    @SerializedName("networkIdentifierSpoofEnabled") var networkIdentifierSpoofEnabled: Boolean = false,
    @SerializedName("spoofMacAddress") var spoofMacAddress: String = "02:00:00:00:00:00",

    // ===== Shizuku 桥接（Root 专属，settings put + pm clear） =====
    @SerializedName("shizukuBridgeEnabled") var shizukuBridgeEnabled: Boolean = false,
    @SerializedName("clearAppTrackingData") var clearAppTrackingData: Boolean = false,

    @SerializedName("lastModified") var lastModified: Long = System.currentTimeMillis()
)
