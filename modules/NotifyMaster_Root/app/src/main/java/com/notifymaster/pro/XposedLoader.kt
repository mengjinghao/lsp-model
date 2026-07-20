package com.notifymaster.pro

import android.app.Application
import com.notifymaster.pro.hooks.*
import com.notifymaster.pro.models.NotifyConfig
import com.notifymaster.pro.utils.ConfigManager
import com.notifymaster.pro.utils.HookConfigReader
import com.notifymaster.pro.utils.LogX
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * NotifyMaster Pro - Xposed 模块唯一入口（Root 版）
 *
 * 实现 IXposedHookLoadPackage + IXposedHookZygoteInit。
 *
 * 工作流程：
 *  APP启动 -> handleLoadPackage ->
 *    判断是否为目标APP ->
 *    读取全局配置 ->
 *    [1] 通知过滤 [2] 防通知撤回 [3] 通知历史 [4] 通知美化
 *    [实验] 通知分组 / 优先级覆盖 / 静默通知
 *    [Root] 系统通知策略 / NotificationListenerService / 全局过滤 / Shizuku 桥接
 *
 * 硬性限制：
 *  - Root 系统级 Hook 必须先检查 ShizukuHelper.isShizukuAvailable()
 *  - 系统级 Hook 失败时降级为应用层 Hook
 *  - LSPatch 模式下系统级 Hook 不可用，仅应用层 Hook 生效
 */
class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.8"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("NotifyMaster Pro v$VERSION 初始化 | Root 版 | LSPatch/LSPosed 兼容")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName ?: return
        if (!isTargetApp(pkg)) return

        LogX.i("===== APP启动: $pkg =====")
        currentPkg = pkg

        initConfig(lpparam)

        val cfg = loadConfig()
        cfg.packageName = pkg
        LogX.i("配置: 总开关=${cfg.masterEnabled} 过滤=${cfg.notifyFilterEnabled} " +
                "防撤回=${cfg.antiRecallNotifyEnabled} 历史=${cfg.notifyHistoryEnabled} " +
                "美化=${cfg.notifyBeautifyEnabled} [实验]分组=${cfg.batchNotifyEnabled} " +
                "优先级=${cfg.priorityOverrideEnabled} 静默=${cfg.silentNotifyEnabled} " +
                "[Root]系统通知=${cfg.systemNotifyHookEnabled} Listener=${cfg.notifyListenerHookEnabled} " +
                "全局过滤=${cfg.globalNotifyFilterEnabled} Shizuku桥接=${cfg.shizukuNotifyBridgeEnabled}")

        if (!cfg.masterEnabled) {
            LogX.i("总开关关闭，跳过所有Hook")
            return
        }

        // ===== 基础功能（同 NoRoot） =====
        if (cfg.notifyFilterEnabled) NotifyFilterHook.apply(lpparam, cfg)
        if (cfg.antiRecallNotifyEnabled) AntiRecallNotifyHook.apply(lpparam, cfg)
        if (cfg.notifyHistoryEnabled) NotifyHistoryHook.apply(lpparam, cfg)
        if (cfg.notifyBeautifyEnabled) NotifyBeautifyHook.apply(lpparam, cfg)

        // ===== 应用层实验性（同 NoRoot） =====
        if (cfg.batchNotifyEnabled) BatchNotifyHook.apply(lpparam, cfg)
        if (cfg.priorityOverrideEnabled) PriorityOverrideHook.apply(lpparam, cfg)
        if (cfg.silentNotifyEnabled) SilentNotifyHook.apply(lpparam, cfg)

        // ===== Root 专属：系统级 Hook（需 Shizuku） =====
        if (cfg.systemNotifyHookEnabled) SystemNotifyHook.apply(lpparam, cfg)
        if (cfg.notifyListenerHookEnabled) NotifyListenerServiceHook.apply(lpparam, cfg)

        // ===== Root 实验性 =====
        if (cfg.globalNotifyFilterEnabled) GlobalNotifyFilterHook.apply(lpparam, cfg)
        if (cfg.shizukuNotifyBridgeEnabled) ShizukuNotifyBridgeHook.apply(lpparam, cfg)

        hookAppLifecycle(lpparam)
        LogX.i("===== 全部Hook就绪: $pkg =====")
    }

    /** 目标APP包名白名单 */
    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.tencent.mm", "com.tencent.mobileqq",
        "com.eg.android.AlipayGphone", "com.taobao.taobao",
        "com.jingdong.app.mall", "com.xunmeng.pinduoduo",
        "com.ss.android.ugc.aweme", "com.smile.gifmaker",
        "com.netease.cloudmusic", "com.tencent.wmusic",
        "com.sina.weibo", "com.zhihu.android",
        "com.baidu.searchbox", "com.ss.android.article.news"
    )

    /** 读取配置：优先XSharedPreferences，回退Context */
    private fun loadConfig(): NotifyConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { ConfigManager.getGlobalConfig() } catch (_: Throwable) { NotifyConfig(packageName = "global") }
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
