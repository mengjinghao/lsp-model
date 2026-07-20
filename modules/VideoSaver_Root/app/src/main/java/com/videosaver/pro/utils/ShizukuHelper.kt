package com.videosaver.pro.utils

import java.lang.reflect.Method

/**
 * Shizuku 联动助手（Root 版）
 *
 * 功能：
 *  1. 检测 Shizuku 服务是否可用
 *  2. 通过反射调用 Shizuku.newProcess 执行系统级 Shell 命令
 *     - am broadcast 触发系统下载
 *     - settings put 修改系统设置
 *     - echo ... > /etc/hosts 修改 hosts（需 root）
 *     - 写 /sys/class/video/* 节点
 *
 * 硬性限制：
 *  - 系统级 Hook 必须先检查 isShizukuAvailable()
 *  - 写 /sys /etc/hosts 需要 root 级别 Shizuku 授权
 *  - 所有调用通过 try-catch 保护，失败不影响其他 Hook
 *  - catch 块使用 `catch (_: Throwable) {}` 静默处理
 */
object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    private var shizukuAvailable: Boolean? = null

    /** 检查 Shizuku 是否可用 */
    fun isShizukuAvailable(): Boolean {
        if (shizukuAvailable != null) return shizukuAvailable!!
        shizukuAvailable = try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            val method: Method = cls.getMethod("pingBinder")
            val result = method.invoke(null) as? Boolean ?: false
            LogX.d("Shizuku状态: $result")
            result
        } catch (_: Throwable) {
            LogX.w("Shizuku不可用或未安装")
            false
        }
        return shizukuAvailable!!
    }

    /** 通过 Shizuku 执行 shell 命令，返回 stdout */
    fun execShell(command: String): String? {
        return try {
            if (!isShizukuAvailable()) return null
            val shizukuCls = Class.forName("rikka.shizuku.Shizuku")
            val newProcessMethod = shizukuCls.getMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) ?: return null

            val isMethod = process.javaClass.getMethod("getInputStream")
            val isStr = isMethod.invoke(process) as? java.io.InputStream
            val out = isStr?.bufferedReader()?.readText()

            try {
                val waitFor = process.javaClass.getMethod("waitFor")
                waitFor.invoke(process)
            } catch (_: Throwable) { }

            out
        } catch (e: Exception) {
            LogX.e("Shizuku Shell执行异常: $command", e)
            null
        }
    }

    /** 仅执行不关心输出，返回是否执行成功 */
    fun execShellSilent(command: String): Boolean {
        return try {
            if (!isShizukuAvailable()) return false
            execShell(command) != null
        } catch (_: Throwable) {
            false
        }
    }

    /** 通过 Shizuku 设置系统属性 */
    fun setSystemProperty(key: String, value: String): Boolean {
        return execShellSilent("setprop $key $value")
    }

    /** 通过 Shizuku 写入文件（需 root 级别） */
    fun writeFile(path: String, content: String): Boolean {
        return try {
            if (!isShizukuAvailable()) return false
            val escaped = content.replace("'", "'\\''")
            execShell("echo '$escaped' > $path") != null
        } catch (_: Throwable) {
            false
        }
    }

    /** 通过 Shizuku 读取文件内容 */
    fun readFile(path: String): String? {
        return try {
            if (!isShizukuAvailable()) return null
            execShell("cat $path 2>/dev/null")
        } catch (_: Throwable) { null }
    }

    /** 通过 Shizuku 执行 am broadcast */
    fun broadcast(action: String, vararg extras: Pair<String, String>): Boolean {
        val cmd = StringBuilder("am broadcast -a $action")
        for ((k, v) in extras) {
            cmd.append(" --es $k \"").append(v.replace("\"", "\\\"")).append("\"")
        }
        return execShellSilent(cmd.toString())
    }

    /** 通过 Shizuku 启动系统下载服务 */
    fun startSystemDownload(url: String, fileName: String): Boolean {
        val cmd = "am start -a android.intent.action.VIEW -d \"$url\" -t video/mp4"
        return execShellSilent(cmd)
    }

    /** 重置 Shizuku 状态（重新检测） */
    fun reset() {
        shizukuAvailable = null
    }

    /** 释放资源 */
    fun release() {
        shizukuAvailable = null
    }
}
