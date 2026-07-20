package com.batteryopt.pro.hooks

import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import com.batteryopt.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * AppOps 系统级后台限制 Hook（Root 版独有）
 *
 * 通过 Shizuku 使用 cmd appops 进行系统级后台限制：
 *  - cmd appops set <pkg> RUN_IN_BACKGROUND deny
 *  - cmd appops set <pkg> WAKE_LOCK deny
 *  - cmd appops set <pkg> START_FOREGROUND deny
 *  - am set-standby-bucket <pkg> rare
 *  - 系统级后台运行限制，电池消耗降低
 *
 * 硬性限制：
 *  - 必须 ShizukuHelper.isShizukuAvailable()
 *  - 全部 try-catch 保护
 */
object AppOpsManagerHook {

    private var isApplied = false

    private val batteryHogPkgs = listOf(
        "com.tencent.mm",
        "com.tencent.mobileqq",
        "com.ss.android.ugc.aweme",
        "com.smile.gifmaker",
        "com.taobao.taobao",
        "com.jingdong.app.mall",
        "com.xunmeng.pinduoduo",
        "com.eg.android.AlipayGphone"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        if (!cfg.appOpsManagerEnabled) {
            LogX.d("AppOpsManagerHook 未启用，跳过")
            return
        }
        if (isApplied) return

        LogX.i("AppOpsManagerHook 启动：AppOps 系统级后台限制")

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        isApplied = true
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku 不可用，跳过 AppOps 后台限制")
                            return
                        }
                        applyBatteryRestrictions(cfg)
                    }
                })
            LogX.hookSuccess("Application", "onCreate->AppOpsManagerHook")
        } catch (e: Throwable) {
            LogX.e("AppOpsManagerHook Application.onCreate Hook 异常", e)
        }
    }

    private fun applyBatteryRestrictions(cfg: BatteryConfig) {
        for (pkg in batteryHogPkgs) {
            try {
                val r1 = ShizukuHelper.execShell("cmd appops set $pkg RUN_IN_BACKGROUND deny 2>&1")
                LogX.d("$pkg RUN_IN_BACKGROUND deny -> $r1")
            } catch (e: Throwable) { LogX.w("$pkg RUN_IN_BACKGROUND 异常: ${e.message}") }

            try {
                val r2 = ShizukuHelper.execShell("cmd appops set $pkg WAKE_LOCK deny 2>&1")
                LogX.d("$pkg WAKE_LOCK deny -> $r2")
            } catch (e: Throwable) { LogX.w("$pkg WAKE_LOCK 异常: ${e.message}") }

            try {
                val r3 = ShizukuHelper.execShell("cmd appops set $pkg START_FOREGROUND deny 2>&1")
                LogX.d("$pkg START_FOREGROUND deny -> $r3")
            } catch (e: Throwable) { LogX.w("$pkg START_FOREGROUND 异常: ${e.message}") }

            try {
                val r4 = ShizukuHelper.execShell("am set-standby-bucket $pkg rare 2>&1")
                LogX.d("$pkg standby-bucket rare -> $r4")
            } catch (e: Throwable) { LogX.w("$pkg standby-bucket 异常: ${e.message}") }
        }

        LogX.i("AppOpsManagerHook: 后台限制已应用 (${batteryHogPkgs.size} 个 APP)")
    }

    fun restrictApp(pkg: String): Boolean {
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) return false
            ShizukuHelper.execShell("cmd appops set $pkg RUN_IN_BACKGROUND deny 2>&1")
            ShizukuHelper.execShell("cmd appops set $pkg WAKE_LOCK deny 2>&1")
            ShizukuHelper.execShell("am set-standby-bucket $pkg rare 2>&1")
            LogX.d("已限制: $pkg")
            true
        } catch (e: Throwable) {
            LogX.e("限制 APP 异常: $pkg", e)
            false
        }
    }

    fun unrestrictApp(pkg: String): Boolean {
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) return false
            ShizukuHelper.execShell("cmd appops set $pkg RUN_IN_BACKGROUND allow 2>&1")
            ShizukuHelper.execShell("cmd appops set $pkg WAKE_LOCK allow 2>&1")
            ShizukuHelper.execShell("cmd appops set $pkg START_FOREGROUND allow 2>&1")
            LogX.d("已解除限制: $pkg")
            true
        } catch (e: Throwable) {
            LogX.e("解除限制 APP 异常: $pkg", e)
            false
        }
    }

    fun getAppOps(pkg: String, op: String): String? {
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) return null
            ShizukuHelper.execShell("cmd appops get $pkg $op 2>&1")
        } catch (e: Throwable) { null }
    }

    fun release() {
        isApplied = false
    }
}
