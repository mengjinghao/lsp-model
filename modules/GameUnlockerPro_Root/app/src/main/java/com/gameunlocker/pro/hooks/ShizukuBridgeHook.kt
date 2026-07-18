package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import com.gameunlocker.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku联动桥接Hook
 *
 * 功能：
 *  - 模块主动调用Shizuku API，同步修改系统刷新率属性
 *  - 后台冻结多余进程，释放内存给游戏
 *
 * 集成模式 vs 本地模式：
 *  集成模式：Shizuku作为系统服务运行，可直接调用API
 *  本地模式(LSPatch)：Shizuku未必运行，通过反射调用+adb授权
 */
object ShizukuBridgeHook {

    private var isApplied = false

    /** Shizuku需要修改的系统属性列表（用于解锁刷新率） */
    private val REFRESH_RATE_PROPS = listOf(
        "ro.surface_flinger.refresh_rate" to "120",
        "ro.surface_flinger.set_idle_timer_ms" to "1000",
        "ro.surface_flinger.max_frame_buffer_acquired_buffers" to "3",
        "ro.surface_flinger.vsync_event_phase_offset_ns" to "0",
        "ro.surface_flinger.vsync_sf_event_phase_offset_ns" to "0",
        "debug.egl.swapinterval" to "0",
        "debug.gr.swapinterval" to "0"
    )

    /**
     * 应用Shizuku联动
     */
    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, config: GameConfig) {
        if (!config.shizukuBridgeEnabled) {
            LogX.d("Shizuku联动未开启，跳过")
            return
        }
        if (isApplied) return
        isApplied = true

        LogX.i("Shizuku联动桥接启动")

        // 1. 设置系统刷新率相关属性
        applyRefreshRateProps()

        // 2. 释放后台内存（通过Shizuku执行）
        freeBackgroundMemory()
    }

    /**
     * 通过Shizuku修改刷新率系统属性
     */
    private fun applyRefreshRateProps() {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku不可用，跳过系统属性修改")
            return
        }

        REFRESH_RATE_PROPS.forEach { (key, value) ->
            val success = ShizukuHelper.setSystemProperty(key, value)
            if (success) {
                LogX.d("Shizuku属性设置: $key = $value")
            }
        }

        // 额外的刷新率优化命令
        ShizukuHelper.execShell("settings put system peak_refresh_rate 120")
        ShizukuHelper.execShell("settings put system min_refresh_rate 120")
    }

    /**
     * 释放后台内存
     * 通过Shizuku执行am kill命令清理非必要后台进程
     */
    private fun freeBackgroundMemory() {
        if (!ShizukuHelper.isShizukuAvailable()) return

        try {
            // 清理缓存
            ShizukuHelper.execShell("echo 3 > /proc/sys/vm/drop_caches")

            // 强制停止非关键后台进程
            val killCommands = listOf(
                "am force-stop com.miui.cleaner",
                "am force-stop com.miui.powerkeeper",
                "am force-stop com.xiaomi.joyose",
                "am kill-all"
            )
            for (cmd in killCommands) {
                ShizukuHelper.execShell(cmd)
            }

            LogX.d("后台内存已释放")
        } catch (e: Exception) {
            LogX.d("释放内存异常: ${e.message}")
        }
    }

    /**
     * 释放Shizuku资源（游戏退出时调用）
     */
    fun release() {
        ShizukuHelper.release()
        isApplied = false
        LogX.d("Shizuku联动资源已释放")
    }
}
