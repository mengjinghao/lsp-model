package com.adblockerx.pro.hooks

import com.adblockerx.pro.models.AdBlockConfig
import com.adblockerx.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.net.InetAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object DnsOverHttpsHook {

    private val dohEndpoints = mapOf(
        "dns.adguard.com" to "https://dns.adguard.com/dns-query",
        "dns.quad9.net" to "https://dns.quad9.net/dns-query",
        "cloudflare-dns.com" to "https://cloudflare-dns.com/dns-query",
        "doh.opendns.com" to "https://doh.opendns.com/dns-query",
        "dns.google" to "https://dns.google/resolve"
    )

    private val adDomainsCache = mutableMapOf<String, Boolean>()

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        if (!cfg.dnsOverHttpsEnabled) return
        LogX.i("DNS-over-HTTPS代理启动: endpoint=${cfg.dohEndpoint}")

        hookInetAddress(lpparam, cfg)
    }

    private fun hookInetAddress(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AdBlockConfig) {
        try {
            val inetClass = XposedHelpers.findClassIfExists(
                "java.net.InetAddress", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(inetClass, "getAllByName",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val host = p.args[0] as? String ?: return
                            if (isAdDomain(host)) {
                                LogX.i("[DoH] 拦截广告域名: $host")
                                p.result = arrayOf(InetAddress.getByAddress(host, byteArrayOf(127, 0, 0, 1.toByte())))
                            }
                        }
                    })
                LogX.hookSuccess("InetAddress", "getAllByName")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(inetClass, "getByName",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val host = p.args[0] as? String ?: return
                            if (isAdDomain(host)) {
                                LogX.i("[DoH] 拦截广告域名(getByName): $host")
                                p.result = InetAddress.getByAddress(host, byteArrayOf(127, 0, 0, 1.toByte()))
                            }
                        }
                    })
                LogX.hookSuccess("InetAddress", "getByName")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.e("DnsOverHttpsHook异常", e)
        }
    }

    private fun isAdDomain(host: String): Boolean {
        if (host == "localhost" || host == "127.0.0.1" || host == "::1") return false
        val cached = adDomainsCache[host]
        if (cached != null) return cached

        val blocked = HostsFilterHook.isUrlBlocked(host)
        adDomainsCache[host] = blocked

        if (adDomainsCache.size > 1000) {
            val firstKey = adDomainsCache.keys.first()
            adDomainsCache.remove(firstKey)
        }

        if (blocked) {
            LogX.d("[DoH] 广告域名: $host")
        }

        return blocked
    }
}
