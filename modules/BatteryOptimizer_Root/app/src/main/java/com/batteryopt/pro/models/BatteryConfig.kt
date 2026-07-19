package com.batteryopt.pro.models

/**
 * 省电优化配置（Root 版）
 *
 * 包含两套配置：
 *  1. 应用层 Hook 开关（与 NoRoot 版同步）
 *  2. 系统级 Hook 开关（仅 Root 版，依赖 Shizuku）
 *  3. 实验性功能开关
 */
data class BatteryConfig(
    // ===== 基础 =====
    var packageName: String = "",
    var masterEnabled: Boolean = true,

    // ===== 应用层 Hook 开关 =====
    var wakeLockEnabled: Boolean = true,
    var alarmEnabled: Boolean = true,
    var syncEnabled: Boolean = true,
    var jobEnabled: Boolean = true,
    var locationEnabled: Boolean = true,
    var animationEnabled: Boolean = false,
    var sensorEnabled: Boolean = true,

    // ===== 实验性（应用层）=====
    var bluetoothScanThrottleEnabled: Boolean = false,
    var cameraBackgroundBlockEnabled: Boolean = false,
    var vibratorThrottleEnabled: Boolean = false,

    // ===== 系统级 Hook 开关（仅 Root 版，需 Shizuku）=====
    /** 系统 Doze 强制（屏幕关闭后进入深度 Doze） */
    var dozeEnabled: Boolean = false,
    /** Doze 进入等待时间（秒），默认 60s */
    var dozeDelaySec: Int = 60,

    /** 后台 APP 冻结（屏幕关闭 N 分钟后 force-stop 黑名单） */
    var freezeEnabled: Boolean = false,
    /** 屏幕关闭后多少分钟开始冻结，默认 5 分钟 */
    var freezeDelayMin: Int = 5,
    /** 冻结黑名单 APP 列表（包名） */
    var freezeBlacklist: MutableList<String> = mutableListOf(),

    /** CPU governor 调度优化（屏幕关闭切 powersave） */
    var cpuGovernorEnabled: Boolean = false,
    /** 屏幕亮起时使用的 governor（默认 interactive） */
    var cpuGovernorActive: String = "interactive",
    /** 屏幕关闭时使用的 governor（默认 powersave） */
    var cpuGovernorIdle: String = "powersave",

    /** 孤儿 WakeLock 清理（dumpsys power 分析） */
    var greenifyEnabled: Boolean = false,
    /** 清理周期（秒），默认 300s = 5 分钟 */
    var greenifyIntervalSec: Int = 300,

    // ===== 实验性（系统级）=====
    /** 低电量模式自动切换（Shizuku settings put global low_power） */
    var lowPowerModeAutoEnabled: Boolean = false,
    /** 电量统计重置（Shizuku dumpsys batterystats --reset） */
    var batteryStatsResetEnabled: Boolean = false,

    // ===== 参数 =====
    var wakeLockMaxHoldSec: Int = 60,
    var alarmMinIntervalMin: Int = 5,

    var lastModified: Long = 0L
) {
    val wakeLockMaxHoldMs: Long get() = wakeLockMaxHoldSec * 1000L
    val alarmMinIntervalMs: Long get() = alarmMinIntervalMin * 60 * 1000L
    val syncMinIntervalMs: Long get() = 30 * 60 * 1000L
    val jobMinPeriodMs: Long get() = 15 * 60 * 1000L
    val jobRequireIdle: Boolean get() = true
    val locationMinIntervalMs: Long get() = 30_000L
    val locationDowngradeGps: Boolean get() = true
    val alarmExactDowngrade: Boolean get() = true
    val wakeLockBlockRedundant: Boolean get() = true
    val animationScale: Float get() = 0f
    val sensorMaxRateUs: Int get() = 200_000
    val bluetoothScanMinIntervalMs: Long get() = 60_000L
    val vibratorMinIntervalMs: Long get() = 1000L
}
