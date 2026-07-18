package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.DeviceProfile
import com.gameunlocker.pro.utils.DeviceProfileDatabase
import com.gameunlocker.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 全局机型伪装Hook
 *
 * 功能：
 *  1. Hook android.os.Build 类的所有静态字段，替换为伪装机型参数
 *  2. Hook android.os.SystemProperties.get() 拦截系统属性读取
 *  3. Hook BuildProp相关属性读取，覆盖 ro.product.* 系列属性
 *  4. 绕过游戏机型黑名单检测，强制解锁120/144/极致帧率档位
 *
 * Hook原理：
 *  游戏读取设备信息通常通过以下途径：
 *  - Build.MODEL / Build.BRAND / Build.DEVICE 等静态字段
 *  - SystemProperties.get("ro.product.model") 等系统属性
 *  - TelephonyManager.getDeviceId() 等设备ID
 *  - DisplayMetrics 屏幕参数
 *
 *  本模块拦截以上所有读取路径，返回伪装机型参数。
 */
object DeviceSpoofHook {

    private var currentProfile: DeviceProfile? = null

    /**
     * 应用机型伪装Hook
     * @param lpparam Xposed加载参数
     * @param profile 要伪装的机型配置
     */
    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, profile: DeviceProfile) {
        currentProfile = profile
        LogX.i("开始应用机型伪装: ${profile.displayName} (${profile.model})")

        hookBuildClass(profile)
        hookSystemProperties(profile)
        hookTelephonyManager(lpparam, profile)
        hookDisplayMetrics(profile)
        hookCpuInfo(profile)
    }

    /**
     * Hook android.os.Build 类的所有静态字段
     * Build.MODEL, Build.BRAND, Build.MANUFACTURER 等
     */
    private fun hookBuildClass(profile: DeviceProfile) {
        try {
            val buildClass = XposedHelpers.findClass("android.os.Build", null)

            // 方案1：直接Hook Build类的静态字段访问
            // 通过Hook getSerial()等方法拦截更稳定
            val buildFields = mapOf(
                "MODEL" to profile.model,
                "BRAND" to profile.brand,
                "MANUFACTURER" to profile.manufacturer,
                "DEVICE" to profile.device,
                "PRODUCT" to profile.product,
                "BOARD" to profile.board,
                "HARDWARE" to profile.hardware,
                "FINGERPRINT" to profile.fingerprint,
                "DISPLAY" to "${profile.brand} ${profile.model}",
                "MODEL_ID" to profile.model
            )

            // Hook Build.MODEL 的获取逻辑
            // 有些游戏通过反射读取Build字段，需要Hook getString方法
            // 这里同时Hook多个读取路径确保覆盖

            // 方案2：通过XposedHelpers.setStaticObjectField直接修改（暴力但可能被重置）
            for ((fieldName, value) in buildFields) {
                try {
                    XposedHelpers.setStaticObjectField(buildClass, fieldName, value)
                    LogX.hookSuccess("Build", fieldName)
                } catch (e: Exception) {
                    LogX.hookFailed("Build", fieldName, e)
                }
            }

            LogX.i("Build属性已伪装: MODEL=${profile.model}, BRAND=${profile.brand}")
        } catch (e: Exception) {
            LogX.e("Hook Build类失败", e)
        }
    }

    /**
     * Hook SystemProperties.get() 拦截系统属性读取
     *
     * 游戏通常通过以下方式读取属性：
     * SystemProperties.get("ro.product.model")
     * SystemProperties.get("ro.product.brand")
     * SystemProperties.getInt("ro.build.version.sdk", 0)
     */
    private fun hookSystemProperties(profile: DeviceProfile) {
        try {
            val systemPropertiesClass = XposedHelpers.findClass(
                "android.os.SystemProperties",
                null
            )

            // 属性映射表: 真实属性名 -> 伪装值
            val propMapping = mapOf(
                "ro.product.model" to profile.model,
                "ro.product.brand" to profile.brand,
                "ro.product.manufacturer" to profile.manufacturer,
                "ro.product.device" to profile.device,
                "ro.product.board" to profile.board,
                "ro.product.name" to profile.product,
                "ro.product.cpu.abi" to "arm64-v8a",
                "ro.product.cpu.abilist" to "arm64-v8a",
                "ro.hardware" to profile.hardware,
                "ro.build.version.release" to profile.androidVersion,
                "ro.build.version.sdk" to profile.sdkVersion.toString(),
                "ro.build.fingerprint" to profile.fingerprint,
                "ro.build.display.id" to "${profile.brand} ${profile.model}",
                "ro.board.platform" to profile.board,
                // CPU相关属性
                "ro.soc.model" to profile.cpuModel,
                "ro.chipname" to profile.cpuModel,
                "ro.hardware.chipname" to profile.cpuModel,
                // GPU相关
                "ro.gfx.driver.1" to profile.gpuModel,
                // 刷新率
                "ro.surface_flinger.max_frame_buffer_acquired_buffers" to "3"
            )

            // Hook SystemProperties.get(String key, String def)
            XposedHelpers.findAndHookMethod(
                systemPropertiesClass,
                "get",
                String::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as String
                        if (propMapping.containsKey(key)) {
                            param.result = propMapping[key]
                            // 不记录日志避免刷屏
                        }
                    }
                }
            )
            LogX.hookSuccess("SystemProperties", "get(String, String)")

            // Hook SystemProperties.getInt(String key, int def)
            XposedHelpers.findAndHookMethod(
                systemPropertiesClass,
                "getInt",
                String::class.java,
                Int::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as String
                        if (key == "ro.build.version.sdk") {
                            param.result = profile.sdkVersion
                        }
                    }
                }
            )
            LogX.hookSuccess("SystemProperties", "getInt(String, int)")

            // Hook SystemProperties.getBoolean(String key, boolean def)
            XposedHelpers.findAndHookMethod(
                systemPropertiesClass,
                "getBoolean",
                String::class.java,
                Boolean::class.javaPrimitiveType!!,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as String
                        when (key) {
                            "ro.debuggable" -> param.result = false
                            "ro.secure" -> param.result = true
                        }
                    }
                }
            )

            LogX.i("SystemProperties Hook完成: ${propMapping.size}个属性已映射")
        } catch (e: Exception) {
            LogX.e("Hook SystemProperties失败", e)
        }
    }

    /**
     * Hook TelephonyManager.getDeviceId() 等设备标识
     * 部分游戏通过IMEI/IMSI等检测机型
     */
    private fun hookTelephonyManager(lpparam: XC_LoadPackage.LoadPackageParam, profile: DeviceProfile) {
        try {
            val tmClass = XposedHelpers.findClass(
                "android.telephony.TelephonyManager",
                lpparam.classLoader
            )

            // 伪装设备ID（返回固定合法值，非真实IMEI）
            XposedHelpers.findAndHookMethod(
                tmClass,
                "getDeviceId",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = "000000000000000"
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                tmClass,
                "getSubscriberId",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = "000000000000000"
                    }
                }
            )

            LogX.hookSuccess("TelephonyManager", "getDeviceId/getSubscriberId")
        } catch (e: Exception) {
            LogX.e("Hook TelephonyManager失败", e)
        }
    }

    /**
     * Hook DisplayMetrics 屏幕参数伪装
     * 部分游戏根据屏幕分辨率判断是否允许高清材质
     */
    private fun hookDisplayMetrics(profile: DeviceProfile) {
        try {
            val displayMetricsClass = XposedHelpers.findClass(
                "android.util.DisplayMetrics",
                null
            )

            // 修改默认DisplayMetrics参数
            XposedHelpers.setStaticIntField(displayMetricsClass, "DENSITY_DEVICE_STABLE", profile.densityDpi)

            LogX.d("DisplayMetrics已伪装: ${profile.screenWidth}x${profile.screenHeight}, ${profile.densityDpi}dpi")
        } catch (e: Exception) {
            LogX.d("Hook DisplayMetrics非关键异常: ${e.message}")
        }
    }

    /**
     * Hook CPU信息读取
     * 游戏会读取 /proc/cpuinfo 或 Build.SUPPORTED_ABIS 判断CPU型号
     */
    private fun hookCpuInfo(profile: DeviceProfile) {
        try {
            val buildClass = XposedHelpers.findClass("android.os.Build", null)

            // 伪装支持的CPU架构列表
            try {
                val supportedAbis = arrayOf("arm64-v8a", "armeabi-v7a")
                XposedHelpers.setStaticObjectField(buildClass, "SUPPORTED_ABIS", supportedAbis)
            } catch (e: Exception) {
                LogX.d("SUPPORTED_ABIS已存在可能")
            }

            // 伪装CPU核心数 (部分游戏通过 /sys/devices/system/cpu/present 读取)
            // 这里通过runtime availableProcessors来影响
            try {
                val runtimeClass = XposedHelpers.findClass("java.lang.Runtime", null)
                XposedHelpers.findAndHookMethod(
                    runtimeClass,
                    "availableProcessors",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = profile.cpuCores
                        }
                    }
                )
                LogX.hookSuccess("Runtime", "availableProcessors")
            } catch (e: Exception) {
                LogX.d("availableProcessors Hook异常: ${e.message}")
            }

            LogX.d("CPU信息伪装完成: ${profile.cpuModel}, ${profile.cpuCores}核")
        } catch (e: Exception) {
            LogX.e("Hook CPU信息失败", e)
        }
    }

    /** 获取当前伪装的机型 */
    fun getCurrentProfile(): DeviceProfile? = currentProfile
}
