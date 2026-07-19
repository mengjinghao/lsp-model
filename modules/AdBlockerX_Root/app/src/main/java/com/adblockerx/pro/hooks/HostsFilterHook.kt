package com.adblockerx.pro.hooks

import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.AdBlockList
import com.adblockerx.pro.utils.ConfigManager
import com.adblockerx.pro.utils.LogX
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 内存 hosts 过滤器（不写任何文件）
 *
 * 设计目标：
 *  - 维护一份广告域名黑名单（内置 90 条 + 用户自定义）
 *  - 提供统一查询接口 [isBlocked] 供其他 Hook 调用
 *  - 同时提供 currentBlockedHosts() 供 SystemHostsHook 写入系统 hosts
 *
 * 边界声明：
 *  - 内存查询仅作用于本 APP 进程内的网络请求
 *  - SystemHostsHook 通过 Shizuku 写入系统级 hosts 文件（独立操作）
 */
object HostsFilterHook {

    @Volatile
    private var currentConfig: AdBlockConfig = AdBlockConfig()

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        currentConfig = cfg
        LogX.i("HostsFilter 启用：内置黑名单=${cfg.builtinBlocklistEnabled}，自定义条数=${cfg.customBlocklist.size}")
        LogX.i("内置黑名单条数：${AdBlockList.BUILTIN_AD_DOMAINS.size}")
    }

    fun refreshConfig(cfg: AdBlockConfig) {
        currentConfig = cfg
    }

    fun isBlocked(host: String?): Boolean {
        if (!currentConfig.hostsFilterEnabled) return false
        if (host.isNullOrBlank()) return false
        val hit = AdBlockList.isBlocked(
            host = host,
            builtinEnabled = currentConfig.builtinBlocklistEnabled,
            customList = currentConfig.customBlocklist
        )
        if (hit) {
            try { ConfigManager.incrementBlockedCount(1) } catch (_: Throwable) {}
            LogX.d("[拦截命中] host=$host")
        }
        return hit
    }

    fun isUrlBlocked(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = AdBlockList.extractHost(url) ?: return false
        return isBlocked(host)
    }

    /** 返回当前生效的所有拦截 host 列表（内置+自定义），供 SystemHostsHook 写入 */
    fun currentBlockedHosts(): List<String> {
        val list = mutableListOf<String>()
        if (currentConfig.builtinBlocklistEnabled) list.addAll(AdBlockList.BUILTIN_AD_DOMAINS)
        list.addAll(currentConfig.customBlocklist.filter { it.isNotBlank() && !it.startsWith("#") })
        return list
    }
}
