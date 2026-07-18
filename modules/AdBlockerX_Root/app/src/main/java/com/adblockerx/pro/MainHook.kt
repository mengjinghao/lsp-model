package com.adblockerx.pro

import android.app.Application
import com.adblockerx.pro.hooks.AdViewHideHook
import com.adblockerx.pro.hooks.DnsResolverHook
import com.adblockerx.pro.hooks.HostsFilterHook
import com.adblockerx.pro.hooks.OkHttpAdHook
import com.adblockerx.pro.hooks.PrivateDnsHook
import com.adblockerx.pro.hooks.ShizukuBridgeHook
import com.adblockerx.pro.hooks.SystemHostsHook
import com.adblockerx.pro.hooks.URLConnectionAdHook
import com.adblockerx.pro.hooks.WebViewAdHook
import com.adblockerx.pro.utils.ConfigManager
import com.adblockerx.pro.utils.LogX
import com.adblockerx.pro.utils.ShizukuHelper
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * AdBlockerX Pro 主入口（Root 版）
 *
 * 架构说明：
 *  1. 实现 IXposedHookLoadPackage + IXposedHookZygoteInit 双接口
 *  2. 包含 NoRoot 版全部应用层 Hook（WebView/OkHttp/URLConnection/HostsFilter/AdViewHide）
 *  3. 额外增加系统级能力（需 Shizuku/Root）：
 *     - SystemHostsHook：通过 Shizuku 修改 /data/adb/hosts（Magisk overlay 风格）
 *     - PrivateDnsHook：通过 Shizuku 设置系统级 Private DNS
 *     - DnsResolverHook：Hook 系统 DNS 解析（容错，默认关闭）
 *     - ShizukuBridgeHook：Shizuku 桥接，刷新 DNS 缓存
 *
 * 工作流程：
 *  APP 启动 -> handleLoadPackage() 触发 ->
 *    判断是否为目标 APP 包名 ->
 *    读取拦截配置 ->
 *    [1] 内存 hosts 过滤器初始化
 *    [2] WebView 广告拦截
 *    [3] OkHttp 广告拦截
 *    [4] URLConnection 广告拦截
 *    [5] 广告 SDK View 隐藏
 *    [6] 系统 DNS 解析 Hook（容错，默认关闭）
 *    [7] 系统级 hosts 写入（Shizuku）
 *    [8] 系统级 Private DNS 设置（Shizuku）
 *    [9] Shizuku 桥接刷新 DNS 缓存
 *
 * 注意事项：
 *  - 系统级操作前必须检查 Shizuku 可用性
 *  - 系统级操作在 APP 进程执行（不在 system_server 中跑复杂 Hook）
 *  - 仅作用域中的 APP 启动时才触发系统级 hosts/Private DNS 修改
 */
class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.0"
        const val MODULE_NAME = "AdBlockerX Pro"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("===== $MODULE_NAME v$VERSION 初始化 (Root版) =====")
        LogX.i("Shizuku 状态: ${ShizukuHelper.isShizukuAvailable()}")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName ?: return
        if (!isTargetApp(pkg)) return

        LogX.i("===== 目标 APP 启动: $pkg =====")
        currentPkg = pkg

        // 初始化配置
        initConfig(lpparam)

        // 加载配置
        val cfg = try { ConfigManager.getConfig() } catch (e: Exception) {
            ConfigManager.defaultConfig()
        }
        LogX.debugEnabled = cfg.logEnabled
        LogX.i("配置: WebView=${cfg.webViewBlockEnabled} OkHttp=${cfg.okHttpBlockEnabled} " +
                "URLConnection=${cfg.urlConnectionBlockEnabled} Hosts=${cfg.hostsFilterEnabled} " +
                "AdView=${cfg.adViewHideEnabled} 注入JS=${cfg.injectJsEnabled} " +
                "内置黑名单=${cfg.builtinBlocklistEnabled} 自定义=${cfg.customBlocklist.size}条 | " +
                "系统Hosts=${cfg.systemHostsEnabled} PrivateDns=${cfg.privateDnsEnabled}(${cfg.privateDnsHost}) " +
                "DNS Hook=${cfg.dnsResolverHookEnabled} ShizukuBridge=${cfg.shizukuBridgeEnabled}")

        // ===== [1] 内存 hosts 过滤器（最先初始化） =====
        if (cfg.hostsFilterEnabled) {
            HostsFilterHook.apply(lpparam, cfg)
        }

        // ===== 应用层 Hook（同 NoRoot 版） =====
        WebViewAdHook.apply(lpparam, cfg)               // [2]
        OkHttpAdHook.apply(lpparam, cfg)                 // [3]
        URLConnectionAdHook.apply(lpparam, cfg)          // [4]
        AdViewHideHook.apply(lpparam, cfg)               // [5]

        // ===== 系统级能力（Root 版独有） =====
        DnsResolverHook.apply(lpparam, cfg)              // [6] 容错，默认关闭
        SystemHostsHook.apply(lpparam, cfg)              // [7]
        PrivateDnsHook.apply(lpparam, cfg)               // [8]
        ShizukuBridgeHook.apply(lpparam, cfg)            // [9]

        // 注册App生命周期Hook确保ConfigManager初始化
        hookAppLifecycle(lpparam)

        LogX.i("===== 全部Hook就绪: $pkg =====")
    }

    /**
     * 作用域判断：同 NoRoot 版
     * 注意：系统 hosts 通过 Shizuku 操作，不需要 hook system_server (android 包名)
     */
    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.mi.globalbrowser",
        "com.huawei.browser",
        "com.sec.android.app.sbrowser",
        "com.tencent.mm",
        "com.tencent.mobileqq",
        "com.ss.android.ugc.aweme",
        "com.smile.gifmaker",
        "com.taobao.taobao",
        "com.jingdong.app.mall",
        "com.xunmeng.pinduoduo",
        "com.eg.android.AlipayGphone",
        "com.zhihu.android",
        "com.netease.cloudmusic",
        "com.tencent.wmusic"
    )

    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            val app = XposedHelpers.callMethod(cat, "getApplication") as? Application
            if (app != null) ConfigManager.init(app)
        } catch (_: Exception) {}
    }

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
