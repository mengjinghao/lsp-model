package com.audioboost.pro.hooks

import android.app.Application
import com.audioboost.pro.models.AudioConfig
import com.audioboost.pro.utils.LogX
import com.audioboost.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * AudioFlinger 节点写入Hook（Root 版专属）
 *
 * 功能：
 *  - 通过 Shizuku 写 /sys/class/audio/pcm 节点（部分设备支持）
 *  - 部分厂商 ROM 暴露 PCM 节点用于直接控制音量/增益，本 Hook 尝试探测并写入
 *  - Hook Application.onCreate 触发节点探测与写入
 *
 * 硬性限制：
 *  - 必须先检查 ShizukuHelper.isShizukuAvailable()
 *  - 节点路径因厂商而异，部分设备无此节点
 *  - 写入需 root 级别 Shizuku 授权
 */
object AudioFlingerHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.audioFlingerNodeEnabled) return
        LogX.i("AudioFlinger 节点写入启动 path=${cfg.pcmNodePath}")

        // Hook Application.onCreate 触发节点写入
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val app = p.thisObject as? Application ?: return
                        try { writePcmNode(cfg) } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("Application", "onCreate(AudioFlinger)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        // Hook AudioTrack.write 时同步刷新节点（保守策略，仅首次）
        hookAudioTrackWrite(lpparam, cfg)
    }

    /** 探测并写入 PCM 节点 */
    private fun writePcmNode(cfg: AudioConfig) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过 PCM 节点写入")
            return
        }
        try {
            val basePath = cfg.pcmNodePath
            // 先列出该目录下的节点
            val listing = ShizukuHelper.execShell("ls $basePath 2>/dev/null") ?: ""
            if (listing.isBlank()) {
                LogX.w("PCM 节点目录不存在或为空: $basePath")
                // 尝试常见备选路径
                val alternatives = listOf(
                    "/sys/class/audio/pcm",
                    "/sys/class/sound/pcm",
                    "/proc/asound/card0/pcm0p"
                )
                for (alt in alternatives) {
                    val test = ShizukuHelper.execShell("ls $alt 2>/dev/null") ?: ""
                    if (test.isNotBlank()) {
                        LogX.i("找到备选节点目录: $alt")
                        // 写入音量增益标识（值因厂商而异，这里只写入一个通用数字增益值）
                        val gainValue = ((cfg.boostLevel - 100) / 10).coerceIn(0, 20).toString()
                        val written = ShizukuHelper.writeFile("$alt/volume_gain", gainValue)
                        LogX.i("尝试写入 $alt/volume_gain = $gainValue (success=$written)")
                        return
                    }
                }
                LogX.w("未找到任何 PCM 节点目录，设备可能不支持")
                return
            }
            // 写入主增益节点
            val gainValue = ((cfg.boostLevel - 100) / 10).coerceIn(0, 20).toString()
            val written = ShizukuHelper.writeFile("$basePath/volume_gain", gainValue)
            LogX.i("写入 $basePath/volume_gain = $gainValue (success=$written)")
        } catch (e: Throwable) {
            LogX.e("PCM 节点写入异常", e)
        }
    }

    /** Hook AudioTrack.write 同步刷新节点（保守，仅前 3 次） */
    private fun hookAudioTrackWrite(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists("android.media.AudioTrack", lpparam.classLoader) ?: return
            var refreshCount = 0
            try {
                XposedHelpers.findAndHookMethod(cls, "write",
                    ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            if (refreshCount < 3) {
                                refreshCount++
                                try { writePcmNode(cfg) } catch (_: Throwable) {}
                            }
                        }
                    })
                LogX.hookSuccess("AudioTrack", "write(byte[])")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("AudioTrack", "write(byte[])", e)
        }
    }
}
