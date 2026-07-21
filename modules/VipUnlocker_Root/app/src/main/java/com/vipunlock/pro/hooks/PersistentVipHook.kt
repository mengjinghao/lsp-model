package com.vipunlock.pro.hooks

import com.vipunlock.pro.models.VipConfig
import com.vipunlock.pro.utils.LogX
import com.vipunlock.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 持久化VIP状态到Magisk overlay（Root 专属）
 *
 * 通过 Shizuku 执行系统级操作。
 * 硬性限制：需 Shizuku root 级授权
 */
object PersistentVipHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.persistentVipEnabled) return
        LogX.i("PersistentVipHook 启动（Root 专属）")

        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku不可用，跳过PersistentVipHook")
                            return
                        }
                        execute()
                        LogX.i("PersistentVipHook 完成")
                    } catch (e: Throwable) {
                        LogX.w("PersistentVipHook 异常: ${e.message}")
                    }
                }
            })
        LogX.hookSuccess("Application", "onCreate->PersistentVipHook")
    }

    private fun execute() {
        // 持久化 VIP 状态到 Magisk overlay
        if (ShizukuHelper.createMagiskOverlay("vipunlock")) {
            ShizukuHelper.writeMagiskOverlay("vipunlock", "etc/vip_status.conf",
                "vip_enabled=1\nunlock_time=" + System.currentTimeMillis() + "\n")
            LogX.d("VIP 状态已持久化到 Magisk overlay")
        }
    }
}
