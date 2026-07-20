package com.videosaver.pro.hooks

import com.videosaver.pro.models.VideoConfig
import com.videosaver.pro.utils.LogX
import com.videosaver.pro.utils.VideoFileSaver
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 抖音无水印下载 Hook（Root 版，应用进程内）
 *
 * 与 NoRoot 版逻辑相同，增加 Shizuku 写文件回退支持。
 *
 * 候选类名：
 *  - com.ss.android.ugc.aweme.feed.model.Aweme
 *  - com.ss.android.ugc.aweme.feed.model.VideoModel
 */
object DouyinNoWatermarkHook {

    private val AWEME_CLASS_CANDIDATES = arrayOf(
        "com.ss.android.ugc.aweme.feed.model.Aweme",
        "com.ss.android.ugc.aweme.feed.model.AwemeStruct",
        "com.ss.android.ugc.aweme.feed.model.VideoModel",
        "com.ss.android.ugc.aweme.shortvideo.AwemeCreateModel",
        "com.ss.android.ugc.aweme.model.VideoModel"
    )

    private val DOWNLOAD_METHOD_CANDIDATES = arrayOf(
        "getDownloadUrl", "getVideoUrl", "getPlayUrl", "getDownloadAddr",
        "getPlayAddr", "getShareUrl"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        if (!cfg.douyinNoWatermark) return
        LogX.i("抖音无水印下载 Hook 启动（Root 版）")

        hookAwemeUrlGetters(lpparam, cfg)
        hookVideoDownloadEntry(lpparam, cfg)
    }

    private fun hookAwemeUrlGetters(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        for (clsName in AWEME_CLASS_CANDIDATES) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                for (methodName in DOWNLOAD_METHOD_CANDIDATES) {
                    try {
                        XposedHelpers.findAndHookMethod(cls, methodName, object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                try {
                                    val raw = p.result as? String ?: return
                                    val cleaned = stripWatermark(raw)
                                    if (cleaned != raw) {
                                        p.result = cleaned
                                        LogX.d("抖音 URL 去水印: $raw -> $cleaned")
                                    }
                                } catch (_: Throwable) { }
                            }
                        })
                        LogX.hookSuccess(clsName, methodName)
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
        }
    }

    private fun hookVideoDownloadEntry(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        val downloadEntryCandidates = arrayOf(
            "com.ss.android.ugc.aweme.feed.ui.FeedRecommendFragment",
            "com.ss.android.ugc.aweme.share.SharePackage",
            "com.ss.android.ugc.aweme.services.video.IDownloadService"
        )
        for (clsName in downloadEntryCandidates) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                try {
                    XposedHelpers.findAndHookMethod(cls, "downloadVideo",
                        String::class.java, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val url = p.args.firstOrNull() as? String ?: return
                                val cleaned = stripWatermark(url)
                                if (cleaned != url) {
                                    p.args[0] = cleaned
                                    LogX.d("抖音 downloadVideo 参数已替换为无水印 URL")
                                }
                                triggerDownload(cleaned, "douyin", cfg)
                            }
                        })
                    LogX.hookSuccess(clsName, "downloadVideo")
                } catch (_: Throwable) { }
                try {
                    XposedHelpers.findAndHookMethod(cls, "shareImpl",
                        String::class.java, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val url = p.args.firstOrNull() as? String ?: return
                                val cleaned = stripWatermark(url)
                                if (cleaned != url) {
                                    p.args[0] = cleaned
                                    LogX.d("抖音 shareImpl 参数已替换")
                                }
                            }
                        })
                    LogX.hookSuccess(clsName, "shareImpl")
                } catch (_: Throwable) { }
            } catch (_: Throwable) { }
        }
    }

    private fun stripWatermark(url: String): String {
        if (url.isBlank()) return url
        var u = url
        u = u.replace("playwm", "play")
        u = u.replace(Regex("&?watermark=[^&]*"), "")
        u = u.replace(Regex("&?ttwatermark=[^&]*"), "")
        u = u.replace(Regex("&?wm=[^&]*"), "")
        u = u.replace(Regex("&?enable_watermark=[^&]*"), "")
        u = u.replace(Regex("[?&]$"), "")
        u = u.replace("?&", "?")
        return u
    }

    private fun triggerDownload(url: String, platform: String, cfg: VideoConfig) {
        if (url.isBlank()) return
        Thread {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 60000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) VideoSaver/1.0")
                    instanceFollowRedirects = true
                }
                if (conn.responseCode != 200) {
                    LogX.w("抖音视频下载失败 HTTP ${conn.responseCode}")
                    conn.disconnect()
                    return@Thread
                }
                conn.inputStream.use { ins ->
                    VideoFileSaver.saveStream(
                        context = null,
                        input = ins,
                        platform = platform,
                        extension = guessExtension(url),
                        customPath = cfg.customSavePath,
                        autoRename = cfg.autoRenameEnabled
                    )
                }
                conn.disconnect()
            } catch (e: Throwable) {
                LogX.w("抖音视频下载异常: ${e.message}")
            }
        }.start()
    }

    private fun guessExtension(url: String): String {
        val lower = url.substringBefore("?").lowercase()
        return when {
            lower.endsWith(".mp4") -> "mp4"
            lower.endsWith(".m3u8") -> "m3u8"
            lower.endsWith(".webm") -> "webm"
            else -> "mp4"
        }
    }

    fun saveBytesDirect(bytes: ByteArray, platform: String, cfg: VideoConfig) {
        VideoFileSaver.saveBytes(
            context = null,
            bytes = bytes,
            platform = platform,
            extension = "mp4",
            customPath = cfg.customSavePath,
            autoRename = cfg.autoRenameEnabled
        )
    }

    fun toInputStream(bytes: ByteArray): InputStream = ByteArrayInputStream(bytes)
}
