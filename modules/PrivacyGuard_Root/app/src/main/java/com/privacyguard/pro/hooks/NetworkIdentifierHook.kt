package com.privacyguard.pro.hooks

import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.LogX
import com.privacyguard.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 网络标识伪造Hook（Root 专属，需 Shizuku root 级授权）
 *
 * 功能：
 *  - 通过 Shizuku 修改 /sys/class/net/wlan0/address 网卡 MAC（或 ip link set）
 *  - 同时在 Java 层 Hook NetworkInterface.getHardwareAddress 保持一致
 *  - 重置网络标识，防追踪
 *
 * 硬性限制：
 *  - 必须先检查 ShizukuHelper.isShizukuAvailable()
 *  - 写 /sys/class/net/wlan0/address 需要 root 级权限，Shizuku adb 级可能不足
 *  - 修改网卡 MAC 可能导致 Wi-Fi 重连，影响网络稳定性
 *  - Android 6+ 系统默认对 APP 返回随机 MAC，本Hook针对需要真实 MAC 的场景
 */
object NetworkIdentifierHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.networkIdentifierSpoofEnabled) return
        LogX.i("网络标识伪造启动（Root 专属）")

        // 1. 应用层 Hook NetworkInterface.getHardwareAddress
        hookNetworkInterface(lpparam, cfg.spoofMacAddress)

        // 2. 通过 Shizuku 修改系统网卡 MAC
        modifySystemMacViaShizuku(cfg.spoofMacAddress)
    }

    /**
     * 应用层 Hook NetworkInterface.getHardwareAddress
     * 修改返回的 MAC 字节数组
     */
    private fun hookNetworkInterface(lpparam: XC_LoadPackage.LoadPackageParam, spoofMac: String) {
        try {
            val ni = XposedHelpers.findClassIfExists(
                "java.net.NetworkInterface", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(ni, "getHardwareAddress", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = macStringToBytes(spoofMac)
                    }
                })
                LogX.hookSuccess("NetworkInterface", "getHardwareAddress")
            } catch (_: Exception) {}

            // Hook getName / getDisplayName 隐藏真实网卡名（可选）
            try {
                XposedHelpers.findAndHookMethod(ni, "getHardwareAddress",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            // 二次保护，确保返回伪造 MAC
                            p.result = macStringToBytes(spoofMac)
                        }
                    })
            } catch (_: Exception) {}
        } catch (e: Exception) {
            LogX.hookFailed("NetworkInterface", "getHardwareAddress", e)
        }
    }

    /**
     * 通过 Shizuku 修改系统网卡 MAC
     * 方法1: 写 /sys/class/net/wlan0/address（需要 root 级）
     * 方法2: ip link set dev wlan0 address XX:XX:XX:XX:XX:XX（需要 root 级）
     */
    private fun modifySystemMacViaShizuku(spoofMac: String) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku不可用，跳过系统网卡 MAC 修改（仅应用层 Hook 生效）")
            return
        }

        // 方法1: 直接写 /sys/class/net/wlan0/address
        val writeResult = ShizukuHelper.writeFile("/sys/class/net/wlan0/address", spoofMac)
        if (writeResult) {
            LogX.i("Shizuku 写 /sys/class/net/wlan0/address 成功: $spoofMac")
            return
        }

        // 方法2: 通过 ip link set 命令
        val ipLinkResult = ShizukuHelper.execShell("ip link set dev wlan0 address $spoofMac")
        if (ipLinkResult != null) {
            LogX.i("Shizuku ip link set MAC 成功: $spoofMac")
        } else {
            LogX.w("Shizuku 修改网卡 MAC 失败（可能需要 root 级授权）")
        }
    }

    /** MAC 字符串转字节数组 "AA:BB:CC:DD:EE:FF" -> byte[6] */
    private fun macStringToBytes(mac: String): ByteArray {
        val parts = mac.split(":")
        if (parts.size != 6) return ByteArray(6)
        return parts.map { it.toInt(16).toByte() }.toByteArray()
    }
}
