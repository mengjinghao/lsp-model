package com.privacyguard.noroot

import android.app.Application
import com.privacyguard.noroot.hooks.*
import com.privacyguard.noroot.models.PrivacyConfig
import com.privacyguard.noroot.utils.*
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * PrivacyGuard NoRoot 主入口
 *
 * 架构说明：
 *  1. 实现 IXposedHookLoadPackage + IXposedHookZygoteInit 双接口
 *  2. LSPatch本地模式下，模块在目标APP进程启动时加载
 *  3. 全部Hook在应用进程内执行，无系统级修改
 *
 * 工作流程：
 *  APP启动 -> handleLoadPackage() 触发 ->
 *    判断是否为目标APP包名 ->
 *    读取该APP的独立配置 ->
 *    [1] 设备ID伪造 (最先执行，防追踪)
 *    [2] 剪贴板保护
 *    [3] 权限欺骗
 *    [4] 位置伪造
 *    [5] 传感器伪造
 *    [6] 广告ID屏蔽
 *    -> APP退出自动释放
 *
 * 硬性限制总览（NoRoot版严格遵守）：
 *  1. 仅在应用进程内做 Java 层 Hook，绝不修改系统属性(setprop)
 *  2. 不修改 /system 或 /sys 文件
 *  3. 不进行全局权限拦截
 *  4. 不调用 Shizuku 系统级操作（Shizuku依赖 compileOnly 保留但不主动调用）
 *  5. 所有伪造的设备标识在单次进程生命周期内稳定
 */
class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "2.0.0"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("PrivacyGuard NoRoot v$VERSION 初始化 | LSPatch本地模式")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName ?: return
        if (!isTargetApp(pkg)) return

        LogX.i("===== APP启动: $pkg =====")
        currentPkg = pkg

        // 初始化配置（使用目标APP的Context）
        initConfig(lpparam)

        // 加载配置
        val cfg = try {
            ConfigManager.getConfig(pkg)
        } catch (e: Exception) {
            ConfigManager.createDefault(pkg)
        }

        LogX.i("配置: 设备ID=${cfg.deviceIdSpoofEnabled} 剪贴板=${cfg.clipboardGuardEnabled} " +
                "权限=${cfg.permissionSpoofEnabled} 位置=${cfg.locationSpoofEnabled} " +
                "传感器=${cfg.sensorFakerEnabled} 广告ID=${cfg.advertisingIdBlockEnabled}")

        // ===== [1] 设备ID伪造（最先执行，防追踪） =====
        if (cfg.deviceIdSpoofEnabled) {
            DeviceIdSpoofHook.apply(lpparam, cfg)
        }

        // ===== [2] 剪贴板保护 =====
        if (cfg.clipboardGuardEnabled) {
            ClipboardGuardHook.apply(lpparam, cfg)
        }

        // ===== [3] 权限欺骗 =====
        if (cfg.permissionSpoofEnabled) {
            PermissionSpoofHook.apply(lpparam, cfg)
        }

        // ===== [4] 位置伪造 =====
        if (cfg.locationSpoofEnabled) {
            LocationSpoofHook.apply(lpparam, cfg)
        }

        // ===== [5] 传感器伪造 =====
        if (cfg.sensorFakerEnabled) {
            SensorFakerHook.apply(lpparam, cfg)
        }

        // ===== [6] 广告ID屏蔽 =====
        if (cfg.advertisingIdBlockEnabled) {
            AdvertisingIdHook.apply(lpparam, cfg)
        }

        // 注册App生命周期Hook确保ConfigManager初始化
        hookAppLifecycle(lpparam)

        LogX.i("===== 全部Hook就绪: $pkg =====")
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
        } catch (_: Exception) {}
    }

    /** Application.onCreate时补初始化ConfigManager */
    private fun hookAppLifecycle(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val app = p.thisObject as? Application ?: return
                        try { ConfigManager.init(app) } catch (_: Exception) {}
                    }
                })
        } catch (_: Exception) {}
    }
}
