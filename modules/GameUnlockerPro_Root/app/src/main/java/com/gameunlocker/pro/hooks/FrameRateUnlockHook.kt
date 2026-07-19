package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 游戏渲染帧率强制解锁 Hook
 *
 * 应用层 Hook：
 *  - Display.getMode/getSupportedModes/getRefreshRate
 *  - Surface.setFrameRate
 *  - Unity Application.targetFrameRate / Unreal GameActivity
 *  - OEM 厂商游戏加速器帧率锁定屏蔽
 *
 * 系统级修改（需 Shizuku，由 ShizukuBridgeHook 执行 setprop / settings put system）
 */
object FrameRateUnlockHook {

    private var fps = 120

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.frameRateUnlockEnabled) return
        fps = if (cfg.targetFps <= 0) detectMaxRefreshRate() else cfg.targetFps
        LogX.i("帧率解锁: ${fps}fps（应用层 + Shizuku 提示）")

        hookDisplay(lpparam)
        hookSurface(lpparam)
        hookUnity(lpparam)
        hookUnreal(lpparam)
        hookOemLockers(lpparam)
    }

    private fun hookDisplay(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val dc = XposedHelpers.findClassIfExists(
                "android.view.Display", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(dc, "getMode", object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val mode = p.result ?: return
                            mode.javaClass.getField("refreshRate").setFloat(mode, fps.toFloat())
                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(dc, "getSupportedModes", object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val modes = p.result as? Array<*> ?: return
                        for (m in modes) {
                            try {
                                m?.javaClass?.getField("refreshRate")?.setFloat(m, fps.toFloat())
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    }
                })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(dc, "getRefreshRate", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) { p.result = fps.toFloat() }
                })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            LogX.hookSuccess("Display", "getMode/getSupportedModes/getRefreshRate -> ${fps}fps")
        } catch (e: Throwable) {
            LogX.hookFailed("Display", "frameRate", e)
        }
    }

    private fun hookSurface(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val sc = XposedHelpers.findClassIfExists("android.view.Surface", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(sc, "setFrameRate",
                    Float::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val rq = p.args[0] as Float
                            if (rq > 0 && rq < fps) p.args[0] = fps.toFloat()
                        }
                    })
                LogX.hookSuccess("Surface", "setFrameRate")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("Surface", "setFrameRate", e)
        }
    }

    private fun hookUnity(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val unityPlayer = XposedHelpers.findClassIfExists(
                "com.unity3d.player.UnityPlayer", lpparam.classLoader) ?: return
            LogX.i("检测到 Unity 引擎")
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            XposedHelpers.callStaticMethod(unityPlayer, "setTargetFrameRate", fps)
                            LogX.d("Unity targetFrameRate = $fps")
                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun hookUnreal(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val ga = XposedHelpers.findClassIfExists(
                "com.epicgames.unreal.GameActivity", lpparam.classLoader) ?: return
            LogX.i("检测到 Unreal 引擎")
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
                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

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
                LogX.d("OEM 锁屏蔽: $cls.$method")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }
    }

    private fun detectMaxRefreshRate(): Int {
        return try {
            val sp = Class.forName("android.os.SystemProperties")
            val max = XposedHelpers.callStaticMethod(sp, "getInt",
                "ro.vendor.dfps.refresh_rate.max", 120) as? Int ?: 120
            LogX.d("屏幕最大刷新率检测: ${max}Hz")
            max
        } catch (_: Throwable) { 120 }
    }
}
