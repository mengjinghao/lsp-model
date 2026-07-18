package com.gameunlocker.noroot.hooks

import com.gameunlocker.noroot.models.GameConfig
import com.gameunlocker.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 游戏渲染帧率强制解锁Hook
 *
 * 硬性限制：
 *  - 全部Hook在游戏应用进程内执行，不修改系统级SurfaceFlinger
 *  - 屏幕硬件刷新率上限由Shizuku setprop设置（需Shizuku授权）
 *  - 无Root无法直接写入 /sys/class/graphics/fb0/ 节点
 *
 * 拦截路径：
 *  1. Display.getMode/getSupportedModes/getRefreshRate -> 报告目标帧率
 *  2. Surface.setFrameRate -> 覆盖游戏请求的帧率
 *  3. Unity引擎 Application.targetFrameRate -> 强制目标帧率
 *  4. Unreal引擎 GameActivity 初始化 -> 注入帧率参数
 *  5. 各厂商游戏加速器帧率锁定 -> 空实现屏蔽
 */
object FrameRateUnlockHook {

    private var fps = 120

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.frameRateUnlockEnabled) return
        // 自动模式取屏幕最高刷，否则取用户设定值
        fps = if (cfg.targetFps <= 0) detectMaxRefreshRate() else cfg.targetFps
        LogX.i("帧率解锁: ${fps}fps")

        hookDisplay(lpparam)
        hookSurface(lpparam)
        hookUnity(lpparam)
        hookUnreal(lpparam)
        hookOemLockers(lpparam)

        // 通过Shizuku传递刷新率属性（需Shizuku授权）
        ShizukuExecutor.applyRefreshRate(fps)
    }

    // ===== Display Hook =====
    private fun hookDisplay(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val dc = XposedHelpers.findClass("android.view.Display", lpparam.classLoader)

            // getMode: 修改返回的Mode.refreshRate
            XposedHelpers.findAndHookMethod(dc, "getMode", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val mode = param.result ?: return
                        mode.javaClass.getField("refreshRate").setFloat(mode, fps.toFloat())
                    } catch (_: Exception) {}
                }
            })

            // getSupportedModes: 所有模式刷到目标帧率
            XposedHelpers.findAndHookMethod(dc, "getSupportedModes", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val modes = param.result as? Array<*> ?: return
                    for (m in modes) {
                        try {
                            m?.javaClass?.getField("refreshRate")?.setFloat(m, fps.toFloat())
                        } catch (_: Exception) {}
                    }
                }
            })

            // getRefreshRate 直接返回目标帧率
            XposedHelpers.findAndHookMethod(dc, "getRefreshRate", object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) { p.result = fps.toFloat() }
            })
            LogX.d("Display Hook完成")
        } catch (e: Exception) {
            LogX.e("Display Hook异常", e)
        }
    }

    // ===== Surface Hook (Android 11+) =====
    private fun hookSurface(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val sc = XposedHelpers.findClass("android.view.Surface", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(sc, "setFrameRate",
                Float::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val rq = p.args[0] as Float
                        if (rq > 0 && rq < fps) p.args[0] = fps.toFloat()
                    }
                })
            LogX.d("Surface.setFrameRate Hook完成")
        } catch (e: Exception) {
            LogX.d("Surface Hook异常(低版本Android不支持): ${e.message}")
        }
    }

    // ===== Unity引擎帧率 =====
    private fun hookUnity(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val unityPlayer = XposedHelpers.findClassIfExists(
                "com.unity3d.player.UnityPlayer", lpparam.classLoader)
            if (unityPlayer == null) return
            LogX.i("检测到Unity引擎")

            // Hook Application.onCreate 设置帧率
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            // QualitySettings.vSyncCount = 0
                            val qs = XposedHelpers.findClassIfExists(
                                "com.unity3d.player.UnityPlayer", lpparam.classLoader)
                            // Application.targetFrameRate = fps
                            XposedHelpers.callStaticMethod(qs, "setTargetFrameRate", fps)
                            LogX.d("Unity targetFrameRate = $fps")
                        } catch (_: Exception) {}
                    }
                })
        } catch (e: Exception) {
            LogX.d("Unity Hook异常: ${e.message}")
        }
    }

    // ===== Unreal引擎帧率 =====
    private fun hookUnreal(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val ga = XposedHelpers.findClassIfExists(
                "com.epicgames.unreal.GameActivity", lpparam.classLoader)
            if (ga == null) return
            LogX.i("检测到Unreal引擎")

            XposedHelpers.findAndHookMethod(ga, "onCreate",
                android.os.Bundle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            XposedHelpers.callStaticMethod(ga,
                                "nativeSetGlobalActivity",
                                p.thisObject,
                                "FullscreenFrameRate=$fps")
                            LogX.d("Unreal FullscreenFrameRate=$fps")
                        } catch (_: Exception) {}
                    }
                })
        } catch (e: Exception) {
            LogX.d("Unreal Hook异常: ${e.message}")
        }
    }

    // ===== OEM厂商游戏加速器帧率锁屏蔽 =====
    private fun hookOemLockers(lpparam: XC_LoadPackage.LoadPackageParam) {
        val targets = listOf(
            "com.miui.gamebooster.service.GameBoosterService" to "onFrameRateLimit",
            "com.xiaomi.joyose.JoyoseManager" to "getPerformanceLevel",
            "com.miui.powerkeeper.PowerKeeperService" to "notifyFrameRateLimit",
            "com.vivo.gamewatch.GameWatchService" to "setMaxFrameRate",
            "com.vivo.pem.PowerExpertService" to "onPerformanceMode",
            "com.oplus.games.GameSpaceService" to "lockFrameRate",
            "com.oplus.hyperboost.HyperBoostEngine" to "getMaxRefreshRate",
            "com.samsung.android.game.gametools.GameBoosterService" to "setFrameRateCap",
            "com.samsung.android.gos.GameOptimizingService" to "onPerformanceCheck"
        )
        for ((cls, method) in targets) {
            try {
                val c = XposedHelpers.findClassIfExists(cls, lpparam.classLoader) ?: continue
                XposedHelpers.findAndHookMethod(c, method, XC_MethodReplacement.DO_NOTHING)
                LogX.d("OEM锁屏蔽: $cls.$method")
            } catch (_: Exception) {}
        }
    }

    /** 检测屏幕硬件最大刷新率（应用层方式） */
    private fun detectMaxRefreshRate(): Int {
        return try {
            val dm = Class.forName("android.hardware.display.DisplayManager")
            // 无Root回退：尝试系统属性
            val sp = Class.forName("android.os.SystemProperties")
            val max = XposedHelpers.callStaticMethod(sp, "getInt",
                "ro.vendor.dfps.refresh_rate.max", 120) as? Int ?: 120
            LogX.d("屏幕最大刷新率检测: ${max}Hz")
            max
        } catch (e: Exception) {
            120
        }
    }
}
