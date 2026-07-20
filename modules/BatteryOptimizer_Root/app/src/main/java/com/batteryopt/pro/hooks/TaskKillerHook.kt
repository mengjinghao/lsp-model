package com.batteryopt.pro.hooks

import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import com.batteryopt.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Auto Task Killer（实验性）
 *
 * Root 版：使用 Shizuku `top -n 1` + `am force-stop` 监控并杀死高CPU进程
 * - Hook Process.killProcess 扩增为完整的 am force-stop
 * - 定时通过 Shizuku top 扫描，CPU 超阈值则 force-stop
 */
object TaskKillerHook {

    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "TaskKiller").apply { isDaemon = true }
    }
    private var monitorFuture: ScheduledFuture<*>? = null

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("【实验性】Auto Task Killer 启动 | CPU阈值=${cfg.taskKillerCpuThreshold}%")

        hookProcessKill(lpparam, cfg)
        startCpuMonitor(cfg)
    }

    private fun hookProcessKill(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.os.Process", lpparam.classLoader,
                "killProcess",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val pid = p.args[0] as Int
                            LogX.d("killProcess($pid) 升级为 am force-stop")
                            if (ShizukuHelper.isShizukuAvailable()) {
                                val pkg = lpparam.packageName
                                ShizukuHelper.execShell("am force-stop $pkg")
                            }
                        } catch (e: Exception) {
                            LogX.e("killProcess 升级异常", e)
                        }
                    }
                })
            LogX.hookSuccess("Process", "killProcess->ForceStop")
        } catch (e: Exception) {
            LogX.e("Hook killProcess 异常", e)
        }
    }

    private fun startCpuMonitor(cfg: BatteryConfig) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过 CPU 监控")
            return
        }
        try {
            monitorFuture = executor.scheduleAtFixedRate(
                { scanAndKill(cfg) },
                30,
                60,
                TimeUnit.SECONDS
            )
            LogX.i("CPU 监控已启动，每 60 秒扫描一次")
        } catch (e: Exception) {
            LogX.e("CPU 监控启动异常", e)
        }
    }

    private fun scanAndKill(cfg: BatteryConfig) {
        try {
            val output = ShizukuHelper.execShell("top -n 1 -b") ?: return
            val lines = output.lines()
            var startParsing = false
            for (line in lines) {
                if (line.contains("PID") && line.contains("CPU")) {
                    startParsing = true
                    continue
                }
                if (!startParsing) continue
                if (line.isBlank()) continue

                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size < 9) continue

                val cpuStr = parts.getOrNull(8)?.replace("%", "") ?: continue
                val cpuUsage = try { cpuStr.toFloat() } catch (_: Exception) { continue }
                if (cpuUsage > cfg.taskKillerCpuThreshold) {
                    val pid = parts[0]
                    val procName = parts.getOrNull(parts.size - 1) ?: "unknown"
                    LogX.w("CPU 超标: $procName (PID=$pid, CPU=${cpuUsage}% > ${cfg.taskKillerCpuThreshold}%)")
                    if (procName != "top" && procName != "sh") {
                        ShizukuHelper.execShell("kill -9 $pid")
                        LogX.i("已 kill -9 $pid ($procName)")
                    }
                }
            }
        } catch (e: Exception) {
            LogX.e("CPU 扫描异常", e)
        }
    }

    fun stopMonitor() {
        monitorFuture?.cancel(false)
    }
}
