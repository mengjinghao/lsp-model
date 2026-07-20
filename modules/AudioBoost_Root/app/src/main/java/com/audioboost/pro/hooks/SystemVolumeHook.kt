package com.audioboost.pro.hooks

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.audioboost.pro.models.AudioConfig
import com.audioboost.pro.utils.LogX
import com.audioboost.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 系统级音量突破Hook（Root 版专属）
 *
 * 功能：
 *  - 通过 Shizuku 调用 media volume --set 设置系统音量突破上限
 *  - Hook AudioManager.setStreamVolume 拦截应用层调用，按 systemVolumeMaxBoost 放大后通过 Shizuku 写入
 *  - 监听 SCREEN_ON 广播定期重置（避免被系统回滚）
 *
 * 硬性限制：
 *  - 必须先检查 ShizukuHelper.isShizukuAvailable()
 *  - 系统级音量修改可能被系统回滚，需在 SCREEN_ON 时重新应用
 *  - 部分设备需 root 级别 Shizuku 授权
 */
object SystemVolumeHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.systemVolumeBoostEnabled) return
        LogX.i("系统级音量突破启动 boost=${cfg.systemVolumeMaxBoost}%")

        // Hook Application.onCreate 触发初始化与广播注册
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val app = p.thisObject as? Application ?: return
                        try {
                            applySystemVolume(cfg)
                            registerScreenReceiver(app, cfg)
                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("Application", "onCreate(SystemVolume)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        // Hook AudioManager.setStreamVolume 拦截应用层调用
        hookAudioManagerSetStreamVolume(lpparam, cfg)
    }

    /** 通过 Shizuku 设置系统音量（突破原上限） */
    private fun applySystemVolume(cfg: AudioConfig) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过系统级音量修改")
            return
        }
        try {
            // STREAM_MUSIC=3，先 dumpsys 读取 max
            val dump = ShizukuHelper.execShell("dumpsys audio | grep -i 'STREAM_MUSIC'") ?: ""
            // 简单解析 max volume index
            val maxIdx = Regex("""max:\s*(\d+)""").find(dump)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: 15  // 默认媒体音量最大值
            // 按比例放大并写入
            val target = (maxIdx.toLong() * (100 + cfg.systemVolumeMaxBoost) / 100L).toInt()
            LogX.i("系统级音量: max=$maxIdx -> 目标=$target")
            // 通过 media volume --set 设置（部分 Android 12+ 支持）
            val ok = ShizukuHelper.setSystemVolume(3, target)
            // 备选方案: 通过 audio service set-stream-volume
            if (!ok) {
                ShizukuHelper.execShellSilent(
                    "cmd audio set-stream-volume 3 $target 1"
                )
            }
        } catch (e: Throwable) {
            LogX.e("系统级音量修改异常", e)
        }
    }

    /** Hook AudioManager.setStreamVolume 拦截应用层调用 */
    private fun hookAudioManagerSetStreamVolume(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.AudioManager", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "setStreamVolume",
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val streamType = (p.args[0] as? Int) ?: return
                            val index = (p.args[1] as? Int) ?: return
                            // 仅对媒体/铃声/通知/闹钟放大
                            if (streamType in listOf(2, 3, 4, 5)) {
                                val amplified = (index.toLong() * (100 + cfg.systemVolumeMaxBoost) / 100L).toInt()
                                p.args[1] = amplified
                                LogX.d("setStreamVolume stream=$streamType: $index -> $amplified")
                            }
                        }
                    })
                LogX.hookSuccess("AudioManager", "setStreamVolume")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("AudioManager", "setStreamVolume", e)
        }
    }

    /** 注册 SCREEN_ON 广播接收器，亮屏时重新应用系统音量 */
    private fun registerScreenReceiver(app: Application, cfg: AudioConfig) {
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    try { applySystemVolume(cfg) } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            app.registerReceiver(receiver, filter)
            LogX.d("SCREEN_ON 广播已注册，亮屏时重应用系统音量")
        } catch (e: Throwable) {
            LogX.w("注册广播失败: ${e.message}")
        }
    }
}
