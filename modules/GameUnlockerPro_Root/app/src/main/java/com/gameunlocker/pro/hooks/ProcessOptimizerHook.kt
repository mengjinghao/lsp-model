package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.model.GameConfig
import com.gameunlocker.pro.utils.LogX
import com.gameunlocker.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 进程性能优化 Hook（Root 版，应用层 + Shizuku 冻结后台）
 *
 * 应用层：
 *  1. 提升游戏渲染线程优先级
 *  2. Hook PowerManager 热状态回调 -> 返回 STATUS_NONE
 *
 * 系统级（需 Shizuku）：
 *  - 通过 am force-stop 冻结黑名单后台进程
 *  - 通过 echo 3 > /proc/sys/vm/drop_caches 清理页面缓存
 */
object ProcessOptimizerHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.processOptimizeEnabled) return
        LogX.i("进程性能优化启动（应用层 + Shizuku 冻结后台）")

        boostRenderThread(lpparam)
        hookPowerThermalStatus(lpparam)
        freezeBackgroundApps()
    }

    private fun boostRenderThread(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val glsv = XposedHelpers.findClassIfExists(
                "android.opengl.GLSurfaceView", lpparam.classLoader)
            if (glsv != null) {
                try {
                    XposedHelpers.findAndHookMethod(glsv, "setRenderMode",
                        Int::class.javaPrimitiveType,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                if (p.args[0] as Int != 1) p.args[0] = 1
                            }
                        })
                    LogX.hookSuccess("GLSurfaceView", "setRenderMode -> CONTINUOUSLY")
                } catch (_: Throwable) {}
            }

            try {
                val pt = Class.forName("android.os.Process")
                val setThreadPriority = pt.getMethod("setThreadPriority", Int::class.javaPrimitiveType)
                setThreadPriority.invoke(null, -8)
                LogX.d("主线程优先级提升至 URGENT_DISPLAY(-8)")
            } catch (_: Throwable) {}
        } catch (e: Throwable) {
            LogX.e("渲染线程优先级提升异常", e)
        }
    }

    private fun hookPowerThermalStatus(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val pm = XposedHelpers.findClassIfExists(
                "android.os.PowerManager", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(pm, "getCurrentThermalStatus",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = 0
                        }
                    })
                LogX.hookSuccess("PowerManager", "getCurrentThermalStatus -> STATUS_NONE")
            } catch (_: Throwable) {}
        } catch (e: Throwable) {
            LogX.hookFailed("PowerManager", "getCurrentThermalStatus", e)
        }
    }

    /** 通过 Shizuku 冻结非必要后台进程 */
    private fun freezeBackgroundApps() {
        if (!ShizukuHelper.isAvailable()) {
            LogX.w("Shizuku 不可用，跳过后台冻结")
            return
        }
        val apps = listOf(
            "com.miui.cleaner",
            "com.miui.powerkeeper",
            "com.xiaomi.joyose",
            "com.miui.systemAdSolution",
            "com.xiaomi.mi_connect_service",
            "com.miui.analytics",
            "com.miui.daemon",
            "com.vivo.pem"
        )
        for (app in apps) {
            ShizukuHelper.execShell("am force-stop $app")
        }
        ShizukuHelper.execShell("echo 3 > /proc/sys/vm/drop_caches")
        LogX.i("Shizuku 后台冻结完成: ${apps.size} 个进程")
    }
}
