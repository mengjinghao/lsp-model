package com.batteryopt.pro.hooks

import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import com.batteryopt.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Screen Dimmer（实验性）
 *
 * Root 版：Hook WindowManager 降低屏幕亮度至系统最低以下
 * - Hook WindowManager.LayoutParams.screenBrightness 覆写为 dimLevel
 * - 通过 Shizuku settings put system screen_brightness 同步调节系统亮度
 */
object ScreenDimmerHook {

    private var isDimmed = false

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("【实验性】Screen Dimmer 启动 | dimLevel=${cfg.screenDimLevel}")

        hookWindowBrightness(lpparam, cfg)
        hookApplyBrightness(lpparam, cfg)
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
                            val currentBrightness = XposedHelpers.getFloatField(
                                params, "screenBrightness"
                            )
                            if (currentBrightness > cfg.screenDimLevel && currentBrightness > 0f) {
                                XposedHelpers.setFloatField(
                                    params, "screenBrightness", cfg.screenDimLevel
                                )
                                LogX.d("Screen Dimmer: ${currentBrightness} -> ${cfg.screenDimLevel}")
                                if (!isDimmed) {
                                    applySystemDimmer(cfg)
                                    isDimmed = true
                                }
                            }
                        } catch (e: Exception) {
                            LogX.e("Window dimmer 异常", e)
                        }
                    }
                })
            LogX.hookSuccess("WindowManager.LayoutParams", "screenBrightness->Dim")
        } catch (e: Exception) {
            LogX.e("Hook screenBrightness 异常", e)
        }
    }

    private fun hookApplyBrightness(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        try {
            val wmCls = XposedHelpers.findClassIfExists(
                "android.view.WindowManagerImpl", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                wmCls, "getDefaultDisplay",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        if (isDimmed) {
                            applySystemDimmer(cfg)
                        }
                    }
                })
            LogX.hookSuccess("WindowManagerImpl", "getDefaultDisplay->ForceDim")
        } catch (e: Exception) {
            LogX.e("Hook getDefaultDisplay 异常", e)
        }
    }

    private fun applySystemDimmer(cfg: BatteryConfig) {
        if (!ShizukuHelper.isShizukuAvailable()) return
        try {
            val dimValue = (cfg.screenDimLevel * 255).toInt()
            ShizukuHelper.execShell("settings put system screen_brightness $dimValue")
            LogX.d("系统亮度已设为 $dimValue")
        } catch (e: Exception) {
            LogX.e("系统亮度设置异常", e)
        }
    }
}
