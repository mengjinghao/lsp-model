package com.adblockerx.noroot.models

/**
 * AdBlockerX NoRoot 拦截配置
 *
 * 仅作用于当前 APP 进程内的网络请求/视图层级，
 * 不修改任何系统文件、不影响其他 APP 和系统 DNS。
 */
data class AdBlockConfig(
    // ===== Hook 开关 =====
    /** WebView 广告拦截 (shouldOverrideUrlLoading / shouldInterceptRequest / loadUrl / onPageFinished 注入 CSS/JS) */
    var webViewBlockEnabled: Boolean = true,
    /** OkHttp 请求拦截 (newCall / RealCall.execute / Interceptor.Chain.proceed) */
    var okHttpBlockEnabled: Boolean = true,
    /** URLConnection / HttpURLConnection 拦截 */
    var urlConnectionBlockEnabled: Boolean = true,
    /** 内存 hosts 黑名单查询（启用内置+自定义黑名单） */
    var hostsFilterEnabled: Boolean = true,
    /** 广告 SDK View 隐藏 (GDT/百度/字节/小米 等广告 SDK) */
    var adViewHideEnabled: Boolean = true,

    /** WebView 注入 JS 隐藏广告元素（实验性，可能影响页面正常显示） */
    var injectJsEnabled: Boolean = false,

    /** 是否启用内置广告域名黑名单 */
    var builtinBlocklistEnabled: Boolean = true,
    /** 用户自定义广告域名（一行一个，支持子域名 endsWith 匹配） */
    var customBlocklist: List<String> = emptyList(),

    /** 是否启用日志输出 */
    var logEnabled: Boolean = true,

    /** 拦截命中计数（仅用于显示） */
    var blockedCount: Long = 0L,

    var lastModified: Long = System.currentTimeMillis()
)
