package com.gameunlocker.noroot.models

/**
 * 单个游戏独立配置
 */
data class GameConfig(
    val packageName: String,
    val gameName: String = "",

    // 机型伪装
    var deviceSpoofEnabled: Boolean = true,
    var selectedDeviceProfileId: String = "xiaomi15",
    var customDeviceProfile: DeviceProfile? = null,

    // 帧率解锁
    var frameRateUnlockEnabled: Boolean = true,
    var targetFps: Int = 120,

    // 环境隐藏
    var detectionHideEnabled: Boolean = true,
    var hideShizuku: Boolean = true,
    var hideXposed: Boolean = true,
    var hideLspatch: Boolean = true,

    // 分辨率伪装
    var resolutionSpoofEnabled: Boolean = false,
    var spoofWidth: Int = 2560,
    var spoofHeight: Int = 1440,
    var spoofDpi: Int = 560,

    // 进程优化（提升渲染线程优先级+冻结后台）
    var processOptimizeEnabled: Boolean = true,

    var lastModified: Long = System.currentTimeMillis()
)
