package com.vipunlock.pro.hooks

import com.vipunlock.pro.models.VipConfig
import com.vipunlock.pro.utils.LogX
import com.vipunlock.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 系统属性伪装 Hook（Root 专属，需 Shizuku adb 级授权）
 *
 * 目标：让 APP 通过 ro.product.model / ro.product.brand 等读取到高端机型标识，
 * 部分APP据此开放"高端机型专享"的 VIP 权益（如 B站 4K/杜比、爱奇艺星钻）。
 *
 * 双通道实现：
 *  1. 应用层 Hook android.os.SystemProperties.get 拦截读取
 *  2. 通过 Shizuku setprop 实际修改系统属性（影响 APP 所有进程，但重启后消失）
 *
 * 硬性限制：
 *  - 必须先检查 ShizukuHelper.isShizukuAvailable()
 *  - ro.* 属性原生不可写，setprop 对部分只读属性无效（应用层 Hook 兜底）
 *  - 持久化需写入 build.prop（需要 root 级 Shizuku），重启后消失
 */
object SystemPropVipHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        if (!cfg.systemPropVipEnabled) return
        LogX.i("系统属性伪装VIP启动（Root 专属）")

        hookSystemProperties(lpparam, cfg)
        applySystemPropsViaShizuku(cfg)
        hookBuildFields(lpparam, cfg)
    }

    /** 应用层 Hook SystemProperties.get 拦截读取 */
    private fun hookSystemProperties(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        try {
            val sp = XposedHelpers.findClassIfExists(
                "android.os.SystemProperties", lpparam.classLoader) ?: return

            val props = mapOf(
                "ro.product.model" to cfg.spoofProductModel,
                "ro.product.brand" to cfg.spoofProductBrand,
                "ro.product.manufacturer" to cfg.spoofProductManufacturer,
                "ro.product.name" to cfg.spoofProductModel.replace(" ", "_"),
                "ro.product.device" to cfg.spoofProductDevice,
                "ro.product.cpu.abi" to "arm64-v8a",
                "ro.build.fingerprint" to "${cfg.spoofProductBrand}/${cfg.spoofProductModel.replace(" ", "_")}/" +
                    "${cfg.spoofProductDevice}:14/UQ1A.240205.004/${System.currentTimeMillis()}/user/release-keys"
            )

            // get(String, String)
            try {
                XposedHelpers.findAndHookMethod(sp, "get",
                    String::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val key = p.args[0] as String
                            if (props.containsKey(key)) {
                                p.result = props[key]
                            }
                        }
                    })
                LogX.hookSuccess("SystemProperties", "get(key, def)")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }

            // get(String)
            try {
                XposedHelpers.findAndHookMethod(sp, "get",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val key = p.args[0] as String
                            if (props.containsKey(key)) {
                                p.result = props[key]
                            }
                        }
                    })
                LogX.hookSuccess("SystemProperties", "get(key)")
            } catch (e: NoSuchMethodError) { /* 忽略 */ }
            catch (e: Exception) { LogX.w("异常: ${e.message}") }

            LogX.i("SystemProperties Hook完成: ${props.size}个属性")
        } catch (e: Exception) {
            LogX.hookFailed("SystemProperties", "get", e)
        }
    }

    /** Hook Build 静态字段（部分APP直接读 Build.MODEL） */
    private fun hookBuildFields(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VipConfig) {
        try {
            val build = XposedHelpers.findClassIfExists(
                "android.os.Build", lpparam.classLoader) ?: return

            try {
                XposedHelpers.setStaticObjectField(build, "MODEL", cfg.spoofProductModel)
                XposedHelpers.setStaticObjectField(build, "BRAND", cfg.spoofProductBrand)
                XposedHelpers.setStaticObjectField(build, "MANUFACTURER", cfg.spoofProductManufacturer)
                XposedHelpers.setStaticObjectField(build, "DEVICE", cfg.spoofProductDevice)
                XposedHelpers.setStaticObjectField(build, "PRODUCT", cfg.spoofProductModel.replace(" ", "_"))
                LogX.hookSuccess("Build", "static fields")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("Build", "static fields", e)
        }
    }

    /** 通过 Shizuku setprop 修改系统属性 */
    private fun applySystemPropsViaShizuku(cfg: VipConfig) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku不可用，跳过 setprop 系统属性修改（仅应用层 Hook 生效）")
            return
        }

        val props = listOf(
            "ro.product.model" to cfg.spoofProductModel,
            "ro.product.brand" to cfg.spoofProductBrand,
            "ro.product.manufacturer" to cfg.spoofProductManufacturer,
            "ro.product.device" to cfg.spoofProductDevice,
            "ro.product.name" to cfg.spoofProductModel.replace(" ", "_")
        )

        var success = 0
        for ((key, value) in props) {
            if (ShizukuHelper.setSystemProperty(key, value)) {
                success++
            }
        }

        LogX.i("Shizuku setprop 完成: $success/${props.size} 个属性设置成功")
    }
}
