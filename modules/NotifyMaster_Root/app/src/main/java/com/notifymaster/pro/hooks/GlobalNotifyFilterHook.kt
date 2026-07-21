package com.notifymaster.pro.hooks

import com.notifymaster.pro.models.NotifyConfig
import com.notifymaster.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 全局通知过滤 Hook（实验性 - Root 专属，跨 APP）
 *
 * 功能：通过 Hook NotificationListenerService.onNotificationPosted，对全局通知进行关键词过滤。
 *  - 命中关键词的通知：通过 Shizuku 执行 cmd notification cancel 移除
 *
 * 拦截路径：
 *  - Hook NotificationListenerService.onNotificationPosted
 *  - 在回调中提取通知文本，命中关键词则 cmd notification cancel <key>
 *
 * 硬性限制：
 *  - 需 LSPosed 加载到 system_server 或对应 NotificationListenerService 进程
 *  - cmd notification cancel 需要 Shizuku adb 级授权
 *  - 实验性：依赖系统 NotificationListenerService 实例存在
 */
object GlobalNotifyFilterHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.globalNotifyFilterEnabled) return
        LogX.i("全局通知过滤 Hook 启动（实验性，跨 APP）")

        hookListenerForFilter(lpparam, cfg)
    }

    private fun hookListenerForFilter(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        val listenerCls = XposedHelpers.findClassIfExists(
            "android.service.notification.NotificationListenerService",
            lpparam.classLoader) ?: run {
            LogX.w("NotificationListenerService 类未找到，全局过滤不可用")
            return
        }

        // onNotificationPosted(StatusBarNotification)
        try {
            XposedHelpers.findAndHookMethod(
                listenerCls, "onNotificationPosted",
                "android.service.notification.StatusBarNotification",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val sbn = p.args[0] ?: return
                            val pkg = XposedHelpers.callMethod(sbn, "getPackageName") as? String ?: return
                            val key = XposedHelpers.callMethod(sbn, "getKey") as? String ?: return
                            val notif = XposedHelpers.callMethod(sbn, "getNotification") ?: return

                            val text = extractNotificationText(notif) ?: return
                            if (shouldGlobalFilter(text, cfg)) {
                                LogX.d("[GlobalFilter] 命中全局关键词，移除通知: pkg=$pkg key=$key")
                                cancelNotificationViaShizuku(key)
                            }
                        } catch (e: Throwable) {
                            LogX.w("全局过滤 onNotificationPosted 异常: ${e.message}")
                        }
                    }
                })
            LogX.hookSuccess("NotificationListenerService", "onNotificationPosted[global-filter]")
        } catch (e: Exception) { LogX.w("全局过滤 onNotificationPosted Hook 失败: ${e.message}") }

        // onNotificationPosted(StatusBarNotification, RankingMap)
        try {
            XposedHelpers.findAndHookMethod(
                listenerCls, "onNotificationPosted",
                "android.service.notification.StatusBarNotification",
                "android.service.notification.NotificationListenerService.RankingMap",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val sbn = p.args[0] ?: return
                            val key = XposedHelpers.callMethod(sbn, "getKey") as? String ?: return
                            val notif = XposedHelpers.callMethod(sbn, "getNotification") ?: return
                            val text = extractNotificationText(notif) ?: return
                            if (shouldGlobalFilter(text, cfg)) {
                                LogX.d("[GlobalFilter] 命中关键词（带ranking），移除: key=$key")
                                cancelNotificationViaShizuku(key)
                            }
                        } catch (e: Throwable) {
                            LogX.w("全局过滤 onNotificationPosted(ranking) 异常: ${e.message}")
                        }
                    }
                })
            LogX.hookSuccess("NotificationListenerService", "onNotificationPosted(sbn, ranking)[global-filter]")
        } catch (e: Exception) { LogX.w("全局过滤 onNotificationPosted(ranking) Hook 失败: ${e.message}") }
    }

    private fun shouldGlobalFilter(text: String, cfg: NotifyConfig): Boolean {
        if (cfg.globalFilterKeywords.isEmpty()) return false
        return cfg.globalFilterKeywords.any { kw -> kw.isNotBlank() && text.contains(kw) }
    }

    private fun extractNotificationText(notif: Any): String? {
        return try {
            val sb = StringBuilder()
            try {
                val ticker = XposedHelpers.callMethod(notif, "getTickerText")
                if (ticker != null) sb.append(ticker.toString())
            } catch (_: Throwable) { }
            try {
                val extras = XposedHelpers.callMethod(notif, "getExtras")
                if (extras != null) {
                    val title = XposedHelpers.callMethod(extras, "getCharSequence", "android.title")
                    val text = XposedHelpers.callMethod(extras, "getCharSequence", "android.text")
                    if (title != null) sb.append(title.toString())
                    if (text != null) sb.append(text.toString())
                }
            } catch (_: Throwable) { }
            if (sb.isEmpty()) null else sb.toString()
        } catch (_: Throwable) { null }
    }

    /** 通过 Shizuku 执行 cmd notification cancel <key> */
    private fun cancelNotificationViaShizuku(key: String) {
        try {
            val shizukuCls = Class.forName("rikka.shizuku.Shizuku")
            val ping = shizukuCls.getMethod("pingBinder")
            val ok = ping.invoke(null) as? Boolean ?: false
            if (!ok) {
                LogX.w("[GlobalFilter] Shizuku 不可用，无法移除通知")
                return
            }
            // cmd notification cancel <key>
            val cmd = "cmd notification cancel $key"
            val newProcessMethod = shizukuCls.getMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", cmd), null, null)
            try {
                val waitFor = process?.javaClass?.getMethod("waitFor")
                waitFor?.invoke(process)
            } catch (_: Throwable) { }
            LogX.d("[GlobalFilter] 已执行 $cmd")
        } catch (e: Throwable) {
            LogX.w("[GlobalFilter] Shizuku 调用异常: ${e.message}")
        }
    }
}
