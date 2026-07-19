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
 *  - 通过 Shizuku pm revoke 真实回收指定 APP 危险权限
 *  - Hook PackageManager.queryIntentActivities 隐藏特定 Intent
 *
 * 与 PermissionSpoofHook 区别：
 *  - PermissionSpoofHook: 仅欺骗 APP 自身检查（应用层）
 *  - GlobalPermissionHook: 真的回收权限，影响系统全局（系统级）
 */
object GlobalPermissionHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.globalPermissionHookEnabled) return
        LogX.i("全局权限控制启动（Root 专属）")

        revokePermissionsViaShizuku(cfg)

        if (cfg.hiddenIntents.isNotEmpty()) {
            hookQueryIntentActivities(lpparam, cfg.hiddenIntents.toSet())
        }
    }

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

    private fun hookQueryIntentActivities(
        lpparam: XC_LoadPackage.LoadPackageParam, hiddenIntents: Set<String>) {
        try {
            val pm = XposedHelpers.findClassIfExists(
                "android.content.pm.PackageManager", lpparam.classLoader) ?: return

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
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

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
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("PackageManager", "queryIntentActivities", e)
        }
    }
}
