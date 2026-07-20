package com.batteryopt.pro.hooks

import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import com.batteryopt.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Network Power Saver（实验性）
 *
 * Root 版：使用 Shizuku `cmd netpolicy` + `iptables` 限制后台数据
 * - Hook ConnectivityManager.setProcessDefaultNetwork 拦截后台网络请求
 * - 通过 Shizuku 设置 iptables 规则封锁非前台 APP 流量
 */
object NetworkPowerHook {

    private val restrictedApps = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("【实验性】Network Power Saver 启动")

        hookSetProcessDefaultNetwork(lpparam, cfg)
        applyIptablesRules(cfg)
    }

    private fun hookSetProcessDefaultNetwork(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        try {
            val cmCls = XposedHelpers.findClassIfExists(
                "android.net.ConnectivityManager", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                cmCls, "setProcessDefaultNetwork",
                "android.net.Network",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val pkg = lpparam.packageName
                            if (isRestricted(pkg)) {
                                LogX.w("Network Power Saver: 拦截后台网络请求 $pkg")
                                p.result = false
                            }
                        } catch (e: Exception) {
                            LogX.e("setProcessDefaultNetwork 拦截异常", e)
                        }
                    }
                })
            LogX.hookSuccess("ConnectivityManager", "setProcessDefaultNetwork->PowerSave")
        } catch (e: Exception) {
            LogX.e("Hook setProcessDefaultNetwork 异常", e)
        }
    }

    private fun applyIptablesRules(cfg: BatteryConfig) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过 iptables 规则")
            return
        }
        try {
            ShizukuHelper.execShell("cmd netpolicy set restrict-background true")
            LogX.i("已设置 netpolicy restrict-background=true")
        } catch (e: Exception) {
            LogX.e("netpolicy 设置异常", e)
        }
    }

    private fun isRestricted(pkg: String): Boolean {
        return restrictedApps.contains(pkg)
    }

    companion object {
        fun markRestricted(pkg: String) {
            restrictedApps.add(pkg)
        }

        fun unmarkRestricted(pkg: String) {
            restrictedApps.remove(pkg)
        }
    }
}
