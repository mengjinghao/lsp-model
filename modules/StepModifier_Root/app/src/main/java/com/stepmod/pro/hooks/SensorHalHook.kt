package com.stepmod.pro.hooks

import com.stepmod.pro.models.StepConfig
import com.stepmod.pro.utils.LogX
import com.stepmod.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 传感器 HAL 直接注入 Hook（Root 版独有）
 *
 * 通过 Shizuku 直接写入传感器 HAL 设备节点：
 *  - find /dev/ -name "iio*" -o -name "step*" 定位传感器设备节点
 *  - 写入步数数据到 IIO 设备节点
 *  - 写入 /dev/iio:device*/in_step_counter_raw 内核级注入
 *  - 增强已有 /sys/class/sensors 写入
 *
 * 硬性限制：
 *  - 必须 ShizukuHelper.isShizukuAvailable()
 *  - 需要 root 写 /dev 设备节点
 *  - 全部 try-catch 保护
 */
object SensorHalHook {

    private var isApplied = false

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        if (!cfg.sensorHalDirectEnabled) {
            LogX.d("SensorHalHook 未启用，跳过")
            return
        }
        if (isApplied) return

        LogX.i("SensorHalHook 启动：传感器 HAL 直接注入")

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        isApplied = true
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku 不可用，跳过传感器 HAL 注入")
                            return
                        }
                        probeAndInject(cfg)
                    }
                })
            LogX.hookSuccess("Application", "onCreate->SensorHalHook")
        } catch (e: Throwable) {
            LogX.e("SensorHalHook Application.onCreate Hook 异常", e)
        }
    }

    private fun probeAndInject(cfg: StepConfig) {
        try {
            val iioDevices = ShizukuHelper.execShell(
                "find /dev/ -name \"iio*\" -o -name \"step*\" 2>/dev/null"
            )
            if (!iioDevices.isNullOrBlank()) {
                LogX.d("检测到的传感器设备节点:\n$iioDevices")
            } else {
                LogX.w("未检测到 iio/step 设备节点")
            }
        } catch (e: Throwable) { LogX.w("探测设备节点异常: ${e.message}") }

        try {
            val result = ShizukuHelper.execShell(
                "echo ${cfg.customSteps} > /sys/class/sensors/step_counter/value 2>&1"
            )
            LogX.d("写入 /sys/class/sensors/step_counter/value=${cfg.customSteps}: $result")
        } catch (e: Throwable) { LogX.w("写入 step_counter/value 异常: ${e.message}") }

        try {
            val iioList = ShizukuHelper.execShell(
                "find /dev/ -name \"iio:device*\" 2>/dev/null"
            )?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

            for (dev in iioList) {
                try {
                    val inStepPath = "$dev/in_step_counter_raw"
                    val check = ShizukuHelper.execShell("test -e $inStepPath && echo exists 2>/dev/null")
                    if (check?.contains("exists") == true) {
                        val result = ShizukuHelper.execShell(
                            "echo ${cfg.customSteps} > $inStepPath 2>&1"
                        )
                        LogX.d("写入 $inStepPath=${cfg.customSteps}: $result")
                    }
                } catch (e: Throwable) { LogX.w("写入 $dev 异常: ${e.message}") }
            }
        } catch (e: Throwable) { LogX.w("IIO 设备注入异常: ${e.message}") }

        LogX.i("SensorHalHook: 传感器 HAL 注入完成")
    }

    fun injectStepValue(steps: Int): Boolean {
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) return false
            ShizukuHelper.execShell("echo $steps > /sys/class/sensors/step_counter/value 2>&1") != null
        } catch (e: Throwable) {
            LogX.e("传感器 HAL 步数注入异常: $steps", e)
            false
        }
    }

    fun probeDeviceNodes(): String? {
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) return null
            ShizukuHelper.execShell("find /dev/ -name \"iio*\" -o -name \"step*\" 2>/dev/null")
        } catch (e: Throwable) { null }
    }

    fun release() {
        isApplied = false
    }
}
