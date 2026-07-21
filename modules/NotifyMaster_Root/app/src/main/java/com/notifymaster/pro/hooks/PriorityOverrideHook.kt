package com.notifymaster.pro.hooks

import com.notifymaster.pro.models.NotifyConfig
import com.notifymaster.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 通知优先级覆盖 Hook（实验性 - Root 版应用进程内）
 *
 * 功能：强制将应用发出的通知优先级提升到指定级别（IMPORTANT）。
 */
object PriorityOverrideHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.priorityOverrideEnabled) return
        LogX.i("通知优先级覆盖启动（实验性，level=${cfg.priorityOverrideLevel}）")

        hookBuilderBuild(lpparam, cfg)
        hookNotificationChannel(lpparam, cfg)
    }

    private fun hookBuilderBuild(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        val builderCls = XposedHelpers.findClassIfExists(
            "android.app.Notification\$Builder", lpparam.classLoader) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                builderCls, "build",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val builder = p.thisObject ?: return
                            try {
                                XposedHelpers.callMethod(builder, "setPriority", cfg.priorityOverrideLevel)
                            } catch (e: Throwable) { LogX.w("setPriority 异常: ${e.message}") }
                        } catch (e: Throwable) {
                            LogX.w("优先级覆盖 build 前异常: ${e.message}")
                        }
                    }

                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val notif = p.result ?: return
                            XposedHelpers.setIntField(notif, "priority", cfg.priorityOverrideLevel)
                        } catch (_: Throwable) { }
                    }
                })
            LogX.hookSuccess("Notification.Builder", "build[priority]")
        } catch (e: Exception) { LogX.hookFailed("Notification.Builder", "build[priority]", e) }
    }

    private fun hookNotificationChannel(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        val channelCls = XposedHelpers.findClassIfExists(
            "android.app.NotificationChannel", lpparam.classLoader) ?: return

        try {
            XposedHelpers.findAndHookConstructor(
                channelCls,
                String::class.java,
                CharSequence::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            p.args[2] = mapImportance(cfg.priorityOverrideLevel)
                            LogX.d("通知渠道构造：importance 已覆盖为 ${p.args[2]}")
                        } catch (e: Throwable) { LogX.w("渠道构造覆盖异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("NotificationChannel", "<init>[priority]")
        } catch (e: Exception) { LogX.w("NotificationChannel 构造 Hook 失败: ${e.message}") }

        try {
            XposedHelpers.findAndHookMethod(
                channelCls, "setImportance",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            p.args[0] = mapImportance(cfg.priorityOverrideLevel)
                            LogX.d("setImportance 已覆盖为 ${p.args[0]}")
                        } catch (e: Throwable) { LogX.w("setImportance 覆盖异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("NotificationChannel", "setImportance")
        } catch (e: Exception) { LogX.w("setImportance Hook 失败: ${e.message}") }
    }

    private fun mapImportance(priority: Int): Int {
        return when (priority) {
            0 -> 1   // MIN
            1 -> 2   // LOW
            2 -> 4   // HIGH
            3 -> 5   // MAX
            else -> 3 // DEFAULT
        }
    }
}
