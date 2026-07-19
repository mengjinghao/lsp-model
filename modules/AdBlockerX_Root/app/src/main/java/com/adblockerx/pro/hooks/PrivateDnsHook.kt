package com.adblockerx.pro.hooks

import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.LogX
import com.adblockerx.pro.utils.ShizukuHelper
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 系统级 Private DNS 设置 Hook（Root 版独有）
 *
 * 通过 Shizuku 执行：
 *  - settings put global private_dns_mode hostname
 *  - settings put global private_dns_specifier <用户指定的广告过滤 DNS>
 */
object PrivateDnsHook {

    private var isApplied = false

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.privateDnsEnabled) {
            LogX.d("PrivateDns 未启用，跳过")
            return
        }
        if (isApplied) return
        isApplied = true

        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku不可用，无法设置 Private DNS")
            return
        }

        val host = cfg.privateDnsHost.trim()
        if (host.isBlank()) {
            LogX.w("Private DNS 主机名为空，跳过")
            return
        }

        LogX.i("PrivateDns 启动：设置系统级广告过滤 DNS -> $host")

        val r1 = ShizukuHelper.execShell("settings put global private_dns_mode hostname")
        LogX.d("private_dns_mode=hostname -> $r1")

        val r2 = ShizukuHelper.execShell("settings put global private_dns_specifier $host")
        LogX.d("private_dns_specifier=$host -> $r2")

        LogX.i("Private DNS 已设置，整机 DNS 走 $host")
    }

    /** 恢复 Private DNS 为自动模式（UI 调用） */
    fun restorePrivateDns(): Boolean {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku不可用，无法恢复 Private DNS")
            return false
        }
        try {
            ShizukuHelper.execShell("settings put global private_dns_mode auto")
            ShizukuHelper.execShell("settings delete global private_dns_specifier")
            LogX.i("Private DNS 已恢复为自动模式")
            isApplied = false
            return true
        } catch (e: Throwable) {
            LogX.e("恢复 Private DNS 异常", e)
            return false
        }
    }

    fun release() {
        isApplied = false
    }
}
