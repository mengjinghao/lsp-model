package com.privacyguard.pro.hooks

import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.LogX
import com.privacyguard.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Magisk overlay持久化系统属性（Root 专属）
 *
 * 通过 Shizuku 执行系统级操作。
 * 硬性限制：需 Shizuku root 级授权
 */
object BuildPropSpoofHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.buildPropSpoofEnabled) return
        LogX.i("BuildPropSpoofHook 启动（Root 专属）")

        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku不可用，跳过BuildPropSpoofHook")
                            return
                        }
                        execute()
                        LogX.i("BuildPropSpoofHook 完成")
                    } catch (e: Throwable) {
                        LogX.w("BuildPropSpoofHook 异常: ${e.message}")
                    }
                }
            })
        LogX.hookSuccess("Application", "onCreate->BuildPropSpoofHook")
    }

    private fun execute() {
        // 通过 Magisk overlay 持久化伪装属性
        if (ShizukuHelper.createMagiskOverlay("privacyguard_spoof")) {
            ShizukuHelper.writeMagiskOverlay("privacyguard_spoof", "build.prop",
                "ro.product.model=Pixel 8 Pro\nro.product.brand=google\nro.product.manufacturer=Google\nro.serialno=FAKE12345678\n")
            LogX.d("Magisk overlay 已写入 build.prop 伪装")
        }
    }
}
