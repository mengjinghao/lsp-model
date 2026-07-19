package com.adblockerx.pro.hooks

import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】本地 VPN 拦截 Hook（Root 版独有）
 *
 * 功能：
 *  - Hook VpnService.Builder.establish() 拦截 APP 自身建立的 VPN（避免与本模块冲突）
 *  - Hook ConnectivityManager（可选，记录网络切换）
 *
 * 设计思路：
 *  - 真正的本地 VPN 拦截需要建立 VpnService 进程，超出 Xposed 模块范围
 *  - 本 Hook 仅作为"防止 APP 建立 VPN 绕过本模块拦截"的对策
 *  - 同时 Hook ParcelFileDescriptor.adoptStream / detachFd 记录 fd 使用
 *
 * 注意事项：
 *  - 实验性功能，默认关闭
 *  - 拦截 VpnService.establish 可能导致部分 VPN APP 无法工作
 *  - 仅 Hook 当前 APP 进程内的调用
 */
object VpnBasedBlockHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.vpnBasedBlockEnabled) return
        LogX.i("【实验性】VpnBasedBlockHook 启动（Root 专属）")

        hookVpnServiceEstablish(lpparam)
        hookConnectivityManager(lpparam)
    }

    /**
     * Hook VpnService.Builder.establish
     * 拦截 APP 自身建立 VPN（防止绕过本模块的 DNS/网络拦截）
     */
    private fun hookVpnServiceEstablish(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val builderCls = XposedHelpers.findClassIfExists(
                "android.net.VpnService\$Builder", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(builderCls, "establish",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            LogX.w("[VPN] 检测到 APP 尝试建立 VPN，已阻断（防止绕过拦截）")
                            // 返回 null 阻止 VPN 建立
                            p.result = null
                        }
                    })
                LogX.hookSuccess("VpnService.Builder", "establish")
            } catch (_: Throwable) {}
        } catch (e: Throwable) {
            LogX.e("VpnBasedBlockHook.hookVpnServiceEstablish 异常", e)
        }
    }

    /**
     * Hook ConnectivityManager 记录网络切换
     * 用于检测 APP 是否尝试切换网络绕过拦截
     */
    private fun hookConnectivityManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val cmCls = XposedHelpers.findClassIfExists(
                "android.net.ConnectivityManager", lpparam.classLoader) ?: return

            // getActiveNetworkInfo
            try {
                XposedHelpers.findAndHookMethod(cmCls, "getActiveNetworkInfo",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            // 仅记录，不阻断
                            LogX.d("[VPN] ConnectivityManager.getActiveNetworkInfo 查询")
                        }
                    })
                LogX.hookSuccess("ConnectivityManager", "getActiveNetworkInfo")
            } catch (_: Throwable) {}

            // registerNetworkCallback
            try {
                val ncCls = XposedHelpers.findClassIfExists(
                    "android.net.NetworkRequest", lpparam.classLoader)
                val cbCls = XposedHelpers.findClassIfExists(
                    "android.net.ConnectivityManager\$NetworkCallback", lpparam.classLoader)
                if (ncCls != null && cbCls != null) {
                    XposedHelpers.findAndHookMethod(cmCls, "registerNetworkCallback",
                        ncCls, cbCls,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                LogX.d("[VPN] APP 注册 NetworkCallback（监控网络切换）")
                            }
                        })
                    LogX.hookSuccess("ConnectivityManager", "registerNetworkCallback")
                }
            } catch (_: Throwable) {}
        } catch (e: Throwable) {
            LogX.e("VpnBasedBlockHook.hookConnectivityManager 异常", e)
        }
    }
}
