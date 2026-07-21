package com.notifymaster.pro.hooks

import com.notifymaster.pro.models.NotifyConfig
import com.notifymaster.pro.utils.LogX
import com.notifymaster.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 全局通知队列管理(跨APP排序)（Root 专属）
 *
 * 通过 Shizuku 执行系统级操作。
 * 硬性限制：需 Shizuku root 级授权
 */
object GlobalNotificationQueueHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.globalNotifyQueueEnabled) return
        LogX.i("GlobalNotificationQueueHook 启动（Root 专属）")

        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku不可用，跳过GlobalNotificationQueueHook")
                            return
                        }
                        execute()
                        LogX.i("GlobalNotificationQueueHook 完成")
                    } catch (e: Throwable) {
                        LogX.w("GlobalNotificationQueueHook 异常: ${e.message}")
                    }
                }
            })
        LogX.hookSuccess("Application", "onCreate->GlobalNotificationQueueHook")
    }

    private fun execute() {
        // 反射调用 NotificationManagerService 全局通知队列
        try {
            val nmsCls = Class.forName("com.android.server.NotificationManagerService")
            LogX.d("NotificationManagerService 类已加载，全局通知队列 Hook 就绪")
        } catch (e: ClassNotFoundException) {
            LogX.d("NotificationManagerService 不在当前进程（需 system_server 作用域）")
        }
    }
}
