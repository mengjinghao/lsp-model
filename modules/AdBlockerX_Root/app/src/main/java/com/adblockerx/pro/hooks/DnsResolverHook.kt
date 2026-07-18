package com.adblockerx.pro.hooks

import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.net.InetAddress

/**
 * 系统 DNS 解析 Hook（Root 版独有，容错）
 *
 * 拦截策略：
 *  - Hook android.net.Network.getAllByName / getAllByAddress
 *  - Hook java.net.InetAddress.getAllByName（兜底）
 *  - Hook libcore.io.Libcore.os（如有）的 DNS 解析入口
 *  - 对广告域名返回 127.0.0.1（localhost）
 *
 * 注意事项：
 *  - 此 Hook 风险较高，默认关闭
 *  - 不同 Android 版本/厂商实现差异大，全部用 findClassIfExists + try-catch 容错
 *  - 类/方法不存在时直接跳过，不影响 APP 正常运行
 */
object DnsResolverHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.dnsResolverHookEnabled) return
        LogX.i("DnsResolverHook 启动（实验性，容错处理）")

        hookInetAddress(lpparam)
        hookNetwork(lpparam)
        hookLibcoreOs(lpparam)
    }

    /**
     * Hook java.net.InetAddress.getAllByName
     * 这是最通用的 DNS 解析入口
     */
    private fun hookInetAddress(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val clazz = XposedHelpers.findClassIfExists(
                "java.net.InetAddress", lpparam.classLoader) ?: return
            XposedHelpers.findAndHookMethod(clazz, "getAllByName",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val host = p.args.getOrNull(0) as? String ?: return
                        if (HostsFilterHook.isBlocked(host)) {
                            try {
                                LogX.i("[DNS] 拦截解析: $host -> 127.0.0.1")
                                // 返回 localhost 地址数组
                                val local = InetAddress.getByName("127.0.0.1")
                                p.result = arrayOf(local)
                            } catch (e: Throwable) {
                                LogX.e("[DNS] 构造 127.0.0.1 异常", e)
                            }
                        }
                    }
                })
            LogX.d("[DNS] 已 Hook InetAddress.getAllByName")
        } catch (e: Throwable) {
            LogX.d("[DNS] Hook InetAddress 异常: ${e.message}")
        }
    }

    /**
     * Hook android.net.Network.getAllByName（Android 5.0+）
     */
    private fun hookNetwork(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val clazz = XposedHelpers.findClassIfExists(
                "android.net.Network", lpparam.classLoader) ?: return
            XposedHelpers.findAndHookMethod(clazz, "getAllByName",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val host = p.args.getOrNull(0) as? String ?: return
                        if (HostsFilterHook.isBlocked(host)) {
                            try {
                                LogX.i("[DNS] 拦截 Network.getAllByName: $host -> 127.0.0.1")
                                val local = InetAddress.getByName("127.0.0.1")
                                p.result = arrayOf(local)
                            } catch (e: Throwable) {
                                LogX.e("[DNS] Network 构造 127.0.0.1 异常", e)
                            }
                        }
                    }
                })
            LogX.d("[DNS] 已 Hook android.net.Network.getAllByName")
        } catch (e: Throwable) {
            LogX.d("[DNS] Hook Network 异常: ${e.message}")
        }
    }

    /**
     * Hook libcore.io.Libcore.os（部分 Android 版本可用）
     */
    private fun hookLibcoreOs(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val libcoreClass = XposedHelpers.findClassIfExists(
                "libcore.io.Libcore", lpparam.classLoader) ?: return
            val osField = XposedHelpers.getStaticObjectField(libcoreClass, "os") ?: return
            val osClass = osField.javaClass

            // 候选方法名：lookupHostByName / getaddrinfo / getHostByName
            val candidates = listOf("lookupHostByName", "getHostByName", "getaddrinfo")
            for (name in candidates) {
                try {
                    XposedHelpers.findAndHookMethod(osClass, name,
                        String::class.java,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val host = p.args.getOrNull(0) as? String ?: return
                                if (HostsFilterHook.isBlocked(host)) {
                                    LogX.i("[DNS] 拦截 Libcore.os.$name: $host -> 抛异常跳过")
                                    // 抛 UnknownHostException 让上层走异常分支
                                    p.throwable = java.net.UnknownHostException("AdBlockerX blocked: $host")
                                }
                            }
                        })
                    LogX.d("[DNS] 已 Hook Libcore.os.$name")
                } catch (_: Throwable) {
                    // 方法不存在，下一个候选
                }
            }
        } catch (e: Throwable) {
            LogX.d("[DNS] Hook Libcore.os 异常: ${e.message}")
        }
    }
}
