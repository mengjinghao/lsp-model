package com.gameunlocker.noroot.utils

/**
 * Shizuku反射调用助手
 *
 * 硬性限制：
 *  - LSPatch本地模式下Shizuku未必在运行，所有调用通过try-catch保护
 *  - 系统属性修改(setprop)需要Shizuku adb级授权，必须提前在Shizuku App中激活
 *  - 冻结后台应用(am force-stop)同样需要Shizuku授权
 *
 * 使用前必须：
 *  1. 安装Shizuku App
 *  2. 通过无线调试或ADB激活Shizuku
 *  3. 在Shizuku中授权本模块
 */
object ShizukuHelper {

    private var available: Boolean? = null

    /** 检测Shizuku是否可用 */
    fun isAvailable(): Boolean {
        if (available != null) return available!!
        available = try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            val m = cls.getMethod("pingBinder")
            (m.invoke(null) as? Boolean) ?: false
        } catch (e: Exception) {
            false
        }
        return available!!
    }

    /**
     * 通过Shizuku执行Shell命令
     * 用于：setprop设置刷新率属性、am force-stop冻结后台
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
        } catch (e: Exception) {
            LogX.e("Shizuku Shell异常: $cmd", e)
            null
        }
    }

    fun reset() { available = null }
}
