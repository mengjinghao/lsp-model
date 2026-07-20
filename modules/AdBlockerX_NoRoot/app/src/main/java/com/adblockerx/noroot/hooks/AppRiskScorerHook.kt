package com.adblockerx.noroot.hooks

import android.content.Context
import com.adblockerx.noroot.models.AdBlockConfig
import com.adblockerx.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONObject

object AppRiskScorerHook {

    private data class AppRiskProfile(
        var trackingDomains: Int = 0,
        var adSdkCount: Int = 0,
        var permissionCount: Int = 0,
        var dataExfiltrations: Int = 0
    )

    private val appProfiles = mutableMapOf<String, AppRiskProfile>()
    private val knownAdSdks = setOf(
        "com.google.android.gms.ads", "com.facebook.ads",
        "com.unity3d.ads", "com.applovin",
        "com.ironsource", "com.vungle",
        "com.chartboost", "com.inmobi",
        "com.mopub", "com.tapjoy",
        "com.bytedance.sdk", "com.qq.e",
        "com.baidu.mobads", "com.miui.zeus",
        "com.sigmob", "com.ksc.ad.sdk",
        "com.kwad", "com.yomob"
    )

    private val trackingDomains = setOf(
        "umeng.com", "talkingdata.com", "bugly.qq.com",
        "baidumt.com", "mob.com", "doubleclick.net",
        "googlesyndication.com", "facebook.com/tr",
        "appsflyer.com", "adjust.com", "branch.io",
        "mixpanel.com", "amplitude.com", "segment.io"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.appRiskScorerEnabled) return
        LogX.i("AppRisk评分器启动: ${lpparam.packageName}")

        val pkg = lpparam.packageName ?: return
        if (!appProfiles.containsKey(pkg)) {
            appProfiles[pkg] = AppRiskProfile()
        }

        hookPackageManager(lpparam, cfg)
        hookPermissions(lpparam, cfg)
        hookNetworkConnections(lpparam, cfg)
    }

    private fun hookPackageManager(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        try {
            val pmClass = XposedHelpers.findClassIfExists(
                "android.content.pm.PackageManager", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(pmClass, "getPackageInfo",
                    String::class.java, Int::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val pkg = p.args[0] as? String ?: return
                            detectAdSdks(pkg)
                        }
                    })
                LogX.hookSuccess("PackageManager", "getPackageInfo")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(pmClass, "getInstalledPackages",
                    Int::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val result = p.result as? List<*> ?: return
                            for (item in result) {
                                try {
                                    val pkgName = XposedHelpers.getObjectField(item, "packageName") as? String ?: continue
                                    detectAdSdks(pkgName)
                                } catch (_: Throwable) {}
                            }
                        }
                    })
                LogX.hookSuccess("PackageManager", "getInstalledPackages")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.e("AppRiskScorerHook PM异常", e)
        }
    }

    private fun hookPermissions(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        try {
            val contextClass = XposedHelpers.findClassIfExists(
                "android.content.ContextWrapper", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(contextClass, "checkSelfPermission",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val perm = p.args[0] as? String ?: return
                            val pkg = lpparam.packageName ?: return
                            val profile = appProfiles.getOrPut(pkg) { AppRiskProfile() }
                            profile.permissionCount++
                            if (profile.permissionCount % 5 == 0) {
                                calculateAndSaveScore(lpparam, pkg)
                            }
                        }
                    })
                LogX.hookSuccess("ContextWrapper", "checkSelfPermission")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.e("AppRiskScorerHook Permission异常", e)
        }
    }

    private fun hookNetworkConnections(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        try {
            val urlClass = XposedHelpers.findClassIfExists(
                "java.net.URL", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(urlClass, "openConnection",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val url = p.thisObject?.toString() ?: return
                            val pkg = lpparam.packageName ?: return
                            val profile = appProfiles.getOrPut(pkg) { AppRiskProfile() }

                            if (trackingDomains.any { url.contains(it, ignoreCase = true) }) {
                                profile.trackingDomains++
                                LogX.d("[RiskScorer] 追踪域名: $url")
                            }

                            if (knownAdSdks.any { url.contains(it, ignoreCase = true) }) {
                                profile.dataExfiltrations++
                                LogX.w("[RiskScorer] 疑似数据外泄: $url")
                            }

                            calculateAndSaveScore(lpparam, pkg)
                        }
                    })
                LogX.hookSuccess("URL", "openConnection")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.e("AppRiskScorerHook Network异常", e)
        }
    }

    private fun detectAdSdks(pkgName: String) {
        if (knownAdSdks.any { pkgName.startsWith(it) || pkgName.contains(it) }) {
            val targetPkg = com.adblockerx.noroot.XposedLoader.currentPkg ?: return
            val profile = appProfiles.getOrPut(targetPkg) { AppRiskProfile() }
            profile.adSdkCount++
            LogX.d("[RiskScorer] 检测到广告SDK: $pkgName 在APP $targetPkg")
        }
    }

    private fun calculateAndSaveScore(lpparam: XC_LoadPackage.LoadPackageParam, pkg: String) {
        val profile = appProfiles[pkg] ?: return
        var score = 0
        score += (profile.trackingDomains * 10).coerceAtMost(40)
        score += (profile.adSdkCount * 15).coerceAtMost(30)
        score += (profile.permissionCount / 5 * 5).coerceAtMost(15)
        score += (profile.dataExfiltrations * 10).coerceAtMost(15)
        score = score.coerceIn(0, 100)

        try {
            val ctx = try {
                val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
                val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
                XposedHelpers.callMethod(cat, "getApplication") as? Context
            } catch (_: Throwable) { null }
            if (ctx == null) return

            val riskJson = JSONObject().apply {
                put("package", pkg)
                put("riskScore", score)
                put("trackingDomains", profile.trackingDomains)
                put("adSdkCount", profile.adSdkCount)
                put("permissionCount", profile.permissionCount)
                put("dataExfiltrations", profile.dataExfiltrations)
                put("timestamp", System.currentTimeMillis())
            }

            val prefs = ctx.getSharedPreferences("adblockerx_risk_scores", Context.MODE_PRIVATE)
            val existing = prefs.getString("app_scores", "{}") ?: "{}"
            val obj = try { JSONObject(existing) } catch (_: Throwable) { JSONObject() }
            obj.put(pkg, riskJson)
            prefs.edit()?.putString("app_scores", obj.toString())?.apply()

            val level = when {
                score >= 70 -> "高危"
                score >= 40 -> "中等"
                else -> "低风险"
            }
            LogX.i("[RiskScorer] $pkg 风险评分: $score/100 ($level)")
        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
    }
}
