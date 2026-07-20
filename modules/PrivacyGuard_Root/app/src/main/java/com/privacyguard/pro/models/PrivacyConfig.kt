package com.privacyguard.pro.models

/**
 * 隐私保护配置（Root 版）
 *
 * 包含 NoRoot 版全部字段，并额外增加系统级 Hook 配置：
 *  - SystemPropSpoofHook: Shizuku setprop 修改 ro.* 系统属性
 *  - GlobalPermissionHook: Shizuku pm revoke 全局权限回收
 *  - NetworkIdentifierHook: Shizuku ip link / 写 /sys/class/net 修改网卡 MAC
 *  - ShizukuBridgeHook: Shizuku settings put + pm clear
 *
 * 实验性（Root 版专属）：
 *  - SelinuxContextSpoofHook: Hook getenforce / /proc/self/attr/current
 *  - KernelCmdlineHideHook: Hook 读取 /proc/cmdline 返回混淆内容
 *
 * 硬性限制：
 *  - 系统级 Hook 必须先检查 ShizukuHelper.isShizukuAvailable()
 *  - setprop 修改非持久化，重启后消失
 *  - 写 /sys 节点需要 root 级别 Shizuku 授权
 */
data class PrivacyConfig(
    // ===== 基础 =====
    var packageName: String = "",
    var masterEnabled: Boolean = true,

    // ===== 应用层（同 NoRoot 版） =====
    var deviceIdSpoofEnabled: Boolean = true,
    var clipboardGuardEnabled: Boolean = true,
    var clipboardBlockRead: Boolean = false,
    var permissionSpoofEnabled: Boolean = false,
    var locationSpoofEnabled: Boolean = false,
    var sensorFakerEnabled: Boolean = false,
    var advertisingIdBlockEnabled: Boolean = true,

    // ===== 应用层实验性（同 NoRoot 版） =====
    var packageVisibilitySpoofEnabled: Boolean = false,
    var networkInfoSpoofEnabled: Boolean = false,
    var screenMetricsSpoofEnabled: Boolean = false,
    var storagePathSpoofEnabled: Boolean = false,
    // ===== v1.0.6 新增（对标 HideMyAndroid/伪造安装模块） =====
    var installStatusSpoofEnabled: Boolean = false,   // 应用安装状态伪造
    var mockLocationSystemLevelEnabled: Boolean = false, // 系统级Mock位置(仅Root)
    var profileSwitchEnabled: Boolean = false,

    // ===== 实验性：隐私审计 =====
    var privacyAuditEnabled: Boolean = false,

    // ===== 实验性：Camera/Mic入侵守卫 =====
    var cameraGuardEnabled: Boolean = false,
    var micGuardEnabled: Boolean = false,
    var blockUnauthorizedAv: Boolean = false,

    // ===== 实验性：后台Activity监控 =====
    var backgroundActivityMonitorEnabled: Boolean = false,
    var blockBackgroundActivities: Boolean = false,

    // ===== 实验性：网络泄露检测 =====
    var networkLeakDetectorEnabled: Boolean = false,

    // ===== 实验性：Anti-Fingerprinting =====
    var antiFingerprintEnabled: Boolean = false,

    // ===== Root 专属：系统属性伪造（Shizuku setprop） =====
    var systemPropSpoofEnabled: Boolean = false,
    var spoofSerial: String = "PG2024XYZ001",
    var spoofProductModel: String = "Pixel 8 Pro",
    var spoofProductBrand: String = "google",
    var spoofProductManufacturer: String = "Google",

    // ===== Root 专属：全局权限控制（Shizuku pm revoke） =====
    var globalPermissionHookEnabled: Boolean = false,
    var revokedPermissions: MutableList<String> = mutableListOf(
        "android.permission.READ_CONTACTS",
        "android.permission.READ_CALL_LOG"
    ),
    var hiddenIntents: MutableList<String> = mutableListOf(),

    // ===== Root 专属：网络标识伪造（Shizuku ip link） =====
    var networkIdentifierHookEnabled: Boolean = false,
    var spoofMacAddress: String = "02:00:00:00:00:00",

    // ===== Root 专属：Shizuku 桥接（settings put + pm clear） =====
    var shizukuBridgeEnabled: Boolean = false,
    var clearAppTrackingData: Boolean = false,

    // ===== Root 实验性：SELinux 上下文伪造 =====
    var selinuxContextSpoofEnabled: Boolean = false,

    // ===== Root 实验性：内核 cmdline 隐藏 =====
    var kernelCmdlineHideEnabled: Boolean = false,

    // ===== 参数 =====
    var spoofLatitude: Double = 31.2304,
    var spoofLongitude: Double = 121.4737,
    var sensorNoiseMode: Int = 2,          // 0=静态 其他=加噪
    var deniedPermissions: MutableList<String> = mutableListOf(
        "android.permission.READ_CONTACTS",
        "android.permission.READ_CALL_LOG",
        "android.permission.READ_CALENDAR"
    ),

    var lastModified: Long = 0L
)
