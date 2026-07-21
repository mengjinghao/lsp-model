package com.videosaver.pro.utils

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 视频文件保存器（Root 版）
 *
 * 策略：
 *  - 优先使用 customSavePath
 *  - 路径不可写时回退到外部存储公共目录 Movies/VideoSaver/
 *  - 自动重命名：平台_时间戳.扩展名
 *
 * Root 版额外能力：
 *  - 通过 ShizukuHelper 写入受限路径（如 /sdcard 系统级目录）
 */
object VideoFileSaver {

    private const val DEFAULT_DIR = "/sdcard/Download/VideoSaver/"

    fun saveStream(
        context: Context?,
        input: InputStream,
        platform: String,
        extension: String = "mp4",
        customPath: String? = null,
        autoRename: Boolean = true
    ): String? {
        return try {
            val dir = resolveSaveDir(context, customPath)
            if (!dir.exists()) dir.mkdirs()
            val fileName = buildFileName(platform, extension, autoRename)
            val target = File(dir, fileName)
            FileOutputStream(target).use { out ->
                val buf = ByteArray(8192)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                }
                out.flush()
            }
            LogX.i("视频已保存: ${target.absolutePath} (${target.length() / 1024} KB)")
            target.absolutePath
        } catch (e: Throwable) {
            LogX.w("视频保存失败: ${e.message}")
            null
        }
    }

    fun saveBytes(
        context: Context?,
        bytes: ByteArray,
        platform: String,
        extension: String = "mp4",
        customPath: String? = null,
        autoRename: Boolean = true
    ): String? {
        return try {
            val dir = resolveSaveDir(context, customPath)
            if (!dir.exists()) dir.mkdirs()
            val fileName = buildFileName(platform, extension, autoRename)
            val target = File(dir, fileName)
            FileOutputStream(target).use { it.write(bytes) }
            LogX.i("视频已保存: ${target.absolutePath} (${target.length() / 1024} KB)")
            target.absolutePath
        } catch (e: Throwable) {
            LogX.w("视频保存失败: ${e.message}")
            null
        }
    }

    private fun resolveSaveDir(context: Context?, customPath: String?): File {
        if (!customPath.isNullOrBlank()) {
            val f = File(customPath)
            if (f.isAbsolute) return f
        }
        return try {
            val movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            File(movies, "VideoSaver")
        } catch (_: Throwable) {
            File(DEFAULT_DIR)
        }
    }

    private fun buildFileName(platform: String, extension: String, autoRename: Boolean): String {
        val safePlatform = platform.replace(Regex("[^A-Za-z0-9_]"), "_")
        return if (autoRename) {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            "${safePlatform}_$ts.$extension"
        } else {
            "$safePlatform.$extension"
        }
    }

    fun resolveSaveDirString(customPath: String?): String {
        return resolveSaveDir(null, customPath).absolutePath
    }
}
