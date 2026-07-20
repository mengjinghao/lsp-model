package com.vipunlock.pro.models

/**
 * VIP 解锁配置（Root 版）
 *
 * 包含 NoRoot 版全部字段，并额外增加系统级 Hook 配置：
 *  - SystemPropVipHook: Shizuku setprop 修改 ro.product.model 伪装高端机型（部分APP据此开放VIP）
 *  - LicenseVerifyHook: Hook Google Play LicenseVerificationListener 返回已授权
 *  - ShizukuVipBridgeHook: 【实验性】Shizuku 执行 pm grant 授予隐藏权限
 *  - GlobalAdBlockHook: 【实验性】Shizuku 修改 hosts 全局屏蔽广告域名
 *
 * 硬性限制：
 *  - 系统级 Hook 必须先检查 ShizukuHelper.isShizukuAvailable()
 *  - setprop 修改非持久化，重启后消失
 *  - 修改 /system/etc/hosts 需 root 级 Shizuku 授权
 */
data class VipConfig(
    // ===== 基础 =====
    var packageName: String = "",
    var masterEnabled: Boolean = true,

    // ===== 音乐类 VIP =====
    var netEaseVipEnabled: Boolean = true,
    var qqMusicVipEnabled: Boolean = true,
    var kugouVipEnabled: Boolean = true,
    var kuwoVipEnabled: Boolean = true,

    // ===== 视频类 VIP =====
    var iqiyiVipEnabled: Boolean = true,
    var youkuVipEnabled: Boolean = true,
    var tencentVideoVipEnabled: Boolean = true,
    var biliVipEnabled: Boolean = true,

    // ===== 阅读/资讯类 VIP =====
    var ximalayaVipEnabled: Boolean = true,
    var toutiaoVipEnabled: Boolean = false,
    var zhihuVipEnabled: Boolean = true,

    // ===== 工具类 VIP =====
    var baiduNetdiskVipEnabled: Boolean = true,
    var wpsVipEnabled: Boolean = true,
    var wereadVipEnabled: Boolean = true,

    // ===== 应用层实验性（同 NoRoot） =====
    var universalVipTryEnabled: Boolean = false,
    var removeAdsEnabled: Boolean = false,
    var bypassVerifyEnabled: Boolean = false,

    // ===== Root 专属：系统属性伪装（Shizuku setprop） =====
    var systemPropVipEnabled: Boolean = false,
    var spoofProductModel: String = "Pixel 8 Pro",
    var spoofProductBrand: String = "google",
    var spoofProductManufacturer: String = "Google",
    var spoofProductDevice: String = "husky",

    // ===== Root 专属：Google Play License 授权 Hook =====
    var licenseVerifyEnabled: Boolean = false,

    // ===== Root 实验性：Shizuku 权限桥接（pm grant） =====
    var shizukuVipBridgeEnabled: Boolean = false,
    var grantedHiddenPermissions: MutableList<String> = mutableListOf(
        "android.permission.WRITE_MEDIA_STORAGE",
        "android.permission.INSTALL_PACKAGES"
    ),

    // ===== Root 实验性：全局广告屏蔽（hosts 修改） =====
    var globalAdBlockEnabled: Boolean = false,
    var blockedAdDomains: MutableList<String> = mutableListOf(
        "pgdt.ugdtimg.com",          // GDT
        "ad.toutiao.com",            // 穿山甲
        "pglstatp.com",              // 穿山甲统计
        "nb.adsbtrk.com",            // 百度
        "ad.tui.cn"                  // 推啊
    ),

    var lastModified: Long = 0L
)
