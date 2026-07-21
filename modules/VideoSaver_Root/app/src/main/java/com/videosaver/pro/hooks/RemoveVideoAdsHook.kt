package com.videosaver.pro.hooks

import com.videosaver.pro.models.VideoConfig
import com.videosaver.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】去视频广告 Hook（Root 版，应用层）
 *
 * 与 NoRoot 版逻辑相同。Root 版可配合 GlobalVideoAdBlockHook（修改 hosts）实现更深层的广告屏蔽。
 */
object RemoveVideoAdsHook {

    private val BYTEDANCE_AD_CANDIDATES = arrayOf(
        "com.bytedance.sdk.openadsdk.AdSlot",
        "com.bytedance.sdk.openadsdk.TTAdManager",
        "com.bytedance.sdk.openadsdk.core.AdManager",
        "com.ss.android.ad.landing.LandingPageHelper",
        "com.ss.android.downloadlib.DownloadService"
    )

    private val KUAISHOU_AD_CANDIDATES = arrayOf(
        "com.kwad.sdk.api.KsAdSDK",
        "com.kwad.sdk.core.admodel.AdInfo",
        "com.kwad.components.ad.interstitial.InterstitialAd"
    )

    private val TENCENT_AD_CANDIDATES = arrayOf(
        "com.qq.e.ads.InterstitialAD",
        "com.qq.e.comm.adevent.ADListener",
        "com.qq.e.ads.cfg.AdRequest"
    )

    private val AD_VIEW_CANDIDATES = arrayOf(
        "com.bytedance.sdk.openadsdk.widget.TTRewardVideoAd",
        "com.kwad.components.ad.interstitial.InterstitialAdView",
        "com.qq.e.ads.InterstitialADView"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        if (!cfg.removeAdsEnabled) return
        LogX.i("【实验性】去视频广告 Hook 启动（Root 版）")

        hookBytedanceAd(lpparam)
        hookKuaishouAd(lpparam)
        hookTencentAd(lpparam)
        hookAdViewShow(lpparam)
        hookAdUrlRequest(lpparam)
    }

    private fun hookBytedanceAd(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in BYTEDANCE_AD_CANDIDATES) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                for (methodName in arrayOf("loadAd", "loadFeedAd", "loadRewardVideoAd", "loadInterstitialAd")) {
                    try {
                        XposedHelpers.findAndHookMethod(cls, methodName,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(p: MethodHookParam) {
                                    LogX.d("字节广告方法已阻断: $methodName")
                                    p.result = null
                                }
                            })
                        LogX.hookSuccess(clsName, methodName)
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
        }
    }

    private fun hookKuaishouAd(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in KUAISHOU_AD_CANDIDATES) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                for (methodName in arrayOf("loadAd", "loadInterstitialAd", "loadRewardVideoAd")) {
                    try {
                        XposedHelpers.findAndHookMethod(cls, methodName,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(p: MethodHookParam) {
                                    LogX.d("快手广告方法已阻断: $methodName")
                                    p.result = null
                                }
                            })
                        LogX.hookSuccess(clsName, methodName)
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
        }
    }

    private fun hookTencentAd(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in TENCENT_AD_CANDIDATES) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                for (methodName in arrayOf("loadAd", "loadInterstitial")) {
                    try {
                        XposedHelpers.findAndHookMethod(cls, methodName,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(p: MethodHookParam) {
                                    LogX.d("腾讯广告方法已阻断: $methodName")
                                    p.result = null
                                }
                            })
                        LogX.hookSuccess(clsName, methodName)
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
        }
    }

    private fun hookAdViewShow(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in AD_VIEW_CANDIDATES) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                for (methodName in arrayOf("show", "showAd", "showInterstitial", "showRewardVideo")) {
                    try {
                        XposedHelpers.findAndHookMethod(cls, methodName,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(p: MethodHookParam) {
                                    LogX.d("广告 View 显示已阻断: ${clsName.substringAfterLast('.')}.$methodName")
                                    p.result = null
                                }
                            })
                        LogX.hookSuccess(clsName, methodName)
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
        }
    }

    private fun hookAdUrlRequest(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val urlCls = XposedHelpers.findClassIfExists("java.net.URL", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(urlCls, "openConnection",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val urlObj = p.thisObject
                                val field = urlObj.javaClass.getDeclaredField("host")
                                field.isAccessible = true
                                val host = field.get(urlObj) as? String ?: return
                                if (isAdDomain(host)) {
                                    LogX.d("广告域名请求已阻断: $host")
                                    p.result = null
                                }
                            } catch (_: Throwable) { }
                        }
                    })
                LogX.hookSuccess("URL", "openConnection")
            } catch (_: Throwable) { }
        } catch (_: Throwable) { }
    }

    private val AD_DOMAINS = listOf(
        "pangolin-sdk-toutiao", "ad.toutiao.com", "is.snssdk.com",
        "dsp.toutiao.com", "pglstatp-toutiao.com",
        "ad.toutiaocloud.com", "adsame.cn", "googleads.g.doubleclick.net",
        "cm.adkmob.com", "ad-cn.joydog.com",
        "i.l.inmobicdn.cn", "sdk.e.qq.com"
    )

    private fun isAdDomain(host: String): Boolean {
        if (host.isBlank()) return false
        return AD_DOMAINS.any { host.contains(it, ignoreCase = true) }
    }
}
