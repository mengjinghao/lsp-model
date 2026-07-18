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
 *    - settings put global dns_cache_seconds 0（强制缓存立即过期）
 *  - 联动 SystemHostsHook / PrivateDnsHook 确保修改立即生效
 *
 * 注意事项：
 *  - 需 Root 或 Shizuku adb 级授权
 *  - ndc 命令在不同 Android 版本可能不可用，全部 try-catch
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

    /**
     * 通过 Shizuku 刷新系统 DNS 缓存
     * 让 SystemHosts / PrivateDns 的修改立即生效
     */
    private fun flushDnsCache() {
        try {
            // 1. ndc resolver flushdefaultif（Android N+）
            val r1 = ShizukuHelper.execShell("ndc resolver flushdefaultif 2>&1")
            LogX.d("ndc resolver flushdefaultif -> $r1")

            // 2. ndc resolver flushnetid（Android 8+）
            val r2 = ShizukuHelper.execShell("ndc resolver flushnetid 2>&1")
            LogX.d("ndc resolver flushnetid -> $r2")

            // 3. 设置 DNS 缓存秒数为 0（强制立即过期）
            ShizukuHelper.execShell("settings put global dns_cache_seconds 0 2>/dev/null")

            // 4. 重启 dnsmasq（部分 ROM 可用）
            ShizukuHelper.execShell("killall -HUP dnsmasq 2>/dev/null")

            LogX.i("DNS 缓存刷新完成")
        } catch (e: Throwable) {
            LogX.e("DNS 缓存刷新异常", e)
        }
    }

    /** 释放资源 */
    fun release() {
        isApplied = false
    }
}
