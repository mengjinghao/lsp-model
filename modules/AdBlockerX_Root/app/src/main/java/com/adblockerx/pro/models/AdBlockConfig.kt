package com.adblockerx.pro.models

/**
 * AdBlockerX Pro 拦截配置
 *
 * 包含 NoRoot 版全部应用层开关 + Root 版独有的系统级开关：
 *  - systemHostsEnabled: 通过 Shizuku 修改系统级 hosts（/data/adb/hosts 或 Magisk overlay）
 *  - privateDnsEnabled: 通过 Shizuku 设置系统级广告过滤 Private DNS
 *  - dnsResolverHookEnabled: Hook 系统 DNS 解析（容错）
 *  - shizukuBridgeEnabled: Shizuku 桥接（刷新 DNS 缓存等）
 */
data class AdBlockConfig(
    // ===== 应用层 Hook 开关（同 NoRoot 版） =====
    var webViewBlockEnabled: Boolean = true,
    var okHttpBlockEnabled: Boolean = true,
    var urlConnectionBlockEnabled: Boolean = true,
    var hostsFilterEnabled: Boolean = true,
    var adViewHideEnabled: Boolean = true,
    var injectJsEnabled: Boolean = false,
    var builtinBlocklistEnabled: Boolean = true,
    var customBlocklist: List<String> = emptyList(),
    var logEnabled: Boolean = true,
    var blockedCount: Long = 0L,

    // ===== Root 版独有：系统级开关 =====
    /** 系统级 hosts 文件修改（Shizuku/Root） */
    var systemHostsEnabled: Boolean = false,
    /** 系统级 Private DNS 设置（Shizuku/Root） */
    var privateDnsEnabled: Boolean = false,
    /** Private DNS 主机名（用户可填写自己的广告过滤 DNS） */
    var privateDnsHost: String = "dns.adblockplus.org",
    /** 系统 DNS 解析 Hook（容错，hook android.net.Network/Libcore） */
    var dnsResolverHookEnabled: Boolean = false,
    /** Shizuku 桥接（刷新 DNS 缓存等） */
    var shizukuBridgeEnabled: Boolean = true,

    var lastModified: Long = System.currentTimeMillis()
)
