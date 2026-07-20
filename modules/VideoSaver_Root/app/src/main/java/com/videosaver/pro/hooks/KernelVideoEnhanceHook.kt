package com.videosaver.pro.hooks

import android.app.Application
import com.videosaver.pro.models.VideoConfig
import com.videosaver.pro.utils.LogX
import com.videosaver.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】内核视频增强 Hook（Root 专属，部分设备支持）
 *
 * 实现思路：
 *  - Hook Application.onCreate 在 APP 启动时通过 Shizuku 写 /sys/class/video/* 节点
 *  - 支持亮度/对比度/饱和度增强（部分设备厂商暴露的 sysfs 节点）
 *  - 节点路径示例：
 *      /sys/class/video/brightness
 *      /sys/class/video/contrast
 *      /sys/class/video/saturation
 *      /sys/class/display/brightness
 *      /sys/class/backlight/brightness
 *
 * 硬性限制：
 *  - 必须先检查 ShizukuHelper.isShizukuAvailable()
 *  - 写 /sys 节点需要 root 级别 Shizuku 授权
 *  - 节点路径因厂商而异，部分设备不存在
 *  - catch 块使用 `catch (_: Throwable) {}` 静默处理
 *  - 实验性，仅部分高通/MTK 平台有效
 */
object KernelVideoEnhanceHook {

    /** 视频增强 sysfs 节点路径候选 */
    private val BRIGHTNESS_PATHS = arrayOf(
        "/sys/class/video/brightness",
        "/sys/class/video1/brightness",
        "/sys/class/display/brightness",
        "/sys/class/graphics/fb0/brightness"
    )

    private val CONTRAST_PATHS = arrayOf(
        "/sys/class/video/contrast",
        "/sys/class/video1/contrast",
        "/sys/class/display/contrast"
    )

    private val SATURATION_PATHS = arrayOf(
        "/sys/class/video/saturation",
        "/sys/class/video1/saturation",
        "/sys/class/display/saturation"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        if (!cfg.kernelVideoEnhanceEnabled) return
        LogX.i("【实验性】内核视频增强 Hook 启动（Root 专属）")

        hookAppLifecycle(lpparam, cfg)
        hookVideoDecoder(lpparam, cfg)
    }

    /** Hook Application.onCreate 触发 sysfs 节点写入 */
    private fun hookAppLifecycle(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val app = p.thisObject as? Application ?: return
                        try {
                            applyVideoEnhance(cfg)
                        } catch (_: Throwable) { }
                    }
                })
            LogX.hookSuccess("Application", "onCreate(KernelVideoEnhance)")
        } catch (e: Throwable) {
            LogX.hookFailed("Application", "onCreate", e)
        }
    }

    /** Hook MediaCodec.configure 在视频解码前应用增强 */
    private fun hookVideoDecoder(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.MediaCodec", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "configure",
                    "android.media.MediaFormat",
                    "android.view.Surface",
                    "android.media.MediaCrypto",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                // 视频解码前刷新 sysfs 增强
                                if (cfg.enhanceBrightness > 0 || cfg.enhanceContrast > 0 || cfg.enhanceSaturation > 0) {
                                    applyVideoEnhance(cfg)
                                    LogX.d("MediaCodec.configure 触发视频增强")
                                }
                            } catch (_: Throwable) { }
                        }
                    })
                LogX.hookSuccess("MediaCodec", "configure")
            } catch (_: Throwable) { }
        } catch (_: Throwable) { }
    }

    /** 通过 Shizuku 写 sysfs 节点 */
    private fun applyVideoEnhance(cfg: VideoConfig) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过视频增强")
            return
        }
        Thread {
            try {
                if (cfg.enhanceBrightness > 0) {
                    writeFirstAvailable(BRIGHTNESS_PATHS, cfg.enhanceBrightness.toString())
                }
                if (cfg.enhanceContrast > 0) {
                    writeFirstAvailable(CONTRAST_PATHS, cfg.enhanceContrast.toString())
                }
                if (cfg.enhanceSaturation > 0) {
                    writeFirstAvailable(SATURATION_PATHS, cfg.enhanceSaturation.toString())
                }
            } catch (_: Throwable) { }
        }.start()
    }

    /** 写入第一个可用的节点路径 */
    private fun writeFirstAvailable(paths: Array<String>, value: String) {
        for (path in paths) {
            val ok = ShizukuHelper.writeFile(path, value)
            if (ok) {
                LogX.d("sysfs 节点写入成功: $path = $value")
                return
            }
        }
        LogX.w("无可用 sysfs 节点（候选: ${paths.firstOrNull()}）")
    }

    fun release() {
        ShizukuHelper.release()
    }
}
