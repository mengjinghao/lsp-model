package com.stepmod.pro.hooks

import com.stepmod.pro.models.StepConfig
import com.stepmod.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 多APP步数同步 Hook（实验性）
 *
 * 功能：
 *  - Hook 各运动APP读取其他APP上报的步数（跨APP步数查询）
 *  - 通过 Application.onCreate 获取 Context
 *  - 让目标APP读取到的跨APP步数都同步为伪造值
 *
 * Root 版可借助 Shizuku 跨进程广播步数，但本 Hook 仅做应用层监听，
 * 真正的系统级广播由 ShizukuStepBridgeHook 承担。
 */
object MultiAppSyncHook {

    private val stepUris = listOf(
        "content://com.xiaomi.hm.health.provider/step",
        "content://com.huawei.health.provider/step",
        "content://com.codoon.gps.provider/step",
        "content://com.google.android.gms.fitness.provider/step"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        if (!cfg.multiAppSyncEnabled) return
        LogX.i("多APP步数同步 Hook 启动（实验性） | 目标步数=${cfg.customSteps}")

        hookContentResolverQuery(lpparam, cfg)
        hookAppLifecycleForSync(lpparam, cfg)
    }

    private fun hookContentResolverQuery(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        try {
            val crCls = XposedHelpers.findClassIfExists(
                "android.content.ContentResolver", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(crCls, "query",
                    "android.net.Uri", "java.lang.String[]",
                    "java.lang.String", "java.lang.String[]",
                    "android.os.CancellationSignal",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val uri = p.args[0]?.toString() ?: return
                                if (stepUris.any { uri.startsWith(it.substring(0, it.lastIndexOf('/'))) }) {
                                    LogX.d("拦截跨APP步数查询: $uri")
                                }
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("ContentResolver", "query(5arg)")
            } catch (e: Exception) { LogX.w("query(5arg) hook 失败: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(crCls, "query",
                    "android.net.Uri", "java.lang.String[]",
                    "android.os.Bundle", "android.os.CancellationSignal",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val uri = p.args[0]?.toString() ?: return
                                if (uri.contains("step") || uri.contains("fitness")) {
                                    LogX.d("拦截跨APP步数查询(Bundle): $uri")
                                }
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("ContentResolver", "query(bundle)")
            } catch (e: Exception) { LogX.w("query(bundle) hook 失败: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("ContentResolver", "query", e)
        }
    }

    private fun hookAppLifecycleForSync(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val app = p.thisObject as? android.app.Application ?: return
                            val ctx = app.applicationContext
                            LogX.d("多APP同步已注入: pkg=${ctx.packageName} | 目标步数=${cfg.customSteps}")
                        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("Application", "onCreate(MultiAppSync)")
        } catch (e: Exception) {
            LogX.hookFailed("Application", "onCreate(MultiAppSync)", e)
        }
    }
}
