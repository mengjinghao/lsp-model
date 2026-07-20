package com.batteryopt.noroot.hooks

import com.batteryopt.noroot.models.BatteryConfig
import com.batteryopt.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Screen Dimmer（实验性，NoRoot 版）
 *
 * NoRoot 版：Hook WindowManager 降低屏幕亮度至系统最低以下
 * - Hook WindowManager.LayoutParams.screenBrightness 覆写为 dimLevel
 * - 仅修改当前 APP 窗口亮度，不影响全局系统亮度
 */
object ScreenDimmerHook {

    private var isDimmed = false

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("【实验性】Screen Dimmer 启动（NoRoot）| dimLevel=${cfg.screenDimLevel}")

        hookWindowBrightness(lpparam, cfg)
        hookWindowSetAttributes(lpparam, cfg)
    }

    private fun hookWindowBrightness(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        try {
            val lpCls = XposedHelpers.findClassIfExists(
                "android.view.WindowManager.LayoutParams", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                lpCls, "copyFrom",
                lpCls,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val params = p.thisObject
                            val currentBrightness = try {
                                XposedHelpers.getFloatField(params, "screenBrightness")
                            } catch (e: Exception) { 1.0f }

                            if (currentBrightness > cfg.screenDimLevel && currentBrightness > 0f) {
                                XposedHelpers.setFloatField(
                                    params, "screenBrightness", cfg.screenDimLevel
                                )
                                isDimmed = true
                                LogX.d("Screen Dimmer(NoRoot): ${currentBrightness} -> ${cfg.screenDimLevel}")
                            }
                        } catch (e: Exception) {
                            LogX.e("Window dimmer(NoRoot) 异常", e)
                        }
                    }
                })
            LogX.hookSuccess("WindowManager.LayoutParams", "screenBrightness->Dim(NoRoot)")
        } catch (e: Exception) {
            LogX.e("Hook screenBrightness(NoRoot) 异常", e)
        }
    }

    private fun hookWindowSetAttributes(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        try {
            val viewRootCls = XposedHelpers.findClassIfExists(
                "android.view.ViewRootImpl", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                viewRootCls, "setLayoutParams",
                "android.view.WindowManager.LayoutParams",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val params = p.args[0]
                            if (params == null || !isDimmed) return
                            val currentBrightness = try {
                                XposedHelpers.getFloatField(params, "screenBrightness")
                            } catch (e: Exception) { 1.0f }

                            if (currentBrightness > cfg.screenDimLevel && currentBrightness > 0f) {
                                XposedHelpers.setFloatField(
                                    params, "screenBrightness", cfg.screenDimLevel
                                )
                            }
                        } catch (e: Exception) {
                            LogX.e("setLayoutParams dim(NoRoot) 异常", e)
                        }
                    }
                })
            LogX.hookSuccess("ViewRootImpl", "setLayoutParams->Dim(NoRoot)")
        } catch (e: Exception) {
            LogX.e("Hook setLayoutParams(NoRoot) 异常", e)
        }
    }
}
