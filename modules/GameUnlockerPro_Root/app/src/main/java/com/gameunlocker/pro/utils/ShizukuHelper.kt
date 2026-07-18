package com.gameunlocker.pro.utils

import android.os.IBinder
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method

/**
 * Shizuku联动助手
 * 功能：
 *  1. 检测Shizuku服务是否可用
 *  2. 通过反射调用Shizuku API修改系统属性
 *
 * 说明：LSPatch本地模式下Shizuku可能未运行，所有调用通过反射+try-catch保护
 */
object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    private var shizukuAvailable: Boolean? = null
    private var shizukuService: Any? = null

    /** 检查Shizuku是否可用 */
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
     * 通过Shizuku设置系统属性
     * 用于修改ro.surface_flinger.refresh_rate等刷新率相关属性
     */
    fun setSystemProperty(key: String, value: String): Boolean {
        return try {
            if (!isShizukuAvailable()) {
                LogX.w("Shizuku不可用，跳过属性设置: $key=$value")
                return false
            }

            // 通过Shizuku执行shell命令设置系统属性
            val shizukuCls = Class.forName("rikka.shizuku.Shizuku")
            val newProcessMethod = shizukuCls.getMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
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
     * 通过Shizuku执行shell命令
     */
    fun execShell(command: String): String? {
        return try {
            if (!isShizukuAvailable()) {
                return null
            }
            val shizukuCls = Class.forName("rikka.shizuku.Shizuku")
            val newProcessMethod = shizukuCls.getMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
            )
            val process = XposedHelpers.callStaticMethod(
                shizukuCls, "newProcess",
                arrayOf("sh", "-c", command) as Any,
                arrayOf<String>() as Any,
                null as Any?
            ) ?: return null

            // 读取输出
            val inputStream = XposedHelpers.callMethod(process, "getInputStream") as? java.io.InputStream
            inputStream?.bufferedReader()?.readText()
        } catch (e: Exception) {
            LogX.e("Shizuku Shell执行异常: $command", e)
            null
        }
    }

    /**
     * 释放Shizuku资源
     */
    fun release() {
        shizukuService = null
        shizukuAvailable = null
    }
}
