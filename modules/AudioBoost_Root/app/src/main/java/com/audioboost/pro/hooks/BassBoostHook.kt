package com.audioboost.pro.hooks

import com.audioboost.pro.models.AudioConfig
import com.audioboost.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 低音增强Hook（仅应用层，与 NoRoot 版相同）
 *
 * 拦截路径：
 *  1. BassBoost.setStrength(short) - 强制设置高 strength（0~1000）
 *  2. BassBoost.getStrength() - 返回伪造值
 *  3. BassBoost.getStrengthSupported() - 强制返回 true
 */
object BassBoostHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.bassBoostEnabled) return
        LogX.i("低音增强启动 bassLevel=${cfg.bassLevel}%")

        hookBassBoostSetStrength(lpparam, cfg)
        hookBassBoostGetStrength(lpparam, cfg)
        hookBassBoostSetStrengthSupported(lpparam)
    }

    private fun hookBassBoostSetStrength(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.audiofx.BassBoost", lpparam.classLoader) ?: return
            val targetStrength = (cfg.bassLevel * 10).toShort()
            try {
                XposedHelpers.findAndHookMethod(cls, "setStrength",
                    Short::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.args[0] = targetStrength
                            LogX.d("BassBoost.setStrength -> $targetStrength")
                        }
                    })
                LogX.hookSuccess("BassBoost", "setStrength")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("BassBoost", "setStrength", e)
        }
    }

    private fun hookBassBoostGetStrength(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.audiofx.BassBoost", lpparam.classLoader) ?: return
            val targetStrength = (cfg.bassLevel * 10).toShort()
            try {
                XposedHelpers.findAndHookMethod(cls, "getStrength", object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        p.result = targetStrength
                    }
                })
                LogX.hookSuccess("BassBoost", "getStrength")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("BassBoost", "getStrength", e)
        }
    }

    private fun hookBassBoostSetStrengthSupported(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.audiofx.BassBoost", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "getStrengthSupported", object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        p.result = true
                    }
                })
                LogX.hookSuccess("BassBoost", "getStrengthSupported")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("BassBoost", "getStrengthSupported", e)
        }
    }
}
