package com.adblockerx.pro.models

/**
 * AdBlockerX Pro 拦截配置（Root 版）
 *
 * 包含 NoRoot 版全部应用层开关 + Root 版独有的系统级开关：
 *  - systemHostsEnabled: 通过 Shizuku 修改系统级 hosts（Magisk overlay）
 *  - privateDnsEnabled: 通过 Shizuku 设置系统级广告过滤 Private DNS
 *  - dnsResolverHookEnabled: Hook 系统 DNS 解析返回 127.0.0.1
 *  - shizukuBridgeEnabled: Shizuku 桥接（刷新 DNS 缓存等）
 *
 * 实验性（Root 版独有）：
 *  - iptablesBlockEnabled: Shizuku iptables -A OUTPUT -d <ad_ip> -j DROP
 *  - vpnBasedBlockEnabled: Hook VpnService 建立本地 VPN 拦截
 */
data class AdBlockConfig(
    // ===== 总开关 =====
    var masterEnabled: Boolean = true,

    // ===== 应用层基础 Hook（同 NoRoot 版） =====
    var webviewAdEnabled: Boolean = true,
    var okHttpAdEnabled: Boolean = true,
    var urlConnectionAdEnabled: Boolean = true,
    var hostsFilterEnabled: Boolean = true,
    var adViewHideEnabled: Boolean = true,

    // ===== 应用层实验性（同 NoRoot 版） =====
    var trackerBlockEnabled: Boolean = false,
    var cookieCleanEnabled: Boolean = false,
    var redirectBlockEnabled: Boolean = false,
    var intentInterceptorEnabled: Boolean = false,
    // ===== v1.0.6 新增（对标 AdClose） =====
    /** 截图录屏限制移除（Hook FLAG_SECURE 让目标APP可截图录屏） */
    var screenshotUnlockEnabled: Boolean = false,
    /** 摇一摇广告跳转禁用（Hook SensorManager 加速度计，阻止摇一摇触发广告） */
    var shakeAdBlockEnabled: Boolean = false,
    /** VPN/代理检测绕过（Hook NetworkInfo/ConnectivityManager 返回非VPN） */
    var vpnDetectBypassEnabled: Boolean = false,

    // ===== 实验性：Ad Pattern自学习 =====
    var adPatternLearnEnabled: Boolean = false,

    // ===== 实验性：DNS-over-HTTPS代理 =====
    var dnsOverHttpsEnabled: Boolean = false,
    var dohEndpoint: String = "dns.adguard.com",

    // ===== 实验性：App风险评分 =====
    var appRiskScorerEnabled: Boolean = false,

    // ===== 实验性：WebView DOM Cleaner =====
    var webViewDomCleanerEnabled: Boolean = false,

    // ===== 参数 =====
    var injectJsEnabled: Boolean = false,
    var builtinBlocklistEnabled: Boolean = true,
    var customBlocklist: List<String> = emptyList(),
    var logEnabled: Boolean = true,
    var blockedCount: Long = 0L,

    // ===== Root 版独有：系统级开关 =====
    /** 系统级 hosts 文件修改（Shizuku/Root，Magisk overlay） */
    var systemHostsEnabled: Boolean = false,
    /** 系统级 Private DNS 设置（Shizuku/Root） */
    var privateDnsEnabled: Boolean = false,
    /** Private DNS 主机名（用户可填写自己的广告过滤 DNS） */
    var privateDnsHost: String = "dns.adblockplus.org",
    /** 系统 DNS 解析 Hook（InetAddress/Network/Libcore 返回 127.0.0.1） */
    var dnsResolverHookEnabled: Boolean = false,
    /** Shizuku 桥接（刷新 DNS 缓存等） */
    var shizukuBridgeEnabled: Boolean = true,

    // ===== Root 实验性 =====
    /** iptables OUTPUT 链 DROP 广告 IP（Shizuku/Root） */
    var iptablesBlockEnabled: Boolean = false,
    /** 本地 VPN 拦截（Hook VpnService） */
    var vpnBasedBlockEnabled: Boolean = false,
    /** 系统级 sysfs DNS 配置（Shizuku mount --bind + settings put private_dns + ndc setnetdns） */
    var sysfsDnsEnabled: Boolean = false,

    var x5WebViewEnabled: Boolean = true,
    var layoutInflaterAdEnabled: Boolean = true,
    var whitelistDomains: List<String> = emptyList(),

    var lastModified: Long = System.currentTimeMillis()
)
