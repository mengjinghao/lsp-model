package com.adblockerx.pro.hooks

import android.view.View
import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 广告 SDK View 隐藏 Hook（Root 版同 NoRoot）
 *
 * Hook 21 个广告 SDK 候选类的构造方法 + setVisibility
 */
object AdViewHideHook {

    private val AD_VIEW_CLASS_CANDIDATES = listOf(
        "com.qq.e.ads.nativ.NativeExpressADView",
        "com.qq.e.ads.banner.BannerView",
        "com.qq.e.ads.interstitial.InterstitialAD",
        "com.qq.e.comm.plugin.splash.SplashAdView",
        "com.tencent.gdtad.api.AdView",
        "com.tencent.mobileqq.splashad.SplashADView",
        "com.baidu.mobads.api.BaiduAdView",
        "com.baidu.mobads.banner.BannerAdView",
        "com.baidu.mobads.interstitial.InterstitialAd",
        "com.bytedance.sdk.openadsdk.adapter.view.TTNativeAdView",
        "com.bytedance.sdk.openadsdk.core.widget.SplashAdView",
        "com.bytedance.sdk.openadsdk.core.widget.BannerAdView",
        "com.bytedance.sdk.openadsdk.core.widget.RewardVideoAd",
        "com.bykv.vk.openvk.adapter.view.TTNativeAdView",
        "com.google.android.gms.ads.AdView",
        "com.google.android.gms.ads.InterstitialAd",
        "com.google.android.gms.ads.formats.NativeAdView",
        "com.kwad.sdk.api.KsAdView",
        "com.kwad.sdk.content.KsContentAdView",
        "com.admobile.sdk.AdView",
        "com.anythink.sdk.AdView"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.adViewHideEnabled) return
        LogX.i("AdViewHideHook 启动（应用进程内，多候选类名容错）")

        var hooked = 0
        for (className in AD_VIEW_CLASS_CANDIDATES) {
            val clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader) ?: continue
            if (hookConstructor(clazz)) hooked++
            if (hookSetVisibility(clazz)) hooked++
        }
        LogX.i("AdViewHideHook 完成：命中 ${hooked} 个广告 View 类")
    }

    private fun hookConstructor(clazz: Class<*>): Boolean {
        return try {
            val constructors = clazz.declaredConstructors
            for (c in constructors) {
                try {
                    XposedBridge.hookMethod(c, object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val view = p.thisObject as? View ?: return
                            try {
                                view.visibility = View.GONE
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    })
                    LogX.d("[AdViewHide] Hook 构造: ${clazz.name}")
                } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
            }
            constructors.isNotEmpty()
        } catch (e: Throwable) {
            LogX.d("[AdViewHide] Hook 构造异常: ${clazz.name} - ${e.message}")
            false
        }
    }

    private fun hookSetVisibility(clazz: Class<*>): Boolean {
        return try {
            XposedHelpers.findAndHookMethod(clazz, "setVisibility",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val v = p.args.getOrNull(0) as? Int ?: return
                        if (v == View.VISIBLE) {
                            p.args[0] = View.GONE
                            LogX.d("[AdViewHide] 强制 GONE: ${clazz.name}")
                        }
                    }
                })
            true
        } catch (_: Throwable) {
            false
        }
    }
}
