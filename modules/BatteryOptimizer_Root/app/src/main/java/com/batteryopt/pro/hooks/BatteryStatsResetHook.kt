package com.batteryopt.pro.hooks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import com.batteryopt.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】电池统计重置 Hook（需 Shizuku/Root）
 *
 * 功能：
 *  - 通过 Shizuku 执行 `dumpsys batterystats --reset` 重置系统电量统计
 *  - 监听充电状态：插上充电器后延迟重置（默认 60s）
 *  - 重置后系统将重新统计电量使用情况，便于分析耗电
 *
 * 注意：
 *  - 重置后历史电量统计将清空（不可恢复）
 *  - 部分系统可能不支持该命令（返回 Unknown command）
 *  - 实验性功能，默认关闭
 *
 * §4.2 命令执行型 Hook：通过 Hook Application.onCreate 触发充电广播注册，
 * 由充电广播延迟驱动 `dumpsys batterystats --reset` 命令执行。
 */
object BatteryStatsResetHook {

    private val handler = Handler(Looper.getMainLooper())
    private var powerReceiver: BroadcastReceiver? = null
    private var pendingReset: Runnable? = null

    /** 充电器插上后延迟重置的秒数 */
    private const val RESET_DELAY_SEC = 60L

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        if (!cfg.batteryStatsResetEnabled) {
            LogX.d("【实验性】电量统计重置未开启，跳过")
            return
        }

        LogX.i("【实验性】电量统计重置启动 | 充电${RESET_DELAY_SEC}s 后重置")

        // §4.2 命令执行型 Hook：Hook Application.onCreate 触发充电广播注册
        XposedHelpers.findAndHookMethod(
            "android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val ctx = p.thisObject as? Context ?: return
                    if (!ShizukuHelper.isShizukuAvailable()) {
                        LogX.w("Shizuku 不可用，跳过充电广播注册")
                        return
                    }
                    registerPowerReceiver(ctx)
                }
            })
        LogX.hookSuccess("Application", "onCreate->BatteryStatsReset")
    }

    private fun registerPowerReceiver(ctx: Context) {
        try {
            powerReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_POWER_CONNECTED) {
                        onPowerConnected()
                    } else if (intent?.action == Intent.ACTION_POWER_DISCONNECTED) {
                        onPowerDisconnected()
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            ctx.registerReceiver(powerReceiver, filter)
            LogX.i("充电状态广播已注册")
        } catch (e: Exception) {
            LogX.e("注册充电广播异常", e)
        }
    }

    private fun onPowerConnected() {
        LogX.i("充电器接入，${RESET_DELAY_SEC}s 后重置 batterystats")
        pendingReset?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            if (!ShizukuHelper.isShizukuAvailable()) {
                LogX.w("Shizuku 不可用，跳过 batterystats 重置")
                return@Runnable
            }
            val out = ShizukuHelper.execShell("dumpsys batterystats --reset")
            LogX.i("已重置 batterystats: $out")
        }
        pendingReset = r
        handler.postDelayed(r, RESET_DELAY_SEC * 1000L)
    }

    private fun onPowerDisconnected() {
        LogX.i("充电器断开，取消 batterystats 重置")
        pendingReset?.let { handler.removeCallbacks(it) }
        pendingReset = null
    }
}
