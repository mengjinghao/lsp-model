package com.batteryopt.pro.hooks

import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import com.batteryopt.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * App Hibernation Manager（实验性）
 *
 * Root 版：使用 Shizuku `am force-stop` + `pm disable` 进行主动休眠
 * - Hook Application.onTerminate 触发休眠
 * - Hook ActivityManager.killBackgroundProcesses 拦截并扩大为完整 force-stop
 */
object HibernationManagerHook {

    private val hibernatedApps = ConcurrentHashMap<String, Long>()
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "HibernationMgr").apply { isDaemon = true }
    }

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("【实验性】App Hibernation Manager 启动 | 延迟=${cfg.hibernationDelayMin}min")

        hookOnTerminate(lpparam, cfg)
        hookKillBackground(lpparam, cfg)
    }

    private fun hookOnTerminate(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        try {
            val appCls = XposedHelpers.findClassIfExists(
                "android.app.Application", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                appCls, "onTerminate",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val pkg = lpparam.packageName
                            hibernatedApps[pkg] = System.currentTimeMillis()
                            LogX.i("APP 进入休眠: $pkg | 延迟 ${cfg.hibernationDelayMin}min 后 force-stop")
                            executor.schedule({
                                doForceStop(pkg)
                            }, cfg.hibernationDelayMin.toLong(), TimeUnit.MINUTES)
                        } catch (e: Exception) {
                            LogX.e("onTerminate 休眠异常", e)
                        }
                    }
                })
            LogX.hookSuccess("Application", "onTerminate->Hibernation")
        } catch (e: Exception) {
            LogX.e("Hook onTerminate 异常", e)
        }
    }

    private fun hookKillBackground(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        try {
            val amCls = XposedHelpers.findClassIfExists(
                "android.app.ActivityManager", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                amCls, "killBackgroundProcesses",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val targetPkg = p.args[0] as? String ?: return
                            if (hibernatedApps.containsKey(targetPkg)) {
                                LogX.d("killBackground $targetPkg 升级为完整 force-stop")
                                doForceStop(targetPkg)
                            }
                        } catch (e: Exception) {
                            LogX.e("killBackground 拦截异常", e)
                        }
                    }
                })
            LogX.hookSuccess("ActivityManager", "killBackgroundProcesses->ForceStop")
        } catch (e: Exception) {
            LogX.e("Hook killBackground 异常", e)
        }
    }

    private fun doForceStop(pkg: String) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，无法 force-stop: $pkg")
            return
        }
        try {
            ShizukuHelper.execShell("am force-stop $pkg")
            LogX.i("已 force-stop: $pkg")
        } catch (e: Exception) {
            LogX.e("force-stop 异常: $pkg", e)
        }
        try {
            ShizukuHelper.execShell("pm disable $pkg")
            LogX.i("已 pm disable: $pkg")
        } catch (e: Exception) {
            LogX.e("pm disable 异常: $pkg", e)
        }
    }
}
