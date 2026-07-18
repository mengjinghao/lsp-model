package com.gameunlocker.noroot.hooks

import com.gameunlocker.noroot.utils.LogX
import com.gameunlocker.noroot.utils.ShizukuHelper

/**
 * Shizuku执行器
 *
 * 功能：
 *  1. 通过Shizuku API设置刷新率系统属性(adb级setprop)
 *  2. 冻结后台冗余应用降低整机负载
 *
 * 硬性限制：
 *  - 所有setprop命令依赖Shizuku adb级授权
 *  - 如果Shizuku未激活，setprop命令无效（不会报错）
 *  - 属性设置在重启后消失（setprop非持久化）
 */
object ShizukuExecutor {

    /**
     * 通过Shizuku setprop设置刷新率相关属性
     * 这些属性被SurfaceFlinger读取，影响屏幕刷新率策略
     */
    fun applyRefreshRate(fps: Int) {
        if (!ShizukuHelper.isAvailable()) {
            LogX.w("Shizuku不可用，跳过setprop帧率设置")
            return
        }

        val props = listOf(
            "debug.egl.swapinterval" to "0",
            "debug.gr.swapinterval" to "0",
            "ro.surface_flinger.max_frame_buffer_acquired_buffers" to "3",
            "ro.surface_flinger.set_idle_timer_ms" to "1000",
            "ro.surface_flinger.vsync_event_phase_offset_ns" to "0",
            "ro.surface_flinger.vsync_sf_event_phase_offset_ns" to "0"
        )

        for ((k, v) in props) {
            ShizukuHelper.execShell("setprop $k $v")
        }

        // Settings系统级刷新率限制
        ShizukuHelper.execShell("settings put system peak_refresh_rate $fps")
        ShizukuHelper.execShell("settings put system min_refresh_rate $fps")

        LogX.i("Shizuku刷新率属性已设置: peak=$fps")
    }

    /**
     * 批量冻结后台冗余应用
     * 通过Shizuku执行 am force-stop 命令
     */
    fun freezeBackgroundApps() {
        if (!ShizukuHelper.isAvailable()) {
            LogX.w("Shizuku不可用，跳过后台冻结")
            return
        }

        // 常见的非必要后台进程（可根据实际设备调整）
        val apps = listOf(
            "com.miui.cleaner",
            "com.miui.powerkeeper",
            "com.xiaomi.joyose",
            "com.miui.systemAdSolution",
            "com.xiaomi.mi_connect_service",
            "com.miui.analytics",
            "com.miui.daemon",
            "com.vivo.pem"
        )

        for (app in apps) {
            ShizukuHelper.execShell("am force-stop $app")
        }

        // 清理页面缓存（非root可执行的内核接口）
        ShizukuHelper.execShell("echo 3 > /proc/sys/vm/drop_caches")

        LogX.i("后台应用冻结完成: ${apps.size}个进程")
    }

    /** 释放资源 */
    fun release() {
        ShizukuHelper.reset()
    }
}
