package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import com.gameunlocker.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * GPU 频率锁定 Hook（Root 专属）
 *
 * 通过 Shizuku 锁定 GPU 最大频率：
 *  - 读取 GPU 最大频率
 *  - 写入 max_freq 锁定
 *  - 设置 devfreq governor 为 performance
 *
 * 适配高通 Adreno (kgsl) / MTK / Mali GPU
 */
object GpuFreqLockHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.gpuFreqLockEnabled) return
        LogX.i("GPU频率锁定 Hook 启动（Root 专属）")

        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku不可用，跳过GPU频率锁定")
                            return
                        }
                        lockGpuMaxFreq()
                        setGpuPerformanceGovernor()
                        LogX.i("GPU频率锁定完成")
                    } catch (e: Throwable) {
                        LogX.w("GPU频率锁定异常: ${e.message}")
                    }
                }
            })
        LogX.hookSuccess("Application", "onCreate->GpuFreqLock")
    }

    /** 锁定 GPU 最大频率 */
    private fun lockGpuMaxFreq() {
        // 高通 Adreno kgsl
        val kgslBase = "/sys/class/kgsl/kgsl-3d0"
        val devfreqBase = "$kgslBase/devfreq"
        // 读取可用最大频率
        val maxFreq = ShizukuHelper.readFile("$devfreqBase/max_freq")
            ?: ShizukuHelper.readFile("$kgslBase/max_clock_mhz")
        if (maxFreq != null) {
            // 写入 max_freq 锁定
            ShizukuHelper.execShellSilent("echo $maxFreq > $devfreqBase/max_freq")
            LogX.d("GPU最大频率锁定: $maxFreq")
        }
        // MTK GPU
        val mtkPaths = listOf(
            "/sys/kernel/mtk_gpu/gpu_freq",
            "/sys/devices/platform/13000000.mali/max_clock"
        )
        for (path in mtkPaths) {
            val freq = ShizukuHelper.readFile(path)
            if (freq != null) {
                LogX.d("MTK GPU频率: $freq")
                break
            }
        }
    }

    /** 设置 GPU governor 为 performance */
    private fun setGpuPerformanceGovernor() {
        val governorPaths = listOf(
            "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
            "/sys/devices/platform/13000000.mali/governor"
        )
        for (path in governorPaths) {
            if (ShizukuHelper.execShellSilent("echo performance > $path")) {
                LogX.d("GPU governor → performance: $path")
                return
            }
        }
    }
}
