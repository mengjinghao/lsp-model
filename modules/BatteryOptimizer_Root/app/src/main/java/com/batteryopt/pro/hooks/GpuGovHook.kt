package com.batteryopt.pro.hooks

import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import com.batteryopt.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * GPU Governor 控制 Hook（Root 版独有）
 *
 * 通过 Shizuku 控制 GPU 省电策略：
 *  - echo powersave > /sys/class/kgsl/kgsl-3d0/devfreq/governor
 *  - echo 0 > /sys/class/kgsl/kgsl-3d0/min_pwrlevel
 *  - 强制最低功耗级别实现 GPU 省电
 *
 * 硬性限制：
 *  - 必须 ShizukuHelper.isShizukuAvailable()
 *  - 需要 Qualcomm Adreno GPU (kgsl)
 *  - 全部 try-catch 保护
 */
object GpuGovHook {

    private var isApplied = false

    private const val KGSL_GOVERNOR = "/sys/class/kgsl/kgsl-3d0/devfreq/governor"
    private const val KGSL_MIN_PWRLEVEL = "/sys/class/kgsl/kgsl-3d0/min_pwrlevel"
    private const val KGSL_GPUCLK = "/sys/class/kgsl/kgsl-3d0/gpuclk"

    private val altPaths = listOf(
        "/sys/class/devfreq/*/governor",
        "/sys/devices/platform/kgsl-3d0.0/kgsl/kgsl-3d0/devfreq/governor",
        "/sys/kernel/gpu/gpu_governor"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        if (!cfg.gpuGovBatteryEnabled) {
            LogX.d("GpuGovHook 未启用，跳过")
            return
        }
        if (isApplied) return

        LogX.i("GpuGovHook 启动：GPU Governor 省电控制")

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        isApplied = true
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku 不可用，跳过 GPU Governor 控制")
                            return
                        }
                        applyGpuGovernor()
                    }
                })
            LogX.hookSuccess("Application", "onCreate->GpuGovHook")
        } catch (e: Throwable) {
            LogX.e("GpuGovHook Application.onCreate Hook 异常", e)
        }
    }

    private fun applyGpuGovernor() {
        try {
            val result = ShizukuHelper.execShell("echo powersave > $KGSL_GOVERNOR 2>&1")
            LogX.d("GPU governor -> powersave: $result")
        } catch (e: Throwable) { LogX.w("GPU governor 设置异常: ${e.message}") }

        try {
            val result = ShizukuHelper.execShell("echo 0 > $KGSL_MIN_PWRLEVEL 2>&1")
            LogX.d("GPU min_pwrlevel -> 0: $result")
        } catch (e: Throwable) { LogX.w("GPU min_pwrlevel 设置异常: ${e.message}") }

        try {
            val currentClk = ShizukuHelper.readFile(KGSL_GPUCLK)
            LogX.d("当前 GPU 频率: $currentClk")
        } catch (e: Throwable) { LogX.w("读取 GPU 频率异常: ${e.message}") }

        LogX.i("GpuGovHook: GPU 省电策略已应用")
    }

    fun setGovernor(governor: String): Boolean {
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) return false
            ShizukuHelper.execShell("echo $governor > $KGSL_GOVERNOR 2>&1") != null
        } catch (e: Throwable) {
            LogX.e("设置 GPU governor 异常: $governor", e)
            false
        }
    }

    fun getGovernor(): String? {
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) return null
            ShizukuHelper.readFile(KGSL_GOVERNOR)?.trim()
        } catch (e: Throwable) { null }
    }

    fun getCurrentGpuClk(): String? {
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) return null
            ShizukuHelper.readFile(KGSL_GPUCLK)?.trim()
        } catch (e: Throwable) { null }
    }

    fun release() {
        isApplied = false
    }
}
