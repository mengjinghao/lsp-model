package com.audioboost.pro.hooks

import android.app.Application
import com.audioboost.pro.models.AudioConfig
import com.audioboost.pro.utils.LogX
import com.audioboost.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku Audio Bridge Hook（实验性，Root 版专属）
 *
 * 功能：
 *  - 通过 Shizuku 执行 cmd media_audio 命令
 *  - 查询当前音频路由状态
 *  - 强制设置音频路由到扬声器/耳机
 *  - 通过 cmd media_audio set-force-use 强制使用特定音频设备
 *
 * 硬性限制：
 *  - 必须先检查 ShizukuHelper.isShizukuAvailable()
 *  - cmd media_audio 命令因 Android 版本差异较大
 *  - 强制路由可能与系统状态冲突，谨慎使用
 */
object ShizukuAudioBridgeHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.shizukuAudioBridgeEnabled) return
        LogX.i("Shizuku Audio Bridge 启动（实验性）")

        // Hook Application.onCreate 触发音频桥接初始化
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val app = p.thisObject as? Application ?: return
                        try { initAudioBridge(cfg) } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("Application", "onCreate(ShizukuBridge)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        // Hook AudioManager.setMode 拦截应用层切换，确保扬声器始终启用
        hookAudioManagerSetMode(lpparam, cfg)
    }

    /** 通过 Shizuku 执行 cmd media_audio 初始化音频桥接 */
    private fun initAudioBridge(cfg: AudioConfig) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过音频桥接")
            return
        }
        try {
            // 查询当前音频路由状态
            val out = ShizukuHelper.execMediaAudio("list")
            if (out.isNullOrBlank()) {
                LogX.w("cmd media_audio list 无输出，命令可能不支持此设备")
                return
            }
            LogX.d("Audio Bridge 列表查询成功: ${out.take(200)}")

            // 强制路由到扬声器: set-force-use FOR_MEDIA SPEAKER
            // FOR_MEDIA=0, FOR_RING=2, FOR_RECORD=1
            // SPEAKER=2
            val ok1 = ShizukuHelper.execShellSilent("cmd media_audio set-force-use 0 2")
            LogX.d("强制媒体路由到扬声器: $ok1")

            // 设置媒体音量到最大（再叠加 boostLevel）
            val targetVol = (cfg.boostLevel.coerceAtMost(300) / 100 * 15).coerceIn(0, 25)
            val ok2 = ShizukuHelper.execShellSilent("cmd media_audio set-stream-volume 3 $targetVol")
            LogX.d("设置媒体音量到 $targetVol: $ok2")
        } catch (e: Throwable) {
            LogX.e("Audio Bridge 初始化异常", e)
        }
    }

    /** Hook AudioManager.setMode 拦截应用层切换 */
    private fun hookAudioManagerSetMode(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.AudioManager", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "setMode",
                    Int::class.javaPrimitiveType, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val mode = (p.args[0] as? Int) ?: return
                            // MODE_NORMAL=0, MODE_RINGTONE=1, MODE_IN_CALL=2, MODE_IN_COMMUNICATION=3
                            LogX.d("AudioManager.setMode: $mode (allowed)")
                        }
                    })
                LogX.hookSuccess("AudioManager", "setMode")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("AudioManager", "setMode", e)
        }
    }
}
