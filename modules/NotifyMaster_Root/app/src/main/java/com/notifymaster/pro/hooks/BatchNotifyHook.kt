package com.notifymaster.pro.hooks

import com.notifymaster.pro.models.NotifyConfig
import com.notifymaster.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.atomic.AtomicInteger

/**
 * 通知分组 Hook（实验性 - Root 版应用进程内）
 *
 * 功能：将同一 APP 发出的多条通知合并为一个分组（Notification Group）。
 */
object BatchNotifyHook {

    private val counter = AtomicInteger(0)

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.batchNotifyEnabled) return
        LogX.i("通知分组启动（实验性，group=${cfg.batchGroupKey}, max=${cfg.batchMaxCount}）")

        hookNotify(lpparam, cfg)
    }

    private fun hookNotify(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        val nmCls = XposedHelpers.findClassIfExists(
            "android.app.NotificationManager", lpparam.classLoader) ?: return

        val builderCls = XposedHelpers.findClassIfExists(
            "android.app.Notification\$Builder", lpparam.classLoader) ?: return
        try {
            XposedHelpers.findAndHookMethod(
                builderCls, "build",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val builder = p.thisObject ?: return
                            XposedHelpers.callMethod(builder, "setGroup", cfg.batchGroupKey)
                            XposedHelpers.callMethod(builder, "setGroupSummary", false)
                            val count = counter.incrementAndGet()
                            LogX.d("通知分组：已为通知设置 group=${cfg.batchGroupKey}, count=$count")
                        } catch (e: Throwable) {
                            LogX.w("通知分组 build 前异常: ${e.message}")
                        }
                    }
                })
            LogX.hookSuccess("Notification.Builder", "build[batch]")
        } catch (e: Exception) { LogX.hookFailed("Notification.Builder", "build[batch]", e) }

        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "notify",
                Int::class.javaPrimitiveType,
                "android.app.Notification",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        tryPostSummary(p.thisObject, cfg)
                    }
                })
            LogX.hookSuccess("NotificationManager", "notify(id, Notification)[batch]")
        } catch (e: Exception) { LogX.hookFailed("NotificationManager", "notify(id, Notification)[batch]", e) }
    }

    private fun tryPostSummary(nmObj: Any?, cfg: NotifyConfig) {
        if (nmObj == null) return
        val count = counter.get()
        if (count < cfg.batchMaxCount) return

        try {
            val builderCls = Class.forName("android.app.Notification\$Builder")
            LogX.d("通知分组：累计 $count 条，已超阈值 ${cfg.batchMaxCount}，准备刷新汇总（占位实现）")
        } catch (e: Throwable) {
            LogX.w("通知分组汇总异常: ${e.message}")
        }
    }

    fun reset() {
        counter.set(0)
    }
}
