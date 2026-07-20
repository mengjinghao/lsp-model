package com.vipunlock.pro.hooks

import com.vipunlock.pro.models.VipConfig
import com.vipunlock.pro.utils.LogX
import com.vipunlock.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Google Play License 授权 Hook（Root 专属）
 *
 * 目标：让 APP 通过 Google Play License 校验，返回已授权。
 *
 * 候选 Hook 类：
 *  1. com.google.android.vending.licensing.LicenseChecker
 *  2. com.google.android.vending.licensing.LicenseCheckerResult
 *  3. com.google.android.vending.licensing.Policy
 *  4. com.google.android.vending.licensing.AESObfuscator
 *
 * 硬性限制：
 *  - 仅 Hook 应用进程内 License 校验回调
 *  - 国内 APP 多不走 Google License，本 Hook 主要影响 Google Play 付费应用
 *  - 服务端 License 校验不绕过
 */
object LicenseVerifyHook {

    private val LICENSE_CLASS_CANDIDATES = listOf(
        "com.google.android.vending.licensing.LicenseChecker",
        "com.google.android.vending.licensing.LicenseCheckerResult",
        "com.google.android.vending.licensing.Policy",
        "com.google.android.vending.licensing.StrictPolicy",
        "com.google.android.vending.licensing.ServerManagedPolicy",
        "com.google.android.vending.licensing.AESObfuscator",
        "com.google.android.vending.licensing.ValidationException"
    )

    /** License 校验结果方法名 */
    private val LICENSE_METHODS = listOf(
        "allowAccess", "isLicensed", "isAuthorized", "isPurchased",
        "verifyLicense", "checkLicense", "onLicenseResponse"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.licenseVerifyEnabled) return
        LogX.i("Google License 授权 Hook 启动（Root 专属）")

        hookLicenseChecker(lpparam)
        hookPolicy(lpparam)
        hookLicenseResultCallback(lpparam)
    }

    /** Hook LicenseChecker 的核心校验方法 */
    private fun hookLicenseChecker(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in LICENSE_CLASS_CANDIDATES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            for (m in LICENSE_METHODS) {
                tryHookBooleanReturning(cls, clsName, m, true)
            }
        }
    }

    /** Hook Policy.allowAccess 返回 true */
    private fun hookPolicy(lpparam: XC_LoadPackage.LoadPackageParam) {
        val policyCandidates = listOf(
            "com.google.android.vending.licensing.Policy",
            "com.google.android.vending.licensing.StrictPolicy",
            "com.google.android.vending.licensing.ServerManagedPolicy"
        )
        for (clsName in policyCandidates) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            // allowAccess(int response) -> true
            try {
                XposedHelpers.findAndHookMethod(cls, "allowAccess",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = true
                        }
                    })
                LogX.hookSuccess(clsName, "allowAccess(int)")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // 无参 allowAccess() -> true
            tryHookBooleanReturning(cls, clsName, "allowAccess", true)

            // processServerResponse -> 不修改返回值但日志
            try {
                XposedHelpers.findAndHookMethod(cls, "processServerResponse",
                    Int::class.javaPrimitiveType, "com.google.android.vending.licensing.ResponseData",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            LogX.d("$clsName.processServerResponse 已调用")
                        }
                    })
                LogX.hookSuccess(clsName, "processServerResponse")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }
        }
    }

    /** Hook LicenseCheckerCallback.onAllow / donAllow 强制走 onAllow */
    private fun hookLicenseResultCallback(lpparam: XC_LoadPackage.LoadPackageParam) {
        // LicenseCheckerCallback 是 APP 自己实现的接口，类名不定，用反射找
        val callbackCandidates = listOf(
            "com.google.android.vending.licensing.LicenseCheckerCallback",
            "com.google.android.vending.licensing.LicenseChecker\$LicenseCheckerCallbackImpl"
        )
        for (clsName in callbackCandidates) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue

            // onAllow() -> 不修改，允许通过
            try {
                XposedHelpers.findAndHookMethod(cls, "onAllow",
                    "android.app.Activity",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            LogX.d("LicenseCheckerCallback.onAllow 已触发")
                        }
                    })
                LogX.hookSuccess(clsName, "onAllow")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // onAllow() 无参
            tryHookNoOp(cls, clsName, "onAllow")

            // donAllow() 拦截，改为允许
            try {
                XposedHelpers.findAndHookMethod(cls, "dontAllow",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            LogX.d("LicenseCheckerCallback.dontAllow 被拦截，强制改为允许")
                            p.result = null
                        }
                    })
                LogX.hookSuccess(clsName, "dontAllow(int)")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // applicationError -> 拦截
            try {
                XposedHelpers.findAndHookMethod(cls, "applicationError",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            LogX.d("LicenseCheckerCallback.applicationError 被拦截")
                            p.result = null
                        }
                    })
                LogX.hookSuccess(clsName, "applicationError(int)")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }
        }
    }

    private fun tryHookBooleanReturning(cls: Class<*>, clsName: String, method: String, value: Boolean): Boolean {
        return try {
            XposedHelpers.findAndHookMethod(cls, method, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) { p.result = value }
            })
            LogX.hookSuccess(clsName, method)
            true
        } catch (e: NoSuchMethodError) { false }
        catch (e: Exception) { LogX.w("异常: ${e.message}"); false }
    }

    private fun tryHookNoOp(cls: Class<*>, clsName: String, method: String): Boolean {
        return try {
            XposedHelpers.findAndHookMethod(cls, method, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    p.result = null
                }
            })
            LogX.hookSuccess(clsName, method)
            true
        } catch (e: NoSuchMethodError) { false }
        catch (e: Exception) { LogX.w("异常: ${e.message}"); false }
    }

    /**
     * 【Root】Shizuku 系统级 License 绕过
     * 直接操作 Play Store 数据库、权限、SharedPreferences 实现真实的购买记录注入
     */
    fun applyShizuku(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.rootLicenseBypassEnabled) return
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku不可用，跳过 License 系统级绕过")
            return
        }
        LogX.i("【Root】License 系统级绕过启动")

        val pkg = lpparam.packageName

        try {
            // 1. 授予 BILLING 权限，绕过支付权限检查
            ShizukuHelper.execShell("pm grant $pkg android.permission.BILLING 2>/dev/null")
            LogX.i("已授予 BILLING 权限: $pkg")

            // 2. 读取 Play Store 本地数据库中的购买记录
            val dbResult = ShizukuHelper.execShell(
                "sqlite3 /data/data/com.android.vending/databases/localappstate.db " +
                "\"SELECT package_name,auto_acquire FROM appstate WHERE package_name='$pkg';\" 2>/dev/null"
            )
            LogX.i("Play Store DB 查询结果: $dbResult")

            // 3. 写入伪造购买记录 JSON
            val fakePurchase = """{"packageName":"$pkg","purchaseTime":${System.currentTimeMillis()},"purchaseState":0,"purchaseToken":"inapp.$pkg.fake-token-12345","productId":"premium","autoRenewing":true}"""
            ShizukuHelper.execShell(
                "echo '$fakePurchase' > /data/data/com.android.vending/files/fake_purchases.json 2>/dev/null"
            )
            LogX.i("已写入伪造购买记录 JSON")

            // 4. 修改 Play Store SharedPreferences（标记为目标APP已授权）
            val prefFile = "license_check_${pkg.replace('.', '_')}.xml"
            ShizukuHelper.execShell(
                "echo '<?xml version=\"1.0\" encoding=\"utf-8\"?><map><boolean name=\"${pkg}.licensed\" value=\"true\" /></map>' " +
                "> /data/data/com.android.vending/shared_prefs/$prefFile 2>/dev/null"
            )
            ShizukuHelper.execShell("chmod 660 /data/data/com.android.vending/shared_prefs/$prefFile 2>/dev/null")
            LogX.i("已修改 Play Store shared_prefs: $prefFile")

        } catch (e: Throwable) { LogX.w("License Shizuku异常: ${e.message}") }
    }
}
