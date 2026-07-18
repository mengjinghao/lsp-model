package com.privacyguard.pro.utils

import de.robv.android.xposed.XposedHelpers

/**
 * Shizuku联动助手（Root 版）
 *
 * 功能：
 *  1. 检测 Shizuku 服务是否可用
 *  2. 通过反射调用 Shizuku API 修改系统属性（setprop）
 *  3. 通过 Shizuku 执行 shell 命令（pm grant/revoke、settings put、ip link 等）
 *
 * 硬性限制：
 *  - 系统级 Hook 必须先检查 isShizukuAvailable()
 *  - setprop 修改的属性在重启后消失（非持久化），持久化需写 build.prop
 *  - 写 /sys/class/net/wlan0/address 需要 root 权限，Shizuku adb 级可能不足
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
            val method = cls.getMethod("pingBinder")
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
     * 通过 Shizuku 设置系统属性
     * 用于修改 ro.serialno、ro.boot.serialno、ro.product.* 等属性
     *
     * 注意：ro.* 属性在原生 Android 上不可写，setprop 仅对非只读属性生效。
     * 持久化需写入 build.prop 或 vendor/build.prop（需要 root 级别 Shizuku）。
     */
    fun setSystemProperty(key: String, value: String): Boolean {
        return try {
            if (!isShizukuAvailable()) {
                LogX.w("Shizuku不可用，跳过属性设置: $key=$value")
                return false
            }

            val shizukuCls = Class.forName("rikka.shizuku.Shizuku")
            val newProcessMethod = shizukuCls.getMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )

            val cmd = arrayOf("sh", "-c", "setprop $key $value")
            val process = newProcessMethod.invoke(null, cmd, null, null)
            if (process != null) {
                LogX.d("Shizuku属性设置成功: $key=$value")
                true
            } else {
                LogX.w("Shizuku属性设置返回null: $key=$value")
                false
            }
        } catch (e: Exception) {
            LogX.e("Shizuku属性设置异常: $key=$value", e)
            false
        }
    }

    /**
     * 通过 Shizuku 执行 shell 命令
     * 用于：pm grant/revoke、settings put、ip link、am force-stop 等
     *
     * @return 命令输出（stdout），失败返回 null
     */
    fun execShell(command: String): String? {
        return try {
            if (!isShizukuAvailable()) {
                return null
            }
            val shizukuCls = Class.forName("rikka.shizuku.Shizuku")
            val process = XposedHelpers.callStaticMethod(
                shizukuCls, "newProcess",
                arrayOf("sh", "-c", command) as Any,
                arrayOf<String>() as Any,
                null as Any?
            ) ?: return null

            val inputStream = XposedHelpers.callMethod(process, "getInputStream") as? java.io.InputStream
            inputStream?.bufferedReader()?.readText()
        } catch (e: Exception) {
            LogX.e("Shizuku Shell执行异常: $command", e)
            null
        }
    }

    /**
     * 通过 Shizuku 写入文件（需 root 级别）
     * 用于写 /sys/class/net/wlan0/address、build.prop 等
     */
    fun writeFile(path: String, content: String): Boolean {
        return try {
            if (!isShizukuAvailable()) return false
            val cmd = "echo '$content' > $path"
            execShell(cmd) != null
        } catch (e: Exception) {
            LogX.e("Shizuku写入文件异常: $path", e)
            false
        }
    }

    /** 释放 Shizuku 资源（APP 退出时调用） */
    fun release() {
        shizukuAvailable = null
    }
}
