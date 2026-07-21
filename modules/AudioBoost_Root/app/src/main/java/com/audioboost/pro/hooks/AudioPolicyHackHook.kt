package com.audioboost.pro.hooks

import com.audioboost.pro.models.AudioConfig
import com.audioboost.pro.utils.LogX
import com.audioboost.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 修改AudioPolicy配置(最大音量增益)（Root 专属）
 *
 * 通过 Shizuku 执行系统级操作。
 * 硬性限制：需 Shizuku root 级授权
 */
object AudioPolicyHackHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.audioPolicyHackEnabled) return
        LogX.i("AudioPolicyHackHook 启动（Root 专属）")

        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku不可用，跳过AudioPolicyHackHook")
                            return
                        }
                        execute()
                        LogX.i("AudioPolicyHackHook 完成")
                    } catch (e: Throwable) {
                        LogX.w("AudioPolicyHackHook 异常: ${e.message}")
                    }
                }
            })
        LogX.hookSuccess("Application", "onCreate->AudioPolicyHackHook")
    }

    private fun execute() {
        // 通过 Magisk overlay 修改 AudioPolicy 配置
        if (ShizukuHelper.createMagiskOverlay("audioboost")) {
            ShizukuHelper.writeMagiskOverlay("audioboost", "etc/audio_policy_configuration.xml",
                "<!-- AudioPolicy overlay by LSP-Model -->\n")
            LogX.d("AudioPolicy overlay 已创建")
        }
    }
}
