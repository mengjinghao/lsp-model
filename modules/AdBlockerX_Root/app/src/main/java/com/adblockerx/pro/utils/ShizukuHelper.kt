package com.adblockerx.pro.utils

import java.lang.reflect.Method

/**
 * Shizuku 联动助手（Root 版）
 *
 * 功能：
 *  1. 检测 Shizuku 服务是否可用
 *  2. 通过反射调用 Shizuku.newProcess 执行系统级 Shell 命令
 *     - 修改 /data/adb/modules/adblockerx/system/etc/hosts（Magisk overlay）
 *     - 设置系统 Private DNS（settings put global private_dns_*）
 *     - 刷新 DNS 缓存（ndc resolver）
 *     - iptables 添加/删除规则
 *     - 读写 /sys、/proc 等节点
 *
 * 硬性限制：
 *  - 系统级操作前必须调用 isShizukuAvailable 检查
 *  - mount --bind 需 Root 级 Shizuku 授权
 */
object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    private var shizukuAvailable: Boolean? = null

    fun isShizukuAvailable(): Boolean {
        if (shizukuAvailable != null) return shizukuAvailable!!
        shizukuAvailable = try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            val method: Method = cls.getMethod("pingBinder")
            val result = method.invoke(null) as? Boolean ?: false
            LogX.d("Shizuku状态: $result")
            result
        } catch (e: Exception) {
            LogX.w("Shizuku不可用或未安装: ${e.message}")
            false
        }
        return shizukuAvailable!!
    }

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
            } catch (_: Throwable) {}

            out
        } catch (e: Exception) {
            LogX.e("Shizuku Shell执行异常: $command", e)
            null
        }
    }

    fun execShellSilent(command: String): Boolean {
        return try {
            if (!isShizukuAvailable()) return false
            execShell(command) != null
        } catch (_: Throwable) {
            false
        }
    }

    fun setSystemProperty(key: String, value: String): Boolean {
        return execShellSilent("setprop $key $value")
    }

    fun writeFile(path: String, content: String): Boolean {
        return try {
            if (!isShizukuAvailable()) return false
            val escaped = content.replace("'", "'\\''")
            execShell("echo '$escaped' > $path") != null
        } catch (e: Exception) {
            LogX.e("Shizuku写入文件异常: $path", e)
            false
        }
    }

    fun readFile(path: String): String? {
        return try {
            if (!isShizukuAvailable()) return null
            execShell("cat $path 2>/dev/null")
        } catch (_: Throwable) { null }
    }

    fun reset() {
        shizukuAvailable = null
    }

    fun release() {
        shizukuAvailable = null
    }
}
