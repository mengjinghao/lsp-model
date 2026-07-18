package com.batteryopt.pro.hooks

import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * SensorManager 传感器降频 Hook（应用层）
 *
 * 功能：
 *  - Hook registerListener，对高频传感器（>50Hz）降频到合理值
 *  - 默认上限 200000us = 5Hz，足以满足大部分场景需求
 */
object SensorOptHook {

    /** 高频阈值（微秒）：低于此值视为高频，需要降频。50Hz = 20000us */
    private const val HIGH_FREQ_THRESHOLD_US = 20_000

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("Sensor 传感器优化启动 | 上限=${cfg.sensorMaxRateUs}us")

        hookRegisterListener(lpparam, cfg)
    }

    private fun hookRegisterListener(
        lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig
    ) {
        val smCls = XposedHelpers.findClassIfExists(
            "android.hardware.SensorManager", lpparam.classLoader
        ) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                smCls, "registerListener",
                "android.hardware.SensorEventListener",
                "android.hardware.Sensor",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val period = p.args[2] as Int
                        if (period < HIGH_FREQ_THRESHOLD_US) {
                            val old = period
                            p.args[2] = cfg.sensorMaxRateUs
                            LogX.w("传感器降频: ${old}us -> ${cfg.sensorMaxRateUs}us")
                        }
                    }
                })
            LogX.hookSuccess("SensorManager", "registerListener(3参)")
        } catch (e: Exception) {
            LogX.e("Hook registerListener(3参) 异常", e)
        }

        try {
            XposedHelpers.findAndHookMethod(
                smCls, "registerListener",
                "android.hardware.SensorEventListener",
                "android.hardware.Sensor",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val period = p.args[2] as Int
                        if (period < HIGH_FREQ_THRESHOLD_US) {
                            val old = period
                            p.args[2] = cfg.sensorMaxRateUs
                            LogX.w("传感器降频(带延迟): ${old}us -> ${cfg.sensorMaxRateUs}us")
                        }
                    }
                })
            LogX.hookSuccess("SensorManager", "registerListener(4参)")
        } catch (_: Exception) {}

        try {
            XposedHelpers.findAndHookMethod(
                smCls, "registerListener",
                "android.hardware.SensorEventListener",
                "android.hardware.Sensor",
                Int::class.javaPrimitiveType,
                "android.os.Handler",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val period = p.args[2] as Int
                        if (period < HIGH_FREQ_THRESHOLD_US) {
                            val old = period
                            p.args[2] = cfg.sensorMaxRateUs
                            LogX.w("传感器降频(带Handler): ${old}us -> ${cfg.sensorMaxRateUs}us")
                        }
                    }
                })
            LogX.hookSuccess("SensorManager", "registerListener(带Handler)")
        } catch (_: Exception) {}
    }
}
