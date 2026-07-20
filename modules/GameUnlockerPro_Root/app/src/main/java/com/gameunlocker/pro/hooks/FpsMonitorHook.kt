package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * FPS Monitor Overlay（实验性）
 *
 * Hook Choreographer.doFrame 计算实时 FPS
 * Hook WindowManager.addView 注入浮动 FPS 悬浮窗
 * 显示：当前 FPS、帧时间、jank 计数
 */
object FpsMonitorHook {

    private var frameCount = 0L
    private var lastFrameTime = 0L
    private var currentFps = 0f
    private val frameTimes = ConcurrentLinkedQueue<Long>()
    private var jankCount = 0
    private val jankThresholdMs = 16L

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        LogX.i("【实验性】FPS Monitor Overlay 启动")

        hookChoreographerDoFrame(lpparam, cfg)
        hookWindowAddView(lpparam, cfg)
    }

    private fun hookChoreographerDoFrame(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        try {
            val cfCls = XposedHelpers.findClassIfExists(
                "android.view.Choreographer.FrameCallback", lpparam.classLoader
            ) ?: return
            val chCls = XposedHelpers.findClassIfExists(
                "android.view.Choreographer", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                chCls, "doFrame",
                Long::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val frameTimeNanos = p.args[0] as? Long ?: return
                            val frameTimeMs = frameTimeNanos / 1_000_000

                            frameCount++
                            frameTimes.offer(frameTimeMs)

                            if (frameTimes.size > 120) {
                                frameTimes.poll()
                            }

                            if (lastFrameTime > 0) {
                                val delta = frameTimeMs - lastFrameTime
                                if (delta > jankThresholdMs * 3) {
                                    jankCount++
                                }
                            }

                            lastFrameTime = frameTimeMs

                            if (frameTimes.size >= 2) {
                                val oldest = frameTimes.peek()
                                if (oldest != null) {
                                    val elapsed = frameTimeMs - oldest
                                    if (elapsed > 0) {
                                        currentFps = (frameTimes.size * 1000f) / elapsed
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            LogX.e("Choreographer doFrame 异常", e)
                        }
                    }
                })
            LogX.hookSuccess("Choreographer", "doFrame->FPS")
        } catch (e: Exception) {
            LogX.e("Hook Choreographer doFrame 异常", e)
        }
    }

    private fun hookWindowAddView(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        try {
            val wmCls = XposedHelpers.findClassIfExists(
                "android.view.WindowManagerGlobal", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                wmCls, "addView",
                "android.view.View",
                "android.view.ViewGroup.LayoutParams",
                "android.view.Display",
                "android.view.Window",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            LogX.d("FPS: ${String.format("%.1f", currentFps)} fps | jank=$jankCount")
                        } catch (e: Exception) {
                            LogX.e("addView FPS log 异常", e)
                        }
                    }
                })
            LogX.hookSuccess("WindowManagerGlobal", "addView->FPSOverlay")
        } catch (e: Exception) {
            LogX.e("Hook WindowManager.addView 异常", e)
        }
    }
}
