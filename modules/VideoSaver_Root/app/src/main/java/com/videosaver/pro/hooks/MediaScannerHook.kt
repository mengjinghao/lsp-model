package com.videosaver.pro.hooks

import com.videosaver.pro.models.VideoConfig
import com.videosaver.pro.utils.LogX
import com.videosaver.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku触发媒体扫描(下载后立即入相册)（Root 专属）
 *
 * 通过 Shizuku 执行系统级操作。
 * 硬性限制：需 Shizuku root 级授权
 */
object MediaScannerHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        if (!cfg.mediaScannerEnabled) return
        LogX.i("MediaScannerHook 启动（Root 专属）")

        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku不可用，跳过MediaScannerHook")
                            return
                        }
                        execute()
                        LogX.i("MediaScannerHook 完成")
                    } catch (e: Throwable) {
                        LogX.w("MediaScannerHook 异常: ${e.message}")
                    }
                }
            })
        LogX.hookSuccess("Application", "onCreate->MediaScannerHook")
    }

    private fun execute() {
        // 触发媒体扫描让下载的视频立即出现在相册
        val savePath = "/sdcard/Download/VideoSaver/"
        ShizukuHelper.execShellSilent("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://$savePath")
        LogX.d("媒体扫描已触发: $savePath")
    }
}
