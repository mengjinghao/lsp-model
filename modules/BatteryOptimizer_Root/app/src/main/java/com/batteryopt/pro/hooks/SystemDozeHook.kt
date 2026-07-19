package com.batteryopt.pro.hooks

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import com.batteryopt.pro.utils.ShizukuHelper
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 系统 Doze 强制 Hook（需 Shizuku/Root）
 *
 * 功能：
 *  - 通过 Shizuku 执行 `dumpsys deviceidle force-idle deep` 强制进入深度 Doze
 *  - 通过 `settings put global device_idle_constants ...` 调整 Doze 参数
 *  - 仅在屏幕关闭时触发，屏幕亮起时恢复（dumpsys deviceidle unforce）
 */
object SystemDozeHook {

    private val handler = Handler(Looper.getMainLooper())
    private var screenReceiver: BroadcastReceiver? = null
    private var pendingForceIdle: Runnable? = null

    private val dozeParams = mapOf(
        "inactive_after" to "30s",
        "sensing_after" to "60s",
        "locating_after" to "120s",
        "idle_after" to "300s",
        "idle_pending_timeout" to "60s",
        "max_temp_app_idle_delay_ms" to "300000"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        if (!cfg.dozeEnabled) {
            LogX.d("系统 Doze 强制未开启，跳过")
            return
        }

        LogX.i("系统 Doze 强制启动 | 延迟=${cfg.dozeDelaySec}s")

        applyDozeParams()
        registerScreenReceiver(lpparam, cfg)
    }

    private fun applyDozeParams() {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过 Doze 参数应用")
            return
        }
        for ((key, value) in dozeParams) {
            ShizukuHelper.execShell(
                "settings put global device_idle_constants $key $value"
            )
        }
        LogX.i("已应用 Doze 参数: ${dozeParams.size} 项")
    }

    private fun registerScreenReceiver(
        lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig
    ) {
        try {
            val app = retrieveApplication(lpparam) ?: run {
                LogX.w("无法获取 Application，Doze 屏幕监听延迟到下次启动")
                return
            }
            val ctx = app.applicationContext
            screenReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_SCREEN_OFF -> onScreenOff(cfg.dozeDelaySec)
                        Intent.ACTION_SCREEN_ON -> onScreenOn()
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            ctx.registerReceiver(screenReceiver, filter)
            LogX.i("Doze 屏幕开关广播已注册")
        } catch (e: Exception) {
            LogX.e("注册屏幕广播异常", e)
        }
    }

    private fun onScreenOff(delaySec: Int) {
        LogX.i("屏幕关闭，${delaySec}s 后强制进入深度 Doze")
        pendingForceIdle?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            if (!ShizukuHelper.isShizukuAvailable()) {
                LogX.w("Shizuku 不可用，跳过 force-idle")
                return@Runnable
            }
            val out = ShizukuHelper.execShell("dumpsys deviceidle force-idle deep")
            LogX.i("已强制进入深度 Doze: $out")
        }
        pendingForceIdle = r
        handler.postDelayed(r, delaySec * 1000L)
    }

    private fun onScreenOn() {
        LogX.i("屏幕亮起，恢复 Doze 自动状态")
        pendingForceIdle?.let { handler.removeCallbacks(it) }
        pendingForceIdle = null
        if (ShizukuHelper.isShizukuAvailable()) {
            ShizukuHelper.execShell("dumpsys deviceidle unforce")
            LogX.d("已恢复 Doze 自动状态")
        }
    }

    private fun retrieveApplication(lpparam: XC_LoadPackage.LoadPackageParam): Application? {
        return try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            XposedHelpers.callMethod(cat, "getApplication") as? Application
        } catch (_: Exception) { null }
    }
}
