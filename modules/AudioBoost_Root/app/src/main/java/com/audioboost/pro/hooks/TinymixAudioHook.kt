package com.audioboost.pro.hooks

import com.audioboost.pro.models.AudioConfig
import com.audioboost.pro.utils.LogX
import com.audioboost.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * tinymix ALSA 硬件音频控制 Hook（Root 版独有）
 *
 * 通过 Shizuku 执行 tinymix 命令直接控制硬件音频参数：
 *  - tinymix 列出所有可用控件
 *  - tinymix "RX1 Digital Volume" 84 设置硬件增益
 *  - tinymix "HPHL Volume" / "HPHR Volume" 耳机左右声道
 *  - tinymix "Speaker Driver Volume" 扬声器驱动
 *  - tinymix "ADC1 Volume" 麦克风增益
 *  - tinymix "IIR1 INP1 Volume" DSP 输入
 *
 * 硬性限制：
 *  - 必须 ShizukuHelper.isShizukuAvailable()
 *  - 需要设备支持 tinymix 二进制
 *  - 全部 try-catch 保护
 */
object TinymixAudioHook {

    private var isApplied = false

    private val controls = listOf(
        "RX1 Digital Volume" to "84",
        "HPHL Volume" to "20",
        "HPHR Volume" to "20",
        "Speaker Driver Volume" to "90",
        "ADC1 Volume" to "12",
        "IIR1 INP1 Volume" to "84"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.tinymixEnabled) {
            LogX.d("TinymixAudioHook 未启用，跳过")
            return
        }
        if (isApplied) return

        LogX.i("TinymixAudioHook 启动：tinymix ALSA 硬件音量控制")

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        isApplied = true
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku 不可用，跳过 tinymix 控制")
                            return
                        }
                        probeAndApply()
                    }
                })
            LogX.hookSuccess("Application", "onCreate->TinymixAudioHook")
        } catch (e: Throwable) {
            LogX.e("TinymixAudioHook Application.onCreate Hook 异常", e)
        }
    }

    private fun probeAndApply() {
        try {
            val available = ShizukuHelper.execShell("tinymix 2>&1")
            if (available.isNullOrBlank()) {
                LogX.w("tinymix 不可用，跳过 ALSA 控制")
                return
            }
            LogX.d("tinymix 可用控件列表已获取 (${available.length} bytes)")
        } catch (e: Throwable) { LogX.w("tinymix 探测异常: ${e.message}") }

        try {
            val volumeControls = ShizukuHelper.execShell("tinymix 2>&1 | grep -i \"volume\\|gain\\|boost\"")
            if (!volumeControls.isNullOrBlank()) {
                LogX.d("检测到的音量/增益控件:\n$volumeControls")
            }
        } catch (e: Throwable) { LogX.w("tinymix grep 异常: ${e.message}") }

        for ((control, value) in controls) {
            try {
                val result = ShizukuHelper.execShell("tinymix \"$control\" $value 2>&1")
                LogX.d("tinymix $control=$value -> $result")
            } catch (e: Throwable) {
                LogX.w("tinymix $control 设置异常: ${e.message}")
            }
        }

        LogX.i("TinymixAudioHook: ALSA 硬件音量配置完成")
    }

    fun setControl(name: String, value: Int): Boolean {
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) return false
            ShizukuHelper.execShell("tinymix \"$name\" $value 2>&1") != null
        } catch (e: Throwable) {
            LogX.e("tinymix 设置异常: $name=$value", e)
            false
        }
    }

    fun getControls(): String? {
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) return null
            ShizukuHelper.execShell("tinymix 2>&1")
        } catch (e: Throwable) { null }
    }

    fun release() {
        isApplied = false
    }
}
