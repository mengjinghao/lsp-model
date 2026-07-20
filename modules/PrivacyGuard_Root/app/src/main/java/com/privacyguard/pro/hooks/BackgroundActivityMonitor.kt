package com.privacyguard.pro.hooks

import android.content.Context
import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.LogX
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
        LogX.i("后台Activity监控启动: block=${cfg.blockBackgroundActivities}")

        hookActivityManager(lpparam, cfg)
    }

    private fun hookActivityManager(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        try {
            val amsClass = XposedHelpers.findClassIfExists(
                "com.android.server.am.ActivityManagerService", lpparam.classLoader)

            if (amsClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(amsClass, "startActivity",
                        "android.app.IApplicationThread", String::class.java,
                        "android.content.Intent", String::class.java,
                        "android.os.IBinder", String::class.java,
                        Int::class.java, Int::class.java,
                        "android.app.ProfilerInfo", "android.os.Bundle",
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val intent = p.args[2] as? android.content.Intent ?: return
                                val callingPackage = p.args[4] as? String ?: "unknown"
                                val now = System.currentTimeMillis()
                                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                val timeStr = sdf.format(Date(now))

                                val entry = JSONObject().apply {
                                    put("package", callingPackage)
                                    put("action", intent.action ?: "none")
                                    put("component", intent.component?.flattenToShortString() ?: "none")
                                    put("time", timeStr)
                                    put("timestamp", now)
                                }
                                activityLog.add(entry)
                                LogX.w("[BAM] 后台Activity启动: $callingPackage -> ${intent.component?.flattenToShortString()}")

                                if (cfg.blockBackgroundActivities) {
                                    LogX.w("[BAM] 拦截后台Activity: $callingPackage")
                                    p.result = false
                                }
                                saveLog(lpparam)
                            }
                        })
                    LogX.hookSuccess("ActivityManagerService", "startActivity")
                } catch (e: Exception) { LogX.w("AMS startActivity异常: ${e.message}") }
            }

            try {
                val activityClass = XposedHelpers.findClassIfExists(
                    "android.app.Activity", lpparam.classLoader)
                if (activityClass != null) {
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
                                    put("source", "Activity.startActivity")
                                }
                                activityLog.add(entry)
                                LogX.d("[BAM] startActivity: ${intent.component?.flattenToShortString()}")
                                saveLog(lpparam)
                            }
                        })
                    LogX.hookSuccess("Activity", "startActivity")
                }
            } catch (e: Exception) { LogX.w("Activity.startActivity异常: ${e.message}") }

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
