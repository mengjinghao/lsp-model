package com.batteryopt.pro.models

import com.google.gson.annotations.SerializedName

/**
 * 单个 APP 的省电优化配置（Root 版）
 *
 * 包含两套配置：
 *  1. 应用层 Hook 开关（与 NoRoot 版同步）
 *  2. 系统级 Hook 开关（仅 Root 版，依赖 Shizuku）
 */
data class BatteryConfig(
    @SerializedName("packageName") val packageName: String,
    @SerializedName("appName") val appName: String = "",

    // ===== 应用层 Hook 开关 =====
    @SerializedName("wakeLockOptEnabled") var wakeLockOptEnabled: Boolean = true,
    @SerializedName("wakeLockMaxHoldMs") var wakeLockMaxHoldMs: Long = 60_000L,
    @SerializedName("wakeLockBlockRedundant") var wakeLockBlockRedundant: Boolean = true,

    @SerializedName("alarmOptEnabled") var alarmOptEnabled: Boolean = true,
    @SerializedName("alarmMinIntervalMs") var alarmMinIntervalMs: Long = 5 * 60 * 1000L,
    @SerializedName("alarmExactDowngrade") var alarmExactDowngrade: Boolean = true,

    @SerializedName("syncOptEnabled") var syncOptEnabled: Boolean = true,
    @SerializedName("syncMinIntervalMs") var syncMinIntervalMs: Long = 30 * 60 * 1000L,

    @SerializedName("jobOptEnabled") var jobOptEnabled: Boolean = true,
    @SerializedName("jobMinPeriodMs") var jobMinPeriodMs: Long = 15 * 60 * 1000L,
    @SerializedName("jobRequireIdle") var jobRequireIdle: Boolean = true,

    @SerializedName("locationOptEnabled") var locationOptEnabled: Boolean = true,
    @SerializedName("locationMinIntervalMs") var locationMinIntervalMs: Long = 30_000L,
    @SerializedName("locationDowngradeGps") var locationDowngradeGps: Boolean = true,

    @SerializedName("animationOptEnabled") var animationOptEnabled: Boolean = false,
    @SerializedName("animationScale") var animationScale: Float = 0f,

    @SerializedName("sensorOptEnabled") var sensorOptEnabled: Boolean = true,
    @SerializedName("sensorMaxRateUs") var sensorMaxRateUs: Int = 200_000,

    // ===== 系统级 Hook 开关（仅 Root 版，需 Shizuku） =====
    /** 系统 Doze 强制（屏幕关闭后进入深度 Doze） */
    @SerializedName("dozeEnabled") var dozeEnabled: Boolean = false,
    /** Doze 进入等待时间（秒），默认 0 表示立即 */
    @SerializedName("dozeDelaySec") var dozeDelaySec: Int = 60,

    /** 后台 APP 冻结（屏幕关闭 N 分钟后 force-stop 黑名单） */
    @SerializedName("freezeEnabled") var freezeEnabled: Boolean = false,
    /** 屏幕关闭后多少分钟开始冻结，默认 5 分钟 */
    @SerializedName("freezeDelayMin") var freezeDelayMin: Int = 5,
    /** 冻结黑名单 APP 列表（包名） */
    @SerializedName("freezeBlacklist") var freezeBlacklist: MutableList<String> = mutableListOf(),

    /** CPU governor 调度优化（屏幕关闭切 powersave） */
    @SerializedName("cpuGovernorEnabled") var cpuGovernorEnabled: Boolean = false,
    /** 屏幕亮起时使用的 governor（默认 interactive） */
    @SerializedName("cpuGovernorActive") var cpuGovernorActive: String = "interactive",
    /** 屏幕关闭时使用的 governor（默认 powersave） */
    @SerializedName("cpuGovernorIdle") var cpuGovernorIdle: String = "powersave",

    /** 孤儿 WakeLock 清理（dumpsys power 分析并释放） */
    @SerializedName("greenifyEnabled") var greenifyEnabled: Boolean = false,
    /** 清理周期（秒），默认 300s = 5 分钟 */
    @SerializedName("greenifyIntervalSec") var greenifyIntervalSec: Int = 300,

    @SerializedName("lastModified") var lastModified: Long = System.currentTimeMillis()
)
