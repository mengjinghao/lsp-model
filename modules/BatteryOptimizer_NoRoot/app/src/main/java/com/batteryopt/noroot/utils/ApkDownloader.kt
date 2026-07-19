package com.batteryopt.noroot.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * APK 下载器
 *
 * 下载 APK 到应用缓存目录，下载完成后触发系统安装界面。
 * 需要 FileProvider 配置（AndroidManifest + res/xml/file_paths.xml）。
 */
object ApkDownloader {

    /**
     * 下载 APK 并触发安装
     * @param onProgress 进度回调 0.0 ~ 1.0
     * @return 是否成功下载（安装由系统接管，不保证安装成功）
     */
    fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        fileName: String,
        onProgress: (Float) -> Unit
    ): Boolean {
        return try {
            val cacheDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(cacheDir, fileName)
            if (apkFile.exists()) apkFile.delete()

            val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 60000
                setRequestProperty("User-Agent", "LSP-Model-Updater")
                instanceFollowRedirects = true
            }
            if (conn.responseCode != 200) {
                LogX.w("下载失败: HTTP ${conn.responseCode}")
                return false
            }
            val total = conn.contentLengthLong
            FileOutputStream(apkFile).use { out ->
                conn.inputStream.use { input ->
                    val buf = ByteArray(8192)
                    var read: Int
                    var downloaded = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        downloaded += read
                        if (total > 0) onProgress(downloaded.toFloat() / total)
                    }
                }
            }
            conn.disconnect()
            LogX.i("APK 下载完成: ${apkFile.absolutePath} (${apkFile.length() / 1024}KB)")
            onProgress(1f)
            promptInstall(context, apkFile)
            true
        } catch (e: Exception) {
            LogX.e("APK 下载安装失败", e)
            false
        }
    }

    private fun promptInstall(context: Context, apkFile: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
