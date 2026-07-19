package com.batteryopt.pro.hooks

import android.os.Build
import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.ConcurrentHashMap

/**
 * 【实验性】振动器限频 Hook（应用层）
 */
object VibratorThrottleHook {

    private val lastVibrateTs = ConcurrentHashMap<String, Long>()

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("【实验性】振动器限频启动 | 最小间隔=${cfg.vibratorMinIntervalMs}ms")

        hookVibrateMillis(lpparam, cfg)
        hookVibratePattern(lpparam, cfg)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            hookVibrateVibrationEffect(lpparam, cfg)
        }
    }

    private fun hookVibrateMillis(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        val cls = XposedHelpers.findClassIfExists(
            "android.os.Vibrator", lpparam.classLoader
        ) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                cls, "vibrate",
                java.lang.Long.TYPE,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (shouldThrottle("vibrate_long", cfg)) {
                            p.result = null
                            LogX.d("Vibrator.vibrate(long) 节流")
                        }
                    }
                })
            LogX.hookSuccess("Vibrator", "vibrate(long)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun hookVibratePattern(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        val cls = XposedHelpers.findClassIfExists(
            "android.os.Vibrator", lpparam.classLoader
        ) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                cls, "vibrate",
                LongArray::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (shouldThrottle("vibrate_pattern", cfg)) {
                            p.result = null
                            LogX.d("Vibrator.vibrate(pattern,repeat) 节流")
                        }
                    }
                })
            LogX.hookSuccess("Vibrator", "vibrate(long[],int)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun hookVibrateVibrationEffect(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        val cls = XposedHelpers.findClassIfExists(
            "android.os.Vibrator", lpparam.classLoader
        ) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                cls, "vibrate",
                "android.os.VibrationEffect",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (shouldThrottle("vibrate_effect", cfg)) {
                            p.result = null
                            LogX.d("Vibrator.vibrate(VibrationEffect) 节流")
                        }
                    }
                })
            LogX.hookSuccess("Vibrator", "vibrate(VibrationEffect)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        try {
            XposedHelpers.findAndHookMethod(
                cls, "vibrate",
                "android.os.VibrationEffect",
                "android.media.AudioAttributes",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (shouldThrottle("vibrate_effect_aa", cfg)) {
                            p.result = null
                            LogX.d("Vibrator.vibrate(VibrationEffect,AudioAttributes) 节流")
                        }
                    }
                })
            LogX.hookSuccess("Vibrator", "vibrate(VibrationEffect,AudioAttributes)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun shouldThrottle(key: String, cfg: BatteryConfig): Boolean {
        val now = System.currentTimeMillis()
        val last = lastVibrateTs[key] ?: 0L
        return if (now - last < cfg.vibratorMinIntervalMs) {
            true
        } else {
            lastVibrateTs[key] = now
            false
        }
    }
}
