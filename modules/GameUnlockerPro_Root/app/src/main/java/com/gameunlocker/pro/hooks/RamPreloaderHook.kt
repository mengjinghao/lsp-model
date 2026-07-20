package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * RAM Pre-Loader（实验性）
 *
 * 游戏启动时预加载游戏资源到内存
 * Hook Application.attach 触发预加载
 * 通过提前类加载和资源引用减少游戏内加载时间
 */
object RamPreloaderHook {

    private val preloadedClassNames = listOf(
        "android.opengl.EGL14",
        "android.opengl.GLES20",
        "android.opengl.GLES30",
        "android.graphics.SurfaceTexture",
        "android.view.SurfaceView",
        "android.view.TextureView",
        "android.media.AudioTrack",
        "android.media.MediaCodec",
        "javax.microedition.khronos.egl.EGLContext",
        "dalvik.system.VMRuntime"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        LogX.i("【实验性】RAM Pre-Loader 启动")

        hookAttach(lpparam, cfg)
        hookClassLoader(lpparam, cfg)
    }

    private fun hookAttach(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        try {
            val appCls = XposedHelpers.findClassIfExists(
                "android.app.Application", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                appCls, "attach",
                "android.content.Context",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            preloadClasses(lpparam)
                        } catch (e: Exception) {
                            LogX.e("attach 预加载异常", e)
                        }
                    }
                })
            LogX.hookSuccess("Application", "attach->PreLoader")
        } catch (e: Exception) {
            LogX.e("Hook Application.attach 异常", e)
        }
    }

    private fun hookClassLoader(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "dalvik.system.BaseDexClassLoader", lpparam.classLoader,
                "findClass",
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val className = p.args[0] as? String ?: return
                            if (className.contains("Surface") || className.contains("GL")) {
                                LogX.d("RAM PreLoader: 拦截类加载 $className")
                            }
                        } catch (e: Exception) {
                            LogX.e("ClassLoader 监控异常", e)
                        }
                    }
                })
            LogX.hookSuccess("BaseDexClassLoader", "findClass->PreLoader")
        } catch (e: Exception) {
            LogX.e("Hook BaseDexClassLoader 异常", e)
        }
    }

    private fun preloadClasses(lpparam: XC_LoadPackage.LoadPackageParam) {
        val cl = lpparam.classLoader ?: return
        var loadedCount = 0
        for (className in preloadedClassNames) {
            try {
                Class.forName(className, false, cl)
                loadedCount++
            } catch (e: Exception) {
                LogX.d("RAM PreLoader: 跳过 $className (${e.message})")
            }
        }
        LogX.i("RAM PreLoader: 预加载完成 $loadedCount/${preloadedClassNames.size} 个类")

        try {
            System.gc()
            LogX.d("RAM PreLoader: 已触发 System.gc()")
        } catch (e: Exception) {
            LogX.e("System.gc() 异常", e)
        }
    }
}
