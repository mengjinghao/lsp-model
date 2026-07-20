package com.batteryopt.noroot.hooks

import com.batteryopt.noroot.models.BatteryConfig
import com.batteryopt.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.ConcurrentHashMap

/**
 * Network Power Saver（实验性，NoRoot 版）
 *
 * NoRoot 版：Hook ConnectivityManager 限制后台网络连接
 * - Hook ConnectivityManager.requestNetwork 拦截不必要的后台网络请求
 * - Hook ConnectivityManager.registerNetworkCallback 限制后台网络回调
 */
object NetworkPowerHook {

    private val restrictedUids = ConcurrentHashMap<Int, Long>()
    private val blockedRequestCount = java.util.concurrent.atomic.AtomicInteger(0)

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("【实验性】Network Power Saver 启动（NoRoot）")

        hookRequestNetwork(lpparam, cfg)
        hookRegisterNetworkCallback(lpparam, cfg)
        hookGetActiveNetwork(lpparam, cfg)
    }

    private fun hookRequestNetwork(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        try {
            val cmCls = XposedHelpers.findClassIfExists(
                "android.net.ConnectivityManager", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                cmCls, "requestNetwork",
                "android.net.NetworkRequest",
                "android.net.ConnectivityManager.NetworkCallback",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            if (isBackgroundApp()) {
                                val count = blockedRequestCount.incrementAndGet()
                                if (count <= 5) {
                                    LogX.w("Network Power Saver: 拦截后台网络请求")
                                }
                                p.result = null
                            }
                        } catch (e: Exception) {
                            LogX.e("requestNetwork 拦截异常", e)
                        }
                    }
                })
            LogX.hookSuccess("ConnectivityManager", "requestNetwork->PowerSave(NoRoot)")
        } catch (e: Exception) {
            LogX.e("Hook requestNetwork 异常", e)
        }
    }

    private fun hookRegisterNetworkCallback(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        try {
            val cmCls = XposedHelpers.findClassIfExists(
                "android.net.ConnectivityManager", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                cmCls, "registerNetworkCallback",
                "android.net.NetworkRequest",
                "android.net.ConnectivityManager.NetworkCallback",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            if (isBackgroundApp()) {
                                LogX.d("Network Power Saver: 限制后台 NetworkCallback")
                                p.result = null
                            }
                        } catch (e: Exception) {
                            LogX.e("registerNetworkCallback 拦截异常", e)
                        }
                    }
                })
            LogX.hookSuccess("ConnectivityManager", "registerNetworkCallback->PowerSave(NoRoot)")
        } catch (e: Exception) {
            LogX.e("Hook registerNetworkCallback 异常", e)
        }
    }

    private fun hookGetActiveNetwork(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        try {
            val cmCls = XposedHelpers.findClassIfExists(
                "android.net.ConnectivityManager", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                cmCls, "getActiveNetwork",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            if (isBackgroundApp()) {
                                LogX.d("Network Power Saver: 后台返回 null 网络")
                                p.result = null
                            }
                        } catch (e: Exception) {
                            LogX.e("getActiveNetwork 拦截异常", e)
                        }
                    }
                })
            LogX.hookSuccess("ConnectivityManager", "getActiveNetwork->PowerSave(NoRoot)")
        } catch (e: Exception) {
            LogX.e("Hook getActiveNetwork 异常", e)
        }
    }

    private fun isBackgroundApp(): Boolean {
        return try {
            val atCls = Class.forName("android.app.ActivityThread")
            val app = atCls.getMethod("currentApplication").invoke(null)
                ?: return false
            val ctx = app as? android.content.Context ?: return false
            val am = ctx.getSystemService(
                android.content.Context.ACTIVITY_SERVICE
            ) as? android.app.ActivityManager ?: return false
            val pid = android.os.Process.myPid()
            val processes = am.runningAppProcesses ?: return false
            val mine = processes.firstOrNull { it.pid == pid } ?: return false
            mine.importance != android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        } catch (_: Throwable) {
            false
        }
    }
}
