package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 分辨率伪装Hook
 *
 * 功能：
 *  - 将低分辨率机型伪装成2K屏幕，解锁游戏高清材质
 *  - 修改 Display.getSize / Display.getRealSize / DisplayMetrics
 *  - 修改 WindowManager 默认分辨率
 *
 * 原理：
 *  许多游戏根据屏幕分辨率自动判断设备性能档位：
 *  720P -> 低画质/低帧率
 *  1080P -> 中画质/中帧率
 *  1440P(2K) -> 高画质/高帧率
 *  2160P(4K) -> 极致画质
 *
 *  通过伪装为2K分辨率，可以在1080P设备上解锁高画质选项。
 *  注意：实际渲染分辨率仍为物理分辨率，仅影响游戏设置选项。
 */
object ResolutionSpoofHook {

    private var isApplied = false

    /**
     * 应用分辨率伪装
     */
    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, config: GameConfig) {
        if (!config.resolutionSpoofEnabled) {
            LogX.d("分辨率伪装未开启，跳过")
            return
        }
        if (isApplied) return
        isApplied = true

        val targetWidth = config.spoofWidth
        val targetHeight = config.spoofHeight
        val targetDpi = config.spoofDpi

        LogX.i("分辨率伪装: ${targetWidth}x${targetHeight}, ${targetDpi}dpi")

        hookDisplaySize(lpparam, targetWidth, targetHeight)
        hookDisplayMetricsCore(lpparam, targetWidth, targetHeight, targetDpi)
        hookWindowManager(lpparam, targetWidth, targetHeight)
        hookConfiguration(lpparam, targetWidth, targetHeight, targetDpi)
    }

    /**
     * Hook Display.getSize() / getRealSize() / getRectSize()
     * 修改为伪装分辨率
     */
    private fun hookDisplaySize(
        lpparam: XC_LoadPackage.LoadPackageParam,
        width: Int,
        height: Int
    ) {
        try {
            val displayClass = XposedHelpers.findClass(
                "android.view.Display",
                lpparam.classLoader
            )
            val pointClass = XposedHelpers.findClass(
                "android.graphics.Point",
                lpparam.classLoader
            )

            // Hook getRealSize(Point)
            XposedHelpers.findAndHookMethod(
                displayClass,
                "getRealSize",
                pointClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val point = param.args[0]
                            point?.javaClass?.getDeclaredField("x")?.let { field ->
                                field.isAccessible = true
                                field.setInt(point, width)
                            }
                            point?.javaClass?.getDeclaredField("y")?.let { field ->
                                field.isAccessible = true
                                field.setInt(point, height)
                            }
                            LogX.d("Display.getRealSize -> ${width}x${height}")
                        } catch (e: Exception) {
                            LogX.d("修改getRealSize异常: ${e.message}")
                        }
                    }
                }
            )
            LogX.hookSuccess("Display", "getRealSize")

            // Hook getSize(Point)
            XposedHelpers.findAndHookMethod(
                displayClass,
                "getSize",
                pointClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val point = param.args[0]
                            point?.javaClass?.getDeclaredField("x")?.let { it.setInt(point, width) }
                            point?.javaClass?.getDeclaredField("y")?.let { it.setInt(point, height) }
                        } catch (e: Exception) { }
                    }
                }
            )
            LogX.hookSuccess("Display", "getSize")

            // Hook getRectSize(Rect)
            try {
                val rectClass = XposedHelpers.findClass("android.graphics.Rect", lpparam.classLoader)
                XposedHelpers.findAndHookMethod(
                    displayClass,
                    "getRectSize",
                    rectClass,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val rect = param.args[0]
                                rect?.javaClass?.getDeclaredField("right")?.let { it.setInt(rect, width) }
                                rect?.javaClass?.getDeclaredField("bottom")?.let { it.setInt(rect, height) }
                            } catch (e: Exception) { }
                        }
                    }
                )
            } catch (e: Exception) {
                LogX.d("getRectSize Hook异常: ${e.message}")
            }

        } catch (e: Exception) {
            LogX.e("Hook Display尺寸失败", e)
        }
    }

    /**
     * Hook DisplayMetrics
     * 修改 widthPixels, heightPixels, densityDpi
     */
    private fun hookDisplayMetricsCore(
        lpparam: XC_LoadPackage.LoadPackageParam,
        width: Int,
        height: Int,
        dpi: Int
    ) {
        try {
            val dmClass = XposedHelpers.findClass(
                "android.util.DisplayMetrics",
                lpparam.classLoader
            )

            // Hook DisplayMetrics.setToDefaults()
            XposedHelpers.findAndHookMethod(
                dmClass,
                "setToDefaults",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val dm = param.thisObject
                            dm.javaClass.getDeclaredField("widthPixels").setInt(dm, width)
                            dm.javaClass.getDeclaredField("heightPixels").setInt(dm, height)
                            dm.javaClass.getDeclaredField("densityDpi").setInt(dm, dpi)
                            val density = dpi / 160f
                            dm.javaClass.getDeclaredField("density").setFloat(dm, density)
                            dm.javaClass.getDeclaredField("scaledDensity").setFloat(dm, density)
                            dm.javaClass.getDeclaredField("xdpi").setFloat(dm, dpi.toFloat())
                            dm.javaClass.getDeclaredField("ydpi").setFloat(dm, dpi.toFloat())
                        } catch (e: Exception) {
                            LogX.d("修改DisplayMetrics异常: ${e.message}")
                        }
                    }
                }
            )
            LogX.hookSuccess("DisplayMetrics", "setToDefaults")

            // Hook Resources.getDisplayMetrics()
            try {
                val resClass = XposedHelpers.findClass(
                    "android.content.res.Resources",
                    lpparam.classLoader
                )
                XposedHelpers.findAndHookMethod(
                    resClass,
                    "getDisplayMetrics",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val dm = param.result
                                dm?.javaClass?.getDeclaredField("widthPixels")?.setInt(dm, width)
                                dm?.javaClass?.getDeclaredField("heightPixels")?.setInt(dm, height)
                                dm?.javaClass?.getDeclaredField("densityDpi")?.setInt(dm, dpi)
                            } catch (e: Exception) { }
                        }
                    }
                )
                LogX.hookSuccess("Resources", "getDisplayMetrics")
            } catch (e: Exception) {
                LogX.d("Hook Resources异常: ${e.message}")
            }

        } catch (e: Exception) {
            LogX.e("Hook DisplayMetrics失败", e)
        }
    }

    /**
     * Hook WindowManager对分辨率的访问
     */
    private fun hookWindowManager(
        lpparam: XC_LoadPackage.LoadPackageParam,
        width: Int,
        height: Int
    ) {
        try {
            // Hook WindowManager.getDefaultDisplay()
            // 不需要直接修改，因为已经Hook了Display的相关方法
            LogX.d("WindowManager分辨率伪装由Display层覆盖")
        } catch (e: Exception) {
            LogX.d("Hook WindowManager异常: ${e.message}")
        }
    }

    /**
     * Hook Configuration
     * 游戏通过 Resources.configuration 获取屏幕参数
     */
    private fun hookConfiguration(
        lpparam: XC_LoadPackage.LoadPackageParam,
        width: Int,
        height: Int,
        dpi: Int
    ) {
        try {
            val configClass = XposedHelpers.findClass(
                "android.content.res.Configuration",
                lpparam.classLoader
            )

            // 修改 Configuration.screenWidthDp / screenHeightDp
            // 这些值会影响游戏的UI缩放判断
            val densityDpiDivider = dpi.toFloat()
            val widthDp = (width / (densityDpiDivider / 160f)).toInt()
            val heightDp = (height / (densityDpiDivider / 160f)).toInt()

            // Hook Resources.updateConfiguration
            try {
                val resClass = XposedHelpers.findClass(
                    "android.content.res.Resources",
                    lpparam.classLoader
                )
                XposedHelpers.findAndHookMethod(
                    resClass,
                    "updateConfiguration",
                    configClass,
                    dmClass(),
                    dmClass(),
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            try {
                                val config = param.args[0] as? android.content.res.Configuration
                                    ?: return
                                config.javaClass.getDeclaredField("screenWidthDp")
                                    .setInt(config, widthDp)
                                config.javaClass.getDeclaredField("screenHeightDp")
                                    .setInt(config, heightDp)
                                config.javaClass.getDeclaredField("densityDpi")
                                    .setInt(config, dpi)
                                config.javaClass.getDeclaredField("smallestScreenWidthDp")
                                    .setInt(config, minOf(widthDp, heightDp))
                            } catch (e: Exception) { }
                        }
                    }
                )
                LogX.hookSuccess("Resources", "updateConfiguration")
            } catch (e: Exception) {
                LogX.d("updateConfiguration Hook异常: ${e.message}")
            }

        } catch (e: Exception) {
            LogX.d("Hook Configuration异常: ${e.message}")
        }
    }

    private fun dmClass(): Class<Any>? {
        return try {
            XposedHelpers.findClass("android.util.DisplayMetrics", null)
        } catch (e: Exception) {
            null
        }
    }
}
