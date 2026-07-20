package com.notifymaster.pro.hooks

import com.notifymaster.pro.models.NotifyConfig
import com.notifymaster.pro.utils.LogX
import com.notifymaster.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 系统通知策略直接修改 Hook（Root 版独有）
 *
 * 通过 Shizuku 直接修改系统通知策略文件：
 *  - cp 备份 /data/system/notification_policy.xml
 *  - 使用 sed 或直接写入修改策略规则
 *  - cp 写回 /data/system/
 *  - chmod 600 设置权限
 *  - cmd notification refresh 刷新使生效
 *
 * 硬性限制：
 *  - 必须 ShizukuHelper.isShizukuAvailable()
 *  - 直接修改系统文件风险高
 *  - 全部 try-catch 保护
 */
object NotificationPolicyHook {

    private var isApplied = false

    private const val POLICY_XML = "/data/system/notification_policy.xml"
    private const val BACKUP_XML = "/data/local/tmp/notification_policy.xml"

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.notificationPolicyEditEnabled) {
            LogX.d("NotificationPolicyHook 未启用，跳过")
            return
        }
        if (isApplied) return

        LogX.i("NotificationPolicyHook 启动：系统通知策略直接修改")

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        isApplied = true
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku 不可用，跳过通知策略修改")
                            return
                        }
                        backupAndApplyPolicy()
                    }
                })
            LogX.hookSuccess("Application", "onCreate->NotificationPolicyHook")
        } catch (e: Throwable) {
            LogX.e("NotificationPolicyHook Application.onCreate Hook 异常", e)
        }
    }

    private fun backupAndApplyPolicy() {
        try {
            ShizukuHelper.execShell("cp $POLICY_XML $BACKUP_XML 2>&1")
            LogX.d("备份完成: $POLICY_XML -> $BACKUP_XML")
        } catch (e: Throwable) { LogX.w("备份 notification_policy.xml 异常: ${e.message}") }

        try {
            applyPolicyModifications()
        } catch (e: Throwable) { LogX.w("应用策略修改异常: ${e.message}") }

        try {
            ShizukuHelper.execShell("chmod 600 $POLICY_XML 2>&1")
            LogX.d("权限设置: chmod 600 $POLICY_XML")
        } catch (e: Throwable) { LogX.w("chmod 异常: ${e.message}") }

        try {
            ShizukuHelper.execShell("cmd notification refresh 2>&1")
            LogX.d("通知策略已刷新")
        } catch (e: Throwable) { LogX.w("cmd notification refresh 异常: ${e.message}") }

        LogX.i("NotificationPolicyHook: 系统通知策略已更新")
    }

    private fun applyPolicyModifications() {
        val policyContent = ShizukuHelper.readFile(BACKUP_XML) ?: return

        var modified = policyContent

        try {
            modified = modified.replace(
                Regex("<suppressedVisualEffects\\s+effects=\"\\d+\""),
                "<suppressedVisualEffects effects=\"0\""
            )
            LogX.d("已清除 suppressedVisualEffects")
        } catch (e: Throwable) { LogX.w("sed suppressedVisualEffects 异常: ${e.message}") }

        try {
            ShizukuHelper.execShell(
                "echo '${modified.replace("'", "'\\''")}' > $POLICY_XML 2>&1"
            )
            LogX.d("策略文件已写回: $POLICY_XML")
        } catch (e: Throwable) { LogX.w("写回策略文件异常: ${e.message}") }
    }

    fun restore() {
        try {
            if (!ShizukuHelper.isShizukuAvailable()) return
            ShizukuHelper.execShell("cp $BACKUP_XML $POLICY_XML 2>&1")
            ShizukuHelper.execShell("chmod 600 $POLICY_XML 2>&1")
            ShizukuHelper.execShell("cmd notification refresh 2>&1")
            LogX.i("NotificationPolicyHook: 已恢复原始策略")
        } catch (e: Throwable) { LogX.w("恢复通知策略异常: ${e.message}") }
    }

    fun release() {
        isApplied = false
    }
}
