package com.privacyguard.pro.hooks

import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.Random

/**
 * 传感器伪造Hook（应用层）
 *
 * 对加速度/陀螺仪/磁场读数做静态或加噪处理，防传感器指纹追踪
 */
object SensorFakerHook {

    private val random = Random(System.currentTimeMillis())

    private const val TYPE_ACCELEROMETER = 1
    private const val TYPE_GYROSCOPE = 4
    private const val TYPE_MAGNETIC_FIELD = 2

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.sensorFakerEnabled) return
        LogX.i("传感器伪造启动（应用层）：模式=${if (cfg.sensorNoiseMode == 0) "静态" else "加噪"}")

        hookSensorEventListener(lpparam, cfg.sensorNoiseMode)
    }

    private fun hookSensorEventListener(lpparam: XC_LoadPackage.LoadPackageParam, noiseMode: Int) {
        try {
            val listenerCls = XposedHelpers.findClassIfExists(
                "android.hardware.SensorEventListener", lpparam.classLoader)

            val sensorEventCls = XposedHelpers.findClassIfExists(
                "android.hardware.SensorEvent", lpparam.classLoader) ?: return

            if (listenerCls != null) {
                try {
                    XposedHelpers.findAndHookMethod(listenerCls, "onSensorChanged",
                        sensorEventCls, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                try {
                                    val event = p.args[0] ?: return
                                    val valuesField = sensorEventCls.getDeclaredField("values")
                                    valuesField.isAccessible = true
                                    val values = valuesField.get(event) as? FloatArray ?: return

                                    val sensorField = sensorEventCls.getDeclaredField("sensor")
                                    sensorField.isAccessible = true
                                    val sensor = sensorField.get(event) ?: return
                                    val typeMethod = sensor.javaClass.getMethod("getType")
                                    val sensorType = typeMethod.invoke(sensor) as? Int ?: return

                                    when (sensorType) {
                                        TYPE_ACCELEROMETER, TYPE_GYROSCOPE, TYPE_MAGNETIC_FIELD -> {
                                            if (noiseMode == 0) {
                                                val static = if (sensorType == TYPE_ACCELEROMETER) {
                                                    floatArrayOf(0f, 0f, 9.81f)
                                                } else {
                                                    floatArrayOf(0f, 0f, 0f)
                                                }
                                                for (i in values.indices) {
                                                    if (i < static.size) values[i] = static[i]
                                                }
                                            } else {
                                                for (i in values.indices) {
                                                    val noise = (random.nextFloat() - 0.5f) * 0.1f * values[i]
                                                    values[i] = values[i] + noise
                                                }
                                            }
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        })
                    LogX.hookSuccess("SensorEventListener", "onSensorChanged")
                } catch (_: Exception) {}
            }

            try {
                val listener2 = XposedHelpers.findClassIfExists(
                    "android.hardware.SensorEventListener2", lpparam.classLoader)
                if (listener2 != null) {
                    XposedHelpers.findAndHookMethod(listener2, "onSensorChanged",
                        sensorEventCls, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {}
                        })
                    LogX.hookSuccess("SensorEventListener2", "onSensorChanged")
                }
            } catch (_: Exception) {}
        } catch (e: Exception) {
            LogX.hookFailed("SensorEventListener", "onSensorChanged", e)
        }
    }
}
