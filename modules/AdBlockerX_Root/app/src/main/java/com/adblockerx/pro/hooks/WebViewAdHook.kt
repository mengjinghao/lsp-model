package com.adblockerx.pro.hooks

import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.ByteArrayInputStream

/**
 * WebView 广告拦截 Hook（Root 版同 NoRoot，应用层）
 *
 * 拦截策略：
 *  1. WebViewClient.shouldOverrideUrlLoading：拦截广告页跳转
 *  2. WebViewClient.shouldInterceptRequest：对广告 URL 返回 404 空 WebResourceResponse
 *  3. WebView.loadUrl：拦截广告页加载
 *  4. WebViewClient.onPageFinished：注入 CSS/JS 隐藏常见广告元素（可选）
 */
object WebViewAdHook {

    private val HIDE_AD_JS = """
        (function() {
            try {
                var selectors = [
                    '[id*="ad" i]', '[class*="ad" i]', '[id*="banner" i]',
                    'iframe[src*="doubleclick"]', 'iframe[src*="googlesyndication"]',
                    'ins.adsbygoogle', 'div[class*="banner"]', 'div[id*="sponsor"]'
                ];
                var css = selectors.join(',') + ' { display:none !important; visibility:hidden !important; height:0 !important; width:0 !important; }';
                var style = document.createElement('style');
                style.type = 'text/css';
                style.appendChild(document.createTextNode(css));
                document.head.appendChild(style);
            } catch(e) {}
        })();
    """.trimIndent()

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.webviewAdEnabled) return
        LogX.i("WebViewAdHook 启动（应用进程内）")

        hookShouldOverrideUrlLoading(lpparam)
        hookShouldInterceptRequest(lpparam)
        hookLoadUrl(lpparam)
        if (cfg.injectJsEnabled) hookOnPageFinished(lpparam)
        if (cfg.x5WebViewEnabled) hookX5WebView(lpparam, cfg)
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
                            if (HostsFilterHook.isUrlBlocked(url)) {
                                LogX.i("[WebView] 拦截跳转: $url")
                                p.result = true
                            }
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

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
                            if (HostsFilterHook.isUrlBlocked(url)) {
                                LogX.i("[WebView] 拦截跳转: $url")
                                p.result = true
                            }
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.e("WebViewAdHook.shouldOverrideUrlLoading 异常", e)
        }
    }

    private fun hookShouldInterceptRequest(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val wvcClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebViewClient", lpparam.classLoader) ?: return
            val wrrClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebResourceRequest", lpparam.classLoader) ?: return
            val wrrRespClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebResourceResponse", lpparam.classLoader) ?: return

            XposedHelpers.findAndHookMethod(wvcClass, "shouldInterceptRequest",
                "android.webkit.WebView", wrrClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val req = p.args.getOrNull(1) ?: return
                        val url = try {
                            XposedHelpers.callMethod(req, "getUrl")?.toString()
                        } catch (_: Throwable) { null } ?: return
                        if (!HostsFilterHook.isUrlBlocked(url)) return
                        try {
                            val empty = ByteArrayInputStream(ByteArray(0))
                            val resp = wrrRespClass.getConstructor(
                                String::class.java,
                                String::class.java,
                                java.io.InputStream::class.java
                            ).newInstance("text/plain", "utf-8", empty)
                            XposedHelpers.callMethod(resp, "setStatusCode", 404)
                            try { XposedHelpers.callMethod(resp, "setReasonPhrase", "Not Found") } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                            LogX.i("[WebView] 拦截请求 404: $url")
                            p.result = resp
                        } catch (e: Throwable) {
                            LogX.e("[WebView] 构造 404 响应异常: $url", e)
                        }
                    }
                })
        } catch (e: Throwable) {
            LogX.e("WebViewAdHook.shouldInterceptRequest 异常", e)
        }
    }

    private fun hookLoadUrl(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val wvClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebView", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(wvClass, "loadUrl",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val url = p.args.getOrNull(0) as? String ?: return
                            if (HostsFilterHook.isUrlBlocked(url)) {
                                LogX.i("[WebView] 拦截 loadUrl: $url")
                                p.result = null
                            }
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(wvClass, "loadUrl",
                    String::class.java, MutableMap::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val url = p.args.getOrNull(0) as? String ?: return
                            if (HostsFilterHook.isUrlBlocked(url)) {
                                LogX.i("[WebView] 拦截 loadUrl(Map): $url")
                                p.result = null
                            }
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(wvClass, "loadDataWithBaseURL",
                    String::class.java, String::class.java,
                    String::class.java, String::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val baseUrl = p.args.getOrNull(0) as? String ?: return
                            if (HostsFilterHook.isUrlBlocked(baseUrl)) {
                                LogX.i("[WebView] 拦截 loadDataWithBaseURL: $baseUrl")
                                p.result = null
                            }
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.e("WebViewAdHook.loadUrl 异常", e)
        }
    }

    private fun hookOnPageFinished(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val wvcClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebViewClient", lpparam.classLoader) ?: return
            XposedHelpers.findAndHookMethod(wvcClass, "onPageFinished",
                "android.webkit.WebView", String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val view = p.args.getOrNull(0) ?: return
                        try {
                            XposedHelpers.callMethod(view, "evaluateJavascript",
                                HIDE_AD_JS, null as Any?)
                            LogX.d("[WebView] 已注入广告隐藏 JS")
                        } catch (e: Throwable) {
                            LogX.d("[WebView] 注入 JS 失败: ${e.message}")
                        }
                    }
                })
        } catch (e: Throwable) {
            LogX.e("WebViewAdHook.onPageFinished 异常", e)
        }
    }

    // ===== X5 WebView (Tencent) hooks =====

    private fun hookX5WebView(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        LogX.i("WebViewAdHook X5 WebView hooks 启动")
        try {
            val x5WvcClass = XposedHelpers.findClassIfExists(
                "com.tencent.smtt.sdk.WebViewClient", lpparam.classLoader)
            val x5WvClass = XposedHelpers.findClassIfExists(
                "com.tencent.smtt.sdk.WebView", lpparam.classLoader)

            if (x5WvcClass == null && x5WvClass == null) {
                LogX.d("[X5] X5 WebView 类未找到，跳过")
                return
            }

            // Hook shouldOverrideUrlLoading (String API)
            if (x5WvcClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(x5WvcClass, "shouldOverrideUrlLoading",
                        x5WvClass ?: "com.tencent.smtt.sdk.WebView", String::class.java,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val url = p.args.getOrNull(1) as? String ?: return
                                if (HostsFilterHook.isUrlBlocked(url)) {
                                    LogX.i("[X5] 拦截跳转: $url")
                                    p.result = true
                                }
                            }
                        })
                    LogX.d("[X5] 已 Hook shouldOverrideUrlLoading(String)")
                } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

                // Hook shouldOverrideUrlLoading (WebResourceRequest API)
                try {
                    val wrrClass = XposedHelpers.findClassIfExists(
                        "com.tencent.smtt.export.external.interfaces.WebResourceRequest",
                        lpparam.classLoader)
                    if (wrrClass != null) {
                        XposedHelpers.findAndHookMethod(x5WvcClass, "shouldOverrideUrlLoading",
                            x5WvClass ?: "com.tencent.smtt.sdk.WebView", wrrClass,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(p: MethodHookParam) {
                                    val req = p.args.getOrNull(1) ?: return
                                    val url = try {
                                        XposedHelpers.callMethod(req, "getUrl")?.toString()
                                    } catch (_: Throwable) { null } ?: return
                                    if (HostsFilterHook.isUrlBlocked(url)) {
                                        LogX.i("[X5] 拦截跳转(WRR): $url")
                                        p.result = true
                                    }
                                }
                            })
                        LogX.d("[X5] 已 Hook shouldOverrideUrlLoading(WRR)")
                    }
                } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

                // Hook shouldInterceptRequest
                try {
                    val wrrClass = XposedHelpers.findClassIfExists(
                        "com.tencent.smtt.export.external.interfaces.WebResourceRequest",
                        lpparam.classLoader)
                    val wrrRespClass = XposedHelpers.findClassIfExists(
                        "com.tencent.smtt.export.external.interfaces.WebResourceResponse",
                        lpparam.classLoader)
                    if (wrrClass != null && wrrRespClass != null) {
                        XposedHelpers.findAndHookMethod(x5WvcClass, "shouldInterceptRequest",
                            x5WvClass ?: "com.tencent.smtt.sdk.WebView", wrrClass,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(p: MethodHookParam) {
                                    val req = p.args.getOrNull(1) ?: return
                                    val url = try {
                                        XposedHelpers.callMethod(req, "getUrl")?.toString()
                                    } catch (_: Throwable) { null } ?: return
                                    if (!HostsFilterHook.isUrlBlocked(url)) return
                                    try {
                                        val empty = ByteArrayInputStream(ByteArray(0))
                                        val resp = wrrRespClass.getConstructor(
                                            String::class.java,
                                            String::class.java,
                                            java.io.InputStream::class.java
                                        ).newInstance("text/plain", "utf-8", empty)
                                        XposedHelpers.callMethod(resp, "setStatusCode", 404)
                                        try { XposedHelpers.callMethod(resp, "setReasonPhrase", "Not Found") } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                                        LogX.i("[X5] 拦截请求 404: $url")
                                        p.result = resp
                                    } catch (e: Throwable) {
                                        LogX.e("[X5] 构造 404 响应异常: $url", e)
                                    }
                                }
                            })
                        LogX.d("[X5] 已 Hook shouldInterceptRequest")
                    }
                } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

                // Hook onPageFinished (JS injection)
                if (cfg.injectJsEnabled) {
                    try {
                        XposedHelpers.findAndHookMethod(x5WvcClass, "onPageFinished",
                            x5WvClass ?: "com.tencent.smtt.sdk.WebView", String::class.java,
                            object : XC_MethodHook() {
                                override fun afterHookedMethod(p: MethodHookParam) {
                                    val view = p.args.getOrNull(0) ?: return
                                    try {
                                        XposedHelpers.callMethod(view, "evaluateJavascript",
                                            HIDE_AD_JS, null as Any?)
                                        LogX.d("[X5] 已注入广告隐藏 JS")
                                    } catch (e: Throwable) {
                                        LogX.d("[X5] 注入 JS 失败: ${e.message}")
                                    }
                                }
                            })
                        LogX.d("[X5] 已 Hook onPageFinished")
                    } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                }
            }

            // Hook loadUrl on X5 WebView
            if (x5WvClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(x5WvClass, "loadUrl",
                        String::class.java,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val url = p.args.getOrNull(0) as? String ?: return
                                if (HostsFilterHook.isUrlBlocked(url)) {
                                    LogX.i("[X5] 拦截 loadUrl: $url")
                                    p.result = null
                                }
                            }
                        })
                    LogX.d("[X5] 已 Hook loadUrl(String)")
                } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
            }

        } catch (e: Throwable) {
            LogX.e("WebViewAdHook.hookX5WebView 异常", e)
        }
    }
}
