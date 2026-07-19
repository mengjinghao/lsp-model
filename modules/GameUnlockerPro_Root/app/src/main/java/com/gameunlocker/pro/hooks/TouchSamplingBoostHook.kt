package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 触摸采样率提升 Hook（实验性）
 *
 * 功能：
 *  - Hook InputEventReceiver.dispatchInputEvent 提高 Input 事件处理线程优先级
 *  - Hook InputQueue native 投递回调，提升触屏事件处理优先级
 *
 * 硬性限制：
 *  - 仅修改应用进程内事件分发调度
 *  - 实际触屏硬件采样率由触屏 IC 和驱动决定
 *
 * 实验性声明：本 Hook 仅对响应延迟敏感的玩家有可感知效果。
 */
object TouchSamplingBoostHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.touchSamplingBoostEnabled) return
        LogX.i("触摸采样率提升启动（实验性）")

        hookInputEventReceiver(lpparam)
        hookInputQueue(lpparam)
        boostInputThreadPriority()
    }

    private fun hookInputEventReceiver(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val ier = XposedHelpers.findClassIfExists(
                "android.view.InputEventReceiver", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(ier, "dispatchInputEvent",
                    Int::class.javaPrimitiveType,
                    "android.view.InputEvent",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val pt = Class.forName("android.os.Process")
                                val m = pt.getMethod("setThreadPriority", Int::class.javaPrimitiveType)
                                m.invoke(null, -8)
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("InputEventReceiver", "dispatchInputEvent")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("InputEventReceiver", "dispatchInputEvent", e)
        }
    }

    private fun hookInputQueue(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val iq = XposedHelpers.findClassIfExists(
                "android.view.InputQueue", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(iq, "processInputEvents",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val pt = Class.forName("android.os.Process")
                                val m = pt.getMethod("setThreadPriority", Int::class.javaPrimitiveType)
                                m.invoke(null, -8)
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("InputQueue", "processInputEvents")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("InputQueue", "processInputEvents", e)
        }
    }

    private fun boostInputThreadPriority() {
        try {
            val pt = Class.forName("android.os.Process")
            val m = pt.getMethod("setThreadPriority", Int::class.javaPrimitiveType)
            m.invoke(null, -8)
            LogX.d("输入线程优先级提升至 URGENT_DISPLAY(-8)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }
}
