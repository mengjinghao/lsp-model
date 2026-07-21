package com.stepmod.pro.hooks

import com.stepmod.pro.models.StepConfig
import com.stepmod.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 步数传感器完全阻断 Hook（实验性）
 *
 * 功能：完全阻断应用注册步数传感器，让 SensorManager.registerListener
 *      对 TYPE_STEP_COUNTER/DETECTOR 直接返回 false
 *
 * 注意事项：
 *  - 激进方案，会导致运动类APP无法读取真实步数（仅依赖服务端缓存）
 *  - 配合 StepReportHook 注入伪造值效果更佳
 */
object SensorBlockHook {

    private const val TYPE_STEP_DETECTOR = 18
    private const val TYPE_STEP_COUNTER = 19

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        if (!cfg.sensorBlockEnabled) return
        LogX.i("步数传感器阻断 Hook 启动（实验性）")

        hookRegisterListenerBlock(lpparam)
        hookGetDefaultSensorBlock(lpparam)
    }

    private fun hookRegisterListenerBlock(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val smCls = XposedHelpers.findClassIfExists(
                "android.hardware.SensorManager", lpparam.classLoader) ?: return
            try {
                XposedHelpers.findAndHookMethod(smCls, "registerListener",
                    "android.hardware.SensorEventListener",
                    "android.hardware.Sensor",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                if (isStepSensor(p.args[1])) {
                                    LogX.d("阻断 registerListener(3arg) 步数传感器注册")
                                    p.result = false
                                }
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("SensorManager", "registerListener(block-3arg)")
            } catch (e: Exception) { LogX.w("registerListener(3arg) hook 失败: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(smCls, "registerListener",
                    "android.hardware.SensorEventListener",
                    "android.hardware.Sensor",
                    Int::class.javaPrimitiveType,
                    "android.os.Handler",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                if (isStepSensor(p.args[1])) {
                                    LogX.d("阻断 registerListener(4arg) 步数传感器注册")
                                    p.result = false
                                }
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("SensorManager", "registerListener(block-4arg)")
            } catch (e: Exception) { LogX.w("registerListener(4arg) hook 失败: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("SensorManager", "registerListener(block)", e)
        }
    }

    private fun hookGetDefaultSensorBlock(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val smCls = XposedHelpers.findClassIfExists(
                "android.hardware.SensorManager", lpparam.classLoader) ?: return
            XposedHelpers.findAndHookMethod(smCls, "getDefaultSensor",
                Int::class.javaPrimitiveType, object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val type = p.args[0] as? Int ?: return
                            if (type == TYPE_STEP_COUNTER || type == TYPE_STEP_DETECTOR) {
                                LogX.d("阻断 getDefaultSensor type=$type → null")
                                p.result = null
                            }
                        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("SensorManager", "getDefaultSensor(block)")
        } catch (e: Exception) {
            LogX.hookFailed("SensorManager", "getDefaultSensor(block)", e)
        }
    }

    private fun isStepSensor(sensorObj: Any?): Boolean {
        if (sensorObj == null) return false
        return try {
            val type = sensorObj.javaClass.getMethod("getType").invoke(sensorObj) as? Int ?: return false
            type == TYPE_STEP_COUNTER || type == TYPE_STEP_DETECTOR
        } catch (_: Throwable) { false }
    }
}
