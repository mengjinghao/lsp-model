package com.adblockerx.noroot.hooks

import android.view.View
import android.view.ViewGroup
import com.adblockerx.noroot.models.AdBlockConfig
import com.adblockerx.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object LayoutInflaterHook {

    private val AD_KEYWORDS = listOf(
        "ad_", "_ad_", "banner", "splash_ad", "native_ad", "promote", "sponsor"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.layoutInflaterAdEnabled) return
        LogX.i("LayoutInflaterHook 启动（应用进程内）")

        try {
            val inflaterClass = XposedHelpers.findClassIfExists(
                "android.view.LayoutInflater", lpparam.classLoader) ?: return

            hookInflateXmlParser(inflaterClass)
            hookInflateResourceId(inflaterClass, lpparam)
        } catch (e: Throwable) {
            LogX.e("LayoutInflaterHook 异常", e)
        }
    }

    private fun hookInflateXmlParser(inflaterClass: Class<*>) {
        try {
            val xmlClass = XposedHelpers.findClassIfExists(
                "org.xmlpull.v1.XmlPullParser", inflaterClass.classLoader)
            XposedHelpers.findAndHookMethod(inflaterClass, "inflate",
                xmlClass ?: return, ViewGroup::class.java, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val result = p.result as? View ?: return
                        try {
                            val entryName = getResourceEntryNameFromView(result)
                            if (entryName != null && isAdLayout(entryName)) {
                                result.visibility = View.GONE
                                LogX.i("[LayoutInflater] 隐藏广告布局: $entryName")
                            }
                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.d("[LayoutInflater] 已 Hook inflate(XmlPullParser)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun hookInflateResourceId(inflaterClass: Class<*>, lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(inflaterClass, "inflate",
                Int::class.javaPrimitiveType, ViewGroup::class.java, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val resId = p.args.getOrNull(0) as? Int ?: return
                        if (resId == 0) return
                        try {
                            val resources = try {
                                val context = XposedHelpers.callMethod(p.thisObject, "getContext")
                                XposedHelpers.callMethod(context, "getResources")
                            } catch (_: Throwable) { null }
                            if (resources != null) {
                                val entryName = try {
                                    XposedHelpers.callMethod(resources, "getResourceEntryName", resId)
                                        ?.toString()
                                } catch (_: Throwable) { null }
                                if (entryName != null && isAdLayout(entryName)) {
                                    LogX.i("[LayoutInflater] 标记广告布局资源: $entryName")
                                    p.extra = entryName
                                }
                            }
                        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }

                    override fun afterHookedMethod(p: MethodHookParam) {
                        val tag = p.extra as? String ?: return
                        val result = p.result as? View ?: return
                        result.visibility = View.GONE
                        LogX.i("[LayoutInflater] 隐藏广告布局: $tag")
                    }
                })
            LogX.d("[LayoutInflater] 已 Hook inflate(int)")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun getResourceEntryNameFromView(view: View): String? {
        return try {
            val resources = view.resources ?: return null
            val resId = view.id
            if (resId == View.NO_ID) return null
            XposedHelpers.callMethod(resources, "getResourceEntryName", resId)?.toString()
        } catch (_: Throwable) { null }
    }

    private fun isAdLayout(name: String): Boolean {
        val lower = name.lowercase()
        return AD_KEYWORDS.any { keyword -> lower.contains(keyword) }
    }
}
