package com.audioboost.pro.hooks

import com.audioboost.pro.models.AudioConfig
import com.audioboost.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 音质增强Hook（实验性，仅应用层）
 *
 * 拦截路径：
 *  1. MediaFormat.setInteger - 提升采样率/位深
 *  2. AudioRecord.Builder.setSampleRate - 提升录音采样率
 */
object AudioQualityEnhanceHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.audioQualityEnhanceEnabled) return
        LogX.i("音质增强启动（实验性） sampleRate=${cfg.targetSampleRate} bitDepth=${cfg.targetBitDepth}")

        hookMediaFormatSetInteger(lpparam, cfg)
        hookAudioRecordBuilderSetSampleRate(lpparam, cfg)
    }

    private fun hookMediaFormatSetInteger(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.MediaFormat", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "setInteger",
                    String::class.java, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val key = p.args[0] as? String ?: return
                            val v = (p.args[1] as? Int) ?: return
                            when (key) {
                                "sample-rate" -> {
                                    if (v < cfg.targetSampleRate) {
                                        p.args[1] = cfg.targetSampleRate
                                        LogX.d("MediaFormat sample-rate: $v -> ${cfg.targetSampleRate}")
                                    }
                                }
                                "pcm-encoding" -> {
                                    val targetEnc = when (cfg.targetBitDepth) {
                                        24 -> 3
                                        32 -> 4
                                        else -> 2
                                    }
                                    if (v < targetEnc) {
                                        p.args[1] = targetEnc
                                        LogX.d("MediaFormat pcm-encoding: $v -> $targetEnc")
                                    }
                                }
                            }
                        }
                    })
                LogX.hookSuccess("MediaFormat", "setInteger")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("MediaFormat", "setInteger", e)
        }
    }

    private fun hookAudioRecordBuilderSetSampleRate(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.AudioRecord.Builder", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "setSampleRate",
                    Int::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val v = (p.args[0] as? Int) ?: return
                            if (v < cfg.targetSampleRate) {
                                p.args[0] = cfg.targetSampleRate
                                LogX.d("AudioRecord.Builder sampleRate: $v -> ${cfg.targetSampleRate}")
                            }
                        }
                    })
                LogX.hookSuccess("AudioRecord.Builder", "setSampleRate")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("AudioRecord.Builder", "setSampleRate", e)
        }
    }
}
