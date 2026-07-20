package com.audioboost.pro

import android.app.Application
import com.audioboost.pro.hooks.*
import com.audioboost.pro.models.AudioConfig
import com.audioboost.pro.utils.ConfigManager
import com.audioboost.pro.utils.HookConfigReader
import com.audioboost.pro.utils.LogX
import com.audioboost.pro.utils.ShizukuHelper
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * AudioBoost Pro - Xposed 模块唯一入口（Root 版）
 *
 * 实现 IXposedHookLoadPackage + IXposedHookZygoteInit。
 *
 * 工作流程：
 *  APP启动 -> handleLoadPackage ->
 *    判断是否为目标音乐/音频APP ->
 *    读取全局配置 ->
 *    [基础] 音量增强 / 低音增强 / 均衡器
 *    [实验] 扬声器增强 / 麦克风增益 / 音质增强
 *    [Root 专属] 系统级音量突破 / AudioFlinger 节点写入
 *    [Root 实验性] AudioPolicy 修改 / Shizuku Audio Bridge
 *
 * 与 NoRoot 版区别：
 *  - 增加 SystemVolumeHook 通过 Shizuku 修改系统音量突破上限
 *  - 增加 AudioFlingerHook 写 /sys/class/audio/pcm 节点
 *  - 增加 GlobalAudioPolicyHook 修改 AudioPolicy 配置
 *  - 增加 ShizukuAudioBridgeHook 执行 cmd media_audio
 *  - 所有系统级 Hook 必须先检查 ShizukuHelper.isShizukuAvailable()
 */
class XposedLoader : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        const val VERSION = "1.0.8"
        var currentPkg: String? = null
    }

    override fun initZygote(param: IXposedHookZygoteInit.StartupParam) {
        LogX.i("AudioBoost Pro v$VERSION 初始化 | LSPosed Root 模式")
        // 预热 Shizuku 状态
        try { ShizukuHelper.isShizukuAvailable() } catch (_: Throwable) {}
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName ?: return
        if (!isTargetApp(pkg)) return

        LogX.i("===== APP启动: $pkg =====")
        currentPkg = pkg

        initConfig(lpparam)

        val cfg = loadConfig()
        LogX.i("配置: 总开关=${cfg.masterEnabled} 音量=${cfg.volumeBoostEnabled} " +
                "低音=${cfg.bassBoostEnabled} 均衡器=${cfg.equalizerEnabled} " +
                "[实验]扬声器=${cfg.speakerBoostEnabled} 麦克风=${cfg.micBoostEnabled} 音质=${cfg.audioQualityEnhanceEnabled} " +
                "[Root]系统音量=${cfg.systemVolumeBoostEnabled} AudioFlinger=${cfg.audioFlingerNodeEnabled} " +
                "[Root实验]AudioPolicy=${cfg.globalAudioPolicyEnabled} ShizukuBridge=${cfg.shizukuAudioBridgeEnabled}")

        if (!cfg.masterEnabled) {
            LogX.i("总开关关闭，跳过所有Hook")
            return
        }

        // ===== 基础功能（同 NoRoot） =====
        if (cfg.volumeBoostEnabled) VolumeBoostHook.apply(lpparam, cfg)
        if (cfg.bassBoostEnabled) BassBoostHook.apply(lpparam, cfg)
        if (cfg.equalizerEnabled) EqualizerHook.apply(lpparam, cfg)

        // ===== 实验性功能（同 NoRoot） =====
        if (cfg.speakerBoostEnabled) SpeakerBoostHook.apply(lpparam, cfg)
        if (cfg.micBoostEnabled) MicBoostHook.apply(lpparam, cfg)
        if (cfg.audioQualityEnhanceEnabled) AudioQualityEnhanceHook.apply(lpparam, cfg)

        // ===== Root 专属（系统级，必须先检查 Shizuku） =====
        if (cfg.systemVolumeBoostEnabled) SystemVolumeHook.apply(lpparam, cfg)
        if (cfg.audioFlingerNodeEnabled) AudioFlingerHook.apply(lpparam, cfg)

        // ===== Root 实验性 =====
        if (cfg.globalAudioPolicyEnabled) GlobalAudioPolicyHook.apply(lpparam, cfg)
        if (cfg.shizukuAudioBridgeEnabled) ShizukuAudioBridgeHook.apply(lpparam, cfg)

        hookAppLifecycle(lpparam)
        LogX.i("===== 全部Hook就绪: $pkg =====")
    }

    /** 目标APP包名白名单（同 NoRoot） */
    private fun isTargetApp(pkg: String) = pkg in listOf(
        "com.netease.cloudmusic", "com.tencent.wmusic", "com.kugou.android",
        "com.kuwo.player", "com.netease.cloudmusic.player", "com.spotify.music",
        "com.google.android.apps.youtube.music", "com.tencent.mm",
        "com.ss.android.ugc.aweme", "com.smile.gifmaker",
        "com.miui.player", "com.hihonor.cloudmusic"
    )

    /** 读取配置：优先XSharedPreferences，回退Context */
    private fun loadConfig(): AudioConfig {
        HookConfigReader.readGlobal()?.let { return it }
        return try { ConfigManager.getGlobalConfig() } catch (_: Throwable) { AudioConfig(packageName = "global") }
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
