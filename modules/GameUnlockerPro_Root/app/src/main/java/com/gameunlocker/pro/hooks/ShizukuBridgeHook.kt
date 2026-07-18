package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.model.GameConfig
import com.gameunlocker.pro.utils.LogX
import com.gameunlocker.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku 联动桥接 Hook（系统级，需 Shizuku adb 级授权）
 *
 * 功能：
 *  1. 通过 Shizuku setprop 修改 SurfaceFlinger 刷新率相关属性
 *  2. 通过 Shizuku settings put system 设置系统级刷新率
 *  3. 通过 Shizuku am force-stop 释放后台内存
 *
 * 硬性限制：
 *  - ro.* 属性原生不可写，setprop 非持久化，重启后失效
 *  - LSPatch 本地模式下 Shizuku 未必在运行，所有调用 try-catch 保护
 */
object ShizukuBridgeHook {

    /** Shizuku 需要修改的系统属性列表（用于解锁刷新率） */
    private val REFRESH_RATE_PROPS = listOf(
        "ro.surface_flinger.refresh_rate" to "120",
        "ro.surface_flinger.set_idle_timer_ms" to "1000",
        "ro.surface_flinger.max_frame_buffer_acquired_buffers" to "3",
        "ro.surface_flinger.vsync_event_phase_offset_ns" to "0",
        "ro.surface_flinger.vsync_sf_event_phase_offset_ns" to "0",
        "debug.egl.swapinterval" to "0",
        "debug.gr.swapinterval" to "0"
    )

    private var applied = false

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.shizukuBridgeEnabled) return
        if (applied) return
        applied = true
        LogX.i("Shizuku 联动桥接启动（系统级）")

        applyRefreshRateProps(cfg.targetFps)
        freeBackgroundMemory()

        // 同时 Hook Application.onCreate 以确保 ShizukuHelper 已初始化
        hookAppLifecycle(lpparam)
    }

    /** 通过 Shizuku setprop 修改刷新率属性 */
    private fun applyRefreshRateProps(targetFps: Int) {
        if (!ShizukuHelper.isAvailable()) {
            LogX.w("Shizuku 不可用，跳过 setprop 系统属性修改")
            return
        }

        for ((key, value) in REFRESH_RATE_PROPS) {
            ShizukuHelper.setSystemProperty(key, value)
        }

        ShizukuHelper.execShell("settings put system peak_refresh_rate $targetFps")
        ShizukuHelper.execShell("settings put system min_refresh_rate $targetFps")
        LogX.i("Shizuku 刷新率属性已设置: peak=$targetFps")
    }

    /** 通过 Shizuku am force-stop 释放后台内存 */
    private fun freeBackgroundMemory() {
        if (!ShizukuHelper.isAvailable()) return

        ShizukuHelper.execShell("echo 3 > /proc/sys/vm/drop_caches")

        val killCommands = listOf(
            "am force-stop com.miui.cleaner",
            "am force-stop com.miui.powerkeeper",
            "am force-stop com.xiaomi.joyose",
            "am kill-all"
        )
        for (cmd in killCommands) {
            ShizukuHelper.execShell(cmd)
        }
        LogX.d("Shizuku 后台内存已释放")
    }

    private fun hookAppLifecycle(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        // Shizuku 状态在 onCreate 后再次确认
                        if (ShizukuHelper.isAvailable()) {
                            LogX.d("Shizuku 状态确认: 可用")
                        }
                    }
                })
        } catch (_: Throwable) {}
    }

    /** 释放 Shizuku 资源 */
    fun release() {
        ShizukuHelper.reset()
        applied = false
        LogX.d("Shizuku 联动资源已释放")
    }
}
