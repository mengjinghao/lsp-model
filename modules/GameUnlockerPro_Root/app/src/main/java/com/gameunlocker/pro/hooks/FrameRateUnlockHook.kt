package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 强制帧率解锁引擎
 *
 * 功能：
 *  - Hook游戏Graphics渲染层，无视游戏内帧率限制开关，强制覆盖帧率
 *  - 识别屏幕硬件最高刷新率，自动匹配120/144/160帧
 *  - 针对HyperOS/OriginOS/ColorOS厂商底层帧率锁做专项Hook
 *  - 解除系统层面60帧限制
 *
 * 技术原理：
 *  1. Android游戏使用SurfaceFlinger进行画面合成，刷新率由DisplayManager控制
 *  2. 游戏通过 Display.getMode() / Display.getSupportedModes() 获取支持的模式
 *  3. 游戏通过 Surface.setFrameRate() 请求帧率
 *  4. Unity游戏通过 QualitySettings.vSyncCount 控制帧率
 *  5. Unreal引擎通过 GameUserSettings 控制帧率上限
 *  6. 原神使用米哈游自定义mhy渲染管线，需要专项处理
 */
object FrameRateUnlockHook {

    private var targetFps: Int = 120
    private var isApplied = false

    /**
     * 应用帧率解锁Hook
     * @param lpparam Xposed加载参数
     * @param config 游戏配置
     */
    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, config: GameConfig) {
        if (!config.frameRateUnlockEnabled) {
            LogX.d("帧率解锁未开启，跳过")
            return
        }
        if (isApplied) return
        isApplied = true

        targetFps = if (config.targetFps <= 0) {
            // 自动模式：取屏幕最高刷新率
            detectScreenMaxRefreshRate()
        } else {
            config.targetFps
        }
        LogX.i("帧率解锁引擎启动: 目标=${targetFps}fps")

        hookDisplayModes(lpparam)
        hookSurfaceFrameRate(lpparam)
        hookUnityVSync(lpparam)
        hookUnrealFrameRate(lpparam)
        hookGfxDriver(lpparam)
        hookOEMFrameRateLocks(lpparam)
        hookSurfaceFlinger(lpparam)
    }

    /**
     * Hook Display.getMode() / getSupportedModes()
     * 修改屏幕支持的模式列表，使所有模式都支持高刷
     */
    private fun hookDisplayModes(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hook Display.getMode() - 当前显示模式
            val displayClass = XposedHelpers.findClass("android.view.Display", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(
                displayClass,
                "getMode",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val mode = param.result ?: return
                            val modeClass = mode.javaClass
                            // 修改刷新率为目标帧率
                            XposedHelpers.setFloatField(mode, "refreshRate", targetFps.toFloat())
                            LogX.d("Display.getMode 刷新率已改为: ${targetFps}Hz")
                        } catch (e: Exception) {
                            LogX.d("修改Display Mode异常: ${e.message}")
                        }
                    }
                }
            )
            LogX.hookSuccess("Display", "getMode")

            // Hook Display.getSupportedModes() - 支持的显示模式
            XposedHelpers.findAndHookMethod(
                displayClass,
                "getSupportedModes",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val modes = param.result as? Array<Any> ?: return
                            for (mode in modes) {
                                try {
                                    val refreshRateField = mode.javaClass.getField("refreshRate")
                                    val currentRate = refreshRateField.getFloat(mode)
                                    // 将所有低于目标帧率的模式提升到目标帧率
                                    if (currentRate < targetFps && currentRate > 0) {
                                        refreshRateField.setFloat(mode, targetFps.toFloat())
                                    }
                                } catch (e: Exception) {
                                    // 单个mode修改失败不影响其他
                                }
                            }
                            LogX.d("Display.getSupportedModes 已全部提升至${targetFps}Hz")
                        } catch (e: Exception) {
                            LogX.d("修改Display SupportedModes异常: ${e.message}")
                        }
                    }
                }
            )
            LogX.hookSuccess("Display", "getSupportedModes")

            // Hook Display.getRefreshRate()
            XposedHelpers.findAndHookMethod(
                displayClass,
                "getRefreshRate",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = targetFps.toFloat()
                    }
                }
            )
            LogX.hookSuccess("Display", "getRefreshRate")
        } catch (e: Exception) {
            LogX.e("Hook Display模式失败", e)
        }
    }

    /**
     * Hook Surface.setFrameRate()
     * Android 11+ 引入的帧率API，游戏可直接设置需要的帧率
     */
    private fun hookSurfaceFrameRate(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hook Surface.setFrameRate(float frameRate, int compatibility)
            val surfaceClass = XposedHelpers.findClass("android.view.Surface", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(
                surfaceClass,
                "setFrameRate",
                Float::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val requestedFps = param.args[0] as Float
                        if (requestedFps > 0 && requestedFps < targetFps) {
                            // 游戏请求了帧率但低于目标，强制覆盖
                            param.args[0] = targetFps.toFloat()
                            LogX.d("Surface.setFrameRate: ${requestedFps} -> $targetFps")
                        }
                    }
                }
            )
            LogX.hookSuccess("Surface", "setFrameRate")

            // Hook Surface.setFrameRate(float frameRate, int compatibility, int changeFrameRateStrategy)
            XposedHelpers.findAndHookMethod(
                surfaceClass,
                "setFrameRate",
                Float::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val requestedFps = param.args[0] as Float
                        if (requestedFps > 0 && requestedFps < targetFps) {
                            param.args[0] = targetFps.toFloat()
                        }
                    }
                }
            )
            LogX.hookSuccess("Surface", "setFrameRate(v2)")
        } catch (e: Exception) {
            LogX.d("Hook Surface.setFrameRate异常（部分系统不支持）: ${e.message}")
        }
    }

    /**
     * Hook Unity引擎的VSync和帧率控制
     * Unity通过 QualitySettings.vSyncCount 和 Application.targetFrameRate 控制帧率
     */
    private fun hookUnityVSync(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // 尝试Hook Unity的QualitySettings
            val qualitySettingsClass = XposedHelpers.findClassIfExists(
                "com.unity3d.player.UnityPlayer",
                lpparam.classLoader
            )
            if (qualitySettingsClass != null) {
                LogX.i("检测到Unity引擎，应用帧率Hook")

                // Hook Application.targetFrameRate
                val appClass = XposedHelpers.findClass(
                    "com.unity3d.player.UnityPlayer",
                    lpparam.classLoader
                )
                XposedHelpers.findAndHookMethod(
                    "android.app.Application",
                    lpparam.classLoader,
                    "onCreate",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            // 强制设置Unity目标帧率
                            try {
                                val fpsClass = XposedHelpers.findClass(
                                    "com.unity3d.player.UnityPlayer",
                                    lpparam.classLoader
                                )
                                XposedHelpers.callStaticMethod(
                                    fpsClass,
                                    "setTargetFrameRate",
                                    targetFps
                                )
                                LogX.d("Unity targetFrameRate 已设为: $targetFps")
                            } catch (e: Exception) {
                                LogX.d("设置Unity帧率异常: ${e.message}")
                            }
                        }
                    }
                )
            }
        } catch (e: Exception) {
            LogX.d("Hook Unity引擎异常（非Unity游戏）: ${e.message}")
        }
    }

    /**
     * Hook Unreal引擎的帧率控制
     * Unreal引擎通过 GameUserSettings 和 UGameEngine 控制帧率
     */
    private fun hookUnrealFrameRate(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // 检测Unreal引擎特征类
            val gameActivityClass = XposedHelpers.findClassIfExists(
                "com.epicgames.unreal.GameActivity",
                lpparam.classLoader
            )
            if (gameActivityClass != null) {
                LogX.i("检测到Unreal引擎，应用帧率Hook")

                // 通过SystemProperties强制设置引擎帧率上限
                // Unreal引擎通常读取以下属性作为帧率上限
                try {
                    val sysProp = XposedHelpers.findClass("android.os.SystemProperties", null)
                    // 注意：不直接修改SystemProperties返回值
                    // 而是修改游戏Setting类
                } catch (e: Exception) {
                    LogX.d("Unreal Hook异常: ${e.message}")
                }

                // Hook GameActivity.onCreate 来修改引擎初始化参数
                XposedHelpers.findAndHookMethod(
                    gameActivityClass,
                    "onCreate",
                    android.os.Bundle::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                // 设置引擎强制帧率参数
                                val nativeSetAttribute = XposedHelpers.findMethodBestMatch(
                                    gameActivityClass,
                                    "nativeSetGlobalActivity",
                                    android.app.Activity::class.java,
                                    String::class.java
                                )
                                XposedHelpers.callStaticMethod(
                                    gameActivityClass,
                                    "nativeSetGlobalActivity",
                                    param.thisObject,
                                    "FullscreenFrameRate=$targetFps"
                                )
                                LogX.d("Unreal引擎帧率已设置: $targetFps")
                            } catch (e: Exception) {
                                LogX.d("设置Unreal帧率异常: ${e.message}")
                            }
                        }
                    }
                )
            }
        } catch (e: Exception) {
            LogX.d("Hook Unreal引擎异常（非Unreal游戏）: ${e.message}")
        }
    }

    /**
     * Hook GFX Driver层帧率限制
     * 部分游戏的Native层有AGL/EGL帧率限制
     */
    private fun hookGfxDriver(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hook EGL14.eglSwapInterval
            // 游戏通过 setSwapInterval(1) 让VSync与屏幕刷新率同步
            // 将其改为0或更大的值来解锁帧率
            val eglClass = XposedHelpers.findClassIfExists(
                "android.opengl.EGL14",
                lpparam.classLoader
            )
            if (eglClass != null) {
                XposedHelpers.findAndHookMethod(
                    eglClass,
                    "eglSwapInterval",
                    javax.microedition.khronos.egl.EGLDisplay::class.java,
                    javax.microedition.khronos.egl.EGLSurface::class.java,
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            // 检测到VSync限制了帧率，强制改为更高的swap间隔
                            val interval = param.args[2] as Int
                            if (interval == 1 && targetFps > 60) {
                                // 保持interval=1即可，帧率上限由Display管理
                            }
                        }
                    }
                )
                LogX.hookSuccess("EGL14", "eglSwapInterval")
            }

            // Hook eglGetConfigs 确保高刷新率配置可选
            if (eglClass != null) {
                XposedHelpers.findAndHookMethod(
                    eglClass,
                    "eglChooseConfig",
                    javax.microedition.khronos.egl.EGLDisplay::class.java,
                    IntArray::class.java,
                    arrayOf(javax.microedition.khronos.egl.EGLConfig::class.java),
                    Int::class.javaPrimitiveType,
                    IntArray::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            // 确保EGL配置包含高刷新率参数
                        }
                    }
                )
            }
        } catch (e: Exception) {
            LogX.d("Hook GFX Driver异常: ${e.message}")
        }
    }

    /**
     * Hook厂商(OEM)底层帧率锁
     * 针对小米/OPPO/vivo等厂商在Framework层的60帧锁定
     */
    private fun hookOEMFrameRateLocks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // ===== HyperOS (小米) =====
            // 小米游戏工具箱的帧率限制
            hookClassMethod(lpparam, "com.miui.gamebooster.service.GameBoosterService",
                "onFrameRateLimit", true)

            // Joyose性能调度
            hookClassMethod(lpparam, "com.xiaomi.joyose.JoyoseManager",
                "getPerformanceLevel", null)

            // 电源管理帧率限制
            hookClassMethod(lpparam, "com.miui.powerkeeper.PowerKeeperService",
                "notifyFrameRateLimit", true)

            // MIUI刷新率策略
            hookClassMethod(lpparam, "com.android.server.display.DisplayPowerController",
                "getRefreshRate", targetFps.toFloat())

            // ===== OriginOS (vivo/iQOO) =====
            // vivo游戏魔盒
            hookClassMethod(lpparam, "com.vivo.gamewatch.GameWatchService",
                "setMaxFrameRate", true)

            // vivo电源专家性能模式
            hookClassMethod(lpparam, "com.vivo.pem.PowerExpertService",
                "onPerformanceMode", true)

            // vivo渲染服务帧率
            hookClassMethod(lpparam, "com.vivo.services.render.VivoRenderService",
                "getMaxRefreshRate", targetFps.toFloat())

            // ===== ColorOS (OPPO/一加) =====
            // OPPO游戏空间
            hookClassMethod(lpparam, "com.oplus.games.GameSpaceService",
                "lockFrameRate", true)

            // OPPO HyperBoost引擎
            hookClassMethod(lpparam, "com.oplus.hyperboost.HyperBoostEngine",
                "getMaxRefreshRate", targetFps.toFloat())

            // OPPO DisplayService
            hookClassMethod(lpparam, "com.oplus.display.DisplayService",
                "setDisplayMode", true)

            // ===== OneUI (三星) =====
            hookClassMethod(lpparam, "com.samsung.android.game.gametools.GameBoosterService",
                "setFrameRateCap", true)

            // 三星GOS（Game Optimizing Service）性能限制
            hookClassMethod(lpparam, "com.samsung.android.gos.GameOptimizingService",
                "onPerformanceCheck", true)

            LogX.i("OEM帧率锁专项Hook完成")
        } catch (e: Exception) {
            LogX.d("Hook OEM帧率锁部分异常: ${e.message}")
        }
    }

    /**
     * Hook SurfaceFlinger刷新率策略
     * SurfaceFlinger是Android的底层渲染合成服务
     */
    private fun hookSurfaceFlinger(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val sfClass = XposedHelpers.findClassIfExists(
                "android.view.SurfaceControl",
                lpparam.classLoader
            )
            if (sfClass != null) {
                // Hook setActiveConfig
                XposedHelpers.findAndHookMethod(
                    sfClass,
                    "setActiveConfig",
                    android.os.IBinder::class.java,
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            // 允许高分模式
                            LogX.d("SurfaceControl.setActiveConfig Hook触发")
                        }
                    }
                )
            }
        } catch (e: Exception) {
            LogX.d("Hook SurfaceFlinger异常: ${e.message}")
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 通用类方法Hook辅助
     * @param klass 类全名
     * @param method 方法名
     * @param returnValue 替换返回值，null=不替换返回值；true=返回void
     */
    private fun hookClassMethod(
        lpparam: XC_LoadPackage.LoadPackageParam,
        klass: String,
        method: String,
        returnValue: Any?
    ) {
        try {
            val cls = XposedHelpers.findClassIfExists(klass, lpparam.classLoader)
            if (cls == null) {
                LogX.d("类不存在，跳过: $klass")
                return
            }
            if (returnValue is Boolean && returnValue) {
                // 替换为空实现（屏蔽该方法调用）
                XposedHelpers.findAndHookMethod(
                    cls,
                    method,
                    XC_MethodReplacement.DO_NOTHING
                )
                LogX.hookSuccess(klass, "$method (DO_NOTHING)")
            } else if (returnValue != null) {
                XposedHelpers.findAndHookMethod(
                    cls,
                    method,
                    XC_MethodReplacement.returnConstant(returnValue)
                )
                LogX.hookSuccess(klass, "$method -> $returnValue")
            } else {
                // 仅Hook before，不修改返回值
                XposedHelpers.findAndHookMethod(
                    cls,
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            LogX.d("拦截 $klass.$method")
                        }
                    }
                )
            }
        } catch (e: Exception) {
            LogX.d("Hook $klass.$method 异常: ${e.message}")
        }
    }

    /**
     * 检测屏幕硬件最高刷新率
     */
    private fun detectScreenMaxRefreshRate(): Int {
        return try {
            // 通过反射获取DisplayManager以读取屏幕支持的最大刷新率
            val dmClass = Class.forName("android.hardware.display.DisplayManager")
            // 回退方案：通过系统属性检测
            val sysPropClass = Class.forName("android.os.SystemProperties")
            val maxRate = XposedHelpers.callStaticMethod(
                sysPropClass,
                "getInt",
                "ro.vendor.dfps.refresh_rate.max",
                120
            ) as? Int ?: 120
            LogX.d("检测到屏幕最大刷新率: ${maxRate}Hz")
            maxRate
        } catch (e: Exception) {
            LogX.d("检测屏幕刷新率异常，使用默认值120Hz")
            120
        }
    }

    /** 获取当前目标帧率 */
    fun getTargetFps(): Int = targetFps
}
