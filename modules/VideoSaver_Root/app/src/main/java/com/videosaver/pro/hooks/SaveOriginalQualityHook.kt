package com.videosaver.pro.hooks

import com.videosaver.pro.models.VideoConfig
import com.videosaver.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】强制原画质下载 Hook（Root 版，应用进程内）
 *
 * 与 NoRoot 版逻辑相同。
 */
object SaveOriginalQualityHook {

    private val QUALITY_MANAGER_CANDIDATES = arrayOf(
        "com.ss.android.ugc.aweme.video.VideoQualityManager",
        "com.ss.android.ugc.aweme.feed.model.VideoQualityInfo",
        "com.ss.android.ugc.aweme.feed.model.AwemeQualityInfo",
        "tv.danmaku.bili.player.QualityHelper",
        "com.bilibili.lib.download.QualityManager",
        "com.kuaishou.android.video.QualityManager"
    )

    private val EXO_TRACK_CANDIDATES = arrayOf(
        "com.google.android.exoplayer2.TrackSelectionParameters",
        "androidx.media3.common.TrackSelectionParameters"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        if (!cfg.saveOriginalQualityEnabled) return
        LogX.i("【实验性】强制原画质 Hook 启动（Root 版）")

        hookQualityManager(lpparam)
        hookExoTrackSelection(lpparam)
        hookUrlBuilder(lpparam)
    }

    private fun hookQualityManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in QUALITY_MANAGER_CANDIDATES) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                for (methodName in arrayOf("getCurrentQuality", "getQuality", "getCurrentQN", "getPlayQuality")) {
                    try {
                        XposedHelpers.findAndHookMethod(cls, methodName, object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                try {
                                    val result = p.result
                                    if (result is Int) {
                                        if (result < 100) {
                                            p.result = 1000
                                            LogX.d("画质强制提升: $result -> 1000 (${clsName.substringAfterLast('.')}.$methodName)")
                                        }
                                    }
                                } catch (_: Throwable) { }
                            }
                        })
                        LogX.hookSuccess(clsName, methodName)
                    } catch (_: Throwable) { }
                }
                for (methodName in arrayOf("getMaxQuality", "getMaxQN", "getHighestQuality")) {
                    try {
                        XposedHelpers.findAndHookMethod(cls, methodName, object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                try {
                                    p.result = 1000
                                    LogX.d("最高画质返回 1000 (${clsName.substringAfterLast('.')}.$methodName)")
                                } catch (_: Throwable) { }
                            }
                        })
                        LogX.hookSuccess(clsName, methodName)
                    } catch (_: Throwable) { }
                }
                for (methodName in arrayOf("setQuality", "setPlayQuality", "setCurrentQuality")) {
                    try {
                        XposedHelpers.findAndHookMethod(cls, methodName,
                            Int::class.javaPrimitiveType, object : XC_MethodHook() {
                                override fun beforeHookedMethod(p: MethodHookParam) {
                                    try {
                                        p.args[0] = 1000
                                        LogX.d("画质设置已强制: 1000 (${clsName.substringAfterLast('.')}.$methodName)")
                                    } catch (_: Throwable) { }
                                }
                            })
                        LogX.hookSuccess(clsName, methodName)
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
        }
    }

    private fun hookExoTrackSelection(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in EXO_TRACK_CANDIDATES) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                try {
                    XposedHelpers.findAndHookMethod(cls, "getMaxVideoBitrate", object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            p.result = Int.MAX_VALUE
                            LogX.d("ExoPlayer 视频码率上限已解锁")
                        }
                    })
                    LogX.hookSuccess(clsName, "getMaxVideoBitrate")
                } catch (_: Throwable) { }
                try {
                    XposedHelpers.findAndHookMethod(cls, "getMaxVideoSize", object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            LogX.d("ExoPlayer 视频尺寸上限查询触发")
                        }
                    })
                    LogX.hookSuccess(clsName, "getMaxVideoSize")
                } catch (_: Throwable) { }
            } catch (_: Throwable) { }
        }
    }

    private fun hookUrlBuilder(lpparam: XC_LoadPackage.LoadPackageParam) {
        val urlBuilderCandidates = arrayOf(
            "com.ss.android.ugc.aweme.video.VideoUrlBuilder",
            "com.bytedance.frameworks.baselibnetwork.http.cdn.URLBuilder"
        )
        for (clsName in urlBuilderCandidates) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                try {
                    XposedHelpers.findAndHookMethod(cls, "build", object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                val result = p.result as? String ?: return
                                val cleaned = result
                                    .replace(Regex("_\\d+p"), "")
                                    .replace(Regex("&?resolution=[^&]*"), "")
                                    .replace(Regex("&?quality=[^&]*"), "")
                                if (cleaned != result) {
                                    p.result = cleaned
                                    LogX.d("URL 已强制原画质: $result -> $cleaned")
                                }
                            } catch (_: Throwable) { }
                        }
                    })
                    LogX.hookSuccess(clsName, "build")
                } catch (_: Throwable) { }
            } catch (_: Throwable) { }
        }
    }
}
