package com.audioboost.pro.hooks

import com.audioboost.pro.models.AudioConfig
import com.audioboost.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 扬声器增强Hook（实验性，仅应用层；Root 版另有 SystemVolumeHook 做系统级突破）
 *
 * 拦截路径：
 *  1. AudioManager.getStreamMaxVolume 返回放大的最大值（仅显示用）
 *  2. AudioManager.getStreamVolume 返回放大的当前值（仅显示用）
 *
 * 注意：真实放大系统扬声器输出由 SystemVolumeHook 通过 Shizuku 完成。
 */
object SpeakerBoostHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.speakerBoostEnabled) return
        LogX.i("扬声器增强启动（实验性，仅应用层显示） maxBoost=${cfg.speakerBoostMax}")

        hookAudioManagerGetStreamMaxVolume(lpparam, cfg)
        hookAudioManagerGetStreamVolume(lpparam, cfg)
    }

    private fun hookAudioManagerGetStreamMaxVolume(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.AudioManager", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "getStreamMaxVolume",
                    Int::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val cur = (p.result as? Int) ?: return
                            val streamType = (p.args[0] as? Int) ?: return
                            if (streamType in listOf(2, 3, 4, 5)) {
                                p.result = cur + cfg.speakerBoostMax
                            }
                        }
                    })
                LogX.hookSuccess("AudioManager", "getStreamMaxVolume")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(cls, "getStreamMaxVolume",
                    Int::class.javaPrimitiveType, "android.content.Context",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val cur = (p.result as? Int) ?: return
                            val streamType = (p.args[0] as? Int) ?: return
                            if (streamType in listOf(2, 3, 4, 5)) {
                                p.result = cur + cfg.speakerBoostMax
                            }
                        }
                    })
                LogX.hookSuccess("AudioManager", "getStreamMaxVolume(ctx)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("AudioManager", "getStreamMaxVolume", e)
        }
    }

    private fun hookAudioManagerGetStreamVolume(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.AudioManager", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "getStreamVolume",
                    Int::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val cur = (p.result as? Int) ?: return
                            val streamType = (p.args[0] as? Int) ?: return
                            if (streamType in listOf(2, 3, 4, 5)) {
                                p.result = cur + (cfg.speakerBoostMax / 2)
                            }
                        }
                    })
                LogX.hookSuccess("AudioManager", "getStreamVolume")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("AudioManager", "getStreamVolume", e)
        }
    }
}
