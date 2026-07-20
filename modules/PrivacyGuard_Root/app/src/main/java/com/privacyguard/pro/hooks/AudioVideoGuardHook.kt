package com.privacyguard.pro.hooks

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object AudioVideoGuardHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        if (!cfg.cameraGuardEnabled && !cfg.micGuardEnabled) return
        LogX.i("Camera/Mic入侵守卫启动: camera=${cfg.cameraGuardEnabled} mic=${cfg.micGuardEnabled} block=${cfg.blockUnauthorizedAv}")

        if (cfg.cameraGuardEnabled) hookCamera(lpparam, cfg)
        if (cfg.micGuardEnabled) hookMediaRecorder(lpparam, cfg)
    }

    private fun hookCamera(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        try {
            val cameraClass = XposedHelpers.findClassIfExists(
                "android.hardware.Camera", lpparam.classLoader)
            if (cameraClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(cameraClass, "open", object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            LogX.w("[AVGuard] Camera.open() 被调用: ${lpparam.packageName}")
                            showNotification(lpparam, "相机已激活", "${lpparam.packageName} 正在使用相机")
                            if (cfg.blockUnauthorizedAv) {
                                LogX.w("[AVGuard] 拦截 Camera.open()")
                                p.result = null
                            }
                        }
                    })
                    LogX.hookSuccess("Camera", "open()")
                } catch (e: Exception) { LogX.w("异常: ${e.message}") }

                try {
                    XposedHelpers.findAndHookMethod(cameraClass, "open",
                        Int::class.java, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                LogX.w("[AVGuard] Camera.open(id) 被调用: ${lpparam.packageName}")
                                showNotification(lpparam, "相机已激活", "${lpparam.packageName} 正在使用相机")
                                if (cfg.blockUnauthorizedAv) {
                                    LogX.w("[AVGuard] 拦截 Camera.open(id)")
                                    p.result = null
                                }
                            }
                        })
                    LogX.hookSuccess("Camera", "open(Int)")
                } catch (e: Exception) { LogX.w("异常: ${e.message}") }
            }

            val camera2Class = XposedHelpers.findClassIfExists(
                "android.hardware.camera2.CameraManager", lpparam.classLoader)
            if (camera2Class != null) {
                try {
                    XposedHelpers.findAndHookMethod(camera2Class, "openCamera",
                        String::class.java, "android.hardware.camera2.CameraDevice\$StateCallback",
                        "android.os.Handler",
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                LogX.w("[AVGuard] Camera2.openCamera() 被调用: ${lpparam.packageName}")
                                showNotification(lpparam, "相机已激活", "${lpparam.packageName} 正在使用Camera2")
                                if (cfg.blockUnauthorizedAv) {
                                    LogX.w("[AVGuard] 拦截 Camera2.openCamera()")
                                    p.result = null
                                }
                            }
                        })
                    LogX.hookSuccess("CameraManager", "openCamera")
                } catch (e: Exception) { LogX.w("异常: ${e.message}") }
            }
        } catch (e: Exception) {
            LogX.e("AudioVideoGuardHook Camera异常", e)
        }
    }

    private fun hookMediaRecorder(lpparam: XC_LoadPackage.LoadPackageParam, cfg: PrivacyConfig) {
        try {
            val mrClass = XposedHelpers.findClassIfExists(
                "android.media.MediaRecorder", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(mrClass, "start", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        LogX.w("[AVGuard] MediaRecorder.start() 被调用: ${lpparam.packageName}")
                        showNotification(lpparam, "麦克风已激活", "${lpparam.packageName} 正在使用麦克风录音")
                        if (cfg.blockUnauthorizedAv && cfg.micGuardEnabled) {
                            LogX.w("[AVGuard] 拦截 MediaRecorder.start()")
                            p.result = null
                        }
                    }
                })
                LogX.hookSuccess("MediaRecorder", "start")
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }

            val arClass = XposedHelpers.findClassIfExists(
                "android.media.AudioRecord", lpparam.classLoader)
            if (arClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(arClass, "startRecording", object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            LogX.w("[AVGuard] AudioRecord.startRecording() 被调用: ${lpparam.packageName}")
                            showNotification(lpparam, "麦克风已激活", "${lpparam.packageName} 正在使用AudioRecord录音")
                            if (cfg.blockUnauthorizedAv && cfg.micGuardEnabled) {
                                LogX.w("[AVGuard] 拦截 AudioRecord.startRecording()")
                                p.result = null
                            }
                        }
                    })
                    LogX.hookSuccess("AudioRecord", "startRecording")
                } catch (e: Exception) { LogX.w("异常: ${e.message}") }
            }
        } catch (e: Exception) {
            LogX.e("AudioVideoGuardHook MediaRecorder异常", e)
        }
    }

    private fun showNotification(lpparam: XC_LoadPackage.LoadPackageParam, title: String, content: String) {
        try {
            val ctx = try {
                val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
                val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
                XposedHelpers.callMethod(cat, "getApplication") as? Context
            } catch (_: Throwable) { return }
            if (ctx == null) return

            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channelId = "privacyguard_av_guard"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "隐私防护", NotificationManager.IMPORTANCE_HIGH)
                nm.createNotificationChannel(channel)
            }
            val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            val pi = PendingIntent.getActivity(ctx, 0, intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val notif = NotificationCompat.Builder(ctx, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            nm.notify(99001, notif)
        } catch (e: Exception) { LogX.w("通知异常: ${e.message}") }
    }
}
