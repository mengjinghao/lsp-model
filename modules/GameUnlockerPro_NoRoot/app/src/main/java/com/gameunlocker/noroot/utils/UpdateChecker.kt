package com.gameunlocker.noroot.utils

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 热更新检查器
 *
 * 从 GitHub Release 检查最新版本，对比当前版本号。
 * 不自动安装，仅提供下载链接（由 UI 层触发下载+安装）。
 *
 * 数据源: https://api.github.com/repos/mengjinghao/lsp-model/releases/latest
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val REPO = "mengjinghao/lsp-model"
    private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"

    data class UpdateInfo(
        val latestVersion: String,      // "1.0.3" (去掉 v 前缀)
        val tagName: String,             // "v1.0.3"
        val releaseUrl: String,          // GitHub Release 页面
        val releaseNotes: String,        // 发布说明
        val publishDate: String,         // ISO 时间
        val apkAssets: List<ApkAsset>,   // APK 下载列表
        val hasUpdate: Boolean,           // 是否有更新
        val currentVersion: String
    )

    data class ApkAsset(
        val name: String,                // "PrivacyGuard_NoRoot.apk"
        val downloadUrl: String,         // 直链
        val sizeBytes: Long
    )

    /**
     * 同步检查更新（需在子线程调用）
     * @param currentVersion 当前版本号，如 "1.0.3"
     */
    fun checkUpdate(currentVersion: String): UpdateInfo? {
        return try {
            val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 15000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "LSP-Model-UpdateChecker")
            }
            if (conn.responseCode != 200) {
                Log.w(TAG, "GitHub API 返回 ${conn.responseCode}")
                return null
            }
            val raw = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            conn.disconnect()
            parseRelease(raw, currentVersion)
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失败: ${e.message}")
            null
        }
    }

    private fun parseRelease(json: String, currentVersion: String): UpdateInfo? {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val tagName = root.get("tag_name")?.asString ?: return null
            val latestVersion = tagName.removePrefix("v").trim()
            val releaseUrl = root.get("html_url")?.asString ?: ""
            val releaseNotes = root.get("body")?.asString ?: ""
            val publishDate = root.get("published_at")?.asString ?: ""

            val assets = mutableListOf<ApkAsset>()
            root.getAsJsonArray("assets")?.forEach { el ->
                val a = el.asJsonObject
                val name = a.get("name")?.asString ?: return@forEach
                if (name.endsWith(".apk")) {
                    assets.add(ApkAsset(
                        name = name,
                        downloadUrl = a.get("browser_download_url")?.asString ?: "",
                        sizeBytes = a.get("size")?.asLong ?: 0L
                    ))
                }
            }

            UpdateInfo(
                latestVersion = latestVersion,
                tagName = tagName,
                releaseUrl = releaseUrl,
                releaseNotes = releaseNotes,
                publishDate = publishDate,
                apkAssets = assets,
                hasUpdate = compareVersion(latestVersion, currentVersion) > 0,
                currentVersion = currentVersion
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析 Release 失败: ${e.message}")
            null
        }
    }

    /** 版本号比较: 返回 >0 表示 a 更新, <0 表示 a 更旧, 0 相同 */
    private fun compareVersion(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va - vb
        }
        return 0
    }

    /** 找到与当前模块 APK 同名的 asset（精确匹配），否则返回第一个 */
    fun findMatchingApk(info: UpdateInfo, moduleName: String): ApkAsset? {
        return info.apkAssets.firstOrNull { it.name.startsWith(moduleName, ignoreCase = true) }
            ?: info.apkAssets.firstOrNull()
    }
}
