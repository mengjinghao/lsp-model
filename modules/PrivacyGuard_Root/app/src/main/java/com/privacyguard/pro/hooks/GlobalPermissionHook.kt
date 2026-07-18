package com.privacyguard.pro.hooks

import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.LogX
import com.privacyguard.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 全局权限控制Hook（Root 专属，需 Shizuku adb 级授权）
 *
 * 功能：
 *  - 通过 Shizuku pm revoke 全局回收指定 APP 的危险权限
 *  - 通过 Shizuku pm grant 恢复权限
 *  - Hook PackageManager.queryIntentActivities 隐藏特定 Intent（如某些自启动入口）
 *
 * 硬性限制：
 *  - 必须先检查 ShizukuHelper.isShizukuAvailable()
 *  - pm revoke 是真实回收权限，会影响 APP 所有功能（与 PermissionSpoofHook 不同）
 *  - 谨慎使用，可能导致 APP 部分功能不可用
 *
 * 与 PermissionSpoofHook 的区别：
 *  - PermissionSpoofHook: 仅欺骗 APP 自身检查，不真的修改系统授权（应用层）
 *  - GlobalPermissionHook: 真的回收权限，影响系统全局（系统级）
 */
object GlobalPermissionHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.globalPermissionControlEnabled) return
        LogX.i("全局权限控制启动（Root 专属）")

        // 1. 通过 Shizuku pm revoke 回收权限（针对当前APP包名）
        revokePermissionsViaShizuku(cfg)

        // 2. Hook PackageManager.queryIntentActivities 隐藏特定 Intent
        if (cfg.hiddenIntents.isNotEmpty()) {
            hookQueryIntentActivities(lpparam, cfg.hiddenIntents.toSet())
        }
    }

    /**
     * 通过 Shizuku 执行 pm revoke 回收权限
     * 注意：pm revoke 是真实回收，会影响 APP 所有功能
     */
    private fun revokePermissionsViaShizuku(cfg: PrivacyConfig) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku不可用，跳过 pm revoke 权限回收")
            return
        }

        val pkg = cfg.packageName
        if (pkg.isEmpty()) {
            LogX.w("包名为空，跳过权限回收")
            return
        }

        var success = 0
        for (perm in cfg.revokedPermissions) {
            val result = ShizukuHelper.execShell("pm revoke $pkg $perm")
            if (result != null) {
                success++
                LogX.d("已回收权限: $pkg $perm")
            }
        }

        LogX.i("Shizuku pm revoke 完成: $success/${cfg.revokedPermissions.size} 个权限回收成功")
    }

    /**
     * Hook PackageManager.queryIntentActivities
     * 隐藏配置中指定的 Intent（如某些自启动入口、广告跳转）
     */
    private fun hookQueryIntentActivities(
        lpparam: XC_LoadPackage.LoadPackageParam, hiddenIntents: Set<String>) {
        try {
            val pm = XposedHelpers.findClassIfExists(
                "android.content.pm.PackageManager", lpparam.classLoader) ?: return

            // queryIntentActivities(Intent, int)
            try {
                XposedHelpers.findAndHookMethod(pm, "queryIntentActivities",
                    "android.content.Intent", Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val intent = p.args[0] ?: return
                            val action = XposedHelpers.callMethod(intent, "getAction") as? String
                            if (action != null && hiddenIntents.any { action.equals(it, ignoreCase = true) }) {
                                p.result = java.util.ArrayList<Any>()
                                LogX.d("隐藏 Intent: $action")
                            }
                        }
                    })
                LogX.hookSuccess("PackageManager", "queryIntentActivities(Intent, int)")
            } catch (_: Exception) {}

            // queryIntentActivities(Intent, ResolveInfoFlags) Android 13+
            try {
                XposedHelpers.findAndHookMethod(pm, "queryIntentActivities",
                    "android.content.Intent", "android.content.pm.PackageManager\$ResolveInfoFlags",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val intent = p.args[0] ?: return
                            val action = XposedHelpers.callMethod(intent, "getAction") as? String
                            if (action != null && hiddenIntents.any { action.equals(it, ignoreCase = true) }) {
                                p.result = java.util.ArrayList<Any>()
                            }
                        }
                    })
                LogX.hookSuccess("PackageManager", "queryIntentActivities(Intent, Flags)")
            } catch (_: Exception) {}
        } catch (e: Exception) {
            LogX.hookFailed("PackageManager", "queryIntentActivities", e)
        }
    }
}
