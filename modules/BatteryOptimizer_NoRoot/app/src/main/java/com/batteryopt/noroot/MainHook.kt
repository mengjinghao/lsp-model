package com.batteryopt.noroot

import android.app.Application
import com.batteryopt.noroot.hooks.*
import com.batteryopt.noroot.models.BatteryConfig
import com.batteryopt.noroot.utils.*
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * BatteryOptimizer NoRoot 主入口
 *
 * 架构说明：
 *  1. 实现 IXposedHookLoadPackage + IXposedHookZygoteInit 双接口
 *  2. LSPatch本地模式下，模块在目标 APP 进程启动时加载
 *  3. 全部 Hook 仅在当前 APP 自身进程内执行，无系统级修改
 *
 * 工作流程：
 *  APP启动 -> handleLoadPackage() 触发 ->
 *    判断是否为目标耗电大户包名 ->
 *    读取该 APP 的独立配置 ->
 *    [1] WakeLock 优化 (超长持有自动释放、SDK 统计类拦截)
 *    [2] Alarm 优化 (高频精确闹钟降级为 inexact)
 *    [3] Sync 同步降频
 *    [4] JobScheduler 限频
 *    [5] Location 定位降频
 *    [6] Animation 动画关闭省 GPU
 *    [7] Sensor 高频传感器降频
 *
 * 硬性限制（必须遵守，绝不越界）：
 *  1. 只能优化当前 APP 自身耗电行为，无法冻结其他 APP
 *  2. 不能 force-stop 其他 APP
 *  3. 不能修改系统 doze 状态
 *  4. 不能修改内核调度策略（CPU/GPU governor）
 *  5. 不能 kill 其他进程
 *  6. 无 Root/无 Shizuku 依赖，所有优化在应用层 Hook 完成
 */
class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "2.0.0"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("BatteryOptimizer NoRoot v$VERSION 初始化 | LSPatch本地模式")
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

        // ===== [1] WakeLock 优化 =====
        if (cfg.wakeLockOptEnabled) {
            try { WakeLockHook.apply(lpparam, cfg) } catch (e: Exception) {
                LogX.e("WakeLock Hook 异常", e)
            }
        }

        // ===== [2] Alarm 优化 =====
        if (cfg.alarmOptEnabled) {
            try { AlarmOptimizerHook.apply(lpparam, cfg) } catch (e: Exception) {
                LogX.e("Alarm Hook 异常", e)
            }
        }

        // ===== [3] 同步降频 =====
        if (cfg.syncOptEnabled) {
            try { BackgroundSyncHook.apply(lpparam, cfg) } catch (e: Exception) {
                LogX.e("Sync Hook 异常", e)
            }
        }

        // ===== [4] JobScheduler 限频 =====
        if (cfg.jobOptEnabled) {
            try { JobSchedulerHook.apply(lpparam, cfg) } catch (e: Exception) {
                LogX.e("Job Hook 异常", e)
            }
        }

        // ===== [5] 定位降频 =====
        if (cfg.locationOptEnabled) {
            try { LocationOptHook.apply(lpparam, cfg) } catch (e: Exception) {
                LogX.e("Location Hook 异常", e)
            }
        }

        // ===== [6] 动画关闭 =====
        if (cfg.animationOptEnabled) {
            try { AnimationOptHook.apply(lpparam, cfg) } catch (e: Exception) {
                LogX.e("Animation Hook 异常", e)
            }
        }

        // ===== [7] 传感器降频 =====
        if (cfg.sensorOptEnabled) {
            try { SensorOptHook.apply(lpparam, cfg) } catch (e: Exception) {
                LogX.e("Sensor Hook 异常", e)
            }
        }

        // 注册App生命周期Hook确保ConfigManager初始化
        hookAppLifecycle(lpparam)

        LogX.i("===== 全部Hook就绪: $pkg =====")
    }

    /**
     * 目标耗电大户 APP 包名白名单（与 arrays.xml 同步）
     * 包含社交、电商、视频、音乐、资讯等常见耗电大户
     */
    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.tencent.mm",                 // 微信
        "com.tencent.mobileqq",           // QQ
        "com.ss.android.ugc.aweme",       // 抖音
        "com.smile.gifmaker",             // 快手
        "com.taobao.taobao",              // 淘宝
        "com.jingdong.app.mall",          // 京东
        "com.xunmeng.pinduoduo",          // 拼多多
        "com.eg.android.AlipayGphone",    // 支付宝
        "com.netease.cloudmusic",         // 网易云音乐
        "com.tencent.wmusic",             // 腾讯音乐
        "com.zhihu.android",              // 知乎
        "com.sina.weibo",                 // 微博
        "com.netease.mail",               // 网易邮箱
        "com.tencent.androidqqmail"       // QQ邮箱
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
