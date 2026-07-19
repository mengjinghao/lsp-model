package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.DeviceProfile
import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 单游戏机型伪装 Hook（仅作用于当前被 Hook 的游戏进程）
 *
 * 应用层 Hook：
 *  - 修改当前进程内的 Build 类属性
 *  - Hook SystemProperties.get 拦截属性读取
 *
 * 系统级修改（需 Shizuku，由 ShizukuBridgeHook 单独执行 setprop）
 *
 * 硬性限制：
 *  - ro.* 属性原生不可写，setprop 非持久化，重启后失效
 */
object DeviceSpoofHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.deviceSpoofEnabled) return

        val profile: DeviceProfile = cfg.customDeviceProfile
            ?: com.gameunlocker.pro.utils.DeviceProfileDatabase.findById(cfg.selectedDeviceProfileId)
            ?: com.gameunlocker.pro.utils.DeviceProfileDatabase.findById("xiaomi15")
            ?: return

        LogX.i("机型伪装: ${profile.displayName}（应用层）")

        spoofBuildFields(profile)
        spoofSystemProperties(profile)
        spoofTelephony(lpparam)
        spoofCpuInfo(profile)
    }

    private fun spoofBuildFields(profile: DeviceProfile) {
        try {
            val build = XposedHelpers.findClass("android.os.Build", null)
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
                } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
            }
            LogX.i("Build 属性伪装完成: MODEL=${profile.model}")
        } catch (e: Throwable) {
            LogX.e("Build 伪装异常", e)
        }
    }

    private fun spoofSystemProperties(profile: DeviceProfile) {
        try {
            val sp = XposedHelpers.findClassIfExists("android.os.SystemProperties", null) ?: return
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

            try {
                XposedHelpers.findAndHookMethod(sp, "get",
                    String::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val key = p.args[0] as? String ?: return
                            if (props.containsKey(key)) p.result = props[key]
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(sp, "get",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val key = p.args[0] as? String ?: return
                            if (props.containsKey(key)) p.result = props[key]
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            LogX.hookSuccess("SystemProperties", "get(${props.size} props)")
        } catch (e: Throwable) {
            LogX.hookFailed("SystemProperties", "get", e)
        }
    }

    private fun spoofTelephony(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val tm = XposedHelpers.findClassIfExists(
                "android.telephony.TelephonyManager", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(tm, "getDeviceId",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = "000000000000000"
                        }
                    })
                LogX.hookSuccess("TelephonyManager", "getDeviceId")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("TelephonyManager", "getDeviceId", e)
        }
    }

    private fun spoofCpuInfo(profile: DeviceProfile) {
        try {
            val rt = XposedHelpers.findClassIfExists("java.lang.Runtime", null) ?: return
            XposedHelpers.findAndHookMethod(rt, "availableProcessors",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        p.result = 8
                    }
                })
            LogX.hookSuccess("Runtime", "availableProcessors -> 8")
        } catch (e: Throwable) {
            LogX.hookFailed("Runtime", "availableProcessors", e)
        }
    }
}
