package com.privacyguard.noroot.hooks

import android.content.Context
import com.privacyguard.noroot.models.PrivacyConfig
import com.privacyguard.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.URI

object NetworkLeakDetector {

    private val connectionLog = mutableListOf<JSONObject>()
    private val suspiciousDomains = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "googletagmanager.com", "googletagservices.com", "facebook.com/tr",
        "analytics.google.com", "sdkclick.com", "adjust.com", "appsflyer.com",
        "branch.io", "tune.com", "kochava.com", "singular.net",
        "trackingio.com", "umeng.com", "talkingdata.com", "bugly.qq.com",
        "baidumt.com", "mob.com", "mixpanel.com", "amplitude.com",
        "segment.io", "firebaseio.com", "crashlytics.com"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.networkLeakDetectorEnabled) return
        LogX.i("网络泄露检测器启动")

        hookSocketConnect(lpparam, cfg)
        hookUrlConnection(lpparam, cfg)
    }

    private fun hookSocketConnect(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        try {
            val socketClass = XposedHelpers.findClassIfExists(
                "java.net.Socket", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(socketClass, "connect",
                    "java.net.SocketAddress",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val addr = p.args[0] as? SocketAddress ?: return
                            val host = when (addr) {
                                is InetSocketAddress -> addr.hostString ?: addr.hostName ?: return
                                else -> return
                            }
                            recordConnection(lpparam, host, "Socket.connect")
                        }
                    })
                LogX.hookSuccess("Socket", "connect")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(socketClass, "connect",
                    "java.net.SocketAddress", Int::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val addr = p.args[0] as? SocketAddress ?: return
                            val host = when (addr) {
                                is InetSocketAddress -> addr.hostString ?: addr.hostName ?: return
                                else -> return
                            }
                            recordConnection(lpparam, host, "Socket.connect(timeout)")
                        }
                    })
                LogX.hookSuccess("Socket", "connect(timeout)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.e("NetworkLeakDetector Socket异常", e)
        }
    }

    private fun hookUrlConnection(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        try {
            val urlClass = XposedHelpers.findClassIfExists(
                "java.net.URL", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(urlClass, "openConnection",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val url = p.thisObject?.toString() ?: return
                            try {
                                val uri = URI(url)
                                val host = uri.host ?: return
                                recordConnection(lpparam, host, "URL.openConnection")
                            } catch (_: Throwable) {}
                        }
                    })
                LogX.hookSuccess("URL", "openConnection")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(urlClass, "openConnection",
                    "java.net.Proxy",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val url = p.thisObject?.toString() ?: return
                            try {
                                val uri = URI(url)
                                val host = uri.host ?: return
                                recordConnection(lpparam, host, "URL.openConnection(Proxy)")
                            } catch (_: Throwable) {}
                        }
                    })
                LogX.hookSuccess("URL", "openConnection(Proxy)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.e("NetworkLeakDetector URL异常", e)
        }
    }

    private fun recordConnection(lpparam: XC_LoadPackage.LoadPackageParam, host: String, source: String) {
        val isSuspicious = suspiciousDomains.any { host.contains(it, ignoreCase = true) }
        val now = System.currentTimeMillis()
        val entry = JSONObject().apply {
            put("host", host)
            put("source", source)
            put("package", lpparam.packageName)
            put("suspicious", isSuspicious)
            put("timestamp", now)
        }
        connectionLog.add(entry)

        if (isSuspicious) {
            LogX.w("[NetLeak] 可疑连接: $host (来源: $source, APP: ${lpparam.packageName})")
        } else {
            LogX.d("[NetLeak] 连接: $host (来源: $source)")
        }

        saveLog(lpparam)
    }

    private fun saveLog(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            if (connectionLog.size > 500) {
                connectionLog.removeAt(0)
            }
            val arr = JSONArray(connectionLog.toList())
            val logJson = JSONObject().apply {
                put("lastUpdate", System.currentTimeMillis())
                put("totalConnections", connectionLog.size)
                put("connections", arr)
            }
            val ctx = try {
                val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
                val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
                XposedHelpers.callMethod(cat, "getApplication") as? Context
            } catch (_: Throwable) { null }
            ctx?.getSharedPreferences("privacy_netleak_data", Context.MODE_PRIVATE)
                ?.edit()?.putString("netleak_log", logJson.toString())?.apply()
        } catch (e: Exception) { LogX.w("保存NetLeak日志异常: ${e.message}") }
    }
}
