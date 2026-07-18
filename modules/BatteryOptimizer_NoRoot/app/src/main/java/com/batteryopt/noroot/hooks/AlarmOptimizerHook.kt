package com.batteryopt.noroot.hooks

import com.batteryopt.noroot.models.BatteryConfig
import com.batteryopt.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * AlarmManager 闹钟优化 Hook（应用层）
 *
 * 功能：
 *  1. Hook set/setRepeating/setExact/setWindow，对高频精确闹钟降级为 setWindow（inexact），
 *     给系统更大合并空间，减少频繁唤醒
 *  2. 对明显非关键的重复闹钟（如统计上报）增加最小间隔限制（默认 5 分钟）
 *  3. 日志记录闹钟设置情况
 *
 * 硬性限制（NoRoot 版）：
 *  - 仅作用于当前 APP 调用的 AlarmManager，不影响其他 APP
 *  - 不能修改系统 doze 白名单
 *  - 关键闹钟（如 RTC_WAKEUP 用于闹钟应用的）应谨慎降级，本模块保留 type=RTC_WAKEUP 但放大窗口
 */
object AlarmOptimizerHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("Alarm 优化启动 | 最小间隔=${cfg.alarmMinIntervalMs}ms 降级exact=${cfg.alarmExactDowngrade}")

        hookSet(lpparam, cfg)
        hookSetRepeating(lpparam, cfg)
        hookSetExact(lpparam, cfg)
        hookSetWindow(lpparam, cfg)
    }

    /**
     * Hook set(int type, long when, PendingIntent operation)
     * 普通 set 已经是 inexact，但可以延后到最小间隔
     */
    private fun hookSet(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        try {
            val amCls = XposedHelpers.findClassIfExists(
                "android.app.AlarmManager", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                amCls, "set",
                Int::class.javaPrimitiveType,
                java.lang.Long.TYPE,
                "android.app.PendingIntent",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val type = p.args[0] as Int
                        val whenMs = p.args[1] as Long
                        LogX.d("Alarm set: type=$type when=$whenMs")
                    }
                })
            LogX.hookSuccess("AlarmManager", "set")
        } catch (e: Exception) {
            LogX.e("Hook set 异常", e)
        }
    }

    /**
     * Hook setRepeating(int type, long triggerAt, long interval, PendingIntent op)
     * 对非关键重复闹钟（interval 小于最小间隔的）放大 interval
     */
    private fun hookSetRepeating(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        try {
            val amCls = XposedHelpers.findClassIfExists(
                "android.app.AlarmManager", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                amCls, "setRepeating",
                Int::class.javaPrimitiveType,
                java.lang.Long.TYPE,
                java.lang.Long.TYPE,
                "android.app.PendingIntent",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val interval = p.args[2] as Long
                        if (interval < cfg.alarmMinIntervalMs) {
                            val oldInterval = interval
                            p.args[2] = cfg.alarmMinIntervalMs
                            LogX.w("setRepeating 间隔放大: $oldInterval -> ${cfg.alarmMinIntervalMs}")
                        } else {
                            LogX.d("setRepeating interval=$interval")
                        }
                    }
                })
            LogX.hookSuccess("AlarmManager", "setRepeating")
        } catch (e: Exception) {
            LogX.e("Hook setRepeating 异常", e)
        }
    }

    /**
     * Hook setExact/setExactAndAllowWhileIdle
     * 高频精确闹钟降级为 setWindow，给系统合并空间
     */
    private fun hookSetExact(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        if (!cfg.alarmExactDowngrade) return

        try {
            val amCls = XposedHelpers.findClassIfExists(
                "android.app.AlarmManager", lpparam.classLoader
            ) ?: return

            // setExact(int type, long triggerAtMillis, PendingIntent operation)
            try {
                XposedHelpers.findAndHookMethod(
                    amCls, "setExact",
                    Int::class.javaPrimitiveType,
                    java.lang.Long.TYPE,
                    "android.app.PendingIntent",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val triggerAt = p.args[1] as Long
                            val windowLen = cfg.alarmMinIntervalMs / 2
                            // 改用 setWindow 让系统合并
                            try {
                                val am = p.thisObject
                                XposedHelpers.callMethod(
                                    am, "setWindow",
                                    p.args[0], triggerAt, windowLen, p.args[2]
                                )
                                LogX.w("setExact 降级为 setWindow: windowLen=$windowLen")
                            } catch (e: Exception) {
                                LogX.e("setExact 降级异常", e)
                            }
                            // 阻断原 setExact 调用
                            p.result = null
                        }
                    })
                LogX.hookSuccess("AlarmManager", "setExact")
            } catch (_: Exception) {}

            // setExactAndAllowWhileIdle（最耗电，优先降级）
            try {
                XposedHelpers.findAndHookMethod(
                    amCls, "setExactAndAllowWhileIdle",
                    Int::class.javaPrimitiveType,
                    java.lang.Long.TYPE,
                    "android.app.PendingIntent",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val triggerAt = p.args[1] as Long
                            val windowLen = cfg.alarmMinIntervalMs
                            try {
                                XposedHelpers.callMethod(
                                    p.thisObject, "setWindow",
                                    p.args[0], triggerAt, windowLen, p.args[2]
                                )
                                LogX.w("setExactAndAllowWhileIdle 降级为 setWindow")
                            } catch (e: Exception) {
                                LogX.e("setExactAndAllowWhileIdle 降级异常", e)
                            }
                            p.result = null
                        }
                    })
                LogX.hookSuccess("AlarmManager", "setExactAndAllowWhileIdle")
            } catch (_: Exception) {}
        } catch (e: Exception) {
            LogX.e("Hook setExact 异常", e)
        }
    }

    /**
     * Hook setWindow(int type, long windowStart, long windowLength, PendingIntent op)
     * 仅放大过小的 windowLength
     */
    private fun hookSetWindow(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        try {
            val amCls = XposedHelpers.findClassIfExists(
                "android.app.AlarmManager", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                amCls, "setWindow",
                Int::class.javaPrimitiveType,
                java.lang.Long.TYPE,
                java.lang.Long.TYPE,
                "android.app.PendingIntent",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val windowLen = p.args[2] as Long
                        if (windowLen < cfg.alarmMinIntervalMs / 2) {
                            val oldLen = windowLen
                            p.args[2] = cfg.alarmMinIntervalMs / 2
                            LogX.d("setWindow 窗口放大: $oldLen -> ${cfg.alarmMinIntervalMs / 2}")
                        }
                    }
                })
            LogX.hookSuccess("AlarmManager", "setWindow")
        } catch (e: Exception) {
            LogX.e("Hook setWindow 异常", e)
        }
    }
}
