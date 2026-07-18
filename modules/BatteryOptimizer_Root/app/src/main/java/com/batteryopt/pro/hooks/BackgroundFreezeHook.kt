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
 * 后台 APP 冻结 Hook（需 Shizuku/Root）
 *
 * 功能：
 *  - 通过 Shizuku 执行 `am force-stop <pkg>` 冻结用户选定的后台耗电 APP
 *  - 通过 Shizuku 执行 `am kill <pkg>` 优雅 kill
 *  - 屏幕关闭后 N 分钟自动冻结黑名单 APP
 *  - 屏幕亮起时取消未执行的冻结任务
 *
 * 注意：
 *  - force-stop 会终止 APP 全部进程并清理任务栈，比 kill 更彻底
 *  - 冻结后 APP 推送/消息将无法接收，属于预期效果
 *  - 黑名单不应包含当前运行的目标 APP 自身
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

        registerScreenReceiver(lpparam, cfg)
    }

    private fun registerScreenReceiver(
        lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig
    ) {
        try {
            val app = retrieveApplication(lpparam) ?: run {
                LogX.w("无法获取 Application，冻结屏幕监听延迟")
                return
            }
            val ctx = app.applicationContext
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

    /** 屏幕关闭：延迟后冻结黑名单 APP */
    private fun onScreenOff(cfg: BatteryConfig) {
        LogX.i("屏幕关闭，${cfg.freezeDelayMin} 分钟后冻结黑名单 APP")
        pendingFreeze?.let { handler.removeCallbacks(it) }
        val r = Runnable { freezeAll(cfg.freezeBlacklist) }
        pendingFreeze = r
        handler.postDelayed(r, cfg.freezeDelayMin * 60_000L)
    }

    /** 屏幕亮起：取消待执行冻结 */
    private fun onScreenOn() {
        LogX.i("屏幕亮起，取消待执行冻结任务")
        pendingFreeze?.let { handler.removeCallbacks(it) }
        pendingFreeze = null
    }

    /** 执行 force-stop 黑名单 APP */
    private fun freezeAll(blacklist: List<String>) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过冻结")
            return
        }
        for (pkg in blacklist) {
            try {
                // 优先 force-stop，失败则尝试 am kill
                val out = ShizukuHelper.execShell("am force-stop $pkg")
                LogX.i("已冻结: $pkg | out=$out")
            } catch (e: Exception) {
                LogX.e("冻结 $pkg 异常，尝试 am kill", e)
                try {
                    ShizukuHelper.execShell("am kill $pkg")
                } catch (_: Exception) {}
            }
        }
        LogX.i("冻结批次完成，共 ${blacklist.size} 个 APP")
    }

    private fun retrieveApplication(lpparam: XC_LoadPackage.LoadPackageParam): Application? {
        return try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            XposedHelpers.callMethod(cat, "getApplication") as? Application
        } catch (_: Exception) { null }
    }
}
