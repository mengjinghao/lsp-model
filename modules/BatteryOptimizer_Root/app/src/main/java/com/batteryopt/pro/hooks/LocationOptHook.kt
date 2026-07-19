package com.batteryopt.pro.hooks

import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LocationManager 定位优化 Hook（应用层）
 */
object LocationOptHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("Location 定位优化启动 | 最小间隔=${cfg.locationMinIntervalMs}ms GPS降级=${cfg.locationDowngradeGps}")

        hookRequestLocationUpdates(lpparam, cfg)
    }

    private fun hookRequestLocationUpdates(
        lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig
    ) {
        val lmCls = XposedHelpers.findClassIfExists(
            "android.location.LocationManager", lpparam.classLoader
        ) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                lmCls, "requestLocationUpdates",
                String::class.java,
                java.lang.Long.TYPE,
                java.lang.Float.TYPE,
                "android.location.LocationListener",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val provider = p.args[0] as? String ?: return
                        var minTime = p.args[1] as Long
                        if (minTime < cfg.locationMinIntervalMs) {
                            val old = minTime
                            p.args[1] = cfg.locationMinIntervalMs
                            LogX.w("定位间隔放大: $provider ${old}ms -> ${cfg.locationMinIntervalMs}ms")
                            minTime = cfg.locationMinIntervalMs
                        }
                        if (cfg.locationDowngradeGps &&
                            provider == "gps" && minTime < 60_000L
                        ) {
                            p.args[0] = "network"
                            LogX.w("GPS 高频定位降级为 NETWORK 定位")
                        }
                    }
                })
            LogX.hookSuccess("LocationManager", "requestLocationUpdates(provider,minTime,minDistance,listener)")
        } catch (e: Exception) {
            LogX.e("Hook requestLocationUpdates(4参) 异常", e)
        }

        try {
            XposedHelpers.findAndHookMethod(
                lmCls, "requestLocationUpdates",
                java.lang.Long.TYPE,
                java.lang.Float.TYPE,
                "android.location.Criteria",
                "android.location.LocationListener",
                "android.os.Looper",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val minTime = p.args[0] as Long
                        if (minTime < cfg.locationMinIntervalMs) {
                            val old = minTime
                            p.args[0] = cfg.locationMinIntervalMs
                            LogX.w("定位间隔放大(Criteria): ${old}ms -> ${cfg.locationMinIntervalMs}ms")
                        }
                    }
                })
            LogX.hookSuccess("LocationManager", "requestLocationUpdates(minTime,minDistance,Criteria,listener,looper)")
        } catch (_: Exception) {}

        try {
            XposedHelpers.findAndHookMethod(
                lmCls, "requestLocationUpdates",
                String::class.java,
                java.lang.Long.TYPE,
                java.lang.Float.TYPE,
                "android.app.PendingIntent",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val provider = p.args[0] as? String ?: return
                        val minTime = p.args[1] as Long
                        if (minTime < cfg.locationMinIntervalMs) {
                            val old = minTime
                            p.args[1] = cfg.locationMinIntervalMs
                            LogX.w("定位间隔放大(PI): $provider ${old}ms -> ${cfg.locationMinIntervalMs}ms")
                        }
                        if (cfg.locationDowngradeGps &&
                            provider == "gps" && minTime < 60_000L
                        ) {
                            p.args[0] = "network"
                            LogX.w("GPS(PendingIntent) 降级为 NETWORK")
                        }
                    }
                })
            LogX.hookSuccess("LocationManager", "requestLocationUpdates(provider,minTime,minDistance,intent)")
        } catch (_: Exception) {}
    }
}
