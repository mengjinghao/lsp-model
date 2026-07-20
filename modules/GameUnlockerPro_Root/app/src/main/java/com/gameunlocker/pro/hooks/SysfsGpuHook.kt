package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import com.gameunlocker.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Sysfs GPU Hook（内核级，需 Shizuku adb 级授权）
 *
 * 直接写入 Adreno/Mali GPU 内核节点，从硬件层面锁定最高性能：
 *  - /sys/class/kgsl/kgsl-3d0/max_gpuclk → 强制最大 GPU 频率
 *  - /sys/class/kgsl/kgsl-3d0/max_pwrlevel → 0（最高性能等级）
 *  - /sys/class/kgsl/kgsl-3d0/devfreq/governor → performance
 *  - /sys/class/kgsl/kgsl-3d0/min_pwrlevel → 0（强制最低功耗等级=最高频率）
 *  - /sys/class/kgsl/kgsl-3d0/thermal_pwrlevel → 0（禁用温控调频）
 *  - force_clk_on / force_bus_on / force_rail_on / force_no_nap → 1（强制保持 GPU 活跃）
 *
 * 硬性限制：
 *  - 需要 Shizuku/Root ADB 授权
 *  - 仅支持高通 Adreno GPU（kgsl 驱动）
 *  - Mali GPU 路径不同（/sys/class/misc/mali*/device/devfreq/*）
 *  - 写入 sysfs 非持久化，重启后恢复默认
 */
object SysfsGpuHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.sysfsGpuEnabled) return
        if (!ShizukuHelper.isAvailable()) {
            LogX.w("Shizuku 不可用，跳过 Sysfs GPU 操作")
            return
        }
        LogX.i("Sysfs GPU Hook 启动（内核级）")

        probeAndWriteGpuNodes(cfg)
        hookAppOnCreate(lpparam, cfg)
    }

    /**
     * 探测并写入所有存在的 GPU 内核节点
     * 同时覆盖 Adreno（kgsl）和 Mali（mali）两种 GPU 驱动
     */
    private fun probeAndWriteGpuNodes(cfg: GameConfig) {
        // ===== Adreno GPU (kgsl) =====
        val gpuBase = "/sys/class/kgsl/kgsl-3d0"

        // 强制最大 GPU 时钟频率
        ShizukuHelper.execShell(
            "cat $gpuBase/max_gpuclk > $gpuBase/max_gpuclk"
        )
        // 最高性能等级 (0 = 最高)
        ShizukuHelper.execShell(
            "echo 0 > $gpuBase/max_pwrlevel"
        )
        // GPU devfreq governor 设为 performance
        ShizukuHelper.execShell(
            "echo performance > $gpuBase/devfreq/governor"
        )
        // 最低功耗等级 (0 = 最高频率)
        ShizukuHelper.execShell(
            "echo 0 > $gpuBase/min_pwrlevel"
        )
        // 禁用温控调频等级
        ShizukuHelper.execShell(
            "echo 0 > $gpuBase/thermal_pwrlevel"
        )
        // 强制 GPU 时钟保持开启
        ShizukuHelper.execShell(
            "echo 1 > $gpuBase/force_clk_on"
        )
        // 强制 GPU 总线保持活跃
        ShizukuHelper.execShell(
            "echo 1 > $gpuBase/force_bus_on"
        )
        // 强制 GPU 电源轨保持开启
        ShizukuHelper.execShell(
            "echo 1 > $gpuBase/force_rail_on"
        )
        // 禁用 GPU 休眠（NAP）
        ShizukuHelper.execShell(
            "echo 1 > $gpuBase/force_no_nap"
        )

        // ===== 通过 find 探测所有 kgsl 节点 =====
        ShizukuHelper.execShell(
            "find /sys/class/kgsl/ -name \"force_clk_on\" -type f -exec echo 1 > {} \\;"
        )
        ShizukuHelper.execShell(
            "find /sys/class/kgsl/ -name \"force_bus_on\" -type f -exec echo 1 > {} \\;"
        )
        ShizukuHelper.execShell(
            "find /sys/class/kgsl/ -name \"force_rail_on\" -type f -exec echo 1 > {} \\;"
        )
        ShizukuHelper.execShell(
            "find /sys/class/kgsl/ -name \"force_no_nap\" -type f -exec echo 1 > {} \\;"
        )

        // 禁用 Adreno 空闲超时
        ShizukuHelper.execShell(
            "find /sys/class/kgsl/ -name \"idle_timer\" -type f -exec echo 0 > {} \\;"
        )

        // ===== Mali GPU（兼容处理）=====
        ShizukuHelper.execShell(
            "find /sys/class/misc/ -path \"*mali*/devfreq/governor\" -exec echo performance > {} \\;"
        )
        ShizukuHelper.execShell(
            "find /sys/class/devfreq/ -name \"governor\" -exec echo performance > {} \\;"
        )

        // ===== PowerVR GPU（兼容处理）=====
        ShizukuHelper.execShell(
            "find /sys/kernel/gpu/ -name \"gpu_governor\" -exec echo performance > {} \\;"
        )

        LogX.i("Sysfs GPU 节点已写入（内核级）")
    }

    /** Hook Application.onCreate 以在应用完全初始化后重新应用 GPU 节点写入 */
    private fun hookAppOnCreate(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        try {
            val appCls = XposedHelpers.findClassIfExists(
                "android.app.Application", lpparam.classLoader
            ) ?: return
            XposedHelpers.findAndHookMethod(appCls, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        if (ShizukuHelper.isAvailable()) {
                            probeAndWriteGpuNodes(cfg)
                            LogX.d("Sysfs GPU 节点已重新应用（onCreate 后）")
                        }
                    }
                })
            LogX.hookSuccess("Application", "onCreate->SysfsGpu")
        } catch (e: Throwable) { LogX.w("SysfsGpu App hook 异常: ${e.message}") }
    }
}
