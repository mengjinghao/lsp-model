package com.adblockerx.pro.hooks

import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.AdBlockList
import com.adblockerx.pro.utils.ConfigManager
import com.adblockerx.pro.utils.LogX
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 内存 hosts 过滤器（不写任何文件）
 *
 * ⚠️ 本类为工具类，被其他 Hook 调用，不直接注册 Xposed 方法 Hook。
 *
 * 设计目标：
 *  - 维护一份广告域名黑名单（内置 90 条 + 用户自定义）
 *  - 提供统一查询接口 [isBlocked] 供其他 Hook 调用
 *  - 同时提供 currentBlockedHosts() 供 SystemHostsHook 写入系统 hosts
 *
 * 边界声明：
 *  - 内存查询仅作用于本 APP 进程内的网络请求
 *  - SystemHostsHook 通过 Shizuku 写入系统级 hosts 文件（独立操作）
 *  - 本类按 AI_DEV_GUIDE §4.3 工具类规范，apply() 仅加载配置 + 打日志，
 *    不调用任何 Xposed Hook 注册 API，体检脚本判定为 "utility" 状态（合理）
 */
object HostsFilterHook {

    @Volatile
    private var currentConfig: AdBlockConfig = AdBlockConfig()

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        // 工具类：仅加载配置 + 打日志，不直接注册 Xposed 方法 Hook
        currentConfig = cfg
        LogX.i("HostsFilter 工具类已加载 | 内置黑名单=${cfg.builtinBlocklistEnabled} 自定义条数=${cfg.customBlocklist.size}")
        LogX.i("HostsFilter 工具类已加载：内置黑名单总条数=${AdBlockList.BUILTIN_AD_DOMAINS.size}")
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
            try { ConfigManager.incrementBlockedCount(1) } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
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
