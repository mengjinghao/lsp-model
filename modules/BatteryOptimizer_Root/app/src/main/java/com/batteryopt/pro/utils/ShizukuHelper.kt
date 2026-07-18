package com.batteryopt.pro.utils

/**
 * Shizuku 联动助手
 *
 * 功能：
 *  1. 检测 Shizuku 服务是否可用
 *  2. 通过反射调用 Shizuku API 执行系统命令（dumpsys/settings/am/setprop 等）
 *
 * 硬性限制：
 *  - LSPatch 本地模式下 Shizuku 未必运行，所有调用通过反射 + try-catch 保护
 *  - am force-stop / dumpsys deviceidle / 写 sysfs 节点等系统级操作均需 Shizuku 授权
 *
 * 使用前必须：
 *  1. 安装 Shizuku App
 *  2. 通过无线调试或 ADB 激活 Shizuku
 *  3. 在 Shizuku 中授权本模块
 */
object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    private var shizukuAvailable: Boolean? = null

    /** 检查 Shizuku 是否可用 */
    fun isShizukuAvailable(): Boolean {
        if (shizukuAvailable != null) return shizukuAvailable!!
        shizukuAvailable = try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            val method = cls.getMethod("pingBinder")
            val result = method.invoke(null) as? Boolean ?: false
            LogX.d("Shizuku 状态: $result")
            result
        } catch (e: Exception) {
            LogX.w("Shizuku 不可用或未安装: ${e.message}")
            false
        }
        return shizukuAvailable!!
    }

    /**
     * 通过 Shizuku 执行 Shell 命令
     * 用于：dumpsys deviceidle / am force-stop / settings put global / 写 sysfs 节点等
     *
     * @param command Shell 命令
     * @return 命令输出（stdout），失败返回 null
     */
    fun execShell(command: String): String? {
        return try {
            if (!isShizukuAvailable()) {
                LogX.w("Shizuku 不可用，跳过命令: $command")
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
            val inputStream = process.javaClass.getMethod("getInputStream").invoke(process)
                as? java.io.InputStream
            val stdout = inputStream?.bufferedReader()?.readText()
            // 等待进程结束避免僵尸
            try { process.javaClass.getMethod("waitFor").invoke(process) } catch (_: Exception) {}
            stdout
        } catch (e: Exception) {
            LogX.e("Shizuku Shell 执行异常: $command", e)
            null
        }
    }

    /**
     * 通过 Shizuku 设置系统属性
     */
    fun setSystemProperty(key: String, value: String): Boolean {
        val result = execShell("setprop $key $value")
        return result != null
    }

    /** 释放资源（模块卸载或 APP 退出时调用） */
    fun release() {
        shizukuAvailable = null
    }
}
