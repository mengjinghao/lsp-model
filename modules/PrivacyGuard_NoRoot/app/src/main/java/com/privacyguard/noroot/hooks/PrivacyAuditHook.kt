package com.privacyguard.noroot.hooks

import android.content.Context
import com.privacyguard.noroot.models.PrivacyConfig
import com.privacyguard.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONArray
import org.json.JSONObject

object PrivacyAuditHook {

    private val auditLog = mutableListOf<JSONObject>()
    private var initialized = false

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.privacyAuditEnabled) return
        LogX.i("隐私审计报告启动")

        if (!initialized) {
            initialized = true
            hookAppOpsManager(lpparam, cfg)
        }
    }

    private fun hookAppOpsManager(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        try {
            val aom = XposedHelpers.findClassIfExists(
                "android.app.AppOpsManager", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(aom, "checkOp",
                    String::class.java, Int::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val op = p.args[0] as? String ?: return
                            val pkg = p.args[2] as? String ?: return
                            val now = System.currentTimeMillis()
                            val entry = JSONObject().apply {
                                put("op", op)
                                put("package", pkg)
                                put("timestamp", now)
                            }
                            auditLog.add(entry)
                            LogX.d("[PrivacyAudit] $pkg -> $op")
                            saveAuditData(lpparam)
                        }
                    })
                LogX.hookSuccess("AppOpsManager", "checkOp")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(aom, "checkOp",
                    Int::class.java, Int::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val pkg = p.args[2] as? String ?: return
                            val now = System.currentTimeMillis()
                            val entry = JSONObject().apply {
                                put("op", "opCode:" + (p.args[0] as? Int ?: -1))
                                put("package", pkg)
                                put("timestamp", now)
                            }
                            auditLog.add(entry)
                            saveAuditData(lpparam)
                        }
                    })
                LogX.hookSuccess("AppOpsManager", "checkOp(Int)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(aom, "noteOp",
                    String::class.java, Int::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val op = p.args[0] as? String ?: return
                            val pkg = p.args[2] as? String ?: return
                            val now = System.currentTimeMillis()
                            val entry = JSONObject().apply {
                                put("op", op)
                                put("package", pkg)
                                put("noteType", "start")
                                put("timestamp", now)
                            }
                            auditLog.add(entry)
                            saveAuditData(lpparam)
                        }
                    })
                LogX.hookSuccess("AppOpsManager", "noteOp")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(aom, "finishOp",
                    String::class.java, Int::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val op = p.args[0] as? String ?: return
                            val pkg = p.args[2] as? String ?: return
                            val now = System.currentTimeMillis()
                            val entry = JSONObject().apply {
                                put("op", op)
                                put("package", pkg)
                                put("noteType", "finish")
                                put("timestamp", now)
                            }
                            auditLog.add(entry)
                            saveAuditData(lpparam)
                        }
                    })
                LogX.hookSuccess("AppOpsManager", "finishOp")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

        } catch (e: Exception) {
            LogX.hookFailed("AppOpsManager", "audit", e)
        }
    }

    private fun saveAuditData(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            if (auditLog.size > 500) {
                auditLog.removeAt(0)
            }
            val arr = JSONArray(auditLog.toList())
            val auditJson = JSONObject().apply {
                put("lastUpdate", System.currentTimeMillis())
                put("totalEvents", auditLog.size)
                put("events", arr)
            }
            val ctx = try {
                val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
                val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
                XposedHelpers.callMethod(cat, "getApplication") as? Context
            } catch (_: Throwable) { null }
            ctx?.getSharedPreferences("privacy_audit_data", Context.MODE_PRIVATE)
                ?.edit()?.putString("audit_report", auditJson.toString())?.apply()
        } catch (e: Exception) { LogX.w("保存审计数据异常: ${e.message}") }
    }
}
