package com.adblockerx.pro.utils

import java.lang.reflect.Method

/**
 * Shizuku 联动助手（Root 版）
 *
 * 功能：
 *  1. 检测 Shizuku 服务是否可用
 *  2. 通过反射调用 Shizuku API 执行系统级 Shell 命令
 *     - 修改 /data/adb/hosts（Magisk overlay 风格）
 *     - 设置系统 Private DNS
 *     - 刷新 DNS 缓存
 *
 * 注意事项：
 *  - LSPosed 模式下 Shizuku 通常已授权
 *  - LSPatch 本地模式下 Shizuku 可能未运行，所有调用必须 try-catch
 *  - 系统级操作前必须调用 [isShizukuAvailable] 检查
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
            if (!isShizukuAvailable()) {
                LogX.w("Shizuku不可用，跳过命令: $command")
                return null
            }
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

            // 等待进程结束（防止僵死）
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
     * 通过 Shizuku 设置系统属性
     */
    fun setSystemProperty(key: String, value: String): Boolean {
        return execShellSilent("setprop $key $value")
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
