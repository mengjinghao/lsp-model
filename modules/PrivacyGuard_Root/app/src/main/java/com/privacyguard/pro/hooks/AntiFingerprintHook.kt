package com.privacyguard.pro.hooks

import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

object AntiFingerprintHook {

    private val userAgentPool = listOf(
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-S9080) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; V2330) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Linux; Android 14; 2201117SG) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    )

    private var currentUserAgent: String = userAgentPool.random()
    private var userAgentInitialized = false

    private var spoofSerial: String = ""
    private var spoofModel: String = ""
    private var spoofBrand: String = ""

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.antiFingerprintEnabled) return
        LogX.i("Anti-Fingerprint盾启动")

        if (!userAgentInitialized) {
            currentUserAgent = userAgentPool.random()
            userAgentInitialized = true
        }
        spoofSerial = "PF" + UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
        spoofModel = listOf("Pixel 8 Pro", "SM-S9080", "2201117SG", "V2330", "iPhone 15").random()
        spoofBrand = listOf("google", "samsung", "Xiaomi", "HUAWEI", "OPPO").random()

        hookWebSettingsUserAgent(lpparam)
        hookDisplayMetrics(lpparam)
        hookBuildIdentifiers(lpparam)
        hookLocale(lpparam)
    }

    private fun hookWebSettingsUserAgent(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val wsClass = XposedHelpers.findClassIfExists(
                "android.webkit.WebSettings", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(wsClass, "setUserAgentString",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val original = p.args[0] as? String ?: return
                            LogX.d("[AntiFP] setUserAgentString: $original -> $currentUserAgent")
                            p.args[0] = currentUserAgent
                        }
                    })
                LogX.hookSuccess("WebSettings", "setUserAgentString")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(wsClass, "getUserAgentString",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            if (p.result != null) {
                                LogX.d("[AntiFP] getUserAgentString -> $currentUserAgent")
                                p.result = currentUserAgent
                            }
                        }
                    })
                LogX.hookSuccess("WebSettings", "getUserAgentString")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.e("AntiFingerprintHook WebSettings异常", e)
        }
    }

    private fun hookDisplayMetrics(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val displayClass = XposedHelpers.findClassIfExists(
                "android.view.Display", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(displayClass, "getMetrics",
                    "android.util.DisplayMetrics",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val metrics = p.args[0] ?: return
                            try {
                                val noise = Random.nextFloat() * 0.02f + 0.99f
                                val w = XposedHelpers.getFloatField(metrics, "widthPixels")
                                val h = XposedHelpers.getFloatField(metrics, "heightPixels")
                                XposedHelpers.setFloatField(metrics, "widthPixels", w * noise)
                                XposedHelpers.setFloatField(metrics, "heightPixels", h * noise)
                                LogX.d("[AntiFP] DisplayMetrics加噪: ${w}x${h} -> ${w * noise}x${h * noise}")
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("Display", "getMetrics")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(displayClass, "getRealMetrics",
                    "android.util.DisplayMetrics",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val metrics = p.args[0] ?: return
                            try {
                                val noise = Random.nextFloat() * 0.02f + 0.99f
                                val w = XposedHelpers.getFloatField(metrics, "widthPixels")
                                val h = XposedHelpers.getFloatField(metrics, "heightPixels")
                                XposedHelpers.setFloatField(metrics, "widthPixels", w * noise)
                                XposedHelpers.setFloatField(metrics, "heightPixels", h * noise)
                            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("Display", "getRealMetrics")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.e("AntiFingerprintHook Display异常", e)
        }
    }

    private fun hookBuildIdentifiers(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val buildClass = XposedHelpers.findClassIfExists(
                "android.os.Build", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(buildClass, "getSerial",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            LogX.d("[AntiFP] Build.getSerial -> $spoofSerial")
                            p.result = spoofSerial
                        }
                    })
                LogX.hookSuccess("Build", "getSerial")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.setStaticObjectField(buildClass, "MODEL", spoofModel)
                XposedHelpers.setStaticObjectField(buildClass, "BRAND", spoofBrand)
                LogX.d("[AntiFP] Build.MODEL=$spoofModel BRAND=$spoofBrand")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.e("AntiFingerprintHook Build异常", e)
        }
    }

    private fun hookLocale(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val localeClass = XposedHelpers.findClassIfExists(
                "java.util.Locale", lpparam.classLoader) ?: return

            val localePool = listOf(
                Locale.US, Locale.UK, Locale.CANADA,
                Locale.GERMANY, Locale.FRANCE, Locale.JAPAN
            )
            val fakeLocale = localePool.random()

            try {
                XposedHelpers.findAndHookMethod(localeClass, "getDefault",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            LogX.d("[AntiFP] Locale.getDefault -> $fakeLocale")
                            p.result = fakeLocale
                        }
                    })
                LogX.hookSuccess("Locale", "getDefault")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.e("AntiFingerprintHook Locale异常", e)
        }
    }
}
