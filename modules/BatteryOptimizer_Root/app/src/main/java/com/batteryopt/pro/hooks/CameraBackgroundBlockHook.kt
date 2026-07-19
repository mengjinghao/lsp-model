package com.batteryopt.pro.hooks

import android.app.ActivityManager
import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】后台相机调用阻断 Hook（应用层）
 */
object CameraBackgroundBlockHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("【实验性】后台相机阻断启动")
        hookCamera2(lpparam)
        hookCameraLegacy(lpparam)
    }

    private fun hookCamera2(lpparam: XC_LoadPackage.LoadPackageParam) {
        val cls = XposedHelpers.findClassIfExists(
            "android.hardware.camera2.CameraManager", lpparam.classLoader
        ) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                cls, "openCamera",
                String::class.java,
                "android.hardware.camera2.CameraDevice.StateCallback",
                "android.os.Handler",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (isAppInBackground(p.thisObject)) {
                            val cameraId = p.args[0] as? String ?: "?"
                            LogX.w("后台 openCamera 被拦截: cameraId=$cameraId")
                            p.result = null
                        }
                    }
                })
            LogX.hookSuccess("CameraManager", "openCamera")
        } catch (_: Throwable) {}
    }

    private fun hookCameraLegacy(lpparam: XC_LoadPackage.LoadPackageParam) {
        val cls = XposedHelpers.findClassIfExists(
            "android.hardware.Camera", lpparam.classLoader
        ) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                cls, "open",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (isAppInBackground(null)) {
                            val id = p.args[0] as Int
                            LogX.w("后台 Camera.open($id) 被拦截")
                            p.result = null
                        }
                    }
                })
            LogX.hookSuccess("Camera", "open(int)")
        } catch (_: Throwable) {}

        try {
            XposedHelpers.findAndHookMethod(
                cls, "open",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (isAppInBackground(null)) {
                            LogX.w("后台 Camera.open() 被拦截")
                            p.result = null
                        }
                    }
                })
            LogX.hookSuccess("Camera", "open()")
        } catch (_: Throwable) {}
    }

    private fun isAppInBackground(any: Any?): Boolean {
        return try {
            val atCls = Class.forName("android.app.ActivityThread")
            val app = atCls.getMethod("currentApplication").invoke(null) ?: return false
            val ctx = app as? android.content.Context ?: return false
            val am = ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE)
                as? ActivityManager ?: return false
            val pid = android.os.Process.myPid()
            val processes = am.runningAppProcesses ?: return false
            val mine = processes.firstOrNull { it.pid == pid } ?: return false
            mine.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        } catch (_: Throwable) {
            false
        }
    }
}
