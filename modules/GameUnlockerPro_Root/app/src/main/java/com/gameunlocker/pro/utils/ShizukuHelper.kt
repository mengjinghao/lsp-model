package com.gameunlocker.pro.utils

/**
 * Shizuku 反射调用助手（Root 版主动调用 Shizuku 执行系统级操作）
 *
 * 功能：
 *  1. isAvailable: 检测 Shizuku 服务是否可用（pingBinder）
 *  2. execShell: 通过 Shizuku.newProcess 执行任意 Shell 命令
 *  3. setSystemProperty: 通过 Shizuku setprop 修改系统属性
 *
 * 使用前必须：
 *  1. 安装 Shizuku App
 *  2. 通过无线调试或 ADB 激活 Shizuku
 *  3. 在 Shizuku 中授权本模块
 *
 * 硬性限制：
 *  - ro.* 属性原生不可写，setprop 非持久化，重启后失效
 *  - LSPatch 本地模式下 Shizuku 未必在运行，所有调用通过 try-catch 保护
 */
object ShizukuHelper {

    private var available: Boolean? = null

    /** 检测 Shizuku 是否可用 */
    fun isAvailable(): Boolean {
        if (available != null) return available!!
        available = try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            val m = cls.getMethod("pingBinder")
            (m.invoke(null) as? Boolean) ?: false
        } catch (_: Throwable) {
            false
        }
        return available!!
    }

    /**
     * 通过 Shizuku 执行 Shell 命令
     * 用于：setprop 设置刷新率属性、am force-stop 冻结后台、写 sysfs 节点
     */
    fun execShell(cmd: String): String? {
        if (!isAvailable()) return null
        return try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            val m = cls.getMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            val process = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) ?: return null
            val isField = process.javaClass.getMethod("getInputStream")
            val isStr = isField.invoke(process) as? java.io.InputStream
            isStr?.bufferedReader()?.readText()
        } catch (e: Throwable) {
            LogX.e("Shizuku Shell 异常: $cmd", e)
            null
        }
    }

    /** 通过 Shizuku setprop 修改系统属性 */
    fun setSystemProperty(key: String, value: String): Boolean {
        if (!isAvailable()) {
            LogX.w("Shizuku 不可用，跳过 setprop: $key=$value")
            return false
        }
        val r = execShell("setprop $key $value")
        val ok = r != null
        if (ok) LogX.d("Shizuku setprop: $key=$value")
        else LogX.w("Shizuku setprop 失败: $key=$value")
        return ok
    }

    fun reset() { available = null }
}
