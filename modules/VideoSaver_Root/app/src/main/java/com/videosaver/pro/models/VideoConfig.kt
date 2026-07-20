package com.videosaver.pro.models

/**
 * 短视频去水印下载配置（Root 版）
 *
 * 包含 NoRoot 版全部字段，并额外增加系统级 Hook 配置：
 *  - SystemDownloadHook: Shizuku 调用 downloadmanager 系统下载服务
 *  - ShizukuVideoBridgeHook: Shizuku 执行 am broadcast 触发下载
 *  - GlobalVideoAdBlockHook: Shizuku 修改 hosts 屏蔽视频广告
 *  - KernelVideoEnhanceHook: Shizuku 写 /sys/class/video 节点（部分设备）
 *
 * 硬性限制：
 *  - 系统级 Hook 必须先检查 ShizukuHelper.isShizukuAvailable()
 *  - setprop 修改非持久化，重启后消失
 *  - 写 /sys 节点需要 root 级别 Shizuku 授权
 *  - hosts 修改需 root 权限
 */
data class VideoConfig(
    // ===== 基础 =====
    var packageName: String = "",
    var masterEnabled: Boolean = true,

    // ===== 各平台无水印下载（同 NoRoot） =====
    var douyinNoWatermark: Boolean = true,
    var kuaishouNoWatermark: Boolean = true,
    var xhsNoWatermark: Boolean = true,
    var biliDownload: Boolean = true,

    // ===== 保存路径 =====
    var customSavePath: String = "/sdcard/Download/VideoSaver/",
    var autoRenameEnabled: Boolean = true,

    // ===== 实验性（同 NoRoot） =====
    var autoDownloadEnabled: Boolean = false,
    var removeAdsEnabled: Boolean = false,
    var saveOriginalQualityEnabled: Boolean = false,
    var batchDownloadEnabled: Boolean = false,

    // ===== Root 专属：系统下载服务（Shizuku downloadmanager） =====
    var systemDownloadEnabled: Boolean = false,
    var useSystemDownloadNotification: Boolean = true,  // 使用系统下载通知

    // ===== Root 专属：Shizuku 视频桥接（am broadcast 触发下载） =====
    var shizukuVideoBridgeEnabled: Boolean = false,
    var broadcastAction: String = "com.videosaver.pro.ACTION_DOWNLOAD",

    // ===== Root 实验性：全局视频广告屏蔽（修改 hosts） =====
    var globalVideoAdBlockEnabled: Boolean = false,
    var adBlockHosts: MutableList<String> = mutableListOf(
        "ad.toutiao.com",
        "pangolin-sdk-toutiao.com",
        "dsp.toutiao.com",
        "pglstatp-toutiao.com",
        "sdk.e.qq.com",
        "googleads.g.doubleclick.net"
    ),

    // ===== Root 专属：系统级屏幕录制/截图 =====
    var systemScreenCaptureEnabled: Boolean = false,
    // ===== Root 专属：系统级 HTTP 代理 =====
    var systemProxyEnabled: Boolean = false,

    // ===== Root 实验性：内核视频增强（写 /sys/class/video） =====
    var kernelVideoEnhanceEnabled: Boolean = false,
    var enhanceBrightness: Int = 0,        // 0=默认, 正数=增强
    var enhanceContrast: Int = 0,
    var enhanceSaturation: Int = 0,

    // ===== 元数据 =====
    var lastModified: Long = 0L
)
