package com.batteryopt.noroot.hooks

import com.batteryopt.noroot.models.BatteryConfig
import com.batteryopt.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Auto Task Killer（实验性，NoRoot 版）
 *
 * NoRoot 版：Hook Process.killProcess + System.exit 空闲检测
 * - Hook Process.killProcess 监控被调用情况
 * - Hook System.exit 记录退出原因
 * - 定时检查 CPU 使用（通过 /proc/self/stat），超阈值标记退出
 */
object TaskKillerHook {

    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "TaskKillerNoRoot").apply { isDaemon = true }
    }
    private var cpuCheckFuture: java.util.concurrent.ScheduledFuture<*>? = null

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("【实验性】Auto Task Killer 启动（NoRoot）| CPU阈值=${cfg.taskKillerCpuThreshold}%")

        hookProcessKill(lpparam, cfg)
        hookSystemExit(lpparam, cfg)
        startIdleMonitor(cfg)
    }

    private fun hookProcessKill(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.os.Process", lpparam.classLoader,
                "killProcess",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val pid = p.args[0] as Int
                            val myPid = android.os.Process.myPid()
                            LogX.d("killProcess($pid) 被调用, myPid=$myPid")
                        } catch (e: Exception) {
                            LogX.e("killProcess 监控异常", e)
                        }
                    }
                })
            LogX.hookSuccess("Process", "killProcess->Monitor(NoRoot)")
        } catch (e: Exception) {
            LogX.e("Hook killProcess(NoRoot) 异常", e)
        }
    }

    private fun hookSystemExit(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "java.lang.System", lpparam.classLoader,
                "exit",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val code = p.args[0] as Int
                            LogX.i("System.exit($code) 被调用")
                            stopMonitor()
                        } catch (e: Exception) {
                            LogX.e("System.exit 监控异常", e)
                        }
                    }
                })
            LogX.hookSuccess("System", "exit->Monitor(NoRoot)")
        } catch (e: Exception) {
            LogX.e("Hook System.exit(NoRoot) 异常", e)
        }
    }

    private fun startIdleMonitor(cfg: BatteryConfig) {
        try {
            cpuCheckFuture = executor.scheduleAtFixedRate(
                { checkCpuUsage(cfg) },
                60,
                60,
                TimeUnit.SECONDS
            )
            LogX.i("空闲 CPU 监控已启动（NoRoot）")
        } catch (e: Exception) {
            LogX.e("空闲 CPU 监控启动异常", e)
        }
    }

    private fun checkCpuUsage(cfg: BatteryConfig) {
        try {
            val pid = android.os.Process.myPid()
            val statContent = java.io.File("/proc/$pid/stat").readText()
            val statParts = statContent.split(" ")
            if (statParts.size < 15) return

            val utime = statParts[13].toLongOrNull() ?: return
            val stime = statParts[14].toLongOrNull() ?: return

            Thread.sleep(100)

            val statContent2 = java.io.File("/proc/$pid/stat").readText()
            val statParts2 = statContent2.split(" ")
            if (statParts2.size < 15) return

            val utime2 = statParts2[13].toLongOrNull() ?: return
            val stime2 = statParts2[14].toLongOrNull() ?: return

            val cpuDiff = (utime2 - utime) + (stime2 - stime)
            val cpuPercent = (cpuDiff * 100) / android.os.Process.getElapsedCpuTime()

            if (cpuPercent > cfg.taskKillerCpuThreshold) {
                LogX.w("CPU 超标: ${cpuPercent}% > ${cfg.taskKillerCpuThreshold}%, 标记退出")
            }
        } catch (e: Exception) {
            LogX.e("CPU 检查异常", e)
        }
    }

    fun stopMonitor() {
        cpuCheckFuture?.cancel(false)
    }
}
