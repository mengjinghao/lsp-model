package com.adblockerx.pro.hooks

import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.AdBlockList
import com.adblockerx.pro.utils.ConfigManager
import com.adblockerx.pro.utils.LogX
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 内存 hosts 过滤器（不写文件）
 *
 * 设计目标：
 *  - 维护一份广告域名黑名单（内置 60 条 + 用户自定义）
 *  - 提供统一查询接口 [isBlocked] 供各 Hook 调用
 *  - 所有数据仅存于内存，绝不写入系统 hosts
 *
 * 注意：Root 版的"系统级 hosts"由 SystemHostsHook 通过 Shizuku 单独处理，
 *      本类仅负责应用进程内的网络请求查询。
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

    /** 暴露当前配置，供系统级 Hook 读取黑名单 */
    fun currentBlockedHosts(): List<String> {
        val list = mutableListOf<String>()
        if (currentConfig.builtinBlocklistEnabled) list.addAll(AdBlockList.BUILTIN_AD_DOMAINS)
        list.addAll(currentConfig.customBlocklist)
        return list
    }
}
