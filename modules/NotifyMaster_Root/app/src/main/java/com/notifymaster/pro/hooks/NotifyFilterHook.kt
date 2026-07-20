package com.notifymaster.pro.hooks

import com.notifymaster.pro.models.NotifyConfig
import com.notifymaster.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 通知过滤 Hook（Root 版 - 应用进程内 + 全局过滤可选）
 *
 * 功能：根据关键词列表过滤通知，命中关键词的通知不调用 super.notify（直接 return）。
 *
 * 拦截路径：
 *  1. NotificationManager.notify(int id, Notification n)
 *  2. NotificationManager.notify(String tag, int id, Notification n)
 *  3. NotificationManager.notifyAsUser(String tag, int id, Notification n, UserHandle user)
 *
 * Root 版同时支持 GlobalNotifyFilterHook 提供的跨 APP 全局过滤。
 */
object NotifyFilterHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.notifyFilterEnabled) return
        LogX.i("通知过滤启动（关键词命中即拦截）")

        hookNotify(lpparam, cfg)
    }

    private fun hookNotify(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        val nmCls = XposedHelpers.findClassIfExists(
            "android.app.NotificationManager", lpparam.classLoader) ?: return

        // notify(int, Notification)
        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "notify",
                Int::class.javaPrimitiveType,
                "android.app.Notification",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val notif = p.args[1] ?: return
                        if (shouldFilter(notif, cfg)) {
                            LogX.d("通知过滤命中，已拦截 notify(id, Notification)")
                            p.result = null
                        }
                    }
                })
            LogX.hookSuccess("NotificationManager", "notify(id, Notification)")
        } catch (e: Exception) { LogX.hookFailed("NotificationManager", "notify(id, Notification)", e) }

        // notify(String tag, int id, Notification)
        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "notify",
                String::class.java,
                Int::class.javaPrimitiveType,
                "android.app.Notification",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val notif = p.args[2] ?: return
                        if (shouldFilter(notif, cfg)) {
                            LogX.d("通知过滤命中，已拦截 notify(tag, id, Notification)")
                            p.result = null
                        }
                    }
                })
            LogX.hookSuccess("NotificationManager", "notify(tag, id, Notification)")
        } catch (e: Exception) { LogX.hookFailed("NotificationManager", "notify(tag, id, Notification)", e) }

        // notifyAsUser(String tag, int id, Notification, UserHandle)
        try {
            XposedHelpers.findAndHookMethod(
                nmCls, "notifyAsUser",
                String::class.java,
                Int::class.javaPrimitiveType,
                "android.app.Notification",
                "android.os.UserHandle",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val notif = p.args[2] ?: return
                        if (shouldFilter(notif, cfg)) {
                            LogX.d("通知过滤命中，已拦截 notifyAsUser")
                            p.result = null
                        }
                    }
                })
            LogX.hookSuccess("NotificationManager", "notifyAsUser")
        } catch (e: Exception) { LogX.w("notifyAsUser 不存在或 Hook 失败: ${e.message}") }
    }

    /** 检查通知文本是否命中关键词 */
    private fun shouldFilter(notif: Any, cfg: NotifyConfig): Boolean {
        if (cfg.filterKeywords.isEmpty()) return false
        val text = extractNotificationText(notif) ?: return false
        return cfg.filterKeywords.any { kw -> kw.isNotBlank() && text.contains(kw) }
    }

    /** 提取通知文本（title + text + ticker） */
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
                    val bigText = XposedHelpers.callMethod(extras, "getCharSequence", "android.bigText")
                    if (title != null) sb.append(title.toString())
                    if (text != null) sb.append(text.toString())
                    if (bigText != null) sb.append(bigText.toString())
                }
            } catch (_: Throwable) { }

            if (sb.isEmpty()) null else sb.toString()
        } catch (_: Throwable) { null }
    }
}
