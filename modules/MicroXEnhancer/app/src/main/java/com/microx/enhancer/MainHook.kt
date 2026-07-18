package com.microx.enhancer

import android.content.Context
import com.microx.enhancer.hooks.*
import com.microx.enhancer.utils.ConfigManager
import com.microx.enhancer.utils.HookHelper
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 微X增强模块 — 主入口
 * 实现 IXposedHookLoadPackage 接口，LSPatch通过xposed_init文件找到此类并加载
 *
 * 免Root原理说明：
 * 1. LSPatch将本模块的Hook逻辑注入到微信/QQ的APK中
 * 2. 所有Hook操作完全在微信/QQ的应用进程内存中完成
 * 3. 不涉及任何系统分区读写、内核操作、全局属性修改
 * 4. 不读写/proc、/sys等系统目录节点
 * 5. 仅通过Xposed API对Java层方法进行拦截和修改
 */
class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    /**
     * Zygote初始化阶段（LSPatch本地模式会回调）
     * 用于加载模块自身资源，不做Hook操作
     */
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        // 预加载模块资源路径——LSPatch本地模式需要
        // 集成模式下由打包工具自动处理
        try {
            val modulePath = startupParam.modulePath
            HookHelper.log("模块路径: $modulePath")
        } catch (e: Exception) {
            HookHelper.logE("initZygote异常: ${e.message}", e)
        }
    }

    /**
     * 应用加载回调：每个应用进程启动时都会触发
     * LSPatch根据作用域配置，仅对被勾选的微信/QQ回调此方法
     */
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName
        val processName = lpparam.processName

        // ===== 仅对微信和QQ的主进程注入Hook =====
        when (packageName) {
            "com.tencent.mm" -> {
                if (HookHelper.isWeChatMainProcess(processName)) {
                    onWeChatLoaded(lpparam)
                }
            }
            "com.tencent.mobileqq" -> {
                if (HookHelper.isQQMainProcess(processName)) {
                    onQQLoaded(lpparam)
                }
            }
        }
    }

    /**
     * 微信进程Hook入口
     */
    private fun onWeChatLoaded(lpparam: XC_LoadPackage.LoadPackageParam) {
        HookHelper.log("===== 微X增强模块开始注入微信 =====")
        HookHelper.log("微信版本: 通过类加载器动态适配")
        HookHelper.log("进程名: ${lpparam.processName}")

        try {
            // 1. 首先要初始化配置管理器
            // 在LSPatch集成模式下，通过Activity获取Context
            // 本地模式下需要延迟到UI层初始化
            initConfigManagerByHook(lpparam)

            // 2. 安全适配必须最先执行：绕过微信安全检测
            if (ConfigManager.isEnabled(ConfigManager.KEY_BYPASS_DETECTION)) {
                SecurityBypassHook.hook(lpparam)
            }

            // 3. 加载所有功能模块（每个模块内部检查自己的开关）
            val modules = listOf(
                "广告净化" to { AdBlockHook.hook(lpparam) },
                "消息防撤回" to { AntiRecallHook.hook(lpparam) },
                "防删朋友圈" to { MomentHook.hook(lpparam) },
                "界面美化" to { UIModHook.hook(lpparam) },
                "隐私增强" to { PrivacyHook.hook(lpparam) },
                "批量管理" to { BatchManagerHook.hook(lpparam) },
                "自动回复" to { AutoReplyHook.hook(lpparam) }
            )

            for ((name, hookAction) in modules) {
                try {
                    hookAction.invoke()
                    HookHelper.logD("[$name] Hook加载完成")
                } catch (e: Exception) {
                    HookHelper.logE("[$name] Hook加载失败: ${e.message}", e)
                    // 单个模块失败不影响其他模块
                }
            }

            HookHelper.log("===== 微X增强模块微信注入完成 =====")
        } catch (e: Exception) {
            HookHelper.logE("微信Hook初始化异常: ${e.message}", e)
        }
    }

    /**
     * QQ进程Hook入口
     */
    private fun onQQLoaded(lpparam: XC_LoadPackage.LoadPackageParam) {
        HookHelper.log("===== 微X增强模块开始注入QQ =====")
        HookHelper.log("进程名: ${lpparam.processName}")

        try {
            initConfigManagerByHook(lpparam)

            if (ConfigManager.isEnabled(ConfigManager.KEY_BYPASS_DETECTION)) {
                SecurityBypassHook.hook(lpparam)
            }

            val modules = listOf(
                "广告净化" to { AdBlockHook.hookQQ(lpparam) },
                "消息防撤回" to { AntiRecallHook.hookQQ(lpparam) },
                "界面美化" to { UIModHook.hookQQ(lpparam) },
                "隐私增强" to { PrivacyHook.hookQQ(lpparam) },
                "自动回复" to { AutoReplyHook.hookQQ(lpparam) }
            )

            for ((name, hookAction) in modules) {
                try {
                    hookAction.invoke()
                    HookHelper.logD("[$name-QQ] Hook加载完成")
                } catch (e: Exception) {
                    HookHelper.logE("[$name-QQ] Hook加载失败: ${e.message}", e)
                }
            }

            HookHelper.log("===== 微X增强模块QQ注入完成 =====")
        } catch (e: Exception) {
            HookHelper.logE("QQ Hook初始化异常: ${e.message}", e)
        }
    }

    /**
     * 通过Hook Application类获取Context来初始化ConfigManager
     * 此方法是免Root方案的关键：不依赖任何外部Context来源
     */
    private fun initConfigManagerByHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val appClass = HookHelper.findClassSafe(
                lpparam,
                "com.tencent.mm.app.Application",      // 微信
                "com.tencent.common.app.BaseApplicationImpl", // QQ
                "android.app.Application"
            )
            if (appClass != null) {
                HookHelper.hookAllMethodsSafe(appClass, "onCreate") {
                    HookHelper.logD("Application onCreate 触发，初始化ConfigManager")
                    val context = it.thisObject as? Context
                    if (context != null) {
                        ConfigManager.init(context.applicationContext)
                    }
                }
            }
            // 如果Application已经创建，尝试从其他途径获取
            HookHelper.hookMethodSafe(appClass, "attachBaseContext", {
                HookHelper.logD("Application attachBaseContext 触发")
                val context = it.args[0] as? Context
                if (context != null && !::ConfigManager.isInitialized.isInitialized) {
                    ConfigManager.init(context)
                }
            }, android.content.Context::class.java)
        } catch (e: Exception) {
            HookHelper.logE("ConfigManager初始化失败，使用默认配置: ${e.message}")
        }
    }
}
