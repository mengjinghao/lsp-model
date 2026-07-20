package com.gameunlocker.pro

import android.app.Application
import com.gameunlocker.pro.hooks.*
import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.ConfigManager
import com.gameunlocker.pro.utils.HookConfigReader
import com.gameunlocker.pro.utils.LogX
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * GameUnlocker Pro - Xposed 模块唯一入口（Root 版）
 *
 * 实现 IXposedHookLoadPackage + IXposedHookZygoteInit。
 *
 * 配置读取策略：
 *  1. 优先 XSharedPreferences（LSPosed 模式，跨进程直读模块 prefs）
 *  2. 回退 Context.getSharedPreferences（LSPatch 本地模式，同进程）
 *
 * 工作流程：
 *  游戏启动 -> handleLoadPackage ->
 *    判断是否为目标游戏 ->
 *    读取全局配置 ->
 *    [1] 环境隐藏(最先执行，防检测)
 *    [2] 机型伪装(Build属性)
 *    [3] 帧率解锁(Display/Surface/引擎)
 *    [4] 温控屏蔽(系统级，Hook PowerManager/ThermalService)
 *    [5] GPU调度(系统级，Hook EGL/Choreographer)
 *    [6] 进程优化(线程优先级 + Shizuku 冻结后台)
 *    [7] 分辨率伪装(可选)
 *    [8] Shizuku系统属性修改(setprop 刷新率)
 *    [实验] 触摸采样/网络延迟/音频优先/内存整理
 *    [实验] 游戏模式激活/CPU 大核亲和性（需 Shizuku）
 *
 * 系统级能力（需 Shizuku adb 级授权）：
 *  - 温控屏蔽 / GPU 调频
 *  - setprop 修改 ro.surface_flinger.* 刷新率属性
 *  - am force-stop 冻结后台进程
 *  - cmd game_mode / settings put global game_mode
 *  - 写 /sys/devices/system/cpu/cpuN/cpufreq 节点
 */
class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.8"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("GameUnlocker Pro v$VERSION 初始化 | LSPatch/LSPosed 兼容 | 系统级 Hook 已就绪")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName ?: return
        if (!isTargetGame(pkg)) return

        LogX.i("===== 游戏启动: $pkg =====")
        currentPkg = pkg

        initConfig(lpparam)

        val cfg = loadConfig()
        LogX.i("配置: 总开关=${cfg.masterEnabled} 伪装=${cfg.deviceSpoofEnabled} " +
                "帧率=${cfg.targetFps}fps 隐藏=${cfg.detectionHideEnabled} " +
                "温控=${cfg.thermalBypassEnabled} GPU=${cfg.gpuOptimizeEnabled} " +
                "Shizuku=${cfg.shizukuBridgeEnabled} " +
                "[实验]触摸=${cfg.touchSamplingBoostEnabled} 网络=${cfg.networkLatencyOptEnabled} " +
                "音频=${cfg.audioPriorityBoostEnabled} 内存=${cfg.memoryDefragEnabled} " +
                "游戏模式=${cfg.gameModeActivationEnabled} CPU亲和=${cfg.cpuBigCoreAffinityEnabled}")

        if (!cfg.masterEnabled) {
            LogX.i("总开关关闭，跳过所有 Hook")
            return
        }

        // ===== [1] 环境隐藏（最先执行，防检测）=====
        if (cfg.detectionHideEnabled) GameDetectionHideHook.apply(lpparam, cfg)

        // ===== [2] 机型伪装 =====
        if (cfg.deviceSpoofEnabled) DeviceSpoofHook.apply(lpparam, cfg)

        // ===== [3] 帧率解锁 =====
        if (cfg.frameRateUnlockEnabled) FrameRateUnlockHook.apply(lpparam, cfg)

        // ===== [4] 温控屏蔽（系统级）=====
        if (cfg.thermalBypassEnabled) ThermalBypassHook.apply(lpparam, cfg)

        // ===== [5] GPU 调度优化（系统级）=====
        if (cfg.gpuOptimizeEnabled) GPUSchedulerHook.apply(lpparam, cfg)

        // ===== [6] 进程性能优化 =====
        if (cfg.processOptimizeEnabled) ProcessOptimizerHook.apply(lpparam, cfg)

        // ===== [7] 分辨率伪装（可选）=====
        if (cfg.resolutionSpoofEnabled) ResolutionSpoofHook.apply(lpparam, cfg)

        // ===== [8] Shizuku 系统属性修改（系统级）=====
        if (cfg.shizukuBridgeEnabled) ShizukuBridgeHook.apply(lpparam, cfg)

        // ===== 实验性 - 应用层 =====
        if (cfg.touchSamplingBoostEnabled) TouchSamplingBoostHook.apply(lpparam, cfg)
        if (cfg.networkLatencyOptEnabled) NetworkLatencyOptHook.apply(lpparam, cfg)
        if (cfg.audioPriorityBoostEnabled) AudioPriorityBoostHook.apply(lpparam, cfg)
        if (cfg.memoryDefragEnabled) MemoryDefragHook.apply(lpparam, cfg)

        // ===== 实验性 - 系统级 =====
        if (cfg.gameModeActivationEnabled) GameModeActivationHook.apply(lpparam, cfg)
        if (cfg.cpuBigCoreAffinityEnabled) CpuBigCoreAffinityHook.apply(lpparam, cfg)

        hookAppLifecycle(lpparam)
        LogX.i("===== 全部 Hook 就绪: $pkg =====")
    }

    /** 目标游戏包名白名单 */
    private fun isTargetGame(pkg: String) = pkg in listOf(
        "com.tencent.tmgp.sgame",                  // 王者荣耀
        "com.miHoYo.Yuanshen",                     // 原神国内版
        "com.miHoYo.GenshinImpact",                // 原神国际版
        "com.tencent.tmgp.pubgmhd",                // 和平精英
        "com.tencent.ig",                          // PUBG Mobile
        "com.miHoYo.hkrpg",                        // 崩坏星穹铁道
        "com.tencent.tmgp.cod",                    // 使命召唤国内版
        "com.activision.callofduty.shooter",       // CODM 国际版
        "com.tencent.tmgp.gnyx",                   // 高能英雄
        "com.gameblackmyth.mobile",                // 黑神话手游
        "com.miHoYo.ZenlessZoneZero",              // 绝区零
        "com.kurogame.kjq"                         // 鸣潮
    )

    /** 读取配置：优先 XSharedPreferences，回退 Context */
    private fun loadConfig(): GameConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { ConfigManager.getGlobalConfig() } catch (_: Throwable) { GameConfig(packageName = "global") }
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
