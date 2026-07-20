package com.notifymaster.pro.utils

import java.lang.reflect.Method

/**
 * Shizuku 联动助手（Root 版）
 *
 * 功能：
 *  1. 检测 Shizuku 服务是否可用
 *  2. 通过反射调用 Shizuku.newProcess 执行系统级 Shell 命令
 *     - dumpsys notification 读取通知列表/策略
 *     - settings put global 修改通知策略
 *     - cmd notification post/refresh 发送通知
 *     - cmd notification cancel/cancelAll 撤回通知
 *
 * 硬性限制：
 *  - 系统级 Hook 必须先检查 isShizukuAvailable()
 *  - settings put 修改非持久化（部分项需重启或重新设置）
 *  - 写 /sys 节点需要 root 级别 Shizuku 授权
 *  - 所有调用通过 try-catch 保护，失败不影响其他 Hook
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
        } catch (e: Exception) {
            LogX.w("Shizuku不可用或未安装: ${e.message}")
            false
        }
        return shizukuAvailable!!
    }

    /**
     * 通过 Shizuku 执行 shell 命令
     * @return 命令输出（stdout），失败返回 null
     */
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
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            out
        } catch (e: Exception) {
            LogX.e("Shizuku Shell执行异常: $command", e)
            null
        }
    }

    /** 仅执行不关心输出，返回是否执行成功（未抛异常且 Shizuku 可用） */
    fun execShellSilent(command: String): Boolean {
        return try {
            if (!isShizukuAvailable()) return false
            execShell(command) != null
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 通过 Shizuku 设置系统属性（setprop）
     */
    fun setSystemProperty(key: String, value: String): Boolean {
        return execShellSilent("setprop $key $value")
    }

    /**
     * 通过 Shizuku 写入文件
     */
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

    /**
     * 通过 Shizuku 读取文件内容
     */
    fun readFile(path: String): String? {
        return try {
            if (!isShizukuAvailable()) return null
            execShell("cat $path 2>/dev/null")
        } catch (_: Throwable) { null }
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
