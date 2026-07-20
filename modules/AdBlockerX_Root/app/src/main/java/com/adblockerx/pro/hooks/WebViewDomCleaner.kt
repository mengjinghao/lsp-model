package com.adblockerx.pro.hooks

import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object WebViewDomCleaner {

    private val DOM_CLEAN_JS = """
        (function() {
            try {
                function cleanAds() {
                    var adSelectors = [
                        '[id*="ad" i]:not([id*="head"]):not([id*="loader"])',
                        '[class*="ad" i]:not([class*="header"]):not([class*="loading"])',
                        '[id*="banner" i]',
                        '[class*="banner" i]',
                        'iframe[src*="doubleclick"]',
                        'iframe[src*="googlesyndication"]',
                        'iframe[src*="ads"]',
                        'ins.adsbygoogle',
                        'div[data-ad]',
                        'div[data-adslot]',
                        'div[id*="google_ads"]',
                        'div[id*="sponsor"]',
                        '[aria-label*="广告" i]',
                        '[aria-label*="sponsor" i]',
                        'div[class*="interstitial"]',
                        'div[id*="interstitial"]',
                        'div[class*="preroll"]',
                        'div[id*="preroll"]'
                    ];
                    adSelectors.forEach(function(sel) {
                        try {
                            var els = document.querySelectorAll(sel);
                            els.forEach(function(el) {
                                el.style.display = 'none';
                                el.style.visibility = 'hidden';
                                el.style.height = '0px';
                                el.style.width = '0px';
                                el.style.overflow = 'hidden';
                                el.style.position = 'absolute';
                                el.style.zIndex = '-9999';
                                el.style.opacity = '0';
                                el.removeAttribute('src');
                                el.removeAttribute('data-src');
                            });
                        } catch(e) {}
                    });
                }

                function cleanObfuscatedAds() {
                    var allDivs = document.querySelectorAll('div');
                    allDivs.forEach(function(div) {
                        try {
                            var style = window.getComputedStyle(div);
                            var innerText = (div.innerText || '').toLowerCase();
                            var className = (div.className || '').toLowerCase();
                            var idName = (div.id || '').toLowerCase();

                            if ((className.length < 8 && className.match(/^[a-z][0-9]+$/)) ||
                                (idName.length < 8 && idName.match(/^[a-z][0-9]+$/))) {
                                if (style.position === 'fixed' || style.position === 'absolute') {
                                    var zIndex = parseInt(style.zIndex);
                                    if (zIndex > 1000 || innerText.includes('ad') || innerText.includes('sponsor')) {
                                        div.style.display = 'none';
                                    }
                                }
                            }
                        } catch(e) {}
                    });
                }

                cleanAds();
                cleanObfuscatedAds();

                var observer = new MutationObserver(function(mutations) {
                    mutations.forEach(function(mut) {
                        mut.addedNodes.forEach(function(node) {
                            if (node.nodeType === 1) {
                                setTimeout(function() { cleanAds(); cleanObfuscatedAds(); }, 100);
                                setTimeout(function() { cleanAds(); cleanObfuscatedAds(); }, 500);
                                setTimeout(function() { cleanAds(); cleanObfuscatedAds(); }, 2000);
                            }
                        });
                    });
                });
                observer.observe(document.body || document.documentElement, {
                    childList: true, subtree: true
                });

                window.addEventListener('load', function() {
                    setTimeout(function() { cleanAds(); cleanObfuscatedAds(); }, 1500);
                });
            } catch(e) {}
        })();
    """.trimIndent()

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.webViewDomCleanerEnabled) return
        LogX.i("WebView DOM Cleaner启动")

        hookWebViewLoadUrl(lpparam, cfg)
        hookWebViewOnPageFinished(lpparam, cfg)
    }

    private fun hookWebViewLoadUrl(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        try {
            val wvClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebView", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(wvClass, "loadUrl",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            injectDomCleanerAfterLoad(lpparam, p.thisObject)
                        }
                    })
                LogX.hookSuccess("WebView DOM", "loadUrl")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(wvClass, "loadUrl",
                    String::class.java, MutableMap::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            injectDomCleanerAfterLoad(lpparam, p.thisObject)
                        }
                    })
                LogX.hookSuccess("WebView DOM", "loadUrl(Map)")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.e("WebViewDomCleaner loadUrl异常", e)
        }
    }

    private fun hookWebViewOnPageFinished(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        try {
            val wvcClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebViewClient", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(wvcClass, "onPageFinished",
                    "android.webkit.WebView", String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val view = p.args[0] ?: return
                            injectDomCleaner(lpparam, view)
                        }
                    })
                LogX.hookSuccess("WebViewClient", "onPageFinished")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            val x5WvcClass = XposedHelpers.findClassIfExists(
                "com.tencent.smtt.sdk.WebViewClient", lpparam.classLoader)
            if (x5WvcClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(x5WvcClass, "onPageFinished",
                        "com.tencent.smtt.sdk.WebView", String::class.java,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                val view = p.args[0] ?: return
                                injectDomCleaner(lpparam, view)
                            }
                        })
                    LogX.hookSuccess("X5 WebViewClient", "onPageFinished")
                } catch (e: Exception) { LogX.w("异常: ${e.message}") }
            }
        } catch (e: Exception) {
            LogX.e("WebViewDomCleaner onPageFinished异常", e)
        }
    }

    private fun injectDomCleanerAfterLoad(lpparam: XC_LoadPackage.LoadPackageParam, view: Any?) {
        if (view == null) return
        try {
            XposedHelpers.callMethod(view, "evaluateJavascript",
                DOM_CLEAN_JS, null as Any?)
            LogX.d("[DOMCleaner] JS注入完成(loadUrl后)")
        } catch (e: Throwable) {
            LogX.d("[DOMCleaner] JS注入失败: ${e.message}")
        }
    }

    private fun injectDomCleaner(lpparam: XC_LoadPackage.LoadPackageParam, view: Any?) {
        if (view == null) return
        try {
            XposedHelpers.callMethod(view, "evaluateJavascript",
                DOM_CLEAN_JS, null as Any?)
            LogX.d("[DOMCleaner] JS注入完成(onPageFinished)")
        } catch (e: Throwable) {
            LogX.d("[DOMCleaner] JS注入失败: ${e.message}")
        }
    }
}
