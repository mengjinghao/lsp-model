package com.stepmod.pro.hooks

import com.stepmod.pro.models.StepConfig
import com.stepmod.pro.utils.LogX
import com.stepmod.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 系统级步数传感器节点写入 Hook（Root 专属）
 *
 * 功能：
 *  - 通过 Shizuku 写 /sys/class/sensors/step_counter/value
 *  - 通过 Shizuku 写 /sys/class/sensors/step_detector/value
 *  - Hook Application.onCreate 在 APP 启动后触发系统节点写入
 *
 * 硬性限制（Root 版严格遵守）：
 *  - 系统级 Hook 必须先检查 ShizukuHelper.isShizukuAvailable()
 *  - 写 /sys 节点需要 root 级别 Shizuku 授权（adb 级可能权限不足）
 *  - 不同机型节点路径不同，需多候选
 *  - 失败时降级为应用层 Hook（StepSensorHook 已处理）
 */
object SystemSensorHook {

    /** 系统步数节点候选路径（不同厂商） */
    private val sysNodePaths = listOf(
        "/sys/class/sensors/step_counter/value",
        "/sys/class/sensors/step_counter/raw_data",
        "/sys/class/sensors/steps/value",
        "/sys/devices/virtual/sensors/step_counter/value",
        "/sys/class/health/step_counter/value"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        if (!cfg.systemSensorEnabled) return
        LogX.i("系统级步数传感器 Hook 启动（Root 专属）")

        // 立即尝试一次（如果 Shizuku 可用）
        writeSystemStepNodes(cfg)

        // Hook Application.onCreate 触发周期性写入
        hookAppLifecycleForSystemWrite(lpparam, cfg)
    }

    /** 遍历候选路径写系统节点 */
    private fun writeSystemStepNodes(cfg: StepConfig) {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过系统节点写入（降级为应用层 Hook）")
            return
        }
        var writtenAny = false
        for (path in sysNodePaths) {
            try {
                val ok = ShizukuHelper.writeFile(path, cfg.customSteps.toString())
                if (ok) {
                    LogX.i("系统节点写入成功: $path = ${cfg.customSteps}")
                    writtenAny = true
                } else {
                    LogX.d("系统节点写入失败（可能不存在或无权限）: $path")
                }
            } catch (e: Exception) { LogX.w("写系统节点 $path 异常: ${e.message}") }
        }
        if (!writtenAny) {
            LogX.w("所有系统步数节点写入均失败（机型不匹配或 Shizuku 权限不足）")
        }
    }

    /** Hook Application.onCreate — APP 启动后触发系统节点写入 */
    private fun hookAppLifecycleForSystemWrite(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            LogX.d("APP 启动 → 触发系统节点写入")
                            writeSystemStepNodes(cfg)
                        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("Application", "onCreate(SystemSensor)")
        } catch (e: Exception) {
            LogX.hookFailed("Application", "onCreate(SystemSensor)", e)
        }
    }
}
