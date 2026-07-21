package com.stepmod.pro.hooks

import com.stepmod.pro.models.StepConfig
import com.stepmod.pro.utils.LogX
import com.stepmod.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 内核步数节点注入 Hook（Root 实验性）
 *
 * 功能：
 *  - 通过 Shizuku 写 /proc/step_counter 内核节点
 *  - 通过 Shizuku 写 /proc/sensors/step_counter
 *  - 通过 Shizuku 执行 echo > /dev/step_counter 设备节点
 *
 * 风险提示（实验性）：
 *  - 内核节点写入可能造成传感器异常或内核崩溃
 *  - 不同机型节点路径差异极大，命中率高不保证
 *  - 需 root 级别 Shizuku 授权
 *
 * 硬性限制：
 *  - 必须先检查 ShizukuHelper.isShizukuAvailable()
 *  - 失败时不影响其他 Hook
 */
object KernelStepInjectHook {

    /** 内核节点候选路径 */
    private val kernelNodePaths = listOf(
        "/proc/step_counter",
        "/proc/sensors/step_counter",
        "/dev/step_counter",
        "/sys/kernel/step_counter/value",
        "/proc/driver/step_counter"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        if (!cfg.kernelStepInjectEnabled) return
        LogX.i("内核步数节点注入 Hook 启动（Root 实验性）")

        writeKernelNodes(cfg)
        hookAppLifecycleForKernelInject(lpparam, cfg)
    }

    private fun writeKernelNodes(cfg: StepConfig) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过内核节点注入")
            return
        }
        var hitAny = false
        for (path in kernelNodePaths) {
            try {
                // 探测节点是否存在
                val probe = ShizukuHelper.execShell("test -e $path && echo exists")
                if (probe?.contains("exists") != true) {
                    LogX.d("内核节点不存在: $path")
                    continue
                }
                val ok = ShizukuHelper.writeFile(path, cfg.customSteps.toString())
                if (ok) {
                    LogX.i("内核节点写入成功: $path = ${cfg.customSteps}")
                    hitAny = true
                } else {
                    LogX.w("内核节点写入失败（权限不足）: $path")
                }
            } catch (e: Exception) { LogX.w("写内核节点 $path 异常: ${e.message}") }
        }
        if (!hitAny) {
            LogX.w("所有内核步数节点写入均失败（机型不匹配或权限不足）")
        }
    }

    private fun hookAppLifecycleForKernelInject(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            LogX.d("APP 启动 → 触发内核节点注入")
                            writeKernelNodes(cfg)
                        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("Application", "onCreate(KernelInject)")
        } catch (e: Exception) {
            LogX.hookFailed("Application", "onCreate(KernelInject)", e)
        }
    }
}
