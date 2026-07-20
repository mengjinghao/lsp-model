package com.mjh.shizukufix.hooks

import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.mjh.shizukufix.models.ShizukuFixConfig
import com.mjh.shizukufix.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】Background Service Injector
 *
 * 比 ServiceWatchdog 更激进的 Shizuku 保活方案：
 *  - 检测 Shizuku 服务死亡时注入重启请求
 *  - Hook ActivityManagerService.bindService 确保 Shizuku 绑定持久化
 *  - 在 Shizuku Application 生命周期中注入保活回调
 *
 * 此 Hook 比 ServiceWatchdogHook 更底层，直接干预 AMS 服务绑定。
 */
object BackgroundInjectorHook {

    private val SHIZUKU_SERVICE_CLASSES = arrayOf(
        "rikka.shizuku.ShizukuService",
        "moe.shizuku.api.ShizukuService",
        "rikka.shizuku.service.ShizukuService"
    )

    private var appContext: Context? = null
    private var injectionCount = 0
    private val maxInjections = 5

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: ShizukuFixConfig) {
        if (!cfg.backgroundInjectorEnabled) return
        LogX.i("【实验性】Background Service Injector 启动")

        hookApplicationOnCreate(lpparam)
        hookAMSBindService(lpparam)
        hookServiceLifecycle(lpparam)
    }

    /** Hook Application.onCreate 获取 Context 并注入首次保活 */
    private fun hookApplicationOnCreate(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            appContext = p.thisObject as? Context
                            if (appContext != null) {
                                LogX.d("【BackgroundInjector】Application 就绪，注入保活")
                                scheduleServiceRestart(appContext!!)
                            }
                        } catch (t: Throwable) {
                            LogX.e("【BackgroundInjector】初始化异常", t)
                        }
                    }
                })
            LogX.hookSuccess("BackgroundInjector", "Application.onCreate")
        } catch (e: Throwable) {
            LogX.hookFailed("BackgroundInjector", "Application.onCreate", e)
        }
    }

    /** Hook AMS.bindService 确保 Shizuku 绑定不会被系统回收 */
    private fun hookAMSBindService(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val amsCls = XposedHelpers.findClassIfExists(
                "com.android.server.am.ActivityManagerService", lpparam.classLoader
            )
            if (amsCls != null) {
                XposedHelpers.findAndHookMethod(
                    amsCls, "bindService",
                    android.app.IApplicationThread::class.java,
                    IBinder::class.java,
                    Intent::class.java,
                    String::class.java,
                    android.app.IServiceConnection::class.java,
                    Int::class.javaPrimitiveType,
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val intent = p.args[2] as? Intent ?: return
                                val serviceName = intent.component?.className ?: ""
                                if (SHIZUKU_SERVICE_CLASSES.any { it == serviceName }) {
                                    p.args[5] = android.content.Context.BIND_AUTO_CREATE or
                                            android.content.Context.BIND_IMPORTANT or
                                            0x00000008
                                    LogX.d("【BackgroundInjector】加固 Shizuku bindService 绑定标志")
                                }
                            } catch (_: Throwable) {}
                        }
                    })
                LogX.hookSuccess("ActivityManagerService", "bindService")
            }
        } catch (e: Throwable) {
            LogX.hookFailed("ActivityManagerService", "bindService", e)
        }
    }

    /** Hook Shizuku 服务生命周期，服务死亡时触发重启 */
    private fun hookServiceLifecycle(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in SHIZUKU_SERVICE_CLASSES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            try {
                XposedHelpers.findAndHookMethod(cls, "onDestroy", object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        LogX.w("【BackgroundInjector】检测到 $clsName 被销毁")
                        appContext?.let { ctx ->
                            if (injectionCount < maxInjections) {
                                injectServiceRestart(ctx)
                            } else {
                                LogX.w("【BackgroundInjector】已达最大注入次数 $maxInjections")
                            }
                        }
                    }
                })
                LogX.hookSuccess(clsName, "onDestroy")
            } catch (e: Throwable) {
                LogX.w("【BackgroundInjector】${e.message}")
            }
        }
    }

    /** 延迟调度 Shizuku 服务重启 */
    private fun scheduleServiceRestart(ctx: Context) {
        Thread {
            try {
                Thread.sleep(5000)
                ensureShizukuRunning(ctx)
            } catch (e: Throwable) {
                LogX.e("【BackgroundInjector】调度异常", e)
            }
        }.start()
    }

    /** 注入服务重启请求 */
    private fun injectServiceRestart(ctx: Context) {
        injectionCount++
        LogX.i("【BackgroundInjector】注入第 $injectionCount 次重启请求")
        for (clsName in SHIZUKU_SERVICE_CLASSES) {
            try {
                val cls = Class.forName(clsName)
                val intent = Intent(ctx, cls)
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                ctx.startService(intent)
                LogX.i("【BackgroundInjector】startService 重启: $clsName")
                return
            } catch (e: Throwable) {
                LogX.w("【BackgroundInjector】$clsName 不可达: ${e.message}")
            }
        }
    }

    /** 确保 Shizuku 运行中 */
    private fun ensureShizukuRunning(ctx: Context): Boolean {
        return try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val svcs = am.getRunningServices(Int.MAX_VALUE)
            val running = svcs.any {
                SHIZUKU_SERVICE_CLASSES.any { cls -> it.service.className.contains(cls.substringAfterLast(".")) }
            }
            if (!running) {
                LogX.w("【BackgroundInjector】Shizuku 服务未运行，尝试注入...")
                injectServiceRestart(ctx)
            } else {
                LogX.d("【BackgroundInjector】Shizuku 服务运行中")
            }
            running
        } catch (e: Throwable) {
            LogX.e("【BackgroundInjector】检查异常", e)
            false
        }
    }
}
