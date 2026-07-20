package com.privacyguard.pro

import android.app.Application
import com.privacyguard.pro.hooks.*
import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.ConfigManager
import com.privacyguard.pro.utils.HookConfigReader
import com.privacyguard.pro.utils.LogX
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * PrivacyGuard Pro - Xposed 模块唯一入口（Root 版）
 *
 * 实现 IXposedHookLoadPackage + IXposedHookZygoteInit。
 *
 * 工作流程：
 *  APP启动 -> handleLoadPackage ->
 *    判断是否为目标APP ->
 *    读取全局配置 ->
 *    [1] 设备ID伪造 [2] 剪贴板保护 [3] 权限欺骗
 *    [4] 位置伪造   [5] 传感器伪造 [6] 广告ID屏蔽
 *    [实验] 包可见性/网络信息/屏幕参数/存储路径
 *    [Root] 系统属性/全局权限/网络标识/Shizuku桥接
 *    [Root 实验] SELinux上下文/内核cmdline隐藏
 *
 * 硬性限制：
 *  - Root 系统级 Hook 必须先检查 ShizukuHelper.isShizukuAvailable()
 *  - 系统级 Hook 失败时降级为应用层 Hook
 */
class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.9"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("PrivacyGuard Pro v$VERSION 初始化 | Root 版 | LSPatch/LSPosed 兼容")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName ?: return
        if (!isTargetApp(pkg)) return

        LogX.i("===== APP启动: $pkg =====")
        currentPkg = pkg

        initConfig(lpparam)

        val cfg = loadConfig()
        cfg.packageName = pkg
        LogX.i("配置: 总开关=${cfg.masterEnabled} 设备ID=${cfg.deviceIdSpoofEnabled} " +
                "剪贴板=${cfg.clipboardGuardEnabled} 位置=${cfg.locationSpoofEnabled} " +
                "[实验]包可见=${cfg.packageVisibilitySpoofEnabled} 网络=${cfg.networkInfoSpoofEnabled} " +
                "[Root]系统属性=${cfg.systemPropSpoofEnabled} 全局权限=${cfg.globalPermissionHookEnabled} " +
                "网络标识=${cfg.networkIdentifierHookEnabled} Shizuku桥接=${cfg.shizukuBridgeEnabled} " +
                "[Root实验]SELinux=${cfg.selinuxContextSpoofEnabled} Cmdline=${cfg.kernelCmdlineHideEnabled}")

        if (!cfg.masterEnabled) {
            LogX.i("总开关关闭，跳过所有Hook")
            return
        }

        // ===== 基础功能（同 NoRoot） =====
        if (cfg.deviceIdSpoofEnabled) DeviceIdSpoofHook.apply(lpparam, cfg)
        if (cfg.clipboardGuardEnabled) ClipboardGuardHook.apply(lpparam, cfg)
        if (cfg.permissionSpoofEnabled) PermissionSpoofHook.apply(lpparam, cfg)
        if (cfg.locationSpoofEnabled) LocationSpoofHook.apply(lpparam, cfg)
        if (cfg.sensorFakerEnabled) SensorFakerHook.apply(lpparam, cfg)
        if (cfg.advertisingIdBlockEnabled) AdvertisingIdHook.apply(lpparam, cfg)

        // ===== 应用层实验性（同 NoRoot） =====
        if (cfg.packageVisibilitySpoofEnabled) PackageVisibilitySpoofHook.apply(lpparam, cfg)
        if (cfg.networkInfoSpoofEnabled) NetworkInfoSpoofHook.apply(lpparam, cfg)
        if (cfg.screenMetricsSpoofEnabled) ScreenMetricsSpoofHook.apply(lpparam, cfg)
        if (cfg.storagePathSpoofEnabled) StoragePathSpoofHook.apply(lpparam, cfg)

        // ===== v1.0.6 新增 =====
        if (cfg.installStatusSpoofEnabled || cfg.mockLocationSystemLevelEnabled) {
            PrivacyPlusHook.apply(lpparam, cfg)
        }

        // ===== Root 专属：系统级 Hook（需 Shizuku） =====
        if (cfg.systemPropSpoofEnabled) SystemPropSpoofHook.apply(lpparam, cfg)
        if (cfg.globalPermissionHookEnabled) GlobalPermissionHook.apply(lpparam, cfg)
        if (cfg.networkIdentifierHookEnabled) NetworkIdentifierHook.apply(lpparam, cfg)
        if (cfg.shizukuBridgeEnabled) ShizukuBridgeHook.apply(lpparam, cfg)

        // ===== Root 实验性 =====
        if (cfg.selinuxContextSpoofEnabled) SelinuxContextSpoofHook.apply(lpparam, cfg)
        if (cfg.kernelCmdlineHideEnabled) KernelCmdlineHideHook.apply(lpparam, cfg)

        hookAppLifecycle(lpparam)
        LogX.i("===== 全部Hook就绪: $pkg =====")
    }

    /** 目标APP包名白名单 */
    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.tencent.mm", "com.tencent.mobileqq",
        "com.eg.android.AlipayGphone", "com.taobao.taobao",
        "com.ss.android.ugc.aweme", "com.smile.gifmaker",
        "com.sina.weibo", "com.xunmeng.pinduoduo",
        "com.jingdong.app.mall", "com.android.chrome",
        "com.mi.globalbrowser", "com.zhihu.android",
        "com.netease.cloudmusic"
    )

    /** 读取配置：优先XSharedPreferences，回退Context */
    private fun loadConfig(): PrivacyConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { ConfigManager.getGlobalConfig() } catch (_: Throwable) { PrivacyConfig(packageName = "global") }
    }

    private fun initConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            val app = XposedHelpers.callMethod(cat, "getApplication") as? Application
            if (app != null) ConfigManager.init(app)
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun hookAppLifecycle(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val app = p.thisObject as? Application ?: return
                        try { ConfigManager.init(app) } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                    }
                })
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }
}
