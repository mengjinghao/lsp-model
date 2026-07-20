package com.adblockerx.pro.hooks

import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.LogX
import com.adblockerx.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 系统级 sysfs DNS Hook（Root 版独有）
 *
 * 通过 Shizuku 直接修改 OS 级别 DNS 配置：
 *  - mount --bind 注入自定义 resolv.conf
 *  - settings put global private_dns 设置广告过滤 DNS
 *  - ndc resolver setnetdns 强制网络 DNS
 *  - 适用于全局 DNS 级广告拦截
 *
 * 硬性限制：
 *  - 必须 ShizukuHelper.isShizukuAvailable()
 *  - mount --bind 需 root 级 Shizuku
 *  - 全部 try-catch 保护
 */
object SysfsDnsHook {

    private var isApplied = false

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.sysfsDnsEnabled) {
            LogX.d("SysfsDnsHook 未启用，跳过")
            return
        }
        if (isApplied) return

        LogX.i("SysfsDnsHook 启动：系统级 sysfs DNS 配置")

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        isApplied = true
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku 不可用，跳过 sysfs DNS 配置")
                            return
                        }
                        applySystemDns()
                    }
                })
            LogX.hookSuccess("Application", "onCreate->SysfsDnsHook")
        } catch (e: Throwable) {
            LogX.e("SysfsDnsHook Application.onCreate Hook 异常", e)
        }
    }

    private fun applySystemDns() {
        try {
            val r1 = ShizukuHelper.execShell(
                "echo 'nameserver 127.0.0.1' > /data/local/tmp/resolv.conf && mount --bind /data/local/tmp/resolv.conf /etc/resolv.conf 2>&1"
            )
            LogX.d("mount --bind resolv.conf: $r1")
        } catch (e: Throwable) { LogX.w("mount --bind 异常: ${e.message}") }

        try {
            val r2 = ShizukuHelper.execShell("settings put global private_dns_specifier dns.adguard.com 2>&1")
            LogX.d("private_dns_specifier -> dns.adguard.com: $r2")
        } catch (e: Throwable) { LogX.w("private_dns_specifier 异常: ${e.message}") }

        try {
            val r3 = ShizukuHelper.execShell("settings put global private_dns_mode hostname 2>&1")
            LogX.d("private_dns_mode -> hostname: $r3")
        } catch (e: Throwable) { LogX.w("private_dns_mode 异常: ${e.message}") }

        try {
            val netList = ShizukuHelper.execShell("ls /sys/class/net/ 2>/dev/null") ?: ""
            val netIds = netList.lines().mapNotNull { line ->
                val id = ShizukuHelper.execShell("cat /sys/class/net/${line}/ifindex 2>/dev/null")
                id?.trim()?.takeIf { it.isNotEmpty() }
            }
            for (netId in netIds) {
                val r = ShizukuHelper.execShell("ndc resolver setnetdns $netId \"\" 127.0.0.1 2>&1")
                LogX.d("ndc resolver setnetdns $netId -> $r")
            }
        } catch (e: Throwable) { LogX.w("ndc resolver setnetdns 异常: ${e.message}") }

        LogX.i("SysfsDnsHook: 系统 DNS 配置已应用")
    }

    fun release() {
        isApplied = false
    }
}
