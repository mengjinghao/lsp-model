package com.microx.enhancer.hooks

import com.microx.enhancer.utils.ConfigManager
import com.microx.enhancer.utils.HookHelper
import com.microx.enhancer.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.io.FileOutputStream

/**
 * 【实验性】Sticker Auto-Collector
 *
 * 监听收到的贴纸消息，自动保存 PNG 文件到应用缓存目录。
 * Hook WeChat 贴纸下载相关方法（sogou/WeChat 贴纸 API），
 * 将贴纸图片流写入本地文件。
 */
object StickerCollectorHook {

    private const val STICKER_DIR = "MicroXEnhancer/stickers"

    private val STICKER_DOWNLOAD_CLASSES = arrayOf(
        "com.tencent.mm.plugin.emoji.model.EmojiLogic",
        "com.tencent.mm.plugin.emoji.mgr.EmojiMgr",
        "com.tencent.mm.emoji.loader.EmojiDownloader",
        "com.tencent.mm.emoji.model.EmojiInfo"
    )

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!ConfigManager.isEnabled(ConfigManager.KEY_STICKER_COLLECTOR)) return
        LogX.i("【实验性】Sticker Auto-Collector 启动")

        hookStickerDownload(lpparam)
        hookStickerReceive(lpparam)
    }

    /** Hook 贴纸下载方法 */
    private fun hookStickerDownload(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in STICKER_DOWNLOAD_CLASSES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            try {
                HookHelper.hookAllMethodsSafe(cls, "downloadEmoji", object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            saveStickerData(p)
                        } catch (t: Throwable) {
                            LogX.e("【StickerCollector】保存贴纸失败", t)
                        }
                    }
                })
                LogX.hookSuccess(clsName, "downloadEmoji")
            } catch (e: Throwable) {
                LogX.w("【StickerCollector】${e.message}")
            }
        }
    }

    /** Hook 贴纸接收回调 */
    private fun hookStickerReceive(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val appCls = XposedHelpers.findClass(
                "android.app.Application", lpparam.classLoader
            )
            XposedHelpers.findAndHookMethod(appCls, "onCreate", object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val ctx = p.thisObject as? android.content.Context ?: return
                    val dir = File(ctx.cacheDir, STICKER_DIR)
                    if (!dir.exists()) dir.mkdirs()
                }
            })
            LogX.hookSuccess("StickerCollector", "Application.onCreate")
        } catch (e: Throwable) {
            LogX.w("【StickerCollector】初始化异常: ${e.message}")
        }
    }

    /** 保存贴纸数据到本地 */
    private fun saveStickerData(p: XC_MethodHook.MethodHookParam) {
        try {
            val result = p.result
            if (result is ByteArray) {
                val timestamp = System.currentTimeMillis()
                val cacheDir = File(
                    android.app.ActivityThread.currentApplication().cacheDir,
                    STICKER_DIR
                )
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val file = File(cacheDir, "sticker_$timestamp.png")
                FileOutputStream(file).use { it.write(result) }
                LogX.d("【StickerCollector】已保存贴纸: ${file.name} (${result.size} bytes)")
            }
        } catch (_: Throwable) {}
    }
}
