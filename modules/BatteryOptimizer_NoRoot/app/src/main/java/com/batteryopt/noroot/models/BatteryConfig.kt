package com.batteryopt.noroot.models

/**
 * 单个 APP 的省电优化配置
 *
 * 注意：NoRoot 版所有配置仅用于"当前 APP 自身"的耗电行为优化，
 * 无任何系统级或跨进程能力。
 */
data class BatteryConfig(
    val packageName: String,
    val appName: String = "",

    // ===== 应用层 Hook 开关 =====
    /** WakeLock 持有优化（超长自动释放 + SDK 统计类拦截） */
    var wakeLockOptEnabled: Boolean = true,
    /** WakeLock 最大持有时长（毫秒），超过自动 release。默认 60s */
    var wakeLockMaxHoldMs: Long = 60_000L,
    /** 是否拦截已知冗余 SDK 统计类 wake lock（不 acquire） */
    var wakeLockBlockRedundant: Boolean = true,

    /** AlarmManager 优化（高频精确闹钟降级为 inexact） */
    var alarmOptEnabled: Boolean = true,
    /** 重复闹钟最小间隔（毫秒），默认 5 分钟 */
    var alarmMinIntervalMs: Long = 5 * 60 * 1000L,
    /** 是否将 setExact 降级为 setWindow */
    var alarmExactDowngrade: Boolean = true,

    /** ContentResolver 同步降频 */
    var syncOptEnabled: Boolean = true,
    /** 周期同步最小间隔（毫秒），默认 30 分钟 */
    var syncMinIntervalMs: Long = 30 * 60 * 1000L,

    /** JobScheduler 限频 + 空闲约束 */
    var jobOptEnabled: Boolean = true,
    /** Job 最小周期（毫秒），默认 15 分钟 */
    var jobMinPeriodMs: Long = 15 * 60 * 1000L,
    /** 是否给非紧急 Job 追加 requireDeviceIdle 约束 */
    var jobRequireIdle: Boolean = true,

    /** LocationManager 定位降频 */
    var locationOptEnabled: Boolean = true,
    /** 最低定位时间间隔（毫秒），默认 30s */
    var locationMinIntervalMs: Long = 30_000L,
    /** 是否把后台高频 GPS 降级为网络定位 */
    var locationDowngradeGps: Boolean = true,

    /** 动画关闭（View/属性动画 scale=0）省 GPU */
    var animationOptEnabled: Boolean = false,
    /** 动画 scale 值（0=关闭，1=正常） */
    var animationScale: Float = 0f,

    /** 传感器降频（>50Hz 降至合理值） */
    var sensorOptEnabled: Boolean = true,
    /** 传感器采样周期上限（微秒），默认 200000us = 5Hz */
    var sensorMaxRateUs: Int = 200_000,

    var lastModified: Long = System.currentTimeMillis()
)
