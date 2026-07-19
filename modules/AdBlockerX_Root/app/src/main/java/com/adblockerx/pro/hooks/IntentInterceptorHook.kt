package com.adblockerx.pro.hooks

import android.content.Intent
import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.AdBlockList
import com.adblockerx.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】Intent 跳转拦截 Hook（Root 版同 NoRoot）
 *
 * Hook ContextWrapper.startActivity / startActivityForResult + Instrumentation.execStartActivity
 */
object IntentInterceptorHook {

    private val AD_INTENT_KEYWORDS = arrayOf(
        "ad", "ads", "advert", "banner", "splash",
        "doubleclick", "googlesyndication",
        "toutiao", "gdt", "baidu", "ksad"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.intentInterceptorEnabled) return
        LogX.i("【实验性】IntentInterceptorHook 启动（应用进程内）")

        hookContextStartActivity(lpparam)
        hookInstrumentationExecStartActivity(lpparam)
    }

    private fun hookContextStartActivity(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val cw = XposedHelpers.findClassIfExists(
                "android.content.ContextWrapper", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(cw, "startActivity",
                    Intent::class.java, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val intent = p.args.getOrNull(0) as? Intent ?: return
                            if (shouldBlockIntent(intent)) {
                                LogX.i("[Intent] 拦截 startActivity: ${intent.data} ${intent.action}")
                                p.result = null
                            }
                        }
                    })
                LogX.hookSuccess("ContextWrapper", "startActivity")
            } catch (_: Throwable) {}

            try {
                XposedHelpers.findAndHookMethod(cw, "startActivityForResult",
                    Intent::class.java, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val intent = p.args.getOrNull(0) as? Intent ?: return
                            if (shouldBlockIntent(intent)) {
                                LogX.i("[Intent] 拦截 startActivityForResult: ${intent.data}")
                                p.result = null
                            }
                        }
                    })
                LogX.hookSuccess("ContextWrapper", "startActivityForResult")
            } catch (_: Throwable) {}
        } catch (e: Throwable) {
            LogX.e("IntentInterceptorHook.ContextWrapper 异常", e)
        }
    }

    private fun hookInstrumentationExecStartActivity(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val instr = XposedHelpers.findClassIfExists(
                "android.app.Instrumentation", lpparam.classLoader) ?: return

            val methods = instr.declaredMethods.filter { it.name == "execStartActivity" }
            for (m in methods) {
                try {
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val intent = p.args.getOrNull(0) as? Intent ?: return
                            if (shouldBlockIntent(intent)) {
                                LogX.i("[Intent] 拦截 Instrumentation.execStartActivity: ${intent.data}")
                                p.result = null
                            }
                        }
                    })
                } catch (_: Throwable) {}
            }
            LogX.hookSuccess("Instrumentation", "execStartActivity x${methods.size}")
        } catch (e: Throwable) {
            LogX.e("IntentInterceptorHook.Instrumentation 异常", e)
        }
    }

    private fun shouldBlockIntent(intent: Intent): Boolean {
        val data = intent.data?.toString() ?: ""
        if (data.isNotBlank()) {
            val host = AdBlockList.extractHost(data)
            if (host != null && HostsFilterHook.isBlocked(host)) return true

            val lower = data.lowercase()
            if (AD_INTENT_KEYWORDS.any { lower.contains(it) }) return true
        }

        val action = intent.action?.lowercase() ?: ""
        if (action.isNotBlank() && AD_INTENT_KEYWORDS.any { action.contains(it) }) return true

        val cls = intent.component?.className?.lowercase() ?: ""
        if (cls.isNotBlank() && AD_INTENT_KEYWORDS.any { cls.contains(it) }) return true

        return false
    }
}
