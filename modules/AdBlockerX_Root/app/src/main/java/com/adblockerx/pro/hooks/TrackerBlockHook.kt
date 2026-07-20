package com.adblockerx.pro.hooks

import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.ConfigManager
import com.adblockerx.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】追踪 SDK 拦截 Hook（Root 版同 NoRoot）
 *
 * Hook Umeng/TalkingData/Flurry/Bugly/BaiduMtj 等上报方法直接 return
 */
object TrackerBlockHook {

    private val TRACKER_METHODS: List<Pair<String, List<String>>> = listOf(
        "com.umeng.analytics.MobclickAgent" to listOf("onEvent", "onEventValue", "onProfileSignIn", "onProfileSignOff", "reportError"),
        "com.umeng.commonsdk.UMConfigure" to listOf("init", "getGid"),
        "com.tendcloud.tenddata.TCAgent" to listOf("onEvent", "onPageStart", "onPageEnd", "setReportUncaughtException"),
        "com.flurry.android.FlurryAgent" to listOf("onEvent", "onPageView", "onError", "logEvent"),
        "com.baidu.mobstat.StatService" to listOf("onEvent", "onPageStart", "onPageEnd", "traceEvent"),
        "com.qihoo.mobl.conn.ConnectionService" to listOf("reportEvent"),
        "com.tendcloud.appcpa.TalkingDataSDK" to listOf("onEvent", "onPageBegin", "onPageEnd"),
        // Sensors Data
        "com.sensorsdata.analytics.android.sdk.SensorsDataAPI" to listOf("track", "profileSet", "profileIncrement"),

        // GrowingIO
        "com.growingio.android.sdk.collection.GrowingIO" to listOf("track", "setUserId", "setVisitor"),

        // 神策
        "com.sensorsdata.analytics.android.sdk.SAConfigOptions" to listOf("enableLog"),

        // Google Firebase Analytics
        "com.google.firebase.analytics.FirebaseAnalytics" to listOf("logEvent", "setUserId", "setUserProperty"),

        // Google Analytics (旧版)
        "com.google.android.gms.analytics.Tracker" to listOf("send", "setScreenName"),

        // AppsFlyer
        "com.appsflyer.AppsFlyerLib" to listOf("trackEvent", "trackAppLaunch", "setCustomerId"),

        // Adjust
        "com.adjust.sdk.Adjust" to listOf("trackEvent", "trackAdRevenue", "setOfflineMode"),

        // OneSignal
        "com.onesignal.OneSignal" to listOf("sendTag", "sendOutcome", "setExternalUserId"),

        // Branch
        "io.branch.referral.Branch" to listOf("userCompletedAction", "sendCommerceEvent"),

        // Tencent MTA
        "com.tencent.mta.MTA" to listOf("trackEvent", "trackCustomEvent"),
        // Facebook App Events
        "com.facebook.appevents.AppEventsLogger" to listOf("logEvent", "logPurchase"),
        // ByteDance AppLog
        "com.bytedance.applog.AppLog" to listOf("onEvent", "onEventV3"),
        // Xiaomi Analytics
        "com.xiaomi.analytics.Analytics" to listOf("track", "trackEvent"),
        // CleverTap
        "com.clevertap.android.sdk.CleverTapAPI" to listOf("pushEvent", "profilePush")
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.trackerBlockEnabled) return
        LogX.i("【实验性】TrackerBlockHook 启动（应用进程内）")

        var hooked = 0
        for ((className, methods) in TRACKER_METHODS) {
            val clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader) ?: continue
            for (methodName in methods) {
                if (hookAllOverloads(clazz, methodName)) hooked++
            }
        }
        LogX.i("TrackerBlockHook 完成：已拦截 ${hooked} 个上报方法")
    }

    private fun hookAllOverloads(clazz: Class<*>, methodName: String): Boolean {
        return try {
            val methods = clazz.declaredMethods.filter { it.name == methodName }
            var success = false
            for (m in methods) {
                try {
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try { ConfigManager.incrementBlockedCount(1) } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                            LogX.d("[Tracker] 拦截上报: ${clazz.name}.$methodName")
                            val ret = m.returnType
                            p.result = when (ret) {
                                Void.TYPE -> null
                                Boolean::class.javaPrimitiveType -> false
                                Int::class.javaPrimitiveType -> 0
                                Long::class.javaPrimitiveType -> 0L
                                Float::class.javaPrimitiveType -> 0f
                                Double::class.javaPrimitiveType -> 0.0
                                else -> null
                            }
                        }
                    })
                    success = true
                } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
            }
            success
        } catch (e: Throwable) {
            LogX.d("[Tracker] Hook 异常: ${clazz.name}.$methodName - ${e.message}")
            false
        }
    }
}
