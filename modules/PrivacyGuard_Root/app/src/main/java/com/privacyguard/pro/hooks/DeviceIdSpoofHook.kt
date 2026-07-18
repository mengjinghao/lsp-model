package com.privacyguard.pro.hooks

import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.FakeDeviceCache
import com.privacyguard.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 设备ID伪造Hook（应用层 + 系统属性）
 *
 * Root 版说明：
 *  - 本 Hook 在应用进程内做 Java 层拦截
 *  - 配合 SystemPropSpoofHook 在系统层 setprop，双管齐下确保一致
 *  - 伪造值在进程生命周期内稳定（FakeDeviceCache 缓存）
 *
 * 拦截路径：
 *  1. TelephonyManager.getDeviceId / getImei / getMeid / getSubscriberId / getLine1Number
 *  2. Settings.Secure.getString(ANDROID_ID)
 *  3. WifiInfo.getMacAddress / getBSSID
 *  4. Build.getSerial
 */
object DeviceIdSpoofHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.deviceIdSpoofEnabled) return
        LogX.i("设备ID伪造启动（应用层）")

        hookTelephonyManager(lpparam)
        hookSettingsSecure(lpparam)
        hookWifiInfo(lpparam)
        hookBuildSerial(lpparam)
    }

    /** Hook TelephonyManager 设备标识相关方法 */
    private fun hookTelephonyManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val tm = XposedHelpers.findClassIfExists(
                "android.telephony.TelephonyManager", lpparam.classLoader) ?: return

            listOf("getDeviceId", "getImei", "getMeid", "getSubscriberId", "getLine1Number",
                "getSimSerialNumber").forEach { methodName ->
                // 无参版本
                try {
                    XposedHelpers.findAndHookMethod(tm, methodName, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = fakeValueFor(methodName)
                        }
                    })
                    LogX.hookSuccess("TelephonyManager", methodName)
                } catch (_: Exception) {}

                // 带 slot 参数版本
                try {
                    XposedHelpers.findAndHookMethod(tm, methodName,
                        Int::class.javaPrimitiveType, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                p.result = fakeValueFor(methodName)
                            }
                        })
                    LogX.hookSuccess("TelephonyManager", "$methodName(slot)")
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            LogX.hookFailed("TelephonyManager", "device-id", e)
        }
    }

    private fun fakeValueFor(methodName: String): String = when (methodName) {
        "getDeviceId", "getImei" -> FakeDeviceCache.fakeImei
        "getMeid" -> FakeDeviceCache.fakeMeid
        "getSubscriberId" -> FakeDeviceCache.fakeSubscriberId
        "getLine1Number" -> FakeDeviceCache.fakeLine1Number
        "getSimSerialNumber" -> FakeDeviceCache.fakeSimSerial
        else -> FakeDeviceCache.fakeImei
    }

    /** Hook Settings.Secure.getString 拦截 ANDROID_ID */
    private fun hookSettingsSecure(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val ss = XposedHelpers.findClassIfExists(
                "android.provider.Settings.Secure", lpparam.classLoader) ?: return
            val ANDROID_ID = "android_id"

            try {
                XposedHelpers.findAndHookMethod(ss, "getString",
                    "android.content.ContentResolver", String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val name = p.args[1] as? String ?: return
                            if (name == ANDROID_ID) {
                                p.result = FakeDeviceCache.fakeAndroidId
                            }
                        }
                    })
                LogX.hookSuccess("Settings.Secure", "getString(ANDROID_ID)")
            } catch (_: Exception) {}

            try {
                XposedHelpers.findAndHookMethod(ss, "getString",
                    "android.content.ContentResolver", String::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val name = p.args[1] as? String ?: return
                            if (name == ANDROID_ID) {
                                p.result = FakeDeviceCache.fakeAndroidId
                            }
                        }
                    })
                LogX.hookSuccess("Settings.Secure", "getString(ANDROID_ID, def)")
            } catch (_: Exception) {}
        } catch (e: Exception) {
            LogX.hookFailed("Settings.Secure", "getString", e)
        }
    }

    /** Hook WifiInfo MAC/BSSID */
    private fun hookWifiInfo(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val wifi = XposedHelpers.findClassIfExists(
                "android.net.wifi.WifiInfo", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(wifi, "getMacAddress", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = FakeDeviceCache.fakeMacAddress
                    }
                })
                LogX.hookSuccess("WifiInfo", "getMacAddress")
            } catch (_: Exception) {}

            try {
                XposedHelpers.findAndHookMethod(wifi, "getBSSID", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = FakeDeviceCache.fakeBssid
                    }
                })
                LogX.hookSuccess("WifiInfo", "getBSSID")
            } catch (_: Exception) {}
        } catch (e: Exception) {
            LogX.hookFailed("WifiInfo", "mac/bssid", e)
        }
    }

    /** Hook Build.getSerial */
    private fun hookBuildSerial(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val build = XposedHelpers.findClassIfExists(
                "android.os.Build", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(build, "getSerial", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = FakeDeviceCache.fakeSerial
                    }
                })
                LogX.hookSuccess("Build", "getSerial")
            } catch (_: Exception) {}

            try {
                XposedHelpers.setStaticObjectField(build, "SERIAL", FakeDeviceCache.fakeSerial)
                LogX.d("Build.SERIAL 字段已覆盖")
            } catch (_: Exception) {}
        } catch (e: Exception) {
            LogX.hookFailed("Build", "getSerial", e)
        }
    }
}
