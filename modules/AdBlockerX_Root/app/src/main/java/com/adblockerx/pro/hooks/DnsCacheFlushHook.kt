package com.adblockerx.pro.hooks

import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.LogX
import com.adblockerx.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku刷新系统DNS缓存（Root 专属）
 *
 * 通过 Shizuku 执行系统级操作。
 * 硬性限制：需 Shizuku root 级授权
 */
object DnsCacheFlushHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.dnsCacheFlushEnabled) return
        LogX.i("DnsCacheFlushHook 启动（Root 专属）")

        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku不可用，跳过DnsCacheFlushHook")
                            return
                        }
                        execute()
                        LogX.i("DnsCacheFlushHook 完成")
                    } catch (e: Throwable) {
                        LogX.w("DnsCacheFlushHook 异常: ${e.message}")
                    }
                }
            })
        LogX.hookSuccess("Application", "onCreate->DnsCacheFlushHook")
    }

    private fun execute() {
        // 刷新系统 DNS 缓存
        ShizukuHelper.execShellSilent("ndc resolver flushdefaultif")
        ShizukuHelper.execShellSilent("settings put global private_dns_specifier dns.adguard.com")
        LogX.d("DNS 缓存已刷新，Private DNS 已设置")
    }
}
