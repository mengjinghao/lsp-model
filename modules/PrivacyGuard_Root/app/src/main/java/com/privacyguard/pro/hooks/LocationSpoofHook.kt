package com.privacyguard.pro.hooks

import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 位置伪造Hook（应用层）
 *
 * 功能：
 *  - Hook LocationManager.getLastKnownLocation / requestLocationUpdates
 *  - Hook Location 构造函数，修改经纬度字段
 *  - 仅修改传给 APP 的 Location 对象
 *
 * 注意：不修改系统 GPS 定位，不影响紧急呼叫定位。
 */
object LocationSpoofHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.locationSpoofEnabled) return
        LogX.i("位置伪造启动：lat=${cfg.spoofLatitude} lng=${cfg.spoofLongitude}")

        hookLocationManager(lpparam, cfg.spoofLatitude, cfg.spoofLongitude)
        hookLocationConstructor(lpparam, cfg.spoofLatitude, cfg.spoofLongitude)
    }

    private fun hookLocationManager(
        lpparam: XC_LoadPackage.LoadPackageParam,
        lat: Double, lng: Double) {
        try {
            val lm = XposedHelpers.findClassIfExists(
                "android.location.LocationManager", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(lm, "getLastKnownLocation",
                    String::class.java, object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val loc = p.result ?: return
                            modifyLocation(loc, lat, lng)
                        }
                    })
                LogX.hookSuccess("LocationManager", "getLastKnownLocation")
            } catch (_: Exception) {}

            try {
                XposedHelpers.findAndHookMethod(lm, "getLastKnownLocation",
                    String::class.java, "android.location.LastLocationRequest",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val loc = p.result ?: return
                            modifyLocation(loc, lat, lng)
                        }
                    })
                LogX.hookSuccess("LocationManager", "getLastKnownLocation(API30+)")
            } catch (_: Exception) {}

            // requestLocationUpdates 多个重载统一处理
            try {
                val methods = lm.declaredMethods.filter { it.name == "requestLocationUpdates" }
                for (m in methods) {
                    try {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                LogX.d("requestLocationUpdates 调用拦截: ${p.method}")
                            }
                        })
                    } catch (_: Exception) {}
                }
                LogX.d("requestLocationUpdates ${methods.size} 个重载已Hook")
            } catch (_: Exception) {}
        } catch (e: Exception) {
            LogX.hookFailed("LocationManager", "location", e)
        }
    }

    private fun hookLocationConstructor(
        lpparam: XC_LoadPackage.LoadPackageParam,
        lat: Double, lng: Double) {
        try {
            val locCls = XposedHelpers.findClassIfExists(
                "android.location.Location", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookConstructor(locCls, String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val loc = p.thisObject ?: return
                            modifyLocation(loc, lat, lng)
                        }
                    })
                LogX.hookSuccess("Location", "<init>(provider)")
            } catch (_: Exception) {}

            try {
                XposedHelpers.findAndHookConstructor(locCls, locCls,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val loc = p.thisObject ?: return
                            modifyLocation(loc, lat, lng)
                        }
                    })
                LogX.hookSuccess("Location", "<init>(copy)")
            } catch (_: Exception) {}

            try {
                XposedHelpers.findAndHookMethod(locCls, "setLatitude",
                    Double::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.args[0] = lat
                        }
                    })
                LogX.hookSuccess("Location", "setLatitude")
            } catch (_: Exception) {}

            try {
                XposedHelpers.findAndHookMethod(locCls, "setLongitude",
                    Double::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.args[0] = lng
                        }
                    })
                LogX.hookSuccess("Location", "setLongitude")
            } catch (_: Exception) {}
        } catch (e: Exception) {
            LogX.hookFailed("Location", "constructor", e)
        }
    }

    private fun modifyLocation(loc: Any, lat: Double, lng: Double) {
        try {
            XposedHelpers.callMethod(loc, "setLatitude", lat)
            XposedHelpers.callMethod(loc, "setLongitude", lng)
        } catch (_: Exception) {}
    }
}
