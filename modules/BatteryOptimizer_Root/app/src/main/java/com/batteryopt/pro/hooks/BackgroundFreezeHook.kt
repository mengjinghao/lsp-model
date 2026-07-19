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
 * 后台 APP 冻结 Hook（需 Shizuku/Root）
 *
 * 功能：
 *  - 通过 Shizuku 执行 `am force-stop <pkg>` 冻结用户选定的后台耗电 APP
 *  - 通过 Shizuku 执行 `am kill <pkg>` 优雅 kill
 *  - 屏幕关闭后 N 分钟自动冻结黑名单 APP
 *  - 屏幕亮起时取消未执行的冻结任务
 *
 * §4.2 命令执行型 Hook：通过 Hook Application.onCreate 触发广播注册，
 * 由广播回调驱动 `am force-stop` 命令执行，避免空壳。
 */
object BackgroundFreezeHook {

    private val handler = Handler(Looper.getMainLooper())
    private var screenReceiver: BroadcastReceiver? = null
    private var pendingFreeze: Runnable? = null

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        if (!cfg.freezeEnabled) {
            LogX.d("后台冻结未开启，跳过")
            return
        }
        if (cfg.freezeBlacklist.isEmpty()) {
            LogX.w("冻结黑名单为空，跳过")
            return
        }

        LogX.i("后台冻结启动 | 延迟=${cfg.freezeDelayMin}min 黑名单=${cfg.freezeBlacklist.size}个")

        // §4.2 命令执行型 Hook：Hook Application.onCreate 触发屏幕广播注册
        XposedHelpers.findAndHookMethod(
            "android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val ctx = p.thisObject as? Context ?: return
                    if (!ShizukuHelper.isShizukuAvailable()) {
                        LogX.w("Shizuku 不可用，跳过冻结广播注册")
                        return
                    }
                    registerScreenReceiver(ctx, cfg)
                }
            })
        LogX.hookSuccess("Application", "onCreate->Freeze")
    }

    private fun registerScreenReceiver(ctx: Context, cfg: BatteryConfig) {
        try {
            screenReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_SCREEN_OFF -> onScreenOff(cfg)
                        Intent.ACTION_SCREEN_ON -> onScreenOn()
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            ctx.registerReceiver(screenReceiver, filter)
            LogX.i("冻结屏幕开关广播已注册")
        } catch (e: Exception) {
            LogX.e("注册屏幕广播异常", e)
        }
    }

    private fun onScreenOff(cfg: BatteryConfig) {
        LogX.i("屏幕关闭，${cfg.freezeDelayMin} 分钟后冻结黑名单 APP")
        pendingFreeze?.let { handler.removeCallbacks(it) }
        val r = Runnable { freezeAll(cfg.freezeBlacklist) }
        pendingFreeze = r
        handler.postDelayed(r, cfg.freezeDelayMin * 60_000L)
    }

    private fun onScreenOn() {
        LogX.i("屏幕亮起，取消待执行冻结任务")
        pendingFreeze?.let { handler.removeCallbacks(it) }
        pendingFreeze = null
    }

    private fun freezeAll(blacklist: List<String>) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过冻结")
            return
        }
        for (pkg in blacklist) {
            // execShell 内部已有 try-catch，失败返回 null；此处据此走 am kill 兜底
            val out = ShizukuHelper.execShell("am force-stop $pkg")
            if (out == null) {
                LogX.w("force-stop $pkg 失败，尝试 am kill 兜底")
                ShizukuHelper.execShell("am kill $pkg")
            } else {
                LogX.i("已冻结: $pkg | out=$out")
            }
        }
        LogX.i("冻结批次完成，共 ${blacklist.size} 个 APP")
    }
}
