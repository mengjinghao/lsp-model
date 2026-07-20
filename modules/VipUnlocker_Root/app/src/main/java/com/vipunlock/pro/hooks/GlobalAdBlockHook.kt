package com.vipunlock.pro.hooks

import android.app.Application
import com.vipunlock.pro.models.VipConfig
import com.vipunlock.pro.utils.LogX
import com.vipunlock.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】全局广告屏蔽 Hook（Root 专属）
 *
 * 目标：通过 Shizuku 修改 /system/etc/hosts 或 /data/adb/hosts，
 * 全局屏蔽广告域名（影响所有APP，不仅当前APP）。
 *
 * 双通道实现：
 *  1. 应用层 Hook InetAddress.getAllByName / Network.getAllByName 拦截广告域名解析
 *  2. Shizuku 追加规则到 /system/etc/hosts（需 root 级，且 /system 可写或 Magisk overlay）
 *
 * 触发方式：
 *  - Hook Application.onCreate 在 APP 启动后追加 hosts 规则
 *
 * 硬性限制：
 *  - 必须先检查 ShizukuHelper.isShizukuAvailable()
 *  - 修改 /system/etc/hosts 需要 /system 可写或 Magisk overlay，否则失败
 *  - 实验性默认关闭，可能影响部分APP正常域名解析
 */
object GlobalAdBlockHook {

    private var isApplied = false

    /** 应用层 Hook 域名解析拦截广告域名 */
    private val RESOLVE_CLASS_CANDIDATES = listOf(
        "java.net.InetAddress",
        "android.net.Network"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.globalAdBlockEnabled) return
        if (isApplied) return
        isApplied = true

        LogX.i("【实验性】全局广告屏蔽启动（Root 专属）")

        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku不可用，仅应用层域名解析拦截生效")
        }

        hookDnsResolution(lpparam, cfg)
        hookAppLifecycleForHosts(lpparam, cfg)
    }

    /** 应用层 Hook InetAddress.getAllByName 拦截广告域名解析 */
    private fun hookDnsResolution(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        val blockedDomains = cfg.blockedAdDomains.map { it.lowercase() }.toHashSet()
        if (blockedDomains.isEmpty()) return

        for (clsName in RESOLVE_CLASS_CANDIDATES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue

            // getAllByName(String) -> 返回空数组（域名解析失败）
            try {
                XposedHelpers.findAndHookMethod(cls, "getAllByName",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val host = (p.args[0] as? String)?.lowercase() ?: return
                            if (isBlocked(host, blockedDomains)) {
                                LogX.d("拦截广告域名解析: $host")
                                // 返回空数组让广告加载失败
                                p.result = arrayOf<java.net.InetAddress>()
                            }
                        }
                    })
                LogX.hookSuccess(clsName, "getAllByName")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // getByName(String) -> 返回 null
            try {
                XposedHelpers.findAndHookMethod(cls, "getByName",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val host = (p.args[0] as? String)?.lowercase() ?: return
                            if (isBlocked(host, blockedDomains)) {
                                LogX.d("拦截广告域名解析: $host")
                                p.result = null
                            }
                        }
                    })
                LogX.hookSuccess(clsName, "getByName")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }
        }
    }

    /** 判断域名是否被屏蔽（支持子域名匹配） */
    private fun isBlocked(host: String, blocked: HashSet<String>): Boolean {
        if (host in blocked) return true
        // 子域名匹配：ad.toutiao.com 子域名包含 ad.toutiao.com 即屏蔽
        for (b in blocked) {
            if (host.endsWith(".$b") || host == b) return true
        }
        return false
    }

    /** Hook Application.onCreate 在 APP 启动后追加 hosts 规则 */
    private fun hookAppLifecycleForHosts(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        if (!ShizukuHelper.isShizukuAvailable()) return
                        appendHostsRules(cfg)
                    }
                })
            LogX.hookSuccess("Application", "onCreate(hosts)")
        } catch (e: Exception) {
            LogX.hookFailed("Application", "onCreate", e)
        }
    }

    /** 通过 Shizuku 追加 hosts 规则 */
    private fun appendHostsRules(cfg: VipConfig) {
        if (cfg.blockedAdDomains.isEmpty()) return
        val sb = StringBuilder()
        sb.append("\n# VipUnlocker-Pro-AdBlock\n")
        for (d in cfg.blockedAdDomains) {
            sb.append("127.0.0.1 ").append(d).append("\n")
        }
        // 追加到 /system/etc/hosts（可能因只读失败，降级到 /data/adb/modules/vipunlocker/system/etc/hosts）
        val content = sb.toString()
        // 尝试方案1：直接追加 /system/etc/hosts（root 级，需 /system 可写）
        val r1 = ShizukuHelper.execShell("echo '$content' >> /system/etc/hosts 2>/dev/null")
        if (r1 != null) {
            LogX.i("已追加 ${cfg.blockedAdDomains.size} 条规则到 /system/etc/hosts")
            return
        }
        // 尝试方案2：Magisk overlay 路径
        val magiskPath = "/data/adb/modules/vipunlocker/system/etc/hosts"
        ShizukuHelper.execShell("mkdir -p /data/adb/modules/vipunlocker/system/etc 2>/dev/null")
        ShizukuHelper.execShell("echo '$content' >> $magiskPath 2>/dev/null")
        LogX.i("已追加 ${cfg.blockedAdDomains.size} 条规则到 $magiskPath (Magisk overlay)")
    }

    fun release() {
        isApplied = false
        LogX.d("GlobalAdBlock 资源已释放")
    }
}
