package com.adblockerx.pro.utils

/**
 * 内置广告域名黑名单（Root 版同 NoRoot，约 90 条）
 *
 * 设计原则：
 *  - 内存中维护，同时供 SystemHostsHook 写入系统 hosts 文件
 *  - 子域名匹配：host.endsWith(domain) 或 host.contains(domain)
 */
object AdBlockList {

    val BUILTIN_AD_DOMAINS: List<String> = listOf(
        // ===== Google / DoubleClick =====
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "googletagmanager.com",
        "google-analytics.com",
        "adservice.google.com",
        "adwords.google.com",
        "adsense.google.com",
        "googletagservices.com",
        "adsystem.google.com",
        "partnerad.l.doubleclick.net",

        // ===== 腾讯 GDT / AMS =====
        "pgdt.ugdtimg.com",
        "t.gdt.qq.com",
        "e.qq.com",
        "ad.qq.com",
        "ams.qq.com",
        "gdt.qq.com",
        "qzonestyle.gtimg.cn",
        "mi.gdt.qq.com",

        // ===== 字节跳动 / 穿山甲 / 巨量引擎 =====
        "ad.toutiao.com",
        "pdp.toutiao.com",
        "is.snssdk.com",
        "pg.snssdk.com",
        "ad.toutiao.com.cn",
        "adx.toutiao.com",
        "toblog.ctobsnssdk.com",
        "mssdk.bytedance.com",
        "sf3-fe-tos.pglstatp-tpl.pglstatp.com",
        "log.snssdk.com",
        "init.snssdk.com",

        // ===== 百度联盟 / Mobads =====
        "cpro.baidu.com",
        "mobads.baidu.com",
        "pos.baidu.com",
        "baidumobads.baidu.com",
        "als.baidu.com",
        "e.baidu.com",
        "hmma.baidu.com",
        "duclick.baidu.com",

        // ===== 阿里妈妈 / 淘宝联盟 =====
        "amdc.alibaba.com",
        "acs4baichuan.m.taobao.com",
        "adash-c.ut.taobao.com",
        "adashx.ut.taobao.com",
        "aenbaichuan.com",
        "baichuan.taobao.com",

        // ===== 快手广告 =====
        "ad.kuaishou.com",
        "yt-adp.nsnssdk.com",
        "ssp.ksadx.com",

        // ===== 网易广告 =====
        "ad.bn.netease.com",
        "adwallet.netease.com",
        "g1.163.com",
        "adstest.163.com",

        // ===== 360 / 神马 / 搜狗 =====
        "360.cn",
        "shuzilm.cn",
        "a.shenma.cn",
        "ideasad.com",

        // ===== Mobtech / Umeng / TalkingData =====
        "api.mob.com",
        "ad.talkingdata.com",
        "ulogs.umeng.com",
        "ulogs.umengcloud.com",
        "plbslog.umeng.com",
        "alogs.umeng.com",

        // ===== 其他常见广告/追踪 =====
        "adsame.cn",
        "tanx.com",
        "sax.sina.cn",
        "sax.n.sina.com.cn",
        "r.dmp.cn",
        "admaster.com",
        "mediav.com",
        "miaozhen.com",
        "irs01.com",
        "adcdn.com",
        "moatads.com",
        "rubiconproject.com",
        "pubmatic.com",
        "criteo.com",
        "applovin.com",
        "chartboost.com",
        "inmobi.com",
        "adcolony.com",
        "unityads.unity3d.com",
        "vungle.com",
        "tapjoy.com",
        "admob.com",
        "mm.adtech.com",
        "adtech.com",

        // ===== 追踪 SDK 域名 =====
        "tracking.miui.com",
        "data.adsrvr.org",
        "pixel.facebook.com",
        "analytics.twitter.com",
        "snap.licdn.com",
        "px.ads.linkedin.com",
        "tags.tiqcdn.com",
        "collect.tencent.com",

        // ===== 网盟/RTB 补充 =====
        "adsymptotic.com",
        "yieldlab.net",
        "smartadserver.com",
        "openx.net",
        "3lift.com",
        "bidswitch.net",
        "contextweb.com",
        "quantserve.com",
        "scorecardresearch.com"
    )

    fun isBlocked(
        host: String?,
        builtinEnabled: Boolean,
        customList: List<String>
    ): Boolean {
        if (host.isNullOrBlank()) return false
        val h = host.lowercase().trim()

        if (builtinEnabled) {
            for (domain in BUILTIN_AD_DOMAINS) {
                val d = domain.lowercase()
                if (h == d || h.endsWith(".$d") || h.contains(d)) {
                    return true
                }
            }
        }

        for (domain in customList) {
            val d = domain.lowercase().trim()
            if (d.isBlank()) continue
            if (h == d || h.endsWith(".$d") || h.contains(d)) {
                return true
            }
        }
        return false
    }

    fun extractHost(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            val u = url.trim()
            val start = when {
                u.startsWith("https://", true) -> 8
                u.startsWith("http://", true) -> 7
                else -> 0
            }
            val rest = u.substring(start)
            val end = rest.indexOfFirst { it == '/' || it == ':' || it == '?' || it == '#' }
            if (end < 0) rest else rest.substring(0, end)
        } catch (e: Exception) {
            null
        }
    }
}
