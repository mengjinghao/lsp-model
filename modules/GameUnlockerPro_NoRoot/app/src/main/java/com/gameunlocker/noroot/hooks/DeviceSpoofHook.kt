package com.gameunlocker.noroot.hooks

import com.gameunlocker.noroot.models.DeviceProfile
import com.gameunlocker.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 单应用机型伪装Hook（仅作用于当前被Hook的游戏进程）
 *
 * 硬性限制：本Hook仅修改当前进程内的Build类属性，
 * 退出游戏后恢复真实参数，无法全局伪装手机型号。
 *
 * 拦截路径：
 *  1. android.os.Build 静态字段 -> 游戏读取 MODEL/BRAND/MANUFACTURER
 *  2. SystemProperties.get() -> 游戏读取 ro.product.* 属性
 *  3. TelephonyManager -> 伪装设备ID（防IMEI检测）
 *  4. Runtime.availableProcessors() -> 伪装CPU核心数
 */
object DeviceSpoofHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, profile: DeviceProfile) {
        LogX.i("机型伪装: ${profile.displayName}")

        spoofBuildFields(profile)
        spoofSystemProperties(profile)
        spoofTelephony(lpparam)
        spoofCpuInfo(profile)
    }

    /** 直接修改 Build 类静态字段的值 */
    private fun spoofBuildFields(profile: DeviceProfile) {
        try {
            val build = XposedHelpers.findClass("android.os.Build", null)

            // 无Root限制：使用setStaticObjectField直接覆盖静态字段
            // 注意：某些ROM的Build字段可能为final，setStaticObjectField可能抛异常
            val fields = mapOf(
                "MODEL" to profile.model,
                "BRAND" to profile.brand,
                "MANUFACTURER" to profile.manufacturer,
                "DEVICE" to profile.device,
                "PRODUCT" to profile.product,
                "BOARD" to profile.board,
                "HARDWARE" to profile.hardware,
                "FINGERPRINT" to profile.fingerprint,
                "DISPLAY" to "${profile.brand} ${profile.model}"
            )
            for ((name, value) in fields) {
                try {
                    XposedHelpers.setStaticObjectField(build, name, value)
                } catch (e: Exception) {
                    // 部分ROM字段为final，忽略单字段失败
                    LogX.d("Build.$name 修改失败(可能为final): ${e.message}")
                }
            }
            LogX.i("Build属性伪装完成: MODEL=${profile.model}")
        } catch (e: Exception) {
            LogX.e("Build伪装异常", e)
        }
    }

    /** Hook SystemProperties 拦截属性读取 */
    private fun spoofSystemProperties(profile: DeviceProfile) {
        try {
            val sp = XposedHelpers.findClass("android.os.SystemProperties", null)

            val props = mapOf(
                "ro.product.model" to profile.model,
                "ro.product.brand" to profile.brand,
                "ro.product.manufacturer" to profile.manufacturer,
                "ro.product.device" to profile.device,
                "ro.product.name" to profile.product,
                "ro.product.board" to profile.board,
                "ro.hardware" to profile.hardware,
                "ro.build.version.release" to profile.androidVersion,
                "ro.build.version.sdk" to profile.sdkVersion.toString(),
                "ro.build.fingerprint" to profile.fingerprint,
                "ro.soc.model" to profile.cpuModel,
                "ro.chipname" to profile.cpuModel
            )

            // 无Root限制：仅Hook Java层get方法，不修改底层系统属性文件
            XposedHelpers.findAndHookMethod(sp, "get",
                String::class.java, String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as String
                        if (props.containsKey(key)) {
                            param.result = props[key]
                        }
                    }
                })
            LogX.i("SystemProperties伪装: ${props.size}个属性")
        } catch (e: Exception) {
            LogX.e("SystemProperties伪装异常", e)
        }
    }

    /** 伪装TelephonyManager设备ID */
    private fun spoofTelephony(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val tm = XposedHelpers.findClass(
                "android.telephony.TelephonyManager", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(tm, "getDeviceId",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = "000000000000000"
                    }
                })
        } catch (e: Exception) {
            LogX.d("TelephonyManager Hook异常: ${e.message}")
        }
    }

    /** 伪装CPU核心数 */
    private fun spoofCpuInfo(profile: DeviceProfile) {
        try {
            val rt = XposedHelpers.findClass("java.lang.Runtime", null)
            XposedHelpers.findAndHookMethod(rt, "availableProcessors",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = profile.sdkVersion // 用SDK版本号近似核心数
                    }
                })
        } catch (e: Exception) {
            LogX.d("CPU信息伪装异常: ${e.message}")
        }
    }
}
