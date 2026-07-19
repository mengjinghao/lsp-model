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
 * 【实验性】低电量模式自动切换 Hook（需 Shizuku/Root）
 *
 * 功能：
 *  - 通过 Shizuku 执行 `settings put global low_power 1/0` 切换系统低电量模式
 *  - 监听电池电量广播（ACTION_BATTERY_CHANGED）
 *  - 电量低于阈值（默认 20%）时自动开启低电量模式
 *  - 电量恢复到安全值（默认 30%）时自动关闭
 *
 * 注意：
 *  - 不同 OEM 对 low_power 设置响应不同（部分需 root + setprop）
 *  - 强制开启低电量模式可能限制后台同步、性能等
 *  - 实验性功能，默认关闭
 */
object LowPowerModeAutoHook {

    private val handler = Handler(Looper.getMainLooper())
    private var batteryReceiver: BroadcastReceiver? = null

    /** 电量低阈值（%），低于此值开启低电量模式 */
    private val LOW_THRESHOLD = 20

    /** 电量安全阈值（%），高于此值关闭低电量模式 */
    private val SAFE_THRESHOLD = 30

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        if (!cfg.lowPowerModeAutoEnabled) {
            LogX.d("【实验性】低电量自动切换未开启，跳过")
            return
        }

        LogX.i("【实验性】低电量自动切换启动 | 阈值: 低=$LOW_THRESHOLD% 安=$SAFE_THRESHOLD%")

        registerBatteryReceiver(lpparam)
    }

    private fun registerBatteryReceiver(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val app = retrieveApplication(lpparam) ?: run {
                LogX.w("无法获取 Application，低电量监听延迟")
                return
            }
            val ctx = app.applicationContext
            batteryReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                        val level = intent.getIntExtra("level", -1)
                        val scale = intent.getIntExtra("scale", 100)
                        if (level < 0 || scale <= 0) return
                        val percent = level * 100 / scale
                        onBatteryLevel(percent)
                    }
                }
            }
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ctx.registerReceiver(batteryReceiver, filter)
            LogX.i("电池电量广播已注册")
        } catch (e: Exception) {
            LogX.e("注册电池广播异常", e)
        }
    }

    private var lastLowPowerState: Boolean? = null

    private fun onBatteryLevel(percent: Int) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过低电量切换")
            return
        }

        val shouldEnableLowPower = percent <= LOW_THRESHOLD
        if (lastLowPowerState == shouldEnableLowPower) return
        lastLowPowerState = shouldEnableLowPower

        val value = if (shouldEnableLowPower) 1 else 0
        val out = ShizukuHelper.execShell("settings put global low_power $value")
        LogX.i("电量=$percent%, 切换低电量模式 -> $value (out=$out)")
    }

    private fun retrieveApplication(lpparam: XC_LoadPackage.LoadPackageParam): Application? {
        return try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            XposedHelpers.callMethod(cat, "getApplication") as? Application
        } catch (_: Exception) { null }
    }
}
