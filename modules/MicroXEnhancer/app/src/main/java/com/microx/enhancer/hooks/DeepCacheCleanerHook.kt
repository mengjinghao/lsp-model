package com.microx.enhancer.hooks

import com.microx.enhancer.utils.ConfigManager
import com.microx.enhancer.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File

/**
 * 【实验性】Deep Cache Cleaner
 *
 * 扫描微信数据目录，计算各缓存子目录大小，汇总上报。
 * 扫描目标：
 *  - /data/data/com.tencent.mm/MicroMsg/ 下的 image2, video, voice2, avatar 等
 *  - 缓存目录：cache, code_cache, Glide 缓存等
 *
 * 结果通过日志输出，可供 UI 展示总缓存大小。
 */
object DeepCacheCleanerHook {

    private val SCAN_DIRS = listOf(
        "MicroMsg",
        "image2",
        "video",
        "voice2",
        "avatar",
        "emoji",
        "sfs",
        "app_brand",
        "app_cache",
        "cache",
        "code_cache",
        "files/public",
        "files/fts"
    )

    private val SKIP_DIRS = setOf(
        "databases", "shared_prefs", "lib", "files/tencent"
    )

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!ConfigManager.isEnabled(ConfigManager.KEY_DEEP_CACHE_CLEAN)) return
        LogX.i("【实验性】Deep Cache Cleaner 启动")

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val ctx = p.thisObject as? android.content.Context ?: return
                        Thread {
                            try {
                                Thread.sleep(5000)
                                val report = generateCacheReport(ctx)
                                LogX.i("【DeepCache】Cache Report:\n$report")
                            } catch (t: Throwable) {
                                LogX.e("【DeepCache】扫描异常", t)
                            }
                        }.start()
                    }
                })
            LogX.hookSuccess("DeepCacheCleaner", "Application.onCreate")
        } catch (e: Throwable) {
            LogX.hookFailed("DeepCacheCleaner", "Application.onCreate", e)
        }
    }

    /** 生成缓存大小诊断报告 */
    private fun generateCacheReport(ctx: android.content.Context): String {
        val dataDir = ctx.dataDir ?: return "{\"error\": \"dataDir is null\"}"
        val parentDir = dataDir.parentFile ?: return "{\"error\": \"parentDir is null\"}"

        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"tool\": \"DeepCacheCleaner\",")
        sb.appendLine("  \"timestamp\": ${System.currentTimeMillis()},")
        sb.appendLine("  \"data_dir\": \"${dataDir.absolutePath}\",")
        sb.append("  \"dirs\": {")

        var totalSize = 0L
        var first = true

        for (dirName in SCAN_DIRS) {
            val dir = File(parentDir, dirName)
            if (dir.exists() && dir.isDirectory) {
                val size = getDirSize(dir)
                totalSize += size
                if (!first) sb.append(",")
                sb.appendLine("")
                sb.append("    \"$dirName\": $size")
                first = false
            }
        }

        sb.appendLine()
        sb.appendLine("  },")
        sb.appendLine("  \"summary\": {")
        sb.appendLine("    \"total_bytes\": $totalSize,")
        val mb = totalSize / (1024.0 * 1024.0)
        sb.appendLine("    \"total_mb\": ${"%.2f".format(mb)},")
        val gb = totalSize / (1024.0 * 1024.0 * 1024.0)
        sb.appendLine("    \"total_gb\": ${"%.2f".format(gb)}")
        sb.appendLine("  }")
        sb.appendLine("}")

        return sb.toString()
    }

    /** 递归计算目录大小 */
    private fun getDirSize(dir: File): Long {
        var size = 0L
        try {
            val files = dir.listFiles() ?: return 0L
            for (file in files) {
                try {
                    if (file.isFile) {
                        size += file.length()
                    } else if (file.isDirectory && file.name !in SKIP_DIRS) {
                        size += getDirSize(file)
                    }
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
        return size
    }
}
