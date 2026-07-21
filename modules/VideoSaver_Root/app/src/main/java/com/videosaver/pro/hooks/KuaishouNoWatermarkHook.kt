package com.videosaver.pro.hooks

import com.videosaver.pro.models.VideoConfig
import com.videosaver.pro.utils.LogX
import com.videosaver.pro.utils.VideoFileSaver
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.net.HttpURLConnection
import java.net.URL

/**
 * 快手无水印下载 Hook（Root 版，应用进程内）
 *
 * 与 NoRoot 版逻辑相同。
 */
object KuaishouNoWatermarkHook {

    private val FEED_CLASS_CANDIDATES = arrayOf(
        "com.kuaishou.android.model.feed.VideoFeedInfo",
        "com.kuaishou.android.model.feed.PhotoModel",
        "com.yxcorp.gifshow.feed.model.FeedInfo",
        "com.yxcorp.gifshow.video.KSVideoPlayerItem",
        "com.yxcorp.gifshow.entity.QPhoto"
    )

    private val URL_METHOD_CANDIDATES = arrayOf(
        "getVideoUrl", "getPlayUrl", "getCoverUrl", "getShareUrl",
        "getUrl", "getMainMvUrl"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        if (!cfg.kuaishouNoWatermark) return
        LogX.i("快手无水印下载 Hook 启动（Root 版）")

        hookFeedUrlGetters(lpparam, cfg)
        hookDownloadEntry(lpparam, cfg)
    }

    private fun hookFeedUrlGetters(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        for (clsName in FEED_CLASS_CANDIDATES) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                for (methodName in URL_METHOD_CANDIDATES) {
                    try {
                        XposedHelpers.findAndHookMethod(cls, methodName, object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                try {
                                    val raw = p.result as? String ?: return
                                    val cleaned = stripWatermark(raw)
                                    if (cleaned != raw) {
                                        p.result = cleaned
                                        LogX.d("快手 URL 去水印: $raw -> $cleaned")
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

    private fun hookDownloadEntry(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        val downloadEntryCandidates = arrayOf(
            "com.yxcorp.gifshow.download.DownloadManager",
            "com.yxcorp.gifshow.util.DownloadUtils",
            "com.kuaishou.android.download.VideoDownloadHelper"
        )
        for (clsName in downloadEntryCandidates) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                try {
                    XposedHelpers.findAndHookMethod(cls, "download",
                        String::class.java, String::class.java, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val url = p.args.firstOrNull() as? String ?: return
                                val cleaned = stripWatermark(url)
                                if (cleaned != url) {
                                    p.args[0] = cleaned
                                    LogX.d("快手 download 参数已替换为无水印 URL")
                                }
                                triggerDownload(cleaned, "kuaishou", cfg)
                            }
                        })
                    LogX.hookSuccess(clsName, "download")
                } catch (_: Throwable) { }
                try {
                    XposedHelpers.findAndHookMethod(cls, "start_download",
                        String::class.java, String::class.java, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                val url = p.args.firstOrNull() as? String ?: return
                                val cleaned = stripWatermark(url)
                                if (cleaned != url) {
                                    p.args[0] = cleaned
                                    LogX.d("快手 start_download 参数已替换")
                                }
                                triggerDownload(cleaned, "kuaishou", cfg)
                            }
                        })
                    LogX.hookSuccess(clsName, "start_download")
                } catch (_: Throwable) { }
            } catch (_: Throwable) { }
        }
    }

    private fun stripWatermark(url: String): String {
        if (url.isBlank()) return url
        var u = url
        u = u.replace("/watermark/", "/origin/")
        u = u.replace(Regex("&?watermark=[^&]*"), "")
        u = u.replace(Regex("&?wm=[^&]*"), "")
        u = u.replace(Regex("&?nw=[^&]*"), "")
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
                    LogX.w("快手视频下载失败 HTTP ${conn.responseCode}")
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
                LogX.w("快手视频下载异常: ${e.message}")
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
}
