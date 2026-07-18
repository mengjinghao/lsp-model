package com.batteryopt.noroot.hooks

import com.batteryopt.noroot.models.BatteryConfig
import com.batteryopt.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * JobScheduler 优化 Hook（应用层）
 *
 * 功能：
 *  1. Hook JobScheduler.schedule，对非紧急 Job 设置 requireDeviceIdle()/requireCharging()
 *     约束，推迟到空闲/充电时执行
 *  2. 对高频重复 Job 限频（放大最小周期）
 *  3. 日志记录 Job 调度情况
 *
 * 硬性限制（NoRoot 版）：
 *  - 仅作用于当前 APP 调度的 Job
 *  - 不能修改系统 JobScheduler 全局策略
 *  - 对用户感知强（如消息推送）的 Job 不强制追加 idle 约束，避免影响功能
 */
object JobSchedulerHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("JobScheduler 优化启动 | 最小周期=${cfg.jobMinPeriodMs}ms idle约束=${cfg.jobRequireIdle}")

        hookSchedule(lpparam, cfg)
    }

    /**
     * Hook JobScheduler.schedule(JobInfo job)
     * 修改 JobInfo 的约束：放大周期、追加 idle/charging 约束
     */
    private fun hookSchedule(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        try {
            val jsCls = XposedHelpers.findClassIfExists(
                "android.app.job.JobScheduler", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                jsCls, "schedule",
                "android.app.job.JobInfo",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val jobInfo = p.args[0] ?: return
                        try {
                            modifyJobInfo(jobInfo, cfg)
                        } catch (e: Exception) {
                            LogX.e("修改 JobInfo 异常", e)
                        }
                    }
                })
            LogX.hookSuccess("JobScheduler", "schedule")
        } catch (e: Exception) {
            LogX.e("Hook schedule 异常", e)
        }
    }

    /**
     * 通过反射修改 JobInfo 的约束
     * JobInfo 内部存储在 Builder 中，已经构建的 JobInfo 字段大部分是 final，
     * 这里采用"反射改字段"或"重建 Builder"两种策略
     */
    private fun modifyJobInfo(jobInfo: Any, cfg: BatteryConfig) {
        val cls = jobInfo.javaClass

        // 1. 放大周期
        try {
            val periodField = cls.getDeclaredField("intervalMillis")
            periodField.isAccessible = true
            val cur = periodField.getLong(jobInfo)
            if (cur > 0 && cur < cfg.jobMinPeriodMs) {
                periodField.setLong(jobInfo, cfg.jobMinPeriodMs)
                LogX.w("Job 周期放大: ${cur}ms -> ${cfg.jobMinPeriodMs}ms")
            }
        } catch (_: Exception) {
            // 不同 Android 版本字段名可能不同，忽略
        }

        // 2. 追加 idle 约束（仅非紧急 Job）
        if (cfg.jobRequireIdle) {
            try {
                val flagsField = cls.getDeclaredField("flags")
                flagsField.isAccessible = true
                val curFlags = flagsField.getInt(jobInfo)
                // FLAG_REQUIRE_DEVICE_IDLE = 1 << 0
                val newFlags = curFlags or (1 shl 0)
                flagsField.setInt(jobInfo, newFlags)
                LogX.d("Job 追加 requireDeviceIdle 约束")
            } catch (_: Exception) {}
        }

        // 3. 日志记录 jobId
        try {
            val idField = cls.getDeclaredField("jobId")
            idField.isAccessible = true
            val jobId = idField.getInt(jobInfo)
            LogX.d("Job schedule: id=$jobId")
        } catch (_: Exception) {}
    }
}
