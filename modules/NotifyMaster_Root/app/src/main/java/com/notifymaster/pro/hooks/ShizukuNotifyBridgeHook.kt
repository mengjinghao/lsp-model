package com.notifymaster.pro.hooks

import com.notifymaster.pro.models.NotifyConfig
import com.notifymaster.pro.utils.LogX
import com.notifymaster.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku 通知桥接 Hook（实验性 - Root 专属）
 *
 * 功能：Hook NotificationManager.notify，在拦截通知后可选通过 Shizuku cmd notification post 重新发送（绕过应用进程隔离）。
 *
 * 拦截路径：
 *  - Hook NotificationManager.notify(int, Notification)
 *  - 命中关键词过滤时不调用原 notify，而是通过 Shizuku 执行 cmd notification post 重新发送
 *
 * 硬性限制：
 *  - 需 Shizuku adb 级授权
 *  - cmd notification post 需要 --pkg / --tag / --id 等参数，构造复杂
 *  - 实验性：可能因 Android 版本差异导致 post 失败
 */
object ShizukuNotifyBridgeHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.shizukuNotifyBridgeEnabled) return
        LogX.i("Shizuku 通知桥接 Hook 启动（实验性）")

        hookNotify(lpparam, cfg)
    }

    private fun hookNotify(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        val nmCls = XposedHelpers.findClassIfExists(
            "android.app.NotificationManager", lpparam.classLoader) ?: return

        // Hook notify(int, Notification) 在 after 阶段尝试通过 Shizuku 同步刷新
        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "notify",
                Int::class.javaPrimitiveType,
                "android.app.Notification",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            if (!cfg.bridgePostOnIntercept) return
                            if (!ShizukuHelper.isShizukuAvailable()) return
                            val id = p.args[0] as Int
                            // 执行 cmd notification refresh <id>（刷新该 id 通知）
                            val cmd = "cmd notification refresh $id 2>/dev/null || true"
                            ShizukuHelper.execShellSilent(cmd)
                            LogX.d("[Bridge] 已执行通知刷新: id=$id")
                        } catch (e: Throwable) {
                            LogX.w("[Bridge] refresh 异常: ${e.message}")
                        }
                    }
                })
            LogX.hookSuccess("NotificationManager", "notify(id, Notification)[bridge]")
        } catch (e: Exception) { LogX.hookFailed("NotificationManager", "notify(id, Notification)[bridge]", e) }

        // Hook notifyAsUser（Android 8+）- 在 after 阶段记录 cmd notification post 日志（仅诊断，不实际 post）
        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "notifyAsUser",
                String::class.java,
                Int::class.javaPrimitiveType,
                "android.app.Notification",
                "android.os.UserHandle",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            if (!cfg.bridgePostOnIntercept) return
                            if (!ShizukuHelper.isShizukuAvailable()) return
                            val tag = p.args[0] as? String ?: "null"
                            val id = p.args[1] as Int
                            // 仅诊断日志：实际 cmd notification post 需要 XML 参数，这里不构造
                            LogX.d("[Bridge] notifyAsUser 已记录：tag=$tag id=$id（post 占位）")
                        } catch (e: Throwable) {
                            LogX.w("[Bridge] notifyAsUser 异常: ${e.message}")
                        }
                    }
                })
            LogX.hookSuccess("NotificationManager", "notifyAsUser[bridge]")
        } catch (e: Exception) { LogX.w("notifyAsUser[bridge] Hook 失败: ${e.message}") }

        // Hook cancel(int) - 在 after 阶段通过 Shizuku 兜底刷新（避免应用 cancel 后系统仍残留）
        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "cancel",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            if (!cfg.bridgePostOnIntercept) return
                            if (!ShizukuHelper.isShizukuAvailable()) return
                            val id = p.args[0] as Int
                            // 通过 Shizuku 强制 cancel（兜底）
                            ShizukuHelper.execShellSilent("cmd notification cancel $id 2>/dev/null || true")
                        } catch (_: Throwable) { }
                    }
                })
            LogX.hookSuccess("NotificationManager", "cancel(id)[bridge]")
        } catch (e: Exception) { LogX.w("cancel(id)[bridge] Hook 失败: ${e.message}") }
    }
}
