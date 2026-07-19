package com.adblockerx.pro.hooks

import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.LogX
import com.adblockerx.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】iptables 网络层拦截 Hook（Root 版独有）
 *
 * 功能：
 *  - 在 APP 启动时通过 Shizuku 解析广告域名得到 IP
 *  - 添加 iptables OUTPUT 链规则：iptables -A OUTPUT -d <ad_ip> -j DROP
 *  - APP 退出时清理规则（避免影响其他 APP）
 *
 * 注意事项：
 *  - 需 Root 或 Shizuku shell 授权
 *  - iptables 规则仅对当前用户进程生效，规则残留可能影响系统
 *  - 广告域名 IP 可能动态变化，需周期刷新（本实现仅在启动时刷新一次）
 *  - 默认关闭，谨慎使用
 */
object IptablesBlockHook {

    private const val CHAIN_TAG = "AdBlockerX"

    private var isApplied = false

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.iptablesBlockEnabled) return
        if (isApplied) return
        isApplied = true

        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku不可用，跳过 iptables 拦截")
            return
        }

        LogX.i("【实验性】IptablesBlockHook 启动（Root 专属）")

        ensureChain()
        addRules()
    }

    /** 创建自定义链（避免污染 OUTPUT 主链） */
    private fun ensureChain() {
        try {
            // 创建链（已存在会失败，忽略）
            ShizukuHelper.execShellSilent("iptables -N $CHAIN_TAG 2>/dev/null")
            // 跳转到自定义链
            ShizukuHelper.execShellSilent("iptables -C OUTPUT -j $CHAIN_TAG 2>/dev/null || iptables -A OUTPUT -j $CHAIN_TAG")
            LogX.d("iptables 链 $CHAIN_TAG 已就绪")
        } catch (e: Throwable) {
            LogX.w("创建 iptables 链异常: ${e.message}")
        }
    }

    /** 解析广告域名为 IP 并添加 DROP 规则 */
    private fun addRules() {
        try {
            val hosts = HostsFilterHook.currentBlockedHosts()
            if (hosts.isEmpty()) {
                LogX.w("黑名单为空，跳过 iptables 规则添加")
                return
            }

            var success = 0
            // 仅取前 50 个域名（避免规则过多）
            for (host in hosts.take(50)) {
                try {
                    // 通过 Shizuku 解析域名
                    val ip = ShizukuHelper.execShell("getent hosts $host 2>/dev/null | head -1 | awk '{print \$1}'")
                        ?.trim()?.takeIf { it.isNotBlank() } ?: continue

                    // 添加 DROP 规则
                    val r = ShizukuHelper.execShellSilent("iptables -C $CHAIN_TAG -d $ip -j DROP 2>/dev/null || iptables -A $CHAIN_TAG -d $ip -j DROP")
                    if (r) success++
                } catch (_: Throwable) {}
            }

            LogX.i("iptables 规则添加完成：$success 条 DROP 规则")
        } catch (e: Throwable) {
            LogX.e("iptables 规则添加异常", e)
        }
    }

    /** 清理所有规则（UI 调用 / 模块卸载时） */
    fun cleanup(): Boolean {
        if (!ShizukuHelper.isShizukuAvailable()) return false
        try {
            // 清空链
            ShizukuHelper.execShellSilent("iptables -F $CHAIN_TAG 2>/dev/null")
            // 从 OUTPUT 移除跳转
            ShizukuHelper.execShellSilent("iptables -D OUTPUT -j $CHAIN_TAG 2>/dev/null")
            // 删除链
            ShizukuHelper.execShellSilent("iptables -X $CHAIN_TAG 2>/dev/null")
            LogX.i("iptables 规则已清理")
            isApplied = false
            return true
        } catch (e: Throwable) {
            LogX.e("iptables 清理异常", e)
            return false
        }
    }

    fun release() {
        isApplied = false
    }
}
