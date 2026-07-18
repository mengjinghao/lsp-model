package com.privacyguard.pro

import android.app.Application
import com.privacyguard.pro.hooks.*
import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.*
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * PrivacyGuard Pro 主入口（Root 版）
 *
 * 架构说明：
 *  1. 实现 IXposedHookLoadPackage + IXposedHookZygoteInit 双接口
 *  2. LSPosed/LSPatch模式下，模块在目标APP进程启动时加载
 *  3. 包含 NoRoot 版全部应用层 Hook + 4 个系统级 Hook（需 Shizuku/Root）
 *
 * 工作流程：
 *  APP启动 -> handleLoadPackage() 触发 ->
 *    判断是否为目标APP包名 ->
 *    读取该APP的独立配置 ->
 *    [1] 设备ID伪造 (应用层，最先执行)
 *    [2] 剪贴板保护 (应用层)
 *    [3] 权限欺骗 (应用层)
 *    [4] 位置伪造 (应用层)
 *    [5] 传感器伪造 (应用层)
 *    [6] 广告ID屏蔽 (应用层)
 *    [7] 系统属性伪造 (Root，Shizuku setprop)
 *    [8] 全局权限控制 (Root，Shizuku pm revoke)
 *    [9] 网络标识伪造 (Root，Shizuku ip link)
 *    [10] Shizuku 桥接 (Root，settings put + pm clear)
 *    -> APP退出自动释放
 *
 * 硬性限制总览：
 *  1. 应用层 Hook（1-6）不需要 Root，纯 Java 层拦截
 *  2. 系统级 Hook（7-10）必须先检查 ShizukuHelper.isShizukuAvailable()
 *  3. setprop 修改非持久化，重启后消失
 *  4. pm revoke 真实回收权限，影响 APP 所有功能
 *  5. 写 /sys/* 节点需要 root 级 Shizuku 授权
 */
class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.0"
        const val MODULE_NAME = "PrivacyGuard Pro"
        var currentPkg: String? = null
            private set
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("========================================")
        LogX.i("$MODULE_NAME v$VERSION 初始化")
        LogX.i("模式: LSPosed + Shizuku (Root)")
        LogX.i("========================================")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName ?: return
        if (!isTargetApp(pkg)) return

        LogX.i("------------------------------------------------")
        LogX.i("检测到目标APP: $pkg")
        currentPkg = pkg

        // 初始化配置（使用目标APP的Context）
        initConfig(lpparam)

        // 加载配置
        val cfg = try {
            ConfigManager.getConfig(pkg)
        } catch (e: Exception) {
            LogX.w("加载配置异常，使用默认配置: ${e.message}")
            ConfigManager.createDefault(pkg)
        }

        LogX.i("配置: 设备ID=${cfg.deviceIdSpoofEnabled} 剪贴板=${cfg.clipboardGuardEnabled} " +
                "权限=${cfg.permissionSpoofEnabled} 位置=${cfg.locationSpoofEnabled} " +
                "传感器=${cfg.sensorFakerEnabled} 广告ID=${cfg.advertisingIdBlockEnabled} " +
                "系统属性=${cfg.systemPropSpoofEnabled} 全局权限=${cfg.globalPermissionControlEnabled} " +
                "网络标识=${cfg.networkIdentifierSpoofEnabled} Shizuku桥接=${cfg.shizukuBridgeEnabled}")

        // ===== 应用层 Hook（1-6，不需要 Root） =====
        if (cfg.deviceIdSpoofEnabled) {
            DeviceIdSpoofHook.apply(lpparam, cfg)
        }
        if (cfg.clipboardGuardEnabled) {
            ClipboardGuardHook.apply(lpparam, cfg)
        }
        if (cfg.permissionSpoofEnabled) {
            PermissionSpoofHook.apply(lpparam, cfg)
        }
        if (cfg.locationSpoofEnabled) {
            LocationSpoofHook.apply(lpparam, cfg)
        }
        if (cfg.sensorFakerEnabled) {
            SensorFakerHook.apply(lpparam, cfg)
        }
        if (cfg.advertisingIdBlockEnabled) {
            AdvertisingIdHook.apply(lpparam, cfg)
        }

        // ===== 系统级 Hook（7-10，需 Shizuku/Root） =====
        if (cfg.systemPropSpoofEnabled) {
            SystemPropSpoofHook.apply(lpparam, cfg)
        }
        if (cfg.globalPermissionControlEnabled) {
            GlobalPermissionHook.apply(lpparam, cfg)
        }
        if (cfg.networkIdentifierSpoofEnabled) {
            NetworkIdentifierHook.apply(lpparam, cfg)
        }
        if (cfg.shizukuBridgeEnabled) {
            ShizukuBridgeHook.apply(lpparam, cfg)
        }

        // 注册App生命周期Hook确保ConfigManager初始化
        hookAppLifecycle(lpparam)

        LogX.i("===== 全部Hook就绪: $pkg =====")
        LogX.i("------------------------------------------------")
    }

    /** 目标APP包名白名单 */
    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.tencent.mm",                // 微信
        "com.tencent.mobileqq",          // 手机QQ
        "com.tencent.mobileqqi",         // QQ国际版
        "com.android.settings",          // 系统设置
        "com.android.chrome",            // Chrome浏览器
        "com.mi.globalbrowser",          // 小米浏览器
        "com.eg.android.AlipayGphone",   // 支付宝
        "com.taobao.taobao",             // 淘宝
        "com.xunmeng.pinduoduo",         // 拼多多
        "com.jingdong.app.mall",         // 京东
        "com.ss.android.ugc.aweme",      // 抖音
        "com.smile.gifmaker",            // 快手
        "com.sina.weibo"                 // 微博
    )

    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            val app = XposedHelpers.callMethod(cat, "getApplication") as? Application
            if (app != null) ConfigManager.init(app)
        } catch (e: Exception) {
            LogX.w("ConfigManager初始化异常: ${e.message}，将在Application.onCreate时重试")
        }
    }

    /** Application.onCreate时补初始化ConfigManager */
    private fun hookAppLifecycle(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val app = p.thisObject as? Application ?: return
                        try {
                            if (!ConfigManager.isInitialized()) {
                                ConfigManager.init(app)
                            }
                        } catch (_: Exception) {}
                    }
                })
        } catch (_: Exception) {}
    }
}
