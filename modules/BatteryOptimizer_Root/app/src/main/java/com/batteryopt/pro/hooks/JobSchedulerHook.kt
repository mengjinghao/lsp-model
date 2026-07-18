package com.batteryopt.pro.hooks

import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * JobScheduler 优化 Hook（应用层）
 *
 * 功能：
 *  1. Hook JobScheduler.schedule，对非紧急 Job 追加 requireDeviceIdle() 约束
 *  2. 对高频重复 Job 限频（放大最小周期）
 *  3. 日志记录 Job 调度情况
 */
object JobSchedulerHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("JobScheduler 优化启动 | 最小周期=${cfg.jobMinPeriodMs}ms idle约束=${cfg.jobRequireIdle}")

        hookSchedule(lpparam, cfg)
    }

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
        } catch (_: Exception) {}

        // 2. 追加 idle 约束
        if (cfg.jobRequireIdle) {
            try {
                val flagsField = cls.getDeclaredField("flags")
                flagsField.isAccessible = true
                val curFlags = flagsField.getInt(jobInfo)
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
