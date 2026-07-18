package com.privacyguard.pro.hooks

import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 权限欺骗Hook（应用层）
 *
 * 功能：
 *  - Hook ContextWrapper.checkSelfPermission / PackageManager.checkPermission
 *  - 对配置中选定的危险权限返回 PERMISSION_DENIED
 *  - 仅欺骗 APP 自身的检查，不真的修改系统授权
 *
 * 注意：Root 版可配合 GlobalPermissionHook 真的回收权限（pm revoke）
 */
object PermissionSpoofHook {

    private const val PERMISSION_DENIED = -1

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.permissionSpoofEnabled) return
        if (cfg.deniedPermissions.isEmpty()) {
            LogX.d("权限欺骗开启但未配置拒绝列表，跳过")
            return
        }
        LogX.i("权限欺骗启动（应用层）：拒绝 ${cfg.deniedPermissions.size} 个权限")

        val deniedSet = cfg.deniedPermissions.toSet()

        hookContextWrapperCheckPermission(lpparam, deniedSet)
        hookPackageManagerCheckPermission(lpparam, deniedSet)
        hookContextCompatCheckPermission(lpparam, deniedSet)
    }

    private fun hookContextWrapperCheckPermission(
        lpparam: XC_LoadPackage.LoadPackageParam, denied: Set<String>) {
        try {
            val cw = XposedHelpers.findClassIfExists(
                "android.content.ContextWrapper", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(cw, "checkSelfPermission",
                    String::class.java, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val perm = p.args[0] as? String ?: return
                            if (denied.any { perm.equals(it, ignoreCase = true) }) {
                                p.result = PERMISSION_DENIED
                                LogX.d("权限欺骗: $perm -> DENIED")
                            }
                        }
                    })
                LogX.hookSuccess("ContextWrapper", "checkSelfPermission")
            } catch (_: Exception) {}

            try {
                XposedHelpers.findAndHookMethod(cw, "checkPermission",
                    String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val perm = p.args[0] as? String ?: return
                            if (denied.any { perm.equals(it, ignoreCase = true) }) {
                                p.result = PERMISSION_DENIED
                            }
                        }
                    })
                LogX.hookSuccess("ContextWrapper", "checkPermission")
            } catch (_: Exception) {}
        } catch (e: Exception) {
            LogX.hookFailed("ContextWrapper", "checkSelfPermission", e)
        }
    }

    private fun hookPackageManagerCheckPermission(
        lpparam: XC_LoadPackage.LoadPackageParam, denied: Set<String>) {
        try {
            val pm = XposedHelpers.findClassIfExists(
                "android.content.pm.PackageManager", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(pm, "checkPermission",
                    String::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val perm = p.args[0] as? String ?: return
                            if (denied.any { perm.equals(it, ignoreCase = true) }) {
                                p.result = PERMISSION_DENIED
                            }
                        }
                    })
                LogX.hookSuccess("PackageManager", "checkPermission")
            } catch (_: Exception) {}
        } catch (e: Exception) {
            LogX.hookFailed("PackageManager", "checkPermission", e)
        }
    }

    private fun hookContextCompatCheckPermission(
        lpparam: XC_LoadPackage.LoadPackageParam, denied: Set<String>) {
        try {
            val cc = XposedHelpers.findClassIfExists(
                "androidx.core.content.ContextCompat", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(cc, "checkSelfPermission",
                    "android.content.Context", String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val perm = p.args[1] as? String ?: return
                            if (denied.any { perm.equals(it, ignoreCase = true) }) {
                                p.result = PERMISSION_DENIED
                            }
                        }
                    })
                LogX.hookSuccess("ContextCompat", "checkSelfPermission")
            } catch (_: Exception) {}
        } catch (e: Exception) {
            LogX.d("ContextCompat 未找到，跳过 androidx 兼容Hook")
        }
    }
}
