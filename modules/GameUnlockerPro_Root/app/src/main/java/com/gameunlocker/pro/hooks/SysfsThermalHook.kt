package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import com.gameunlocker.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Sysfs 温控 Hook（内核级，需 Shizuku adb 级授权）
 *
 * 直接写入 Linux 内核温控节点，从硬件层面禁用温控限制：
 *  - /sys/class/thermal/thermal_zone*/policy → "user_space"
 *  - /sys/class/thermal/thermal_zone*/trip_point_*_temp → 95000
 *  - /sys/class/thermal/cooling_device*/cur_state → 0
 *  - /sys/class/thermal/thermal_message/board_sensor_temp → 35000
 *  - /sys/module/msm_thermal/parameters/enabled → "N"（禁用高通温控引擎）
 *  - /sys/module/msm_thermal_v2/parameters/enabled → "N"
 *  - stop thermal-engine / mi_thermald / vendor.thermal-engine（停止厂商温控守护进程）
 *
 * 硬性限制：
 *  - 需要 Shizuku/Root ADB 授权
 *  - sysfs 节点在不同 SoC/机型上位置可能不同
 *  - 高温下 SOC 硬件级保护（约 80-90°C）仍会触发，这是安全机制
 *  - 写入 sysfs 非持久化，重启后恢复默认
 */
object SysfsThermalHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.sysfsThermalEnabled) return
        if (!ShizukuHelper.isAvailable()) {
            LogX.w("Shizuku 不可用，跳过 Sysfs 温控操作")
            return
        }
        LogX.i("Sysfs 温控 Hook 启动（内核级）")

        probeAndWriteThermalNodes()
        if (cfg.thermalEngineDisableEnabled) stopThermalEngine()
        hookAppOnCreate(lpparam, cfg)
    }

    /**
     * 探测并写入所有存在的温控节点
     * 使用 find 命令自动发现 thermal_zone 和 cooling_device 节点
     */
    private fun probeAndWriteThermalNodes() {
        // 将所有 thermal_zone policy 设为 user_space（禁用内核自动温控）
        ShizukuHelper.execShell(
            "find /sys/class/thermal/ -name \"policy\" -exec echo user_space > {} \\;"
        )
        // 将所有 trip_point 温度阈值设为 95000（极高，基本不触发）
        ShizukuHelper.execShell(
            "find /sys/class/thermal/ -name \"trip_point_*_temp\" -type f -exec sh -c 'echo 95000 > \"\$1\"' _ {} \\;"
        )
        // 将所有 cooling_device cur_state 设为 0（关闭冷却限制）
        ShizukuHelper.execShell(
            "find /sys/class/thermal/ -name \"cur_state\" -type f -exec echo 0 > {} \\;"
        )
        // 覆盖板载传感器温度值
        ShizukuHelper.execShell(
            "echo 35000 > /sys/class/thermal/thermal_message/board_sensor_temp"
        )
        // 禁用高通 msm_thermal 内核模块
        ShizukuHelper.execShell(
            "echo N > /sys/module/msm_thermal/parameters/enabled"
        )
        ShizukuHelper.execShell(
            "echo N > /sys/module/msm_thermal_v2/parameters/enabled"
        )
        // 额外厂商节点
        ShizukuHelper.execShell(
            "find /sys/class/thermal/ -name \"mode\" -type f -exec echo disabled > {} \\;"
        )
        ShizukuHelper.execShell(
            "find /sys/class/thermal/ -name \"throttle\" -type f -exec echo 0 > {} \\;"
        )
        // 禁用核心控制（某些内核的 CPU 热插拔）
        ShizukuHelper.execShell(
            "find /sys/module/msm_thermal/ -name \"core_control_enabled\" -exec echo 0 > {} \\;"
        )
        LogX.i("Sysfs 温控节点已写入（内核级）")
    }

    /** 停止各类厂商温控守护进程 */
    private fun stopThermalEngine() {
        val engines = listOf(
            "thermal-engine",
            "mi_thermald",
            "vendor.thermal-engine",
            "thermald",
            "thermal-hal",
            "thermal_manager",
            "vendor.thermal-hal-1-0",
            "vendor.thermal-hal-2-0"
        )
        for (engine in engines) {
            ShizukuHelper.execShell("stop $engine")
        }
        LogX.i("温控引擎守护进程已停止")
    }

    /** Hook Application.onCreate 以在应用完全初始化后重新应用 sysfs 写入 */
    private fun hookAppOnCreate(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        try {
            val appCls = XposedHelpers.findClassIfExists(
                "android.app.Application", lpparam.classLoader
            ) ?: return
            XposedHelpers.findAndHookMethod(appCls, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        if (ShizukuHelper.isAvailable()) {
                            probeAndWriteThermalNodes()
                            if (cfg.thermalEngineDisableEnabled) stopThermalEngine()
                            LogX.d("Sysfs 温控节点已重新应用（onCreate 后）")
                        }
                    }
                })
            LogX.hookSuccess("Application", "onCreate->SysfsThermal")
        } catch (e: Throwable) { LogX.w("SysfsThermal App hook 异常: ${e.message}") }
    }
}
