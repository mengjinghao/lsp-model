package com.batteryopt.pro.hooks

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import com.batteryopt.pro.utils.ShizukuHelper
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * CPU 调度策略 Hook（需 Shizuku/Root）
 *
 * 功能：
 *  - 通过 Shizuku 读写 /sys/devices/system/cpu/cpuN/cpufreq/scaling_governor
 *  - 屏幕关闭时切换为 powersave governor，降低 CPU 频率省电
 *  - 屏幕亮起恢复 interactive / schedutil，恢复性能
 */
object CpuGovernorHook {

    private var screenReceiver: BroadcastReceiver? = null

    private val cpuIndices = 0..7

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        if (!cfg.cpuGovernorEnabled) {
            LogX.d("CPU 调度策略未开启，跳过")
            return
        }

        LogX.i("CPU 调度策略启动 | active=${cfg.cpuGovernorActive} idle=${cfg.cpuGovernorIdle}")

        registerScreenReceiver(lpparam, cfg)
    }

    private fun registerScreenReceiver(
        lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig
    ) {
        try {
            val app = retrieveApplication(lpparam) ?: run {
                LogX.w("无法获取 Application，CPU 监听延迟")
                return
            }
            val ctx = app.applicationContext
            screenReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_SCREEN_OFF -> setGovernor(cfg.cpuGovernorIdle)
                        Intent.ACTION_SCREEN_ON -> setGovernor(cfg.cpuGovernorActive)
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            ctx.registerReceiver(screenReceiver, filter)
            LogX.i("CPU 屏幕开关广播已注册")
        } catch (e: Exception) {
            LogX.e("注册屏幕广播异常", e)
        }
    }

    private fun setGovernor(governor: String) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过 governor 设置")
            return
        }
        var successCount = 0
        var failCount = 0
        for (i in cpuIndices) {
            val path = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor"
            val cmd = "echo $governor > $path"
            ShizukuHelper.execShell(cmd)
            val verify = ShizukuHelper.execShell("cat $path 2>/dev/null")?.trim()
            if (verify == governor) {
                successCount++
            } else {
                failCount++
            }
        }
        LogX.i("Governor=$governor | 成功=$successCount 失败=$failCount")
    }

    private fun retrieveApplication(lpparam: XC_LoadPackage.LoadPackageParam): Application? {
        return try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            XposedHelpers.callMethod(cat, "getApplication") as? Application
        } catch (_: Exception) { null }
    }
}
