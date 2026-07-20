package com.adblockerx.pro.hooks

import android.content.Context
import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONArray
import org.json.JSONObject

object AdPatternLearnHook {

    private val blockedRequests = mutableListOf<JSONObject>()
    private val learnedPatterns = mutableMapOf<String, Int>()
    private const val LEARN_THRESHOLD = 3

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.adPatternLearnEnabled) return
        LogX.i("AdPattern自学习启动")

        loadExistingPatterns(lpparam)
        hookBlockedUrlDetection(lpparam, cfg)
        hookAdViewDetection(lpparam, cfg)
    }

    private fun hookBlockedUrlDetection(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        try {
            val socketClass = XposedHelpers.findClassIfExists(
                "java.net.Socket", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(socketClass, "connect",
                    "java.net.SocketAddress",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val addr = p.args[0] as? java.net.SocketAddress ?: return
                            val host = when (addr) {
                                is java.net.InetSocketAddress -> addr.hostString ?: addr.hostName ?: return
                                else -> return
                            }
                            if (HostsFilterHook.isUrlBlocked(host)) {
                                recordBlockedDomain(lpparam, host, "Socket")
                            }
                        }
                    })
                LogX.hookSuccess("AdPatternLearn", "Socket.connect")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.e("AdPatternLearnHook Socket异常", e)
        }
    }

    private fun hookAdViewDetection(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        try {
            val inflaterClass = XposedHelpers.findClassIfExists(
                "android.view.LayoutInflater", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(inflaterClass, "inflate",
                    Int::class.java, "android.view.ViewGroup",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val result = p.result ?: return
                            val className = result.javaClass.name
                            if (className.lowercase().let { cls ->
                                    cls.contains("ad") || cls.contains("banner") ||
                                    cls.contains("nativead") || cls.contains("interstitial")
                                }) {
                                className.split(".").lastOrNull()?.let { viewName ->
                                    recordBlockedView(lpparam, viewName)
                                }
                            }
                        }
                    })
                LogX.hookSuccess("AdPatternLearn", "LayoutInflater.inflate")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.e("AdPatternLearnHook LayoutInflater异常", e)
        }
    }

    private fun recordBlockedDomain(lpparam: XC_LoadPackage.LoadPackageParam, domain: String, source: String) {
        val count = learnedPatterns.getOrDefault(domain, 0) + 1
        learnedPatterns[domain] = count

        val entry = JSONObject().apply {
            put("domain", domain)
            put("source", source)
            put("count", count)
            put("timestamp", System.currentTimeMillis())
        }
        blockedRequests.add(entry)

        if (count >= LEARN_THRESHOLD) {
            LogX.i("[AdPatternLearn] 建议加入黑名单: $domain (已拦截 $count 次)")
            saveSuggestedRule(lpparam, domain, count)
        }
        savePatterns(lpparam)
    }

    private fun recordBlockedView(lpparam: XC_LoadPackage.LoadPackageParam, viewName: String) {
        val count = learnedPatterns.getOrDefault("view:$viewName", 0) + 1
        learnedPatterns["view:$viewName"] = count

        if (count >= LEARN_THRESHOLD) {
            LogX.i("[AdPatternLearn] 建议隐藏View: $viewName (已出现 $count 次)")
            saveSuggestedView(lpparam, viewName, count)
        }
        savePatterns(lpparam)
    }

    private fun saveSuggestedRule(lpparam: XC_LoadPackage.LoadPackageParam, domain: String, count: Int) {
        try {
            val ctx = getContext(lpparam) ?: return
            val prefs = ctx.getSharedPreferences("adblockerx_learned", Context.MODE_PRIVATE)
            val existing = prefs.getString("suggested_rules", "[]") ?: "[]"
            val arr = try { JSONArray(existing) } catch (_: Throwable) { JSONArray() }
            var found = false
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optString("domain") == domain) {
                    obj.put("count", count)
                    obj.put("timestamp", System.currentTimeMillis())
                    found = true
                    break
                }
            }
            if (!found) {
                arr.put(JSONObject().apply {
                    put("domain", domain)
                    put("count", count)
                    put("type", "domain")
                    put("timestamp", System.currentTimeMillis())
                })
            }
            prefs.edit()?.putString("suggested_rules", arr.toString())?.apply()
        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
    }

    private fun saveSuggestedView(lpparam: XC_LoadPackage.LoadPackageParam, viewName: String, count: Int) {
        try {
            val ctx = getContext(lpparam) ?: return
            val prefs = ctx.getSharedPreferences("adblockerx_learned", Context.MODE_PRIVATE)
            val existing = prefs.getString("suggested_views", "[]") ?: "[]"
            val arr = try { JSONArray(existing) } catch (_: Throwable) { JSONArray() }
            var found = false
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optString("view") == viewName) {
                    obj.put("count", count)
                    found = true
                    break
                }
            }
            if (!found) {
                arr.put(JSONObject().apply {
                    put("view", viewName)
                    put("count", count)
                    put("type", "view")
                    put("timestamp", System.currentTimeMillis())
                })
            }
            prefs.edit()?.putString("suggested_views", arr.toString())?.apply()
        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
    }

    private fun savePatterns(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            if (blockedRequests.size > 300) {
                blockedRequests.removeAt(0)
            }
            val ctx = getContext(lpparam) ?: return
            val prefs = ctx.getSharedPreferences("adblockerx_learned", Context.MODE_PRIVATE)
            val arr = JSONArray(blockedRequests.toList())
            prefs.edit()?.putString("blocked_requests", arr.toString())?.apply()
            prefs.edit()?.putString("pattern_counts", JSONObject(learnedPatterns as Map<*, *>).toString())?.apply()
        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
    }

    private fun loadExistingPatterns(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val ctx = getContext(lpparam) ?: return
            val prefs = ctx.getSharedPreferences("adblockerx_learned", Context.MODE_PRIVATE)
            val json = prefs.getString("pattern_counts", null) ?: return
            val obj = JSONObject(json)
            val iter = obj.keys()
            while (iter.hasNext()) {
                val key = iter.next()
                learnedPatterns[key] = obj.getInt(key)
            }
        } catch (_: Throwable) {}
    }

    private fun getContext(lpparam: XC_LoadPackage.LoadPackageParam): Context? {
        return try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            XposedHelpers.callMethod(cat, "getApplication") as? Context
        } catch (_: Throwable) { null }
    }
}
