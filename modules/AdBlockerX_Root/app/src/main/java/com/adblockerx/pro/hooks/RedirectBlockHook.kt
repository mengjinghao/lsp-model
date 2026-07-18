package com.adblockerx.pro.hooks

import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.AdBlockList
import com.adblockerx.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】重定向拦截 Hook（Root 版同 NoRoot）
 *
 * Hook WebViewClient.shouldOverrideUrlLoading 拦截广告跳转深链 / click 关键字
 */
object RedirectBlockHook {

    private val REDIRECT_KEYWORDS = arrayOf(
        "/ad/", "/ads/", "click", "redirect", "tracker",
        "doubleclick", "googlesyndication",
        "ad.toutiao", "pdp.toutiao",
        "pgdt.ugdtimg", "t.gdt.qq",
        "adsame", "mediav",
        "/jump", "/adclick", "/click?",
        "admaster", "miaozhen"
    )

    private val AD_DEEPLINK_SCHEMES = arrayOf(
        "tbopen://", "tmall://", "jd://", "openapp://",
        "wechat://", "alipays://", "pinduoduo://",
        "snssdk1128://", "snssdk1112://"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.redirectBlockEnabled) return
        LogX.i("【实验性】RedirectBlockHook 启动（应用进程内）")

        hookShouldOverrideUrlLoading(lpparam)
        hookWebViewLoadUrlForRedirect(lpparam)
    }

    private fun hookShouldOverrideUrlLoading(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val wvcClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebViewClient", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(wvcClass, "shouldOverrideUrlLoading",
                    "android.webkit.WebView", String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val url = p.args.getOrNull(1) as? String ?: return
                            if (shouldBlock(url)) {
                                LogX.i("[Redirect] 拦截跳转: $url")
                                p.result = true
                            }
                        }
                    })
                LogX.hookSuccess("WebViewClient", "shouldOverrideUrlLoading(String)")
            } catch (_: Throwable) {}

            try {
                val wrrClass = XposedHelpers.findClassIfExists(
                    "android.webkit.WebResourceRequest", lpparam.classLoader) ?: return
                XposedHelpers.findAndHookMethod(wvcClass, "shouldOverrideUrlLoading",
                    "android.webkit.WebView", wrrClass,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val req = p.args.getOrNull(1) ?: return
                            val url = try {
                                XposedHelpers.callMethod(req, "getUrl")?.toString()
                            } catch (_: Throwable) { null } ?: return
                            if (shouldBlock(url)) {
                                LogX.i("[Redirect] 拦截跳转: $url")
                                p.result = true
                            }
                        }
                    })
                LogX.hookSuccess("WebViewClient", "shouldOverrideUrlLoading(Request)")
            } catch (_: Throwable) {}
        } catch (e: Throwable) {
            LogX.e("RedirectBlockHook.shouldOverrideUrlLoading 异常", e)
        }
    }

    private fun hookWebViewLoadUrlForRedirect(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val wvClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebView", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(wvClass, "loadUrl",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val url = p.args.getOrNull(0) as? String ?: return
                            if (shouldBlock(url)) {
                                LogX.i("[Redirect] 拦截 loadUrl: $url")
                                p.result = null
                            }
                        }
                    })
                LogX.hookSuccess("WebView", "loadUrl(redirect)")
            } catch (_: Throwable) {}
        } catch (e: Throwable) {
            LogX.e("RedirectBlockHook.loadUrl 异常", e)
        }
    }

    private fun shouldBlock(url: String): Boolean {
        if (url.isBlank()) return false

        val host = AdBlockList.extractHost(url)
        if (host != null && HostsFilterHook.isBlocked(host)) return true

        val lower = url.lowercase()
        if (REDIRECT_KEYWORDS.any { lower.contains(it) }) return true

        if (AD_DEEPLINK_SCHEMES.any { lower.startsWith(it) }) {
            if (REDIRECT_KEYWORDS.any { lower.contains(it) }) return true
        }

        return false
    }
}
