package com.batteryopt.pro.hooks

import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import com.batteryopt.pro.utils.ShizukuHelper
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku 桥接 Hook
 *
 * 功能：
 *  - 统一的 Shizuku 调用入口，供其他系统级 Hook 复用
 *  - 启动时检测 Shizuku 可用性并打日志
 *  - 提供 Shell 命令执行/系统属性修改的便捷封装
 *
 * 注意：
 *  - 不直接 Hook 任何方法，仅作为 Shizuku 调用桥
 *  - 所有系统级 Hook 内部已直接调用 ShizukuHelper，本类主要用于
 *    启动时检测和集中日志输出
 */
object ShizukuBridgeHook {

    private var isApplied = false

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        if (isApplied) return
        isApplied = true

        LogX.i("Shizuku 桥接启动")
        checkShizukuAvailability()
        logSystemInfo()
    }

    /** 检测 Shizuku 可用性并打日志 */
    private fun checkShizukuAvailability() {
        val available = ShizukuHelper.isShizukuAvailable()
        if (available) {
            LogX.i("Shizuku 已就绪，系统级 Hook 将可用")
        } else {
            LogX.w("Shizuku 不可用，系统级 Hook（Doze/冻结/CPU/Greenify）将失效")
            LogX.w("请在 Shizuku App 中激活服务并授权本模块")
        }
    }

    /** 输出系统信息用于诊断 */
    private fun logSystemInfo() {
        if (!ShizukuHelper.isShizukuAvailable()) return
        try {
            val buildId = ShizukuHelper.execShell("getprop ro.build.id")?.trim()
            val sdk = ShizukuHelper.execShell("getprop ro.build.version.sdk")?.trim()
            val deviceIdleSupport = ShizukuHelper.execShell(
                "command -v dumpsys && dumpsys deviceidle help 2>&1 | head -1"
            )?.trim()
            LogX.i("系统信息: buildId=$buildId sdk=$sdk deviceIdle=$deviceIdleSupport")
        } catch (e: Exception) {
            LogX.e("获取系统信息异常", e)
        }
    }

    /** 释放资源（模块卸载或 APP 退出时调用） */
    fun release() {
        ShizukuHelper.release()
        isApplied = false
        LogX.d("Shizuku 桥接资源已释放")
    }
}
