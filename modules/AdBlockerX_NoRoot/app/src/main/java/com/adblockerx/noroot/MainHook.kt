package com.adblockerx.noroot

import android.app.Application
import com.adblockerx.noroot.hooks.AdViewHideHook
import com.adblockerx.noroot.hooks.HostsFilterHook
import com.adblockerx.noroot.hooks.OkHttpAdHook
import com.adblockerx.noroot.hooks.URLConnectionAdHook
import com.adblockerx.noroot.hooks.WebViewAdHook
import com.adblockerx.noroot.utils.ConfigManager
import com.adblockerx.noroot.utils.LogX
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * AdBlockerX NoRoot 主入口
 *
 * 架构说明：
 *  1. 实现 IXposedHookLoadPackage + IXposedHookZygoteInit 双接口
 *  2. LSPatch本地模式下，模块在目标 APP 进程启动时加载
 *  3. 全部 Hook 在应用进程内执行，无系统级修改
 *
 * 工作流程：
 *  APP 启动 -> handleLoadPackage() 触发 ->
 *    判断是否为目标 APP 包名 ->
 *    读取拦截配置 ->
 *    [1] 内存 hosts 过滤器初始化（最先执行，供其他 Hook 查询）
 *    [2] WebView 广告拦截
 *    [3] OkHttp 广告拦截
 *    [4] URLConnection 广告拦截
 *    [5] 广告 SDK View 隐藏
 *
 * 硬性限制总览（NoRoot 版）：
 *  1. 仅拦截本 APP 进程内的网络请求，无法影响其他 APP
 *  2. 绝不修改 /system/etc/hosts、不修改系统 DNS、不设置全局 Private DNS
 *  3. 不持久化 hosts 文件，所有数据存于内存
 *  4. 广告 SDK 类名被混淆时自动跳过，绝不抛异常导致 APP 崩溃
 *  5. LSPatch本地模式修改 APK 签名可能被部分反作弊 SDK 检测
 */
class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "2.0.0"
        const val MODULE_NAME = "AdBlockerX NoRoot"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("===== $MODULE_NAME v$VERSION 初始化 | LSPatch本地模式 =====")
        LogX.i("硬性限制：仅拦截本APP进程内网络请求，不修改系统hosts/DNS")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName ?: return
        if (!isTargetApp(pkg)) return

        LogX.i("===== 目标 APP 启动: $pkg =====")
        currentPkg = pkg

        // 初始化配置（使用目标APP的Context）
        initConfig(lpparam)

        // 加载配置（若失败则使用默认配置）
        val cfg = try { ConfigManager.getConfig() } catch (e: Exception) {
            ConfigManager.defaultConfig()
        }
        LogX.debugEnabled = cfg.logEnabled
        LogX.i("配置: WebView=${cfg.webViewBlockEnabled} OkHttp=${cfg.okHttpBlockEnabled} " +
                "URLConnection=${cfg.urlConnectionBlockEnabled} Hosts=${cfg.hostsFilterEnabled} " +
                "AdView=${cfg.adViewHideEnabled} 注入JS=${cfg.injectJsEnabled} " +
                "内置黑名单=${cfg.builtinBlocklistEnabled} 自定义=${cfg.customBlocklist.size}条")

        // ===== [1] 内存 hosts 过滤器（最先初始化） =====
        if (cfg.hostsFilterEnabled) {
            HostsFilterHook.apply(lpparam, cfg)
        }

        // ===== [2] WebView 广告拦截 =====
        WebViewAdHook.apply(lpparam, cfg)

        // ===== [3] OkHttp 广告拦截 =====
        OkHttpAdHook.apply(lpparam, cfg)

        // ===== [4] URLConnection 广告拦截 =====
        URLConnectionAdHook.apply(lpparam, cfg)

        // ===== [5] 广告 SDK View 隐藏 =====
        AdViewHideHook.apply(lpparam, cfg)

        // 注册App生命周期Hook确保ConfigManager初始化
        hookAppLifecycle(lpparam)

        LogX.i("===== 全部Hook就绪: $pkg =====")
    }

    /**
     * 作用域判断：内置主流浏览器 + WebView 较多的 APP
     * 用户可在 LSPatch 中追加自定义包名
     */
    private fun isTargetApp(pkg: String) = pkg in listOf(
        // 主流第三方浏览器
        "com.android.chrome",            // Chrome
        "com.chrome.beta",               // Chrome Beta
        "com.mi.globalbrowser",          // 小米浏览器
        "com.huawei.browser",            // 华为浏览器
        "com.sec.android.app.sbrowser",  // 三星浏览器
        // 内嵌 WebView 较多的 APP
        "com.tencent.mm",                // 微信
        "com.tencent.mobileqq",          // QQ
        "com.ss.android.ugc.aweme",      // 抖音
        "com.smile.gifmaker",            // 快手
        "com.taobao.taobao",             // 淘宝
        "com.jingdong.app.mall",         // 京东
        "com.xunmeng.pinduoduo",         // 拼多多
        "com.eg.android.AlipayGphone",   // 支付宝
        "com.zhihu.android",             // 知乎
        "com.netease.cloudmusic",        // 网易云音乐
        "com.tencent.wmusic"             // 腾讯音乐
    )

    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            val app = XposedHelpers.callMethod(cat, "getApplication") as? Application
            if (app != null) ConfigManager.init(app)
        } catch (_: Exception) {}
    }

    /** Application.onCreate时补初始化ConfigManager */
    private fun hookAppLifecycle(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val app = p.thisObject as? Application ?: return
                        try { ConfigManager.init(app) } catch (_: Exception) {}
                    }
                })
        } catch (_: Exception) {}
    }
}
