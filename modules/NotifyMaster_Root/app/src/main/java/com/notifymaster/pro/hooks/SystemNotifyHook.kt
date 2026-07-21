package com.notifymaster.pro.hooks

import android.app.Application
import android.content.Context
import com.notifymaster.pro.models.NotifyConfig
import com.notifymaster.pro.utils.LogX
import com.notifymaster.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 系统通知策略 Hook（Root 专属 - 需 Shizuku）
 *
 * 功能：
 *  1. Shizuku 调用 dumpsys notification 读取通知列表/策略（诊断）
 *  2. Shizuku 执行 settings put global 调整通知策略：
 *     - settings put global dnd_setting（勿扰）
 *     - settings put global zen_mode（禅模式）
 *     - settings put secure show_notification_snooze（隐藏 snooze）
 *  3. 可选绕过勿扰：让所有通知无视 DND 直接弹出
 *
 * 拦截路径：
 *  - Hook Application.onCreate 触发系统策略同步
 *
 * 硬性限制：
 *  - 必须先检查 ShizukuHelper.isShizukuAvailable()
 *  - settings put 修改非持久化，部分需重新设置
 *  - dumpsys 输出可能因 Android 版本而异
 */
object SystemNotifyHook {

    private var isApplied = false

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: NotifyConfig) {
        if (!cfg.systemNotifyHookEnabled) return
        if (isApplied) return
        isApplied = true

        LogX.i("系统通知策略 Hook 启动（Root 专属）")

        // Hook Application.onCreate 在 APP 启动后同步策略
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val app = p.thisObject as? Application ?: return
                            applySystemNotifyPolicy(app, cfg)
                        } catch (e: Throwable) {
                            LogX.w("系统通知策略同步异常: ${e.message}")
                        }
                    }
                })
            LogX.hookSuccess("Application", "onCreate[system-notify]")
        } catch (e: Exception) {
            LogX.hookFailed("Application", "onCreate[system-notify]", e)
        }

        // 立即同步一次（在 Hook 注册阶段，Shizuku 可能还没绑定，仅尝试）
        applySystemNotifyPolicy(null, cfg)
    }

    private fun applySystemNotifyPolicy(ctx: Context?, cfg: NotifyConfig) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过系统通知策略同步")
            return
        }

        // 1. dumpsys notification 输出（诊断信息）
        try {
            val dump = ShizukuHelper.execShell("dumpsys notification | head -50")
            if (dump != null) {
                LogX.d("系统通知 dumpsys 摘要:\n${dump.take(500)}")
            }
        } catch (e: Throwable) { LogX.w("dumpsys notification 异常: ${e.message}") }

        // 2. 绕过勿扰（如果开启）
        if (cfg.globalPolicyBypassEnabled) {
            try {
                // 关闭 Zen Mode
                ShizukuHelper.execShellSilent("settings put global zen_mode 0")
                // 允许通知绕过 DND
                ShizukuHelper.execShellSilent("settings put secure dnd_setting 0")
                LogX.i("已绕过勿扰策略（zen_mode=0）")
            } catch (e: Throwable) { LogX.w("绕过勿扰异常: ${e.message}") }
        }

        // 3. 全局最低优先级（设置 NotificationManagerService 配置）
        // Note: 系统层面无直接"最低优先级"全局参数，这里通过 settings put secure 配置
        if (cfg.globalImportanceFloor > 0) {
            try {
                ShizukuHelper.execShellSilent(
                    "settings put secure minimum_notification_importance ${cfg.globalImportanceFloor}"
                )
                LogX.d("已设置全局最低通知优先级 floor=${cfg.globalImportanceFloor}")
            } catch (e: Throwable) { LogX.w("最低优先级设置异常: ${e.message}") }
        }

        // 4. 显示通知 snooze 按钮
        try {
            ShizukuHelper.execShellSilent("settings put secure show_notification_snooze 1")
        } catch (_: Throwable) { }
    }

    fun release() {
        isApplied = false
    }
}
