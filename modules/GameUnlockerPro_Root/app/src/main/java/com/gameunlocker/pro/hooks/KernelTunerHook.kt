package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import com.gameunlocker.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 内核调优 Hook（Root 专属）
 *
 * 通过 Shizuku 执行内核参数优化：
 *  - IO 调度器改为 noop/deadline（降低延迟）
 *  - CPU 大核强制在线（cpu4~cpu7）
 *  - CPU 调度器改为 performance
 *  - 内核调度参数优化
 *
 * 硬性限制：需 Shizuku root 级授权，不同设备节点路径不同
 */
object KernelTunerHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.kernelTunerEnabled) return
        LogX.i("内核调优 Hook 启动（Root 专属）")

        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku不可用，跳过内核调优")
                            return
                        }
                        tuneIOScheduler()
                        forceBigCoresOnline()
                        setCPUPerformanceGovernor()
                        tuneKernelSched()
                        LogX.i("内核调优完成")
                    } catch (e: Throwable) {
                        LogX.w("内核调优异常: ${e.message}")
                    }
                }
            })
        LogX.hookSuccess("Application", "onCreate->KernelTuner")
    }

    /** IO 调度器优化 */
    private fun tuneIOScheduler() {
        val schedulers = listOf("noop", "deadline", "cfq")
        val blockPaths = listOf(
            "/sys/block/sda/queue/scheduler",
            "/sys/block/mmcblk0/queue/scheduler",
            "/sys/block/dm-0/queue/scheduler"
        )
        for (path in blockPaths) {
            for (sched in schedulers) {
                if (ShizukuHelper.execShellSilent("echo $sched > $path")) {
                    LogX.d("IO调度器: $path → $sched")
                    return
                }
            }
        }
        LogX.d("IO调度器优化: 无可用节点")
    }

    /** CPU 大核强制在线 */
    private fun forceBigCoresOnline() {
        for (i in 4..7) {
            ShizukuHelper.execShellSilent("echo 1 > /sys/devices/system/cpu/cpu$i/online")
        }
        LogX.d("CPU大核(cpu4-7)强制在线")
    }

    /** CPU 调度器改为 performance */
    private fun setCPUPerformanceGovernor() {
        for (i in 0..7) {
            val path = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor"
            ShizukuHelper.execShellSilent("echo performance > $path")
        }
        LogX.d("CPU调度器 → performance")
    }

    /** 内核调度参数优化 */
    private fun tuneKernelSched() {
        // 减少子进程优先级继承
        ShizukuHelper.execShellSilent("echo 0 > /proc/sys/kernel/sched_child_runs_first")
        // 提升 RT 调度
        ShizukuHelper.execShellSilent("echo 950000 > /proc/sys/kernel/sched_rt_runtime_us")
        // 降低 swappiness
        ShizukuHelper.execShellSilent("echo 10 > /proc/sys/vm/swappiness")
        LogX.d("内核调度参数优化完成")
    }
}
