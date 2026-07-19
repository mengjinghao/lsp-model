package com.privacyguard.pro.hooks

import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.FakeDeviceCache
import com.privacyguard.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】网络信息伪造（应用层）
 *
 * 伪造本机IP地址、DNS等信息，防止网络指纹追踪
 */
object NetworkInfoSpoofHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.networkInfoSpoofEnabled) return
        LogX.i("【实验性】网络信息伪造启动")

        hookWifiInfoIpAddress(lpparam)
        hookDhcpInfo(lpparam)
        hookNetworkInterface(lpparam)
    }

    private fun hookWifiInfoIpAddress(lpparam: XC_LoadPackage.LoadPackageParam) {
        val wifi = XposedHelpers.findClassIfExists(
            "android.net.wifi.WifiInfo", lpparam.classLoader) ?: return
        try {
            XposedHelpers.findAndHookMethod(wifi, "getIpAddress", object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    p.result = ipToInt(FakeDeviceCache.fakeIpAddress)
                }
            })
            LogX.hookSuccess("WifiInfo", "getIpAddress")
        } catch (_: Throwable) {}

        try {
            XposedHelpers.findAndHookMethod(wifi, "getIpAddressAsString", object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    p.result = FakeDeviceCache.fakeIpAddress
                }
            })
            LogX.hookSuccess("WifiInfo", "getIpAddressAsString")
        } catch (_: Throwable) {}
    }

    private fun hookDhcpInfo(lpparam: XC_LoadPackage.LoadPackageParam) {
        val dhcp = XposedHelpers.findClassIfExists(
            "android.net.DhcpInfo", lpparam.classLoader) ?: return
        try {
            XposedHelpers.findAndHookMethod(dhcp, "getIpAddress",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = ipToInt(FakeDeviceCache.fakeIpAddress)
                    }
                })
            LogX.hookSuccess("DhcpInfo", "getIpAddress")
        } catch (_: Throwable) {}

        try {
            XposedHelpers.findAndHookConstructor(dhcp, object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val obj = p.thisObject
                    XposedHelpers.setIntField(obj, "ipAddress", ipToInt(FakeDeviceCache.fakeIpAddress))
                    XposedHelpers.setIntField(obj, "dns1", ipToInt(FakeDeviceCache.fakeDns))
                    XposedHelpers.setIntField(obj, "dns2", ipToInt("8.8.4.4"))
                }
            })
            LogX.hookSuccess("DhcpInfo", "<init>")
        } catch (_: Throwable) {}
    }

    private fun hookNetworkInterface(lpparam: XC_LoadPackage.LoadPackageParam) {
        val ni = XposedHelpers.findClassIfExists(
            "java.net.NetworkInterface", lpparam.classLoader) ?: return
        try {
            XposedHelpers.findAndHookMethod(ni, "getHardwareAddress", object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    p.result = ByteArray(0)
                }
            })
            LogX.hookSuccess("NetworkInterface", "getHardwareAddress")
        } catch (_: Throwable) {}
    }

    private fun ipToInt(ip: String): Int {
        val parts = ip.split(".")
        if (parts.size != 4) return 0
        return try {
            var r = 0
            for (p in parts) r = (r shl 8) or (p.toInt() and 0xFF)
            Integer.reverseBytes(r)
        } catch (_: Throwable) { 0 }
    }
}
