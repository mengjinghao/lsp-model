package com.adblockerx.pro.hooks

import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.LogX
import com.adblockerx.pro.utils.ShizukuHelper
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku 桥接 Hook（Root 版独有）
 *
 * 功能：
 *  - 通过 Shizuku 执行系统命令刷新 DNS 缓存
 *    - ndc resolver flushdefaultif（Android N+）
 *    - settings put global dns_cache_seconds 0
 *  - 联动 SystemHostsHook / PrivateDnsHook 确保修改立即生效
 */
object ShizukuBridgeHook {

    private var isApplied = false

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.shizukuBridgeEnabled) {
            LogX.d("ShizukuBridge 未启用，跳过")
            return
        }
        if (isApplied) return
        isApplied = true

        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku不可用，跳过 DNS 刷新")
            return
        }

        LogX.i("ShizukuBridge 启动：刷新系统 DNS 缓存")

        flushDnsCache()
    }

    private fun flushDnsCache() {
        try {
            val r1 = ShizukuHelper.execShell("ndc resolver flushdefaultif 2>&1")
            LogX.d("ndc resolver flushdefaultif -> $r1")

            val r2 = ShizukuHelper.execShell("ndc resolver flushnetid 2>&1")
            LogX.d("ndc resolver flushnetid -> $r2")

            ShizukuHelper.execShell("settings put global dns_cache_seconds 0 2>/dev/null")
            ShizukuHelper.execShell("killall -HUP dnsmasq 2>/dev/null")

            LogX.i("DNS 缓存刷新完成")
        } catch (e: Throwable) {
            LogX.e("DNS 缓存刷新异常", e)
        }
    }

    fun release() {
        isApplied = false
    }
}
