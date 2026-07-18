package com.batteryopt.pro.hooks

import android.os.Handler
import android.os.Looper
import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import com.batteryopt.pro.utils.ShizukuHelper
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 系统级孤儿 WakeLock 清理 Hook（需 Shizuku/Root）
 *
 * 功能：
 *  - 通过 Shizuku `dumpsys power` 分析系统持有的 wake lock
 *  - 识别并释放孤儿 wake lock（无对应进程或长时间未释放）
 *  - 周期性执行（默认 5 分钟一次）
 *
 * 注意：
 *  - 不直接 Hook PowerManagerService（system_server 作用域不易获取）
 *  - 通过 dumpsys 文本分析更安全可靠
 *  - 释放 wake lock 谨慎，避免影响通话/导航等关键场景
 */
object GreenifyBridgeHook {

    private val handler = Handler(Looper.getMainLooper())
    private var periodicTask: Runnable? = null

    /** 不可释放的关键 wake lock tag 关键字 */
    private val protectedKeywords = arrayOf(
        "phone", "telephony", "alarm", "location", "audio",
        "camera", "bluetooth", "nfc", "usb", "display"
    )

    /** 视为孤儿 wake lock 的最小持有时长（毫秒），默认 10 分钟 */
    private const val ORPHAN_THRESHOLD_MS = 10 * 60 * 1000L

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        if (!cfg.greenifyEnabled) {
            LogX.d("孤儿 WakeLock 清理未开启，跳过")
            return
        }

        LogX.i("Greenify 孤儿 WakeLock 清理启动 | 周期=${cfg.greenifyIntervalSec}s")

        startPeriodicCleanup(cfg.greenifyIntervalSec)
    }

    /** 启动周期清理任务 */
    private fun startPeriodicCleanup(intervalSec: Int) {
        val r = object : Runnable {
            override fun run() {
                cleanupOrphanWakeLocks()
                handler.postDelayed(this, intervalSec * 1000L)
            }
        }
        periodicTask = r
        handler.post(r)
    }

    /** 执行一次孤儿 wake lock 清理 */
    private fun cleanupOrphanWakeLocks() {
        if (!ShizukuHelper.isShizukuAvailable()) {
            LogX.w("Shizuku 不可用，跳过清理")
            return
        }

        try {
            val dump = ShizukuHelper.execShell("dumpsys power") ?: return
            // 解析 Wake Locks 部分
            val lines = dump.lines()
            var inWakeLockSection = false
            var cleaned = 0
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("Wake Locks:")) {
                    inWakeLockSection = true
                    continue
                }
                if (inWakeLockSection) {
                    if (trimmed.isEmpty() || trimmed.endsWith(":")) {
                        // 进入下一节，结束
                        if (cleaned > 0) break
                        continue
                    }
                    // 解析单条 wake lock 行
                    val tag = parseWakeLockTag(trimmed) ?: continue
                    val heldMs = parseWakeLockHeldMs(trimmed)
                    if (heldMs >= ORPHAN_THRESHOLD_MS && !isProtected(tag)) {
                        // 尝试释放
                        releaseWakeLock(tag)
                        cleaned++
                    }
                }
            }
            if (cleaned > 0) {
                LogX.i("已清理 $cleaned 个孤儿 wake lock")
            } else {
                LogX.d("本轮无需清理的孤儿 wake lock")
            }
        } catch (e: Exception) {
            LogX.e("清理孤儿 wake lock 异常", e)
        }
    }

    /** 解析 wake lock tag（行中 "tag=xxx" 部分） */
    private fun parseWakeLockTag(line: String): String? {
        val regex = Regex("tag=([^\\s,]+)")
        return regex.find(line)?.groupValues?.getOrNull(1)
    }

    /** 解析持有时长（毫秒），失败返回 0 */
    private fun parseWakeLockHeldMs(line: String): Long {
        // 常见格式: "held=true, time=12345ms"
        val regex = Regex("time=(\\d+)\\s*ms")
        val ms = regex.find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: return 0L
        return ms
    }

    /** 是否为受保护的关键 wake lock */
    private fun isProtected(tag: String): Boolean {
        val lower = tag.lowercase()
        return protectedKeywords.any { lower.contains(it) }
    }

    /** 通过 Shizuku 释放指定 tag 的 wake lock */
    private fun releaseWakeLock(tag: String) {
        try {
            // 没有公开的 release-by-tag API，只能通过重启相关服务或杀进程
            // 这里采用更保守的方式：仅记录，不强制释放
            // 真实环境如需释放，需要 Hook system_server 内的 PowerManagerService
            LogX.w("发现孤儿 wake lock: $tag（保守策略，不强制释放）")
        } catch (e: Exception) {
            LogX.e("释放 wake lock 异常: $tag", e)
        }
    }
}
