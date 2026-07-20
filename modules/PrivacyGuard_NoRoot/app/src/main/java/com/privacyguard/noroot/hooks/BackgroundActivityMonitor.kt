package com.privacyguard.noroot.hooks

import android.content.Context
import com.privacyguard.noroot.models.PrivacyConfig
import com.privacyguard.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackgroundActivityMonitor {

    private val activityLog = mutableListOf<JSONObject>()

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.backgroundActivityMonitorEnabled) return
        LogX.i("后台Activity监控启动（NoRoot: 仅日志不拦截）")

        hookActivityStart(lpparam, cfg)
    }

    private fun hookActivityStart(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        try {
            val activityClass = XposedHelpers.findClassIfExists(
                "android.app.Activity", lpparam.classLoader)
            if (activityClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(activityClass, "startActivity",
                        "android.content.Intent",
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val intent = p.args[0] as? android.content.Intent ?: return
                                val now = System.currentTimeMillis()
                                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                val timeStr = sdf.format(Date(now))

                                val entry = JSONObject().apply {
                                    put("package", lpparam.packageName)
                                    put("action", intent.action ?: "none")
                                    put("component", intent.component?.flattenToShortString() ?: "none")
                                    put("time", timeStr)
                                    put("timestamp", now)
                                }
                                activityLog.add(entry)
                                LogX.d("[BAM] startActivity: $lpparam.packageName -> ${intent.component?.flattenToShortString()}")
                                saveLog(lpparam)
                            }
                        })
                    LogX.hookSuccess("Activity", "startActivity")
                } catch (e: Exception) { LogX.w("异常: ${e.message}") }
            }

            val contextClass = XposedHelpers.findClassIfExists(
                "android.content.ContextWrapper", lpparam.classLoader)
            if (contextClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(contextClass, "startActivity",
                        "android.content.Intent",
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val intent = p.args[0] as? android.content.Intent ?: return
                                val now = System.currentTimeMillis()
                                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                val timeStr = sdf.format(Date(now))

                                val entry = JSONObject().apply {
                                    put("package", lpparam.packageName)
                                    put("action", intent.action ?: "none")
                                    put("component", intent.component?.flattenToShortString() ?: "none")
                                    put("time", timeStr)
                                    put("timestamp", now)
                                    put("source", "ContextWrapper")
                                }
                                activityLog.add(entry)
                                saveLog(lpparam)
                            }
                        })
                    LogX.hookSuccess("ContextWrapper", "startActivity")
                } catch (e: Exception) { LogX.w("异常: ${e.message}") }
            }
        } catch (e: Exception) {
            LogX.e("BackgroundActivityMonitor异常", e)
        }
    }

    private fun saveLog(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            if (activityLog.size > 200) {
                activityLog.removeAt(0)
            }
            val arr = JSONArray(activityLog.toList())
            val logJson = JSONObject().apply {
                put("lastUpdate", System.currentTimeMillis())
                put("totalEvents", activityLog.size)
                put("activities", arr)
            }
            val ctx = try {
                val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
                val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
                XposedHelpers.callMethod(cat, "getApplication") as? Context
            } catch (_: Throwable) { null }
            ctx?.getSharedPreferences("privacy_bam_data", Context.MODE_PRIVATE)
                ?.edit()?.putString("bam_log", logJson.toString())?.apply()
        } catch (e: Exception) { LogX.w("保存BAM日志异常: ${e.message}") }
    }
}
