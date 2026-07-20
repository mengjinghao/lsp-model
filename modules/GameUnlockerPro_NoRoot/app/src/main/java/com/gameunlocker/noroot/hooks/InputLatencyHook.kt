package com.gameunlocker.noroot.hooks

import com.gameunlocker.noroot.models.GameConfig
import com.gameunlocker.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Input Latency Reducer（实验性，NoRoot 版）
 *
 * Hook InputEventReceiver 减少输入处理延迟
 * Hook View.dispatchTouchEvent 优先处理输入
 * 添加 SYSTEM_UI_FLAG_LOW_PROFILE 减少系统 UI 开销
 */
object InputLatencyHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        LogX.i("【实验性】Input Latency Reducer 启动（NoRoot）")

        hookInputEventReceiver(lpparam, cfg)
        hookDispatchTouchEvent(lpparam, cfg)
        hookSystemUiFlags(lpparam, cfg)
    }

    private fun hookInputEventReceiver(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        try {
            val ierCls = XposedHelpers.findClassIfExists(
                "android.view.InputEventReceiver", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                ierCls, "consumeBatchedInputEvents",
                Long::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            android.os.Process.setThreadPriority(
                                android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY
                            )
                        } catch (e: Exception) {
                            LogX.e("InputEventReceiver 优先级异常(NoRoot)", e)
                        }
                    }
                })
            LogX.hookSuccess("InputEventReceiver", "consumeBatchedInputEvents->LowLatency(NoRoot)")
        } catch (e: Exception) {
            LogX.e("Hook InputEventReceiver(NoRoot) 异常", e)
        }
    }

    private fun hookDispatchTouchEvent(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        try {
            val viewCls = XposedHelpers.findClassIfExists(
                "android.view.View", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                viewCls, "dispatchTouchEvent",
                "android.view.MotionEvent",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val currentPriority = android.os.Process.getThreadPriority(
                                android.os.Process.myTid()
                            )
                            if (currentPriority > android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY) {
                                android.os.Process.setThreadPriority(
                                    android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY
                                )
                            }
                        } catch (e: Exception) {
                            LogX.e("dispatchTouchEvent 优先级异常(NoRoot)", e)
                        }
                    }
                })
            LogX.hookSuccess("View", "dispatchTouchEvent->LowLatency(NoRoot)")
        } catch (e: Exception) {
            LogX.e("Hook View.dispatchTouchEvent(NoRoot) 异常", e)
        }
    }

    private fun hookSystemUiFlags(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        try {
            val viewCls = XposedHelpers.findClassIfExists(
                "android.view.View", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                viewCls, "setSystemUiVisibility",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val currentFlags = p.args[0] as? Int ?: return
                            val newFlags = currentFlags or 0x00000200
                            if (newFlags != currentFlags) {
                                p.args[0] = newFlags
                            }
                        } catch (e: Exception) {
                            LogX.e("setSystemUiVisibility(NoRoot) 异常", e)
                        }
                    }
                })
            LogX.hookSuccess("View", "setSystemUiVisibility->LowProfile(NoRoot)")
        } catch (e: Exception) {
            LogX.e("Hook setSystemUiVisibility(NoRoot) 异常", e)
        }
    }
}
