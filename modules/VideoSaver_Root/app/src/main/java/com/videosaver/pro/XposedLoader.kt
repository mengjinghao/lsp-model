package com.videosaver.pro

import android.app.Application
import com.videosaver.pro.hooks.AutoDownloadHook
import com.videosaver.pro.hooks.BatchDownloadHook
import com.videosaver.pro.hooks.BiliDownloadHook
import com.videosaver.pro.hooks.DouyinNoWatermarkHook
import com.videosaver.pro.hooks.GlobalVideoAdBlockHook
import com.videosaver.pro.hooks.KernelVideoEnhanceHook
import com.videosaver.pro.hooks.KuaishouNoWatermarkHook
import com.videosaver.pro.hooks.RemoveVideoAdsHook
import com.videosaver.pro.hooks.SaveOriginalQualityHook
import com.videosaver.pro.hooks.ShizukuVideoBridgeHook
import com.videosaver.pro.hooks.SystemDownloadHook
import com.videosaver.pro.hooks.XhsNoWatermarkHook
import com.videosaver.pro.models.VideoConfig
import com.videosaver.pro.utils.ConfigManager
import com.videosaver.pro.utils.HookConfigReader
import com.videosaver.pro.utils.LogX
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * VideoSaver Pro - Xposed 模块唯一入口（Root 版）
 *
 * 实现 IXposedHookLoadPackage + IXposedHookZygoteInit。
 *
 * 工作流程：
 *  APP启动 -> handleLoadPackage ->
 *    判断是否为目标APP ->
 *    读取全局配置 ->
 *    [1] 抖音无水印 [2] 快手无水印 [3] 小红书无水印 [4] B站下载
 *    [实验] 自动下载 / 去广告 / 原画质 / 批量下载
 *    [Root] 系统下载 / Shizuku 桥接
 *    [Root 实验] 全局广告屏蔽 / 内核视频增强
 *
 * 硬性限制：
 *  - Root 系统级 Hook 必须先检查 ShizukuHelper.isShizukuAvailable()
 *  - 系统级 Hook 失败时降级为应用层 Hook
 */
class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.10"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("VideoSaver Pro v$VERSION 初始化 | Root 版")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName ?: return
        if (!isTargetApp(pkg)) return

        LogX.i("===== APP启动: $pkg =====")
        currentPkg = pkg

        initConfig(lpparam)

        val cfg = loadConfig()
        cfg.packageName = pkg
        LogX.i("配置: 总开关=${cfg.masterEnabled} 抖音=${cfg.douyinNoWatermark} " +
                "快手=${cfg.kuaishouNoWatermark} 小红书=${cfg.xhsNoWatermark} B站=${cfg.biliDownload} " +
                "[实验]自动下载=${cfg.autoDownloadEnabled} 去广告=${cfg.removeAdsEnabled} " +
                "原画质=${cfg.saveOriginalQualityEnabled} 批量下载=${cfg.batchDownloadEnabled} " +
                "[Root]系统下载=${cfg.systemDownloadEnabled} Shizuku桥接=${cfg.shizukuVideoBridgeEnabled} " +
                "[Root实验]全局广告=${cfg.globalVideoAdBlockEnabled} 内核增强=${cfg.kernelVideoEnhanceEnabled}")

        if (!cfg.masterEnabled) {
            LogX.i("总开关关闭，跳过所有Hook")
            return
        }

        // ===== 基础功能（同 NoRoot） =====
        if (cfg.douyinNoWatermark) DouyinNoWatermarkHook.apply(lpparam, cfg)
        if (cfg.kuaishouNoWatermark) KuaishouNoWatermarkHook.apply(lpparam, cfg)
        if (cfg.xhsNoWatermark) XhsNoWatermarkHook.apply(lpparam, cfg)
        if (cfg.biliDownload) BiliDownloadHook.apply(lpparam, cfg)

        // ===== 实验性（同 NoRoot） =====
        if (cfg.autoDownloadEnabled) AutoDownloadHook.apply(lpparam, cfg)
        if (cfg.removeAdsEnabled) RemoveVideoAdsHook.apply(lpparam, cfg)
        if (cfg.saveOriginalQualityEnabled) SaveOriginalQualityHook.apply(lpparam, cfg)
        if (cfg.batchDownloadEnabled) BatchDownloadHook.apply(lpparam, cfg)

        // ===== Root 专属：系统级 Hook（需 Shizuku） =====
        if (cfg.systemDownloadEnabled) SystemDownloadHook.apply(lpparam, cfg)
        if (cfg.shizukuVideoBridgeEnabled) ShizukuVideoBridgeHook.apply(lpparam, cfg)

        // ===== Root 实验性 =====
        if (cfg.globalVideoAdBlockEnabled) GlobalVideoAdBlockHook.apply(lpparam, cfg)
        if (cfg.kernelVideoEnhanceEnabled) KernelVideoEnhanceHook.apply(lpparam, cfg)

        hookAppLifecycle(lpparam)
        LogX.i("===== 全部Hook就绪: $pkg =====")
    }

    /** 目标APP包名白名单 */
    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.ss.android.ugc.aweme",       // 抖音
        "com.ss.android.ugc.aweme.lite",  // 抖音极速版
        "com.smile.gifmaker",             // 快手
        "com.kuaishou.nebula",            // 快手极速版
        "com.xingin.xhs",                 // 小红书
        "com.xingin.xhscircle",           // 小红书圈子
        "tv.danmaku.bili",                // B站
        "com.tencent.qqlive",             // 腾讯视频
        "com.ss.android.article.video",   // 西瓜视频
        "com.hihonor.cloudmusic"          // 华为音乐
    )

    /** 读取配置：优先XSharedPreferences，回退Context */
    private fun loadConfig(): VideoConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { ConfigManager.getGlobalConfig() } catch (_: Throwable) { VideoConfig(packageName = "global") }
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
