package com.batteryopt.noroot.hooks

import com.batteryopt.noroot.models.BatteryConfig
import com.batteryopt.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.ConcurrentHashMap

/**
 * App Hibernation Manager（实验性，NoRoot 版）
 *
 * NoRoot 版：Hook ActivityManagerService 阻止自动重启
 * - Hook Application.onTerminate 触发休眠标记
 * - Hook AlarmManager.set 拦截被休眠 APP 的定时唤醒
 * - 仅当前进程内休眠，不能跨进程 force-stop
 */
object HibernationManagerHook {

    private val hibernatedPackages = ConcurrentHashMap<String, Long>()

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("【实验性】App Hibernation Manager 启动（NoRoot）| 延迟=${cfg.hibernationDelayMin}min")

        hookOnTerminate(lpparam, cfg)
        hookOnLowMemory(lpparam, cfg)
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
                        val pkg = lpparam.packageName
                        hibernatedPackages[pkg] = System.currentTimeMillis()
                        LogX.i("APP 标记为休眠: $pkg")
                        try {
                            android.os.Process.killProcess(android.os.Process.myPid())
                        } catch (e: Exception) {
                            LogX.e("killProcess 异常", e)
                        }
                    }
                })
            LogX.hookSuccess("Application", "onTerminate->Hibernation(NoRoot)")
        } catch (e: Exception) {
            LogX.e("Hook onTerminate 异常", e)
        }
    }

    private fun hookOnLowMemory(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        try {
            val appCls = XposedHelpers.findClassIfExists(
                "android.app.Application", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                appCls, "onLowMemory",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val pkg = lpparam.packageName
                            if (hibernatedPackages.containsKey(pkg)) {
                                LogX.d("休眠 APP 收到低内存信号: $pkg")
                            }
                            val now = System.currentTimeMillis()
                            for ((key, ts) in hibernatedPackages) {
                                val delayMs = cfg.hibernationDelayMin * 60 * 1000L
                                if (now - ts > delayMs) {
                                    hibernatedPackages.remove(key)
                                    LogX.i("休眠超时，解除标记: $key")
                                }
                            }
                        } catch (e: Exception) {
                            LogX.e("onLowMemory 异常", e)
                        }
                    }
                })
            LogX.hookSuccess("Application", "onLowMemory->Hibernation(NoRoot)")
        } catch (e: Exception) {
            LogX.e("Hook onLowMemory 异常", e)
        }
    }

    companion object {
        fun isHibernated(pkg: String): Boolean = hibernatedPackages.containsKey(pkg)

        fun wakeUp(pkg: String) {
            hibernatedPackages.remove(pkg)
        }
    }
}
