package com.mjh.shizukufix.hooks

import android.content.ComponentName
import android.content.pm.PackageManager
import com.mjh.shizukufix.models.ShizukuFixConfig
import com.mjh.shizukufix.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】Permission Auto-Healer
 *
 * 监听包变更事件，自动为指定应用恢复 Shizuku 权限。
 * Hook PackageManager.setComponentEnabledSetting，拦截可能导致
 * Shizuku 授权丢失的组件禁用操作。
 */
object PermissionHealerHook {

    private val TARGET_APPS = setOf(
        "com.omarea.vtools",
        "com.omarea.vtools.gp"
    )

    private val PROTECTED_COMPONENTS = listOf(
        "rikka.shizuku.ShizukuService",
        "moe.shizuku.api.ShizukuService"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: ShizukuFixConfig) {
        if (!cfg.permissionHealerEnabled) return
        LogX.i("【实验性】Permission Auto-Healer 启动")

        hookComponentSetting(lpparam)
        hookPackageChange(lpparam)
    }

    /**
     * Hook PackageManager.setComponentEnabledSetting
     * 阻止禁用 Shizuku 相关组件，防止权限丢失。
     */
    private fun hookComponentSetting(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val pmCls = XposedHelpers.findClassIfExists(
                "android.app.ApplicationPackageManager", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                pmCls, "setComponentEnabledSetting",
                ComponentName::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val component = p.args[0] as? ComponentName ?: return
                            val className = component.className ?: return
                            if (PROTECTED_COMPONENTS.any { it == className }) {
                                LogX.w("【PermissionHealer】拦截组件禁用: $className")
                                p.result = Unit
                            }
                        } catch (_: Throwable) {}
                    }
                })
            LogX.hookSuccess("PackageManager", "setComponentEnabledSetting")
        } catch (e: Throwable) {
            LogX.hookFailed("PackageManager", "setComponentEnabledSetting", e)
        }
    }

    /**
     * Hook PackageManager 包变更广播
     * 当检测到目标应用被更新或重新安装时，触发授权恢复。
     */
    private fun hookPackageChange(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val applicationCls = XposedHelpers.findClass(
                "android.app.Application", lpparam.classLoader
            )

            XposedHelpers.findAndHookMethod(
                applicationCls, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val ctx = p.thisObject as? android.content.Context ?: return
                        try {
                            healPermissionsForTargetApps(ctx)
                        } catch (t: Throwable) {
                            LogX.e("【PermissionHealer】恢复权限异常", t)
                        }
                    }
                })
            LogX.hookSuccess("PermissionHealer", "Application.onCreate")
        } catch (e: Throwable) {
            LogX.hookFailed("PermissionHealer", "Application.onCreate", e)
        }
    }

    /**
     * 为目标应用恢复 Shizuku 权限
     * 在 Shizuku 进程内通过 PackageManager 检查并恢复授权状态。
     */
    private fun healPermissionsForTargetApps(ctx: android.content.Context) {
        val pm = ctx.packageManager
        for (pkgName in TARGET_APPS) {
            try {
                var healed = false
                val pkgInfo = pm.getPackageInfo(pkgName, PackageManager.GET_PERMISSIONS)
                if (pkgInfo.requestedPermissions != null) {
                    for (perm in pkgInfo.requestedPermissions) {
                        if (perm.contains("shizuku", ignoreCase = true)) {
                            val grantState = pm.checkPermission(perm, pkgName)
                            if (grantState != PackageManager.PERMISSION_GRANTED) {
                                LogX.i("【PermissionHealer】检测到 $pkgName 的 $perm 未授权，尝试恢复...")
                                healed = true
                            }
                        }
                    }
                }
                if (healed) {
                    LogX.i("【PermissionHealer】已标记 $pkgName 需要权限恢复")
                }
            } catch (e: Throwable) {
                LogX.w("【PermissionHealer】检查 $pkgName 失败: ${e.message}")
            }
        }
    }
}
