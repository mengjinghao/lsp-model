package com.gameunlocker.noroot

import android.app.Application
import com.gameunlocker.noroot.hooks.*
import com.gameunlocker.noroot.models.GameConfig
import com.gameunlocker.noroot.utils.*
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Game-Unlocker Pro NoRoot 主入口
 *
 * 架构说明：
 *  1. 实现 IXposedHookLoadPackage + IXposedHookZygoteInit 双接口
 *  2. LSPatch本地模式下，模块在目标游戏进程启动时加载
 *  3. 全部Hook在应用进程内执行，无系统级修改
 *
 * 工作流程：
 *  游戏启动 -> handleLoadPackage() 触发 ->
 *    判断是否为目标游戏包名 ->
 *    读取该游戏的独立配置 ->
 *    [1] 环境隐藏 (最先执行，防检测)
 *    [2] 机型伪装 (Build属性)
 *    [3] 帧率解锁 (Display/Surface/引擎)
 *    [4] 进程优化 (线程优先级+后台冻结)
 *    [5] 分辨率伪装 (可选)
 *    -> 游戏退出自动释放
 *
 * 硬性限制总览：
 *  1. 仅作用于被LSPatch修补的单款游戏，无法全局伪装
 *  2. 无Root不能修改内核温控节点，高温下SOC硬件级保护仍会触发
 *  3. 所有setprop需Shizuku授权，否则无效
 *  4. LSPatch本地模式修改APK签名可能导致部分游戏反作弊SDK检测
 */
class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "2.0.0"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("Game-Unlocker Pro NoRoot v$VERSION 初始化 | LSPatch本地模式")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName ?: return
        if (!isGame(pkg)) return

        LogX.i("===== 游戏启动: $pkg =====")
        currentPkg = pkg

        // 初始化配置（使用目标APP的Context）
        initConfig(lpparam)

        // 加载配置
        val cfg = try {
            ConfigManager.getGameConfig(pkg)
        } catch (e: Exception) {
            ConfigManager.createDefault(pkg)
        }

        LogX.i("配置: 伪装=${cfg.deviceSpoofEnabled} 帧率=${cfg.targetFps}fps " +
                "隐藏=${cfg.detectionHideEnabled} 优化=${cfg.processOptimizeEnabled}")

        // ===== [1] 环境隐藏（最先执行） =====
        if (cfg.detectionHideEnabled) {
            GameDetectionHideHook.apply(lpparam, cfg)
        }

        // ===== [2] 机型伪装 =====
        val profile = if (cfg.deviceSpoofEnabled) {
            cfg.customDeviceProfile
                ?: DeviceProfileDatabase.findById(cfg.selectedDeviceProfileId)
                ?: DeviceProfileDatabase.findById("xiaomi15")
        } else null

        if (profile != null) {
            DeviceSpoofHook.apply(lpparam, profile)
        }

        // ===== [3] 帧率解锁 =====
        if (cfg.frameRateUnlockEnabled) {
            FrameRateUnlockHook.apply(lpparam, cfg)
        }

        // ===== [4] 进程性能优化 =====
        if (cfg.processOptimizeEnabled) {
            ProcessOptimizerHook.apply(lpparam, cfg)
        }

        // ===== [5] 分辨率伪装（可选） =====
        if (cfg.resolutionSpoofEnabled) {
            ResolutionSpoofHook.apply(lpparam, cfg)
        }

        // 注册App生命周期Hook确保ConfigManager初始化
        hookAppLifecycle(lpparam)

        LogX.i("===== 全部Hook就绪: $pkg =====")
    }

    /** 游戏包名白名单 */
    private fun isGame(pkg: String) = pkg in listOf(
        "com.tencent.tmgp.sgame",      // 王者荣耀
        "com.miHoYo.Yuanshen",         // 原神
        "com.miHoYo.GenshinImpact",    // 原神国际版
        "com.tencent.tmgp.pubgmhd",    // 和平精英
        "com.tencent.ig",              // PUBG Mobile
        "com.miHoYo.hkrpg",            // 崩坏星穹铁道
        "com.tencent.tmgp.cod",        // 使命召唤
        "com.activision.callofduty.shooter",
        "com.tencent.tmgp.gnyx",       // 高能英雄
        "com.gameblackmyth.mobile"     // 黑神话手游
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
