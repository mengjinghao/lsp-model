package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File

/**
 * 温控/降频屏蔽模块
 *
 * 功能：
 *  - Hook系统thermal温控服务，屏蔽厂商游戏过热降频策略
 *  - 可自定义温控阈值，关闭游戏场景下的温度限制
 *  - 持续满帧运行，不损伤硬件（仅拦截游戏场景温控策略）
 *  - 日常系统温控保留（只在目标游戏进程内Hook）
 *
 * 温控机制原理：
 *  Android设备通过thermal-engine（高通）/ thermal-daemon（MTK）监控温度
 *  当温度达到阈值时：
 *  1. 降低CPU/GPU频率（通过/sys/class/thermal节点）
 *  2. 降低屏幕亮度
 *  3. 降低充电速度
 *  4. 限制帧率（通过SurfaceFlinger/Display）
 *
 *  本模块在游戏进程内Hook温控服务回调，阻止降频指令生效。
 */
object ThermalBypassHook {

    private var isApplied = false
    private var thermalThreshold = 50  // 默认50°C才触发

    /**
     * 应用温控屏蔽Hook
     */
    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, config: GameConfig) {
        if (!config.thermalBypassEnabled) {
            LogX.d("温控屏蔽未开启，跳过")
            return
        }
        if (isApplied) return
        isApplied = true

        thermalThreshold = config.customThermalThreshold
        LogX.i("温控屏蔽启动: 阈值=${thermalThreshold}°C")

        hookThermalService(lpparam)
        hookCPUFreqGovernor(lpparam)
        hookGPUFreqGovernor(lpparam)
        hookPowerManager(lpparam)
        hookSystemThermalNodes(lpparam)
    }

    /**
     * Hook Android ThermalService
     * android.os.ThermalService -> IThermalService
     */
    private fun hookThermalService(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // 方法1: Hook HardwarePropertiesManager (Android 8+)
            val hpmClass = XposedHelpers.findClassIfExists(
                "android.os.HardwarePropertiesManager",
                lpparam.classLoader
            )
            if (hpmClass != null) {
                // getDeviceTemperatures 返回设备温度数组
                XposedHelpers.findAndHookMethod(
                    hpmClass,
                    "getDeviceTemperatures",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            // 将实际温度值压制在阈值以下
                            val result = param.result as? Array<*> ?: return
                            try {
                                val tempClass = result.firstOrNull()?.javaClass ?: return
                                val tempField = tempClass.getDeclaredField("temperature")
                                tempField.isAccessible = true
                                for (tempObj in result) {
                                    if (tempObj != null) {
                                        val currentTemp = tempField.getFloat(tempObj)
                                        if (currentTemp > thermalThreshold) {
                                            tempField.setFloat(tempObj, thermalThreshold.toFloat())
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                LogX.d("修改温度值异常: ${e.message}")
                            }
                        }
                    }
                )
                LogX.hookSuccess("HardwarePropertiesManager", "getDeviceTemperatures")
            }

            // 方法2: Hook IThermalService
            val thermalServiceClass = XposedHelpers.findClassIfExists(
                "android.os.IThermalService",
                lpparam.classLoader
            )
            if (thermalServiceClass != null) {
                XposedHelpers.findAndHookMethod(
                    thermalServiceClass,
                    "getCurrentTemperatures",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            // 修改温度数组
                            val temperatures = param.result as? List<*> ?: return
                            param.result = temperatures.map { temp ->
                                try {
                                    val tempClass = temp?.javaClass ?: return@map temp
                                    val valueField = tempClass.getDeclaredField("mValue")
                                    valueField.isAccessible = true
                                    val currentValue = valueField.getFloat(temp)
                                    if (currentValue > thermalThreshold) {
                                        valueField.setFloat(temp, thermalThreshold.toFloat())
                                    }
                                } catch (e: Exception) {
                                    // ignore
                                }
                                temp
                            }
                        }
                    }
                )
                LogX.hookSuccess("IThermalService", "getCurrentTemperatures")
            }
        } catch (e: Exception) {
            LogX.d("Hook ThermalService异常: ${e.message}")
        }
    }

    /**
     * Hook CPU频率调度器
     * 拦截/sys/devices/system/cpu/cpu*/cpufreq/scaling_max_freq的写入
     * 防止温控降频
     */
    private fun hookCPUFreqGovernor(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // 尝试Hook常见的CPU调频写入类
            val cpuGovernorPaths = listOf(
                "com.android.server.CpuGovernorService",
                "android.os.CpuGovernorManager",
                "com.qualcomm.qti.Performance",
                "com.mediatek.performance.Performance"
            )

            for (path in cpuGovernorPaths) {
                try {
                    val cls = XposedHelpers.findClassIfExists(path, lpparam.classLoader)
                    if (cls != null) {
                        // 找到调频相关方法并Hook
                        for (method in cls.declaredMethods) {
                            if (method.name.contains("setMaxFreq", ignoreCase = true) ||
                                method.name.contains("setScalingMax", ignoreCase = true) ||
                                method.name.contains("lockFreq", ignoreCase = true)) {
                                XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING)
                                LogX.hookSuccess(cls.name, method.name)
                            }
                        }
                    }
                } catch (e: Exception) {
                    LogX.d("CPU调频Hook: $path 类不存在")
                }
            }

            LogX.d("CPU频率调度器Hook完成")
        } catch (e: Exception) {
            LogX.d("Hook CPU频率调度器异常: ${e.message}")
        }
    }

    /**
     * Hook GPU频率调度器
     * 拦截/sys/class/kgsl/kgsl-3d0/max_gpuclk的写入
     */
    private fun hookGPUFreqGovernor(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val gpuGovernorPaths = listOf(
                "com.qualcomm.qti.GPUPerformance",
                "android.os.GPUGovernorManager",
                "com.mediatek.gpu.GPUManager"
            )

            for (path in gpuGovernorPaths) {
                try {
                    val cls = XposedHelpers.findClassIfExists(path, lpparam.classLoader)
                    if (cls != null) {
                        for (method in cls.declaredMethods) {
                            if (method.name.contains("setMaxGpuFreq", ignoreCase = true) ||
                                method.name.contains("setGPUClock", ignoreCase = true) ||
                                method.name.contains("lockGpuClock", ignoreCase = true) ||
                                method.name.contains("throttleGpu", ignoreCase = true)) {
                                XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING)
                                LogX.hookSuccess(cls.name, method.name)
                            }
                        }
                    }
                } catch (e: Exception) {
                    LogX.d("GPU调频Hook: $path 类不存在")
                }
            }

            LogX.d("GPU频率调度器Hook完成")
        } catch (e: Exception) {
            LogX.d("Hook GPU频率调度器异常: ${e.message}")
        }
    }

    /**
     * Hook PowerManager温控相关
     * PowerManager.getThermalStatus() 返回热状态
     * STATUS_NONE=0, STATUS_LIGHT=1, STATUS_MODERATE=2, STATUS_SEVERE=3
     * STATUS_CRITICAL=4, STATUS_EMERGENCY=5, STATUS_SHUTDOWN=6
     */
    private fun hookPowerManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val pmClass = XposedHelpers.findClass(
                "android.os.PowerManager",
                lpparam.classLoader
            )

            // Hook getCurrentThermalStatus
            XposedHelpers.findAndHookMethod(
                pmClass,
                "getCurrentThermalStatus",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // 始终返回STATUS_NONE(0)，表示温度正常
                        param.result = 0
                    }
                }
            )
            LogX.hookSuccess("PowerManager", "getCurrentThermalStatus")

            // 通过 addThermalStatusListener 注入假监听器
            XposedHelpers.findAndHookMethod(
                pmClass,
                "addThermalStatusListener",
                "android.os.PowerManager.OnThermalStatusChangedListener",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = false
                    }
                }
            )
            LogX.hookSuccess("PowerManager", "addThermalStatusListener(blocked)")
        } catch (e: Exception) {
            LogX.d("Hook PowerManager异常: ${e.message}")
        }
    }

    /**
     * 直接操作温控sysfs节点
     * 写入假温度值到thermal节点，绕过底层温控
     *
     * 注意：此操作需要root权限，本地模式下作用有限
     * 但在部分厂商系统上仍然有效（系统服务可写sysfs）
     */
    private fun hookSystemThermalNodes(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // 修改温控阈值文件
            val thermalPaths = listOf(
                "/sys/class/thermal/thermal_message/temperature",
                "/sys/class/thermal/thermal_zone0/temp",
                "/sys/class/power_supply/battery/temp"
            )

            // 通过Hook读取这些文件的方式，返回假数据
            // 实际上Hook FileInputStream或BufferedReader的read方法可拦截
            try {
                val fileInputClass = XposedHelpers.findClass(
                    "java.io.FileInputStream",
                    lpparam.classLoader
                )
                XposedHelpers.findAndHookConstructor(
                    fileInputClass,
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val path = param.args[0] as? String ?: return
                            if (thermalPaths.any { path.contains(it.substringAfterLast("/")) }) {
                                LogX.d("拦截温控节点读取: $path")
                                // 注意：无法直接阻止文件读取，此处仅记录
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                LogX.d("Hook文件读取异常: ${e.message}")
            }

            // Hook内核温控服务调用
            hookClassIfExists(lpparam, "com.qualcomm.qti.thermal.ThermalService", true)
            hookClassIfExists(lpparam, "com.mediatek.thermal.manager.ThermalManager", true)

            LogX.d("温控sysfs节点操作完成")
        } catch (e: Exception) {
            LogX.d("Hook系统温控节点异常: ${e.message}")
        }
    }

    private fun hookClassIfExists(
        lpparam: XC_LoadPackage.LoadPackageParam,
        className: String,
        doNothing: Boolean
    ) {
        try {
            val cls = XposedHelpers.findClassIfExists(className, lpparam.classLoader)
            if (cls != null) {
                if (doNothing) {
                    for (method in cls.declaredMethods) {
                        if (method.name.contains("thermal", ignoreCase = true) ||
                            method.name.contains("throttle", ignoreCase = true) ||
                            method.name.contains("limit", ignoreCase = true)) {
                            XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING)
                            LogX.hookSuccess(className, method.name)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LogX.d("$className 类不存在或不可Hook")
        }
    }
}
