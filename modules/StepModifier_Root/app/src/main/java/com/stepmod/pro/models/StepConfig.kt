package com.stepmod.pro.models

/**
 * 步数修改配置（Root 版）
 *
 * 包含 NoRoot 版全部字段 + Root 专属系统级 Hook 开关。
 *
 * 硬性限制（Root 版严格遵守）：
 *  - 系统级 Hook 必须先检查 ShizukuHelper.isShizukuAvailable()
 *  - 系统级写入失败时降级为应用层 Hook
 *  - 写 /sys /proc 内核节点需要 root 级别 Shizuku 授权
 */
data class StepConfig(
    // ===== 基础（同 NoRoot） =====
    var packageName: String = "",
    var masterEnabled: Boolean = true,
    var stepModifyEnabled: Boolean = true,

    var customSteps: Int = 10000,
    var randomFluctuation: Int = 200,

    var targetAppList: MutableList<String> = mutableListOf(
        "com.eg.android.AlipayGphone",
        "com.tencent.mm",
        "com.tencent.mobileqq",
        "com.tencent.tim",
        "com.xiaomi.hm.health",
        "com.huawei.health",
        "com.codoon.gps",
        "com.joyrun.gps",
        "com.keepfitness",
        "com.ss.android.ugc.aweme",
        "com.smile.gifmaker",
        "com.netease.cloudmusic",
        "com.tencent.wmusic",
        "com.taobao.taobao",
        "com.jingdong.app.mall"
    ),

    // ===== 实验性（同 NoRoot） =====
    var sensorBlockEnabled: Boolean = false,
    var multiAppSyncEnabled: Boolean = false,
    var stepHistoryFakeEnabled: Boolean = false,

    // ===== Root 专属：系统级 Hook =====
    var systemSensorEnabled: Boolean = false,        // 系统传感器节点写入
    var healthServiceEnabled: Boolean = false,       // Google Fit / 华为健康 API 系统级写入

    // ===== Root 实验性 =====
    var kernelStepInjectEnabled: Boolean = false,    // 内核节点注入
    var shizukuStepBridgeEnabled: Boolean = false,   // Shizuku 广播桥接

    var lastModified: Long = 0L
)
