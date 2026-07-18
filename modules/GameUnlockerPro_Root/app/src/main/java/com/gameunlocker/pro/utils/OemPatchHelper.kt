package com.gameunlocker.pro.utils

/**
 * 国产系统专项兼容补丁
 * 每个厂商系统在底层帧率/性能调度方面有差异，需要专项Hook修复
 *
 * 支持系统：HyperOS(小米)、OriginOS(vivo/iQOO)、ColorOS(OPPO/一加)、MagicOS(荣耀)、OneUI(三星)
 */
object OemPatchHelper {

    /**
     * 检测当前运行的系统类型
     */
    enum class OemType {
        HYPER_OS,       // 小米 HyperOS / MIUI
        ORIGIN_OS,      // vivo OriginOS
        COLOR_OS,       // OPPO ColorOS
        MAGIC_OS,       // 荣耀 MagicOS
        ONE_UI,         // 三星 OneUI
        FUNTOUCH_OS,    // vivo旧版FuntouchOS
        UNKNOWN         // 未知系统
    }

    /** 获取当前系统类型 */
    fun detectOemType(): OemType {
        return try {
            val buildDisplay = android.os.Build.DISPLAY ?: ""
            val buildFingerprint = android.os.Build.FINGERPRINT ?: ""

            return when {
                buildDisplay.contains("HyperOS", ignoreCase = true) ||
                buildDisplay.contains("MIUI", ignoreCase = true) ||
                buildFingerprint.contains("Xiaomi", ignoreCase = true) -> OemType.HYPER_OS

                buildDisplay.contains("OriginOS", ignoreCase = true) ||
                buildFingerprint.contains("vivo", ignoreCase = true) -> OemType.ORIGIN_OS

                buildDisplay.contains("ColorOS", ignoreCase = true) ||
                buildFingerprint.contains("OPPO", ignoreCase = true) ||
                buildFingerprint.contains("OnePlus", ignoreCase = true) -> OemType.COLOR_OS

                buildDisplay.contains("MagicOS", ignoreCase = true) ||
                buildFingerprint.contains("HONOR", ignoreCase = true) -> OemType.MAGIC_OS

                buildDisplay.contains("One UI", ignoreCase = true) ||
                buildFingerprint.contains("samsung", ignoreCase = true) -> OemType.ONE_UI

                buildDisplay.contains("Funtouch", ignoreCase = true) -> OemType.FUNTOUCH_OS

                else -> OemType.UNKNOWN
            }
        } catch (e: Exception) {
            LogX.e("检测OEM类型异常", e)
            OemType.UNKNOWN
        }
    }

    /**
     * 获取各系统需要Hook的帧率锁定目标列表
     * 返回 List<HookTarget>，每项为(类名, 方法名)
     */
    data class HookTarget(val className: String, val methodName: String, val description: String)

    fun getOemFrameRateHookTargets(): List<HookTarget> {
        val targets = mutableListOf<HookTarget>()

        // ===== 通用帧率锁定Hook目标 =====
        // Android Framework RefreshRate限制
        targets.add(HookTarget(
            "com.android.server.display.DisplayManagerService",
            "getDisplayInfo",
            "Android原生DisplayManager帧率限制"
        ))

        // ===== HyperOS专项补丁 =====
        // 小米游戏工具箱帧率限制、Joyose性能调度
        targets.add(HookTarget(
            "com.miui.gamebooster.service.GameBoosterService",
            "onFrameRateLimit",
            "HyperOS游戏加速器帧率限制"
        ))
        targets.add(HookTarget(
            "com.xiaomi.joyose.JoyoseManager",
            "getPerformanceLevel",
            "HyperOS Joyose性能等级限制"
        ))
        targets.add(HookTarget(
            "com.miui.powerkeeper.PowerKeeperService",
            "notifyFrameRateLimit",
            "HyperOS电源管理帧率限制"
        ))

        // ===== OriginOS专项补丁 =====
        // vivo游戏魔盒帧率限制
        targets.add(HookTarget(
            "com.vivo.gamewatch.GameWatchService",
            "setMaxFrameRate",
            "OriginOS游戏魔盒帧率限制"
        ))
        targets.add(HookTarget(
            "com.vivo.pem.PowerExpertService",
            "onPerformanceMode",
            "OriginOS电源专家性能模式"
        ))

        // ===== ColorOS专项补丁 =====
        // OPPO游戏空间帧率限制、HyperBoost
        targets.add(HookTarget(
            "com.oplus.games.GameSpaceService",
            "lockFrameRate",
            "ColorOS游戏空间帧率锁定"
        ))
        targets.add(HookTarget(
            "com.oplus.hyperboost.HyperBoostEngine",
            "getMaxRefreshRate",
            "ColorOS HyperBoost最大刷新率"
        ))
        targets.add(HookTarget(
            "com.oplus.thermal.ThermalDaemonService",
            "onThermalLimit",
            "ColorOS温控帧率限制"
        ))

        // ===== MagicOS专项补丁 =====
        targets.add(HookTarget(
            "com.hihonor.gameassistant.GameAssistantService",
            "setFrameSettings",
            "MagicOS游戏助手帧率设置"
        ))

        // ===== OneUI专项补丁 =====
        targets.add(HookTarget(
            "com.samsung.android.game.gametools.GameBoosterService",
            "setFrameRateCap",
            "OneUI GameBooster帧率上限"
        ))
        targets.add(HookTarget(
            "com.samsung.android.gos.GameOptimizingService",
            "onPerformanceCheck",
            "OneUI GOS性能检查"
        ))

        return targets
    }

    /**
     * 获取各系统温控相关Hook目标
     */
    fun getOemThermalHookTargets(): List<HookTarget> {
        return listOf(
            HookTarget("com.miui.powerkeeper.thermal.MiuiThermalService", "notifyThermalLevel",
                "HyperOS温控通知"),
            HookTarget("com.vivo.thermal.ThermalService", "onTemperatureChanged",
                "OriginOS温控回调"),
            HookTarget("com.oplus.thermal.ThermalAdapter", "getCurrentTemperature",
                "ColorOS当前温度获取"),
            HookTarget("com.samsung.android.thermal.ThermalManagerService", "onThermalEvent",
                "OneUI温控事件"),
            HookTarget("com.hihonor.thermal.HonorThermalService", "updateThermalState",
                "MagicOS温控状态更新")
        )
    }
}
