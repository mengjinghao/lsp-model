package com.adblockerx.pro

import android.app.Application
import com.adblockerx.pro.hooks.AdViewHideHook
import com.adblockerx.pro.hooks.AdClosePlusHook
import com.adblockerx.pro.hooks.CookieCleanHook
import com.adblockerx.pro.hooks.DnsResolverHook
import com.adblockerx.pro.hooks.HostsFilterHook
import com.adblockerx.pro.hooks.IntentInterceptorHook
import com.adblockerx.pro.hooks.IptablesBlockHook
import com.adblockerx.pro.hooks.LayoutInflaterHook
import com.adblockerx.pro.hooks.OkHttpAdHook
import com.adblockerx.pro.hooks.PrivateDnsHook
import com.adblockerx.pro.hooks.RedirectBlockHook
import com.adblockerx.pro.hooks.ShizukuBridgeHook
import com.adblockerx.pro.hooks.SystemHostsHook
import com.adblockerx.pro.hooks.TrackerBlockHook
import com.adblockerx.pro.hooks.URLConnectionAdHook
import com.adblockerx.pro.hooks.VpnBasedBlockHook
import com.adblockerx.pro.hooks.WebViewAdHook
import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.ConfigManager
import com.adblockerx.pro.utils.HookConfigReader
import com.adblockerx.pro.utils.LogX
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * AdBlockerX Pro - Xposed 模块唯一入口（Root 版）
 *
 * 实现 IXposedHookLoadPackage + IXposedHookZygoteInit。
 *
 * 工作流程：
 *  APP启动 -> handleLoadPackage ->
 *    判断是否为目标APP ->
 *    读取全局配置 ->
 *    [1] 内存 hosts 过滤器初始化（最先执行）
 *    [2] WebView 广告拦截
 *    [3] OkHttp 广告拦截
 *    [4] URLConnection 广告拦截
 *    [5] 广告 SDK View 隐藏
 *    [实验] 追踪 / Cookie / 重定向 / Intent 拦截
 *    [Root] 系统hosts / PrivateDNS / DNS解析 / Shizuku桥接
 *    [Root 实验] iptables / VPN 拦截
 *
 * 硬性限制：
 *  - Root 系统级 Hook 必须先检查 ShizukuHelper.isShizukuAvailable()
 */
class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.10"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("AdBlockerX Pro v$VERSION 初始化 | Root 版 | LSPatch/LSPosed 兼容")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName ?: return
        if (!isTargetApp(pkg)) return

        LogX.i("===== APP启动: $pkg =====")
        currentPkg = pkg

        initConfig(lpparam)

        val cfg = loadConfig()
        LogX.debugEnabled = cfg.logEnabled
        LogX.i("配置: 总开关=${cfg.masterEnabled} WebView=${cfg.webviewAdEnabled} OkHttp=${cfg.okHttpAdEnabled} " +
                "URLConnection=${cfg.urlConnectionAdEnabled} Hosts=${cfg.hostsFilterEnabled} AdView=${cfg.adViewHideEnabled} " +
                "[实验]Tracker=${cfg.trackerBlockEnabled} Cookie=${cfg.cookieCleanEnabled} " +
                "Redirect=${cfg.redirectBlockEnabled} Intent=${cfg.intentInterceptorEnabled} " +
                "X5WebView=${cfg.x5WebViewEnabled} LayoutInflaterAd=${cfg.layoutInflaterAdEnabled} " +
                "[Root]SystemHosts=${cfg.systemHostsEnabled} PrivateDns=${cfg.privateDnsEnabled} " +
                "DnsResolver=${cfg.dnsResolverHookEnabled} ShizukuBridge=${cfg.shizukuBridgeEnabled} " +
                "[Root实验]Iptables=${cfg.iptablesBlockEnabled} VPN=${cfg.vpnBasedBlockEnabled}")

        if (!cfg.masterEnabled) {
            LogX.i("总开关关闭，跳过所有Hook")
            return
        }

        // ===== [1] 内存 hosts 过滤器（最先初始化） =====
        if (cfg.hostsFilterEnabled) {
            HostsFilterHook.apply(lpparam, cfg)
        }

        // ===== [2]-[5] 应用层基础 Hook =====
        if (cfg.webviewAdEnabled) WebViewAdHook.apply(lpparam, cfg)
        if (cfg.okHttpAdEnabled) OkHttpAdHook.apply(lpparam, cfg)
        if (cfg.urlConnectionAdEnabled) URLConnectionAdHook.apply(lpparam, cfg)
        if (cfg.adViewHideEnabled) AdViewHideHook.apply(lpparam, cfg)
        if (cfg.layoutInflaterAdEnabled) LayoutInflaterHook.apply(lpparam, cfg)

        // ===== 应用层实验性 =====
        if (cfg.trackerBlockEnabled) TrackerBlockHook.apply(lpparam, cfg)
        if (cfg.cookieCleanEnabled) CookieCleanHook.apply(lpparam, cfg)
        if (cfg.redirectBlockEnabled) RedirectBlockHook.apply(lpparam, cfg)
        if (cfg.intentInterceptorEnabled) IntentInterceptorHook.apply(lpparam, cfg)

        // ===== v1.0.6 新增（对标 AdClose） =====
        if (cfg.screenshotUnlockEnabled || cfg.shakeAdBlockEnabled || cfg.vpnDetectBypassEnabled) {
            AdClosePlusHook.apply(lpparam, cfg)
        }

        // ===== Root 专属：系统级 Hook =====
        if (cfg.systemHostsEnabled) SystemHostsHook.apply(lpparam, cfg)
        if (cfg.privateDnsEnabled) PrivateDnsHook.apply(lpparam, cfg)
        if (cfg.dnsResolverHookEnabled) DnsResolverHook.apply(lpparam, cfg)
        if (cfg.shizukuBridgeEnabled) ShizukuBridgeHook.apply(lpparam, cfg)

        // ===== Root 实验性 =====
        if (cfg.iptablesBlockEnabled) IptablesBlockHook.apply(lpparam, cfg)
        if (cfg.vpnBasedBlockEnabled) VpnBasedBlockHook.apply(lpparam, cfg)

        hookAppLifecycle(lpparam)
        LogX.i("===== 全部Hook就绪: $pkg =====")
    }

    /** 目标APP包名白名单 */
    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.android.chrome",
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
        "com.tencent.wmusic",
        "com.ss.android.article.news",
        "com.tencent.news",
        "com.baidu.tieba",
        "com.sina.weibo",
        "com.tencent.qqlive",
        "com.youku.phone",
        "com.android.browser",
        "com.UCMobile",
        "com.tencent.mtt",
        "com.sogou.activity.src",
        "com.baidu.searchbox",
        "com.quark.browser",
        "com.qihoo.browser"
    )

    private fun loadConfig(): AdBlockConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { ConfigManager.getGlobalConfig() } catch (_: Throwable) { AdBlockConfig() }
    }

    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            val app = XposedHelpers.callMethod(cat, "getApplication") as? Application
            if (app != null) ConfigManager.init(app)
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun hookAppLifecycle(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val app = p.thisObject as? Application ?: return
                        try { ConfigManager.init(app) } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }
}
