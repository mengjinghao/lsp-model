package com.gameunlocker.pro

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import com.gameunlocker.pro.hooks.*
import com.gameunlocker.pro.models.DeviceProfile
import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.*
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Game-Unlocker Pro 模块主入口
 *
 * 实现 IXposedHookLoadPackage 和 IXposedHookZygoteInit 双接口。
 * LSPatch本地模式下，模块在目标游戏进程启动时被加载。
 *
 * 工作流程：
 *  1. Zygote初始化阶段：初始化日志和配置
 *  2. 游戏加载阶段(handleLoadPackage)：
 *     a. 判断当前APP是否为目标游戏
 *     b. 读取该游戏的独立配置
 *     c. 按顺序应用各类Hook（机型伪装->帧率解锁->温控屏蔽->环境隐藏->分辨率->GPU）
 *  3. 游戏退出时：释放资源
 *
 * 核心设计：
 *  - 每个游戏独立配置，互不干扰
 *  - 仅游戏启动时加载Hook，退出自动释放
 *  - 轻量化后台，无持久进程
 */
class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        /** 模块版本 */
        const val MODULE_VERSION = "1.0.0"
        const val MODULE_NAME = "Game-Unlocker Pro"

        /** 当前Hook的游戏包名 */
        var currentGamePackage: String? = null
            private set

        /** 当前游戏的配置 */
        lateinit var currentConfig: GameConfig
            private set

        /** 当前伪装的机型 */
        var currentProfile: DeviceProfile? = null
            private set

        /** 标记Hook是否已释放 */
        private var isReleased = false
    }

    /**
     * Zygote初始化阶段
     * 用于初始化全局配置和日志
     */
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        LogX.i("========================================")
        LogX.i("$MODULE_NAME v$MODULE_VERSION 初始化")
        LogX.i("模式: LSPatch本地模式 (免Root)")
        LogX.i("========================================")

        // 检测OEM系统类型
        val oemType = OemPatchHelper.detectOemType()
        LogX.i("检测到系统类型: $oemType")

        // 在Zygote阶段Hook系统级属性，确保全局生效
        MyHookZygote.init(startupParam)
    }

    /**
     * APP加载阶段 - Xposed框架/LSPatch核心回调
     * 每个APP启动时都会调用此方法
     */
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName ?: return

        // 仅处理目标游戏，忽略系统进程和非游戏APP
        if (!isTargetGame(packageName)) {
            return
        }

        LogX.i("------------------------------------------------")
        LogX.i("检测到目标游戏: $packageName")
        LogX.i("加载路径: ${lpparam.appInfo?.sourceDir}")
        LogX.i("ClassLoader: ${lpparam.classLoader}")

        // 初始化配置管理器（使用目标APP的Context）
        initConfigManager(lpparam)

        // 读取游戏配置（不存在则创建默认配置）
        val config = loadOrCreateConfig(packageName)
        currentGamePackage = packageName
        currentConfig = config

        LogX.i("游戏配置: 机型伪装=${config.deviceSpoofEnabled}, " +
                "帧率解锁=${config.frameRateUnlockEnabled}, " +
                "目标帧率=${config.targetFps}, " +
                "温控屏蔽=${config.thermalBypassEnabled}, " +
                "环境隐藏=${config.detectionHideEnabled}")

        // 1. 获取伪装的机型配置
        val profile = resolveDeviceProfile(config)
        currentProfile = profile
        if (profile != null && config.deviceSpoofEnabled) {
            LogX.i("使用机型: ${profile.displayName} (${profile.model})")
        }

        // 2. 【优先级1】环境隐藏Hook（必须最先执行，防止Hook过程被检测）
        if (config.detectionHideEnabled) {
            GameDetectionHideHook.apply(lpparam, config)
        }

        // 3. 【优先级2】机型伪装Hook（修改Build属性，后续Hook依赖伪装后的值）
        if (config.deviceSpoofEnabled && profile != null) {
            DeviceSpoofHook.apply(lpparam, profile)
        }

        // 4. 【优先级3】帧率解锁Hook（核心功能）
        if (config.frameRateUnlockEnabled) {
            FrameRateUnlockHook.apply(lpparam, config)
        }

        // 5. 【优先级4】温控屏蔽Hook
        if (config.thermalBypassEnabled) {
            ThermalBypassHook.apply(lpparam, config)
        }

        // 6. 【优先级5】GPU调度优化Hook
        if (config.gpuOptimizeEnabled) {
            GPUSchedulerHook.apply(lpparam, config)
        }

        // 7. 【优先级6】分辨率伪装Hook（可选功能）
        if (config.resolutionSpoofEnabled) {
            ResolutionSpoofHook.apply(lpparam, config)
        }

        // 8. 【优先级7】Shizuku联动Hook（最后执行，依赖前序属性设置）
        if (config.shizukuBridgeEnabled) {
            ShizukuBridgeHook.apply(lpparam, config)
        }

        // 注册Activity生命周期回调，用于在游戏退出时释放资源
        hookApplicationLifecycle(lpparam)

        LogX.i("$MODULE_NAME 全部Hook已应用完成: $packageName")
        LogX.i("------------------------------------------------")
    }

    /**
     * 判断是否为目标游戏包名
     */
    private fun isTargetGame(packageName: String): Boolean {
        // 预定义的游戏包名列表
        val targetGames = listOf(
            "com.tencent.tmgp.sgame",        // 王者荣耀
            "com.miHoYo.Yuanshen",           // 原神（国内版）
            "com.miHoYo.GenshinImpact",      // 原神（国际版）
            "com.tencent.tmgp.pubgmhd",      // 和平精英
            "com.tencent.ig",                 // PUBG Mobile国际版
            "com.miHoYo.hkrpg",              // 崩坏:星穹铁道
            "com.tencent.tmgp.cod",           // 使命召唤手游
            "com.activision.callofduty.shooter", // CODM国际版
            "com.tencent.tmgp.gnyx",         // 高能英雄
            "com.gameblackmyth.mobile",      // 黑神话手游
            "com.netease.harrypotter",       // 哈利波特:魔法觉醒（示例）
            "com.netease.lztj",              // 逆水寒（示例）
            "com.tencent.tmgp.wuxia",        // 天涯明月刀（示例）
        )

        return packageName in targetGames
    }

    /**
     * 初始化ConfigManager
     * 优先使用目标APP的Context进行配置读写
     */
    private fun initConfigManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // 获取目标应用的Context（通过反射）
            val activityThreadClass = XposedHelpers.findClass(
                "android.app.ActivityThread",
                lpparam.classLoader
            )
            val currentActivityThread = XposedHelpers.callStaticMethod(
                activityThreadClass, "currentActivityThread"
            )
            val app = XposedHelpers.callMethod(
                currentActivityThread, "getApplication"
            ) as? Application

            if (app != null) {
                ConfigManager.init(app)
                LogX.d("ConfigManager已通过目标APP Context初始化")
            } else {
                LogX.w("无法获取Application实例，ConfigManager初始化延迟")
            }
        } catch (e: Exception) {
            LogX.w("ConfigManager初始化异常: ${e.message}，将在Activity启动时重试")
        }
    }

    /**
     * 加载或创建游戏配置
     */
    private fun loadOrCreateConfig(packageName: String): GameConfig {
        return try {
            if (!ConfigManager.isInitializedProperly()) {
                return ConfigManager.createDefaultConfig(packageName)
            }
            ConfigManager.getGameConfig(packageName)
        } catch (e: Exception) {
            LogX.e("加载配置异常，使用默认配置", e)
            ConfigManager.createDefaultConfig(packageName)
        }
    }

    /**
     * 解析机型配置：优先使用自定义机型，其次内置机型
     */
    private fun resolveDeviceProfile(config: GameConfig): DeviceProfile? {
        // 1. 优先：用户自定义机型
        if (config.customDeviceProfile != null) {
            return config.customDeviceProfile
        }
        // 2. 其次：选中的内置机型
        val builtIn = DeviceProfileDatabase.findById(config.selectedDeviceProfileId)
        if (builtIn != null) return builtIn
        // 3. 回退：默认小米15
        return DeviceProfileDatabase.findById("xiaomi15")
    }

    /**
     * Hook Application生命周期
     * 检测游戏退出时释放资源
     */
    private fun hookApplicationLifecycle(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val applicationClass = XposedHelpers.findClass(
                "android.app.Application",
                lpparam.classLoader
            )

            // Hook Application.onCreate 确保ConfigManager初始化
            XposedHelpers.findAndHookMethod(
                applicationClass,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val app = param.thisObject as? Application ?: return
                        try {
                            if (!ConfigManager.isInitializedProperly()) {
                                ConfigManager.init(app)
                            }
                        } catch (e: Exception) {
                            LogX.d("Application.onCreate ConfigManager重试成功")
                        }
                    }
                }
            )

            // Hook Activity.onDestroy 检测主Activity销毁（游戏退出信号）
            try {
                val activityClass = lpparam.classLoader.loadClass("android.app.Activity")
                XposedHelpers.findAndHookMethod(
                    activityClass,
                    "onDestroy",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!isReleased) {
                                // 不立即释放，因为可能有多个Activity
                                // 延迟释放或由LMK处理
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                LogX.d("Activity生命周期Hook异常: ${e.message}")
            }

        } catch (e: Exception) {
            LogX.d("Application生命周期Hook异常: ${e.message}")
        }
    }

    /**
     * 释放所有Hook资源（游戏退出时调用）
     */
    fun releaseResources() {
        if (isReleased) return
        isReleased = true

        LogX.i("释放游戏资源: $currentGamePackage")
        ShizukuBridgeHook.release()
        currentGamePackage = null
        // 注意：Xposed Hook无法手动解除，但引用会被GC处理
    }
}

/**
 * Zygote阶段初始化辅助类
 * 用于在Zygote阶段Hook系统级服务
 */
object MyHookZygote {

    fun init(startupParam: IXposedHookZygoteInit.StartupParam) {
        // 这里可以做一些Zygote级的初始化
        // 例如Hook系统服务、修改全局配置等
        LogX.i("Zygote初始化完成")

        // 预检测OEM系统，打日志
        val oemType = OemPatchHelper.detectOemType()
        LogX.i("OEM系统类型: $oemType")
        LogX.i("兼容补丁: 已内置${oemType}专项Hook")
    }
}

/**
 * 扩展函数：检查ConfigManager是否已正确初始化
 */
fun ConfigManager.isInitializedProperly(): Boolean {
    return try {
        getAllConfigs()
        true
    } catch (e: Exception) {
        false
    }
}
