package com.batteryopt.pro

import android.app.Application
import com.batteryopt.pro.hooks.*
import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.*
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * BatteryOptimizer Pro 主入口（Root 版）
 *
 * 架构说明：
 *  1. 实现 IXposedHookLoadPackage + IXposedHookZygoteInit 双接口
 *  2. LSPatch本地模式下，模块在目标 APP 进程启动时加载
 *  3. 包含 NoRoot 版全部应用层 Hook + 5 个系统级 Hook（依赖 Shizuku）
 *
 * 工作流程：
 *  APP启动 -> handleLoadPackage() 触发 ->
 *    判断是否为目标耗电大户包名 ->
 *    读取该 APP 的独立配置 ->
 *    [A] 应用层 Hook（7 个）：
 *        WakeLock / Alarm / Sync / Job / Location / Animation / Sensor
 *    [B] 系统级 Hook（5 个，需 Shizuku）：
 *        SystemDoze / BackgroundFreeze / CpuGovernor / GreenifyBridge / ShizukuBridge
 *
 * 系统级 Hook 注意事项：
 *  - 必须先检查 Shizuku 可用性（ShizukuBridgeHook 统一检测）
 *  - 屏幕开关广播由各系统级 Hook 自行注册监听
 *  - 系统 Doze/冻结/CPU 仅在屏幕关闭时触发
 *  - 不可在 system_server 之外修改 PowerManagerService，Greenify 通过 dumpsys 文本分析
 */
class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.0"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("BatteryOptimizer Pro v$VERSION 初始化 | LSPosed + Shizuku 模式")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName ?: return
        if (!isTargetApp(pkg)) return

        LogX.i("===== 目标APP启动: $pkg =====")
        currentPkg = pkg

        // 初始化配置（使用目标APP的Context）
        initConfig(lpparam)

        // 加载配置
        val cfg = try {
            ConfigManager.getAppConfig(pkg)
        } catch (e: Exception) {
            ConfigManager.createDefault(pkg)
        }

        LogX.i("配置: wakelock=${cfg.wakeLockOptEnabled} alarm=${cfg.alarmOptEnabled} " +
                "sync=${cfg.syncOptEnabled} job=${cfg.jobOptEnabled} " +
                "location=${cfg.locationOptEnabled} anim=${cfg.animationOptEnabled} " +
                "sensor=${cfg.sensorOptEnabled}")
        LogX.i("系统级: doze=${cfg.dozeEnabled} freeze=${cfg.freezeEnabled} " +
                "cpu=${cfg.cpuGovernorEnabled} greenify=${cfg.greenifyEnabled}")

        // ===== [A] 应用层 Hook =====
        if (cfg.wakeLockOptEnabled) {
            try { WakeLockHook.apply(lpparam, cfg) } catch (e: Exception) {
                LogX.e("WakeLock Hook 异常", e)
            }
        }
        if (cfg.alarmOptEnabled) {
            try { AlarmOptimizerHook.apply(lpparam, cfg) } catch (e: Exception) {
                LogX.e("Alarm Hook 异常", e)
            }
        }
        if (cfg.syncOptEnabled) {
            try { BackgroundSyncHook.apply(lpparam, cfg) } catch (e: Exception) {
                LogX.e("Sync Hook 异常", e)
            }
        }
        if (cfg.jobOptEnabled) {
            try { JobSchedulerHook.apply(lpparam, cfg) } catch (e: Exception) {
                LogX.e("Job Hook 异常", e)
            }
        }
        if (cfg.locationOptEnabled) {
            try { LocationOptHook.apply(lpparam, cfg) } catch (e: Exception) {
                LogX.e("Location Hook 异常", e)
            }
        }
        if (cfg.animationOptEnabled) {
            try { AnimationOptHook.apply(lpparam, cfg) } catch (e: Exception) {
                LogX.e("Animation Hook 异常", e)
            }
        }
        if (cfg.sensorOptEnabled) {
            try { SensorOptHook.apply(lpparam, cfg) } catch (e: Exception) {
                LogX.e("Sensor Hook 异常", e)
            }
        }

        // ===== [B] 系统级 Hook（需 Shizuku） =====
        // 先启动 Shizuku 桥接（统一检测可用性 + 系统信息日志）
        try { ShizukuBridgeHook.apply(lpparam, cfg) } catch (e: Exception) {
            LogX.e("ShizukuBridge Hook 异常", e)
        }

        if (cfg.dozeEnabled) {
            try { SystemDozeHook.apply(lpparam, cfg) } catch (e: Exception) {
                LogX.e("SystemDoze Hook 异常", e)
            }
        }
        if (cfg.freezeEnabled) {
            try { BackgroundFreezeHook.apply(lpparam, cfg) } catch (e: Exception) {
                LogX.e("BackgroundFreeze Hook 异常", e)
            }
        }
        if (cfg.cpuGovernorEnabled) {
            try { CpuGovernorHook.apply(lpparam, cfg) } catch (e: Exception) {
                LogX.e("CpuGovernor Hook 异常", e)
            }
        }
        if (cfg.greenifyEnabled) {
            try { GreenifyBridgeHook.apply(lpparam, cfg) } catch (e: Exception) {
                LogX.e("GreenifyBridge Hook 异常", e)
            }
        }

        // 注册App生命周期Hook确保ConfigManager初始化
        hookAppLifecycle(lpparam)

        LogX.i("===== 全部Hook就绪: $pkg =====")
    }

    /** 目标耗电大户 APP 包名白名单（与 arrays.xml 同步） */
    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.tencent.mm",
        "com.tencent.mobileqq",
        "com.ss.android.ugc.aweme",
        "com.smile.gifmaker",
        "com.taobao.taobao",
        "com.jingdong.app.mall",
        "com.xunmeng.pinduoduo",
        "com.eg.android.AlipayGphone",
        "com.netease.cloudmusic",
        "com.tencent.wmusic",
        "com.zhihu.android",
        "com.sina.weibo",
        "com.netease.mail",
        "com.tencent.androidqqmail"
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
