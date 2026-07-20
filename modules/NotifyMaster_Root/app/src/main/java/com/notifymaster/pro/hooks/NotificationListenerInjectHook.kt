package com.notifymaster.pro.hooks

import com.notifymaster.pro.models.NotifyConfig
import com.notifymaster.pro.utils.LogX
import com.notifymaster.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 系统级通知监听器注入 Hook（Root 版独有）
 *
 * 通过 Shizuku 注入模块为系统批准的 Notification Listener：
 *  - cp 备份 /data/system/notification_listeners.xml
 *  - 编辑 XML 将模块添加为已批准监听器
 *  - cp 写回 /data/system/
 *  - cmd notification set_listener <component> 启用
 *
 * 硬性限制：
 *  - 必须 ShizukuHelper.isShizukuAvailable()
 *  - 直接修改系统文件，风险极高
 *  - 全部 try-catch 保护
 */
object NotificationListenerInjectHook {

    private var isApplied = false

    private const val LISTENERS_XML = "/data/system/notification_listeners.xml"
    private const val BACKUP_XML = "/data/local/tmp/notification_listeners.xml"

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.listenerInjectEnabled) {
            LogX.d("NotificationListenerInjectHook 未启用，跳过")
            return
        }
        if (isApplied) return

        LogX.i("NotificationListenerInjectHook 启动：系统级通知监听器注入")

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        isApplied = true
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku 不可用，跳过通知监听器注入")
                            return
                        }
                        backupAndInject()
                    }
                })
            LogX.hookSuccess("Application", "onCreate->NotificationListenerInjectHook")
        } catch (e: Throwable) {
            LogX.e("NotificationListenerInjectHook Application.onCreate Hook 异常", e)
        }
    }

    private fun backupAndInject() {
        try {
            ShizukuHelper.execShell("cp $LISTENERS_XML $BACKUP_XML 2>&1")
            LogX.d("备份完成: $LISTENERS_XML -> $BACKUP_XML")
        } catch (e: Throwable) { LogX.w("备份 notification_listeners.xml 异常: ${e.message}") }

        try {
            val currentXml = ShizukuHelper.readFile(BACKUP_XML) ?: return
            val listenerEntry = generateListenerEntry()
            val modified = if (currentXml.contains(listenerEntry)) {
                LogX.d("监听器已存在，跳过注入")
                return
            } else {
                currentXml.replace(
                    "</allowed_listeners>",
                    "    $listenerEntry\n</allowed_listeners>"
                )
            }
            ShizukuHelper.execShell(
                "echo '${modified.replace("'", "'\\''")}' > $LISTENERS_XML 2>&1"
            )
            LogX.d("监听器已写入: $LISTENERS_XML")
        } catch (e: Throwable) { LogX.w("写入监听器异常: ${e.message}") }

        try {
            val component = "com.notifymaster.pro/.service.NotificationListenerService"
            val result = ShizukuHelper.execShell("cmd notification set_listener $component 2>&1")
            LogX.d("cmd notification set_listener -> $result")
        } catch (e: Throwable) { LogX.w("cmd notification set_listener 异常: ${e.message}") }

        LogX.i("NotificationListenerInjectHook: 监听器已注入")
    }

    private fun generateListenerEntry(): String {
        return "<listener package_name=\"com.notifymaster.pro\" " +
                "class_name=\"com.notifymaster.pro.service.NotificationListenerService\" " +
                "user_id=\"0\"/>"
    }

    fun enableListener(component: String): Boolean {
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) return false
            ShizukuHelper.execShell("cmd notification set_listener $component 2>&1") != null
        } catch (e: Throwable) {
            LogX.e("cmd notification set_listener 异常: $component", e)
            false
        }
    }

    fun disableListener(component: String): Boolean {
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) return false
            ShizukuHelper.execShell("cmd notification remove_listener $component 2>&1") != null
        } catch (e: Throwable) {
            LogX.e("cmd notification remove_listener 异常: $component", e)
            false
        }
    }

    fun restore() {
        try {
            if (!ShizukuHelper.isShizukuAvailable()) return
            ShizukuHelper.execShell("cp $BACKUP_XML $LISTENERS_XML 2>&1")
            ShizukuHelper.execShell("cmd notification refresh 2>&1")
            LogX.i("NotificationListenerInjectHook: 已恢复原始监听器配置")
        } catch (e: Throwable) { LogX.w("恢复监听器配置异常: ${e.message}") }
    }

    fun release() {
        isApplied = false
    }
}
