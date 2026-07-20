package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import com.gameunlocker.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * GPU Governor Tuner（实验性，Root 版）
 *
 * Root 版：使用 Shizuku 写 GPU 频率调节节点
 * - 路径：/sys/class/kgsl/kgsl-3d0/
 * - 游戏启动时切换 performance，退出恢复 powersave
 * - Hook Application.onCreate 触发应用
 */
object GpuTunerHook {

    private var isGaming = false

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        LogX.i("【实验性】GPU Governor Tuner 启动 | governor=${cfg.gpuGovernor}")

        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，GPU Governor Tuner 跳过")
            return
        }

        hookAppOnCreate(lpparam, cfg)
        hookAppOnTerminate(lpparam, cfg)
    }

    private fun hookAppOnCreate(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        try {
            val appCls = XposedHelpers.findClassIfExists(
                "android.app.Application", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                appCls, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            if (!isGaming) {
                                setGpuGovernor(cfg.gpuGovernor)
                                isGaming = true
                                LogX.i("GPU governor 已设为 ${cfg.gpuGovernor}（游戏模式）")
                            }
                        } catch (e: Exception) {
                            LogX.e("App onCreate GPU 调谐异常", e)
                        }
                    }
                })
            LogX.hookSuccess("Application", "onCreate->GpuTuner")
        } catch (e: Exception) {
            LogX.e("Hook Application.onCreate GPU 异常", e)
        }
    }

    private fun hookAppOnTerminate(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        try {
            val appCls = XposedHelpers.findClassIfExists(
                "android.app.Application", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                appCls, "onTerminate",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            if (isGaming) {
                                setGpuGovernor("powersave")
                                isGaming = false
                                LogX.i("GPU governor 已恢复 powersave")
                            }
                        } catch (e: Exception) {
                            LogX.e("App onTerminate GPU 恢复异常", e)
                        }
                    }
                })
            LogX.hookSuccess("Application", "onTerminate->GpuRestore")
        } catch (e: Exception) {
            LogX.e("Hook Application.onTerminate GPU 异常", e)
        }
    }

    private fun setGpuGovernor(governor: String) {
        try {
            val gpuPath = "/sys/class/kgsl/kgsl-3d0/devfreq/governor"
            val cmd = "echo $governor > $gpuPath"
            ShizukuHelper.execShell(cmd)
        } catch (e: Exception) {
            try {
                val altPath = "/sys/class/kgsl/kgsl-3d0/gpu_governor"
                val cmd = "echo $governor > $altPath"
                ShizukuHelper.execShell(cmd)
            } catch (e2: Exception) {
                try {
                    val msmPath = "/sys/class/devfreq/*/governor"
                    ShizukuHelper.execShell("echo $governor > $msmPath")
                } catch (e3: Exception) {
                    LogX.e("GPU governor 设置失败", e3)
                }
            }
        }
    }
}
