package com.privacyguard.pro.hooks

import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 广告ID屏蔽Hook（应用层）
 *
 * 返回空/伪造ID，并强制 isLimitAdTrackingEnabled = true（用户已选择退出个性化广告）
 */
object AdvertisingIdHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.advertisingIdBlockEnabled) return
        LogX.i("广告ID屏蔽启动（应用层）")

        hookAdvertisingIdClient(lpparam)
        hookAdvertisingIdInfo(lpparam)
    }

    private fun hookAdvertisingIdClient(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val aicCls = XposedHelpers.findClassIfExists(
                "com.google.android.gms.ads.identifier.AdvertisingIdClient",
                lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(aicCls, "getAdvertisingIdInfo",
                    "android.content.Context", object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            LogX.d("AdvertisingIdClient.getAdvertisingIdInfo 调用拦截")
                        }
                    })
                LogX.hookSuccess("AdvertisingIdClient", "getAdvertisingIdInfo")
            } catch (_: Exception) {}

            try {
                XposedHelpers.findAndHookMethod(aicCls, "getAdvertisingIdInfo",
                    "android.content.Context", Boolean::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            LogX.d("AdvertisingIdClient.getAdvertisingIdInfo(bool) 调用拦截")
                        }
                    })
                LogX.hookSuccess("AdvertisingIdClient", "getAdvertisingIdInfo(bool)")
            } catch (_: Exception) {}
        } catch (e: Exception) {
            LogX.hookFailed("AdvertisingIdClient", "getAdvertisingIdInfo", e)
        }
    }

    private fun hookAdvertisingIdInfo(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val infoCls = XposedHelpers.findClassIfExists(
                "com.google.android.gms.ads.identifier.AdvertisingIdClient\$Info",
                lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(infoCls, "getId", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = ""
                        LogX.d("AdvertisingIdClient.Info.getId -> 空字符串")
                    }
                })
                LogX.hookSuccess("AdvertisingIdClient\$Info", "getId")
            } catch (_: Exception) {}

            try {
                XposedHelpers.findAndHookMethod(infoCls, "isLimitAdTrackingEnabled",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = true
                        }
                    })
                LogX.hookSuccess("AdvertisingIdClient\$Info", "isLimitAdTrackingEnabled")
            } catch (_: Exception) {}
        } catch (e: Exception) {
            LogX.hookFailed("AdvertisingIdClient\$Info", "id/lat", e)
        }
    }
}
