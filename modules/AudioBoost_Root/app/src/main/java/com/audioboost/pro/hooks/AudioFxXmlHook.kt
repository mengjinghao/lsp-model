package com.audioboost.pro.hooks

import com.audioboost.pro.models.AudioConfig
import com.audioboost.pro.utils.LogX
import com.audioboost.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Audio Effects XML 配置 Hook（Root 版独有）
 *
 * 通过 Shizuku 直接修改音频效果配置文件：
 *  - cp 备份原始 audio_effects.xml 到 /data/local/tmp/
 *  - 编辑 XML 添加 compressor/limiter 效果
 *  - mount --bind 注入修改后的配置
 *  - killall audioserver 重新加载
 *  - 同时处理 /vendor/etc/audio_effects.conf 旧格式
 *
 * 硬性限制：
 *  - 必须 ShizukuHelper.isShizukuAvailable()
 *  - mount --bind 需 root 级 Shizuku
 *  - 全部 try-catch 保护
 */
object AudioFxXmlHook {

    private var isApplied = false
    private var isRestored = false

    private const val AUDIO_EFFECTS_XML = "/vendor/etc/audio_effects.xml"
    private const val AUDIO_EFFECTS_CONF = "/vendor/etc/audio_effects.conf"
    private const val BACKUP_XML = "/data/local/tmp/audio_effects.xml"
    private const val BACKUP_CONF = "/data/local/tmp/audio_effects.conf"

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.audioFxXmlEnabled) {
            LogX.d("AudioFxXmlHook 未启用，跳过")
            return
        }
        if (isApplied) return

        LogX.i("AudioFxXmlHook 启动：Audio Effects XML 配置修改")

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        isApplied = true
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku 不可用，跳过 Audio Effects XML 修改")
                            return
                        }
                        backupAndApply()
                    }
                })
            LogX.hookSuccess("Application", "onCreate->AudioFxXmlHook")
        } catch (e: Throwable) {
            LogX.e("AudioFxXmlHook Application.onCreate Hook 异常", e)
        }
    }

    private fun backupAndApply() {
        try {
            ShizukuHelper.execShell("cp $AUDIO_EFFECTS_XML $BACKUP_XML 2>&1")
            LogX.d("备份完成: $AUDIO_EFFECTS_XML -> $BACKUP_XML")
        } catch (e: Throwable) { LogX.w("备份 audio_effects.xml 异常: ${e.message}") }

        try {
            ShizukuHelper.execShell("cp $AUDIO_EFFECTS_CONF $BACKUP_CONF 2>&1")
            LogX.d("备份完成: $AUDIO_EFFECTS_CONF -> $BACKUP_CONF")
        } catch (e: Throwable) { LogX.w("备份 audio_effects.conf 异常: ${e.message}") }

        try {
            injectBassBoost()
        } catch (e: Throwable) { LogX.w("注入低音增强异常: ${e.message}") }

        try {
            ShizukuHelper.execShell("mount --bind $BACKUP_XML $AUDIO_EFFECTS_XML 2>&1")
            LogX.d("mount --bind XML 完成")
        } catch (e: Throwable) { LogX.w("mount --bind XML 异常: ${e.message}") }

        try {
            ShizukuHelper.execShell("mount --bind $BACKUP_CONF $AUDIO_EFFECTS_CONF 2>&1")
            LogX.d("mount --bind CONF 完成")
        } catch (e: Throwable) { LogX.w("mount --bind CONF 异常: ${e.message}") }

        try {
            ShizukuHelper.execShell("killall audioserver 2>&1")
            LogX.d("audioserver 已重启")
        } catch (e: Throwable) { LogX.w("killall audioserver 异常: ${e.message}") }

        LogX.i("AudioFxXmlHook: Audio Effects 配置已注入")
    }

    private fun injectBassBoost() {
        val xmlContent = ShizukuHelper.readFile(BACKUP_XML) ?: return
        if (xmlContent.contains("bassboost")) {
            LogX.d("bassboost 效果已存在，跳过注入")
            return
        }

        val bassEffect = """
            <effect name="bassboost" library="bundle" uuid="8631f300-72e2-11df-b57e-0002a5d5c51b">
                <param name="bass_strength" value="60"/>
                <param name="virtualizer_strength" value="30"/>
                <param name="loudness_enhancer" value="true"/>
                <stream type="music"/>
                <stream type="ring"/>
                <stream type="notification"/>
            </effect>
        """.trimIndent()

        val modified = xmlContent.replace(
            "</audio_effects_conf>",
            "$bassEffect\n</audio_effects_conf>"
        )

        ShizukuHelper.execShell("echo '${modified.replace("'", "'\\''")}' > $BACKUP_XML 2>&1")
        LogX.d("bassboost 效果已注入到 $BACKUP_XML")
    }

    fun restore() {
        if (isRestored) return
        try {
            if (!ShizukuHelper.isShizukuAvailable()) return
            ShizukuHelper.execShell("umount $AUDIO_EFFECTS_XML 2>&1")
            ShizukuHelper.execShell("umount $AUDIO_EFFECTS_CONF 2>&1")
            ShizukuHelper.execShell("killall audioserver 2>&1")
            isRestored = true
            LogX.i("AudioFxXmlHook: 已恢复原始配置")
        } catch (e: Throwable) { LogX.w("恢复 audio_effects 异常: ${e.message}") }
    }

    fun release() {
        isApplied = false
    }
}
