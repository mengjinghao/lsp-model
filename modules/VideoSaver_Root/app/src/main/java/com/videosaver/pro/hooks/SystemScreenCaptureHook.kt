package com.videosaver.pro.hooks

import com.videosaver.pro.models.VideoConfig
import com.videosaver.pro.utils.LogX
import com.videosaver.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 系统级屏幕录制/截图 Hook（Root 版独有）
 *
 * 通过 Shizuku 执行系统命令实现视频捕获：
 *  - screenrecord 系统级录屏
 *  - screencap 系统级截图
 *  - am broadcast 触发媒体扫描
 *  - 输出到 /sdcard/Download/
 *
 * 硬性限制：
 *  - 必须 ShizukuHelper.isShizukuAvailable()
 *  - screenrecord 需 Android 5.0+
 *  - 全部 try-catch 保护
 */
object SystemScreenCaptureHook {

    private var isApplied = false

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        if (!cfg.systemScreenCaptureEnabled) {
            LogX.d("SystemScreenCaptureHook 未启用，跳过")
            return
        }
        if (isApplied) return

        LogX.i("SystemScreenCaptureHook 启动：系统级屏幕录制/截图")

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        isApplied = true
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku 不可用，跳过系统级屏幕捕获")
                            return
                        }
                    }
                })
            LogX.hookSuccess("Application", "onCreate->SystemScreenCaptureHook")
        } catch (e: Throwable) {
            LogX.e("SystemScreenCaptureHook Application.onCreate Hook 异常", e)
        }
    }

    fun startScreenRecord(
        outputPath: String = "/sdcard/Download/recording.mp4",
        bitRate: Int = 8000000,
        timeLimitSec: Int = 300
    ): Boolean {
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) {
                LogX.w("Shizuku 不可用，无法启动录屏")
                return false
            }
            val cmd = "screenrecord --bit-rate $bitRate --time-limit $timeLimitSec $outputPath"
            val result = ShizukuHelper.execShell("nohup $cmd > /dev/null 2>&1 &")
            LogX.i("录屏已启动: $cmd -> $result")
            true
        } catch (e: Throwable) {
            LogX.e("启动录屏异常", e)
            false
        }
    }

    fun stopScreenRecord(): Boolean {
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) return false
            ShizukuHelper.execShell("pkill -SIGINT screenrecord 2>&1")
            LogX.i("录屏已停止")
            triggerMediaScan("/sdcard/Download/recording.mp4")
            true
        } catch (e: Throwable) {
            LogX.e("停止录屏异常", e)
            false
        }
    }

    fun takeScreenshot(outputPath: String = "/sdcard/Download/screenshot.png"): Boolean {
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) return false
            val result = ShizukuHelper.execShell("screencap -p $outputPath 2>&1")
            LogX.i("截图已保存: $outputPath -> $result")
            triggerMediaScan(outputPath)
            true
        } catch (e: Throwable) {
            LogX.e("截图异常", e)
            false
        }
    }

    private fun triggerMediaScan(filePath: String) {
        try {
            ShizukuHelper.execShell(
                "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://$filePath 2>&1"
            )
            LogX.d("媒体扫描已触发: $filePath")
        } catch (e: Throwable) { LogX.w("媒体扫描异常: ${e.message}") }
    }

    fun triggerMediaScanForDir(dirPath: String = "/sdcard/Download/") {
        try {
            if (!ShizukuHelper.isShizukuAvailable()) return
            ShizukuHelper.execShell("am broadcast -a android.intent.action.MEDIA_MOUNTED -d file://$dirPath 2>&1")
            LogX.d("媒体目录扫描已触发: $dirPath")
        } catch (e: Throwable) { LogX.w("媒体目录扫描异常: ${e.message}") }
    }

    fun release() {
        isApplied = false
    }
}
