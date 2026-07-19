package com.privacyguard.pro.hooks

import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.LogX
import com.privacyguard.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku 桥接Hook（Root 专属，需 Shizuku adb 级授权）
 *
 * 功能：
 *  - Shizuku settings put secure 重置广告 ID
 *  - Shizuku pm clear 清理 APP 追踪数据（缓存/数据库）
 *  - Hook Application.onCreate 在 APP 启动后清理 adid 缓存
 *
 * 硬性限制：
 *  - 必须先检查 ShizukuHelper.isShizukuAvailable()
 *  - pm clear 会清除 APP 所有数据（包括登录态），谨慎使用
 *  - settings put secure 修改的是系统级设置，影响全局
 */
object ShizukuBridgeHook {

    private var isApplied = false

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.shizukuBridgeEnabled) return
        if (isApplied) return
        isApplied = true

        LogX.i("Shizuku 桥接启动（Root 专属）")

        resetAdvertisingId()

        if (cfg.clearAppTrackingData) {
            clearAppTrackingData(cfg.packageName)
        }

        hookAppLifecycleForCleanup(lpparam, cfg.packageName)
    }

    private fun resetAdvertisingId() {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku不可用，跳过广告 ID 重置")
            return
        }

        ShizukuHelper.execShell("settings put secure advertising_id_temp \"\"")
        ShizukuHelper.execShell("settings put secure authorized_service_enabled 0")

        LogX.i("Shizuku 广告 ID 已重置")
    }

    private fun clearAppTrackingData(pkg: String) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku不可用，跳过追踪数据清理")
            return
        }
        if (pkg.isEmpty()) return

        val result = ShizukuHelper.execShell("pm clear $pkg")
        if (result != null) {
            LogX.w("Shizuku 已执行 pm clear: $pkg（注意：登录态会丢失）")
        }
    }

    private fun hookAppLifecycleForCleanup(lpparam: XC_LoadPackage.LoadPackageParam, pkg: String) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        if (ShizukuHelper.isShizukuAvailable()) {
                            try {
                                ShizukuHelper.execShell(
                                    "rm -f /data/data/$pkg/cache/*.adid")
                                ShizukuHelper.execShell(
                                    "rm -f /data/data/$pkg/shared_prefs/adid_prefs.xml")
                                LogX.d("已清理 $pkg 的广告ID缓存")
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    }
                })
            LogX.hookSuccess("Application", "onCreate(cleanup)")
        } catch (e: Exception) {
            LogX.hookFailed("Application", "onCreate", e)
        }
    }

    fun release() {
        ShizukuHelper.release()
        isApplied = false
        LogX.d("Shizuku 桥接资源已释放")
    }
}
