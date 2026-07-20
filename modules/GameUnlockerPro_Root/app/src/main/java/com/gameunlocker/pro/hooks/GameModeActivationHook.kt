package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import com.gameunlocker.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 游戏模式激活 Hook（实验性，系统级，需 Shizuku adb 级授权）
 *
 * 功能：
 *  - 通过 Shizuku 执行 `cmd game_mode <mode>` 设置游戏模式
 *  - 通过 Shizuku 执行 `settings put global game_mode 2` 强制游戏模式
 *  - Hook GameManager / GameModeManager 让游戏自身感知已激活游戏模式
 *
 * 硬性限制：
 *  - `cmd game_mode` 命令仅在 Android 12+ 可用
 *  - 不同厂商对 game_mode 的支持差异较大
 *  - settings put global game_mode 为非持久化修改，重启后失效
 *
 * 实验性声明：默认关闭，仅在玩家明确需要时开启。
 */
object GameModeActivationHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.gameModeActivationEnabled) return
        LogX.i("游戏模式激活启动（实验性，系统级）")

        activateGameModeViaShizuku()
        hookGameModeManager(lpparam)
    }

    /** 通过 Shizuku 激活游戏模式 */
    private fun activateGameModeViaShizuku() {
        if (!ShizukuHelper.isAvailable()) {
            LogX.w("Shizuku 不可用，跳过游戏模式激活")
            return
        }

        // Android 12+ cmd game_mode 命令
        ShizukuHelper.execShell("cmd game_mode standard 1")
        ShizukuHelper.execShell("cmd game_mode performance 1")
        ShizukuHelper.execShell("cmd game_mode benchmark 1")

        // settings put global game_mode (2 = PERFORMANCE)
        ShizukuHelper.execShell("settings put global game_mode 2")

        // Game driver preferences
        ShizukuHelper.execShell("settings put global game_driver_preferences \"{\\\"com.tencent.tmgp.sgame\\\":\\\"performance\\\"}\"")

        // performance boost via msm_performance
        ShizukuHelper.execShell("echo 0 > /sys/module/msm_performance/parameters/boost")

        LogX.i("Shizuku 游戏模式已激活（含驱动/boost）")
    }

    /** Hook GameManager 让应用感知已激活游戏模式 */
    private fun hookGameModeManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val gm = XposedHelpers.findClassIfExists(
                "android.app.GameManager", lpparam.classLoader) ?: return

            // getGameMode 返回 GAME_MODE_PERFORMANCE(2)
            try {
                XposedHelpers.findAndHookMethod(gm, "getGameMode",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = 2  // GAME_MODE_PERFORMANCE
                        }
                    })
                LogX.hookSuccess("GameManager", "getGameMode -> PERFORMANCE")
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

            // isAngleEnabled 强制 true（部分游戏用 ANGLE 提升渲染稳定性）
            try {
                XposedHelpers.findAndHookMethod(gm, "isAngleEnabled",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = false  // 保持默认，不强制 ANGLE
                        }
                    })
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        } catch (e: Throwable) {
            LogX.hookFailed("GameManager", "getGameMode", e)
        }
    }
}
