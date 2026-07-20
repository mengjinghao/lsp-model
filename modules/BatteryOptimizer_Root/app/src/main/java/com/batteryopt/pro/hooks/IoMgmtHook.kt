package com.batteryopt.pro.hooks

import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import com.batteryopt.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * I/O Scheduler 省电优化 Hook（Root 版独有）
 *
 * 通过 Shizuku 优化 I/O 调度器减少磁盘耗电：
 *  - echo noop > /sys/block/mmcblk0/queue/scheduler（省电调度器）
 *  - echo 1 > /sys/block/mmcblk0/queue/iosched/low_latency（禁用低延迟）
 *  - 探测可用调度器并选择最省电的
 *  - 自动检测设备块设备路径
 *
 * 硬性限制：
 *  - 必须 ShizukuHelper.isShizukuAvailable()
 *  - 需要 root 写 sysfs
 *  - 全部 try-catch 保护
 */
object IoMgmtHook {

    private var isApplied = false

    private val blockDevices = listOf(
        "/sys/block/mmcblk0/queue/scheduler",
        "/sys/block/sda/queue/scheduler",
        "/sys/block/sdb/queue/scheduler",
        "/sys/block/dm-0/queue/scheduler",
        "/sys/block/nvme0n1/queue/scheduler"
    )

    private val ioSchedLowLatency = listOf(
        "/sys/block/mmcblk0/queue/iosched/low_latency",
        "/sys/block/sda/queue/iosched/low_latency"
    )

    private val powerSaveSchedulers = listOf("noop", "none", "bfq")

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        if (!cfg.ioMgmtEnabled) {
            LogX.d("IoMgmtHook 未启用，跳过")
            return
        }
        if (isApplied) return

        LogX.i("IoMgmtHook 启动：I/O 调度器省电优化")

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        isApplied = true
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku 不可用，跳过 I/O 调度器优化")
                            return
                        }
                        applyIoGovernor()
                    }
                })
            LogX.hookSuccess("Application", "onCreate->IoMgmtHook")
        } catch (e: Throwable) {
            LogX.e("IoMgmtHook Application.onCreate Hook 异常", e)
        }
    }

    private fun applyIoGovernor() {
        for (devicePath in blockDevices) {
            try {
                val current = ShizukuHelper.readFile(devicePath)?.trim()
                if (current.isNullOrEmpty()) continue
                LogX.d("当前 I/O 调度器 [$devicePath]: $current")

                val available = current.replace("[", "").replace("]", "").split(" ").map { it.trim() }
                val selected = powerSaveSchedulers.firstOrNull { it in available } ?: continue

                val result = ShizukuHelper.execShell("echo $selected > $devicePath 2>&1")
                LogX.d("I/O 调度器 [$devicePath]: $selected -> $result")
            } catch (e: Throwable) { LogX.w("[$devicePath] I/O 调度器设置异常: ${e.message}") }
        }

        for (latencyPath in ioSchedLowLatency) {
            try {
                val check = ShizukuHelper.readFile(latencyPath)
                if (check.isNullOrBlank()) continue
                val result = ShizukuHelper.execShell("echo 1 > $latencyPath 2>&1")
                LogX.d("禁用低延迟 [$latencyPath]: $result")
            } catch (e: Throwable) { LogX.w("[$latencyPath] 低延迟设置异常: ${e.message}") }
        }

        LogX.i("IoMgmtHook: I/O 调度器省电优化完成")
    }

    fun setScheduler(devicePath: String, scheduler: String): Boolean {
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) return false
            ShizukuHelper.execShell("echo $scheduler > $devicePath 2>&1") != null
        } catch (e: Throwable) {
            LogX.e("设置 I/O 调度器异常: $devicePath=$scheduler", e)
            false
        }
    }

    fun getScheduler(devicePath: String): String? {
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) return null
            ShizukuHelper.readFile(devicePath)?.trim()
        } catch (e: Throwable) { null }
    }

    fun probeAvailableSchedulers(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        for (path in blockDevices) {
            try {
                val content = ShizukuHelper.readFile(path)?.trim()
                if (!content.isNullOrEmpty()) {
                    result.add(path to content)
                }
            } catch (_: Throwable) {}
        }
        return result
    }

    fun release() {
        isApplied = false
    }
}
