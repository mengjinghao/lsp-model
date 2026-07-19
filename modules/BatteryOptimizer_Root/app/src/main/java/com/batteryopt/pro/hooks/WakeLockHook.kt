package com.batteryopt.pro.hooks

import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.ConcurrentHashMap

/**
 * WakeLock 持有优化 Hook（应用层，仅优化当前 APP 自身）
 *
 * 功能：
 *  1. Hook PowerManager.WakeLock.acquire(timeout)/acquire()，记录持有时长
 *  2. 对超长持有(>配置阈值, 默认60s)的 wake lock 自动 release
 *  3. 对明显冗余的 wake lock（如 SDK 统计类）在 acquire 后立即 release
 *
 * 硬性限制：
 *  - 仅作用于当前 APP 进程内的 WakeLock，跨进程孤儿 wake lock 清理由 GreenifyBridgeHook 负责
 */
object WakeLockHook {

    private val holdRecords = ConcurrentHashMap<String, Long>()
    private val immediateReleaseFlags = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )

    private val redundantKeywords = arrayOf(
        "umeng", "jpush", "baidu", "tencent_mta", "mta",
        "getui", "huawei_push", "xiaomi_push", "oppo_push",
        "stats", "analytics", "tracking", "log_report"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("WakeLock 优化启动（应用层）| 最大持有=${cfg.wakeLockMaxHoldMs}ms 拦截冗余=${cfg.wakeLockBlockRedundant}")

        hookAcquireWithTimeout(lpparam, cfg)
        hookAcquireNoTimeout(lpparam, cfg)
        hookRelease(lpparam)
    }

    private fun hookAcquireWithTimeout(
        lpparam: XC_LoadPackage.LoadPackageParam,
        cfg: BatteryConfig
    ) {
        try {
            val wlCls = XposedHelpers.findClassIfExists(
                "android.os.PowerManager.WakeLock", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                wlCls, "acquire",
                java.lang.Long.TYPE,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val tag = readTag(p.thisObject)
                        if (cfg.wakeLockBlockRedundant && isRedundant(tag)) {
                            immediateReleaseFlags.add(identity(p.thisObject))
                            LogX.d("标记冗余 wake lock(acquire_timeout) 立即释放: $tag")
                            return
                        }
                        val inputTimeout = p.args[0] as Long
                        if (inputTimeout > cfg.wakeLockMaxHoldMs) {
                            p.args[0] = cfg.wakeLockMaxHoldMs
                            LogX.w("缩短 wake lock 超时: $tag $inputTimeout -> ${cfg.wakeLockMaxHoldMs}")
                        }
                    }

                    override fun afterHookedMethod(p: MethodHookParam) {
                        val id = identity(p.thisObject)
                        val tag = readTag(p.thisObject)
                        if (immediateReleaseFlags.remove(id)) {
                            try {
                                val held = XposedHelpers.callMethod(p.thisObject, "isHeld") as? Boolean ?: false
                                if (held) {
                                    XposedHelpers.callMethod(p.thisObject, "release")
                                    LogX.w("已立即释放冗余 wake lock: $tag")
                                }
                            } catch (e: Exception) {
                                LogX.e("立即释放冗余 wake lock 异常: $tag", e)
                            }
                            return
                        }
                        holdRecords[id] = System.currentTimeMillis()
                        scheduleAutoRelease(p.thisObject, id, tag, cfg.wakeLockMaxHoldMs)
                        LogX.d("WakeLock acquire(timeout): $tag")
                    }
                })
            LogX.hookSuccess("PowerManager.WakeLock", "acquire(timeout)")
        } catch (e: Exception) {
            LogX.e("Hook acquire(timeout) 异常", e)
        }
    }

    private fun hookAcquireNoTimeout(
        lpparam: XC_LoadPackage.LoadPackageParam,
        cfg: BatteryConfig
    ) {
        try {
            val wlCls = XposedHelpers.findClassIfExists(
                "android.os.PowerManager.WakeLock", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                wlCls, "acquire",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val tag = readTag(p.thisObject)
                        if (cfg.wakeLockBlockRedundant && isRedundant(tag)) {
                            immediateReleaseFlags.add(identity(p.thisObject))
                            LogX.d("标记冗余 wake lock(acquire) 立即释放: $tag")
                            return
                        }
                    }

                    override fun afterHookedMethod(p: MethodHookParam) {
                        val id = identity(p.thisObject)
                        val tag = readTag(p.thisObject)
                        if (immediateReleaseFlags.remove(id)) {
                            try {
                                val held = XposedHelpers.callMethod(p.thisObject, "isHeld") as? Boolean ?: false
                                if (held) {
                                    XposedHelpers.callMethod(p.thisObject, "release")
                                    LogX.w("已立即释放冗余 wake lock: $tag")
                                }
                            } catch (e: Exception) {
                                LogX.e("立即释放冗余 wake lock 异常: $tag", e)
                            }
                            return
                        }
                        holdRecords[id] = System.currentTimeMillis()
                        scheduleAutoRelease(p.thisObject, id, tag, cfg.wakeLockMaxHoldMs)
                        LogX.w("WakeLock acquire(无超时): $tag | 已安排 ${cfg.wakeLockMaxHoldMs}ms 后自动释放")
                    }
                })
            LogX.hookSuccess("PowerManager.WakeLock", "acquire()")
        } catch (e: Exception) {
            LogX.e("Hook acquire() 异常", e)
        }
    }

    private fun hookRelease(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val wlCls = XposedHelpers.findClassIfExists(
                "android.os.PowerManager.WakeLock", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                wlCls, "release",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val id = identity(p.thisObject)
                        val startTs = holdRecords.remove(id) ?: return
                        val held = System.currentTimeMillis() - startTs
                        LogX.d("WakeLock release: ${readTag(p.thisObject)} 持有 ${held}ms")
                    }
                })

            try {
                XposedHelpers.findAndHookMethod(
                    wlCls, "release",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val id = identity(p.thisObject)
                            val startTs = holdRecords.remove(id) ?: return
                            val held = System.currentTimeMillis() - startTs
                            LogX.d("WakeLock release(): ${readTag(p.thisObject)} 持有 ${held}ms")
                        }
                    })
            } catch (_: Exception) {}
        } catch (e: Exception) {
            LogX.e("Hook release 异常", e)
        }
    }

    private fun readTag(wakeLock: Any?): String {
        return try {
            XposedHelpers.getObjectField(wakeLock, "mTag") as? String ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun identity(obj: Any?): String = System.identityHashCode(obj).toString()

    private fun isRedundant(tag: String): Boolean {
        val lower = tag.lowercase()
        return redundantKeywords.any { lower.contains(it) }
    }

    private fun scheduleAutoRelease(
        wakeLock: Any?, id: String, tag: String, delayMs: Long
    ) {
        Thread {
            try {
                Thread.sleep(delayMs)
                if (!holdRecords.containsKey(id)) return@Thread
                val held = try {
                    XposedHelpers.callMethod(wakeLock, "isHeld") as? Boolean ?: false
                } catch (_: Exception) { false }
                if (held) {
                    try {
                        XposedHelpers.callMethod(wakeLock, "release")
                        LogX.w("自动释放超时 wake lock: $tag | 已持有 $delayMs ms")
                    } catch (e: Exception) {
                        LogX.e("自动释放 wake lock 异常: $tag", e)
                    }
                }
                holdRecords.remove(id)
            } catch (_: InterruptedException) {
            } catch (e: Exception) {
                LogX.e("自动释放线程异常", e)
            }
        }.apply { isDaemon = true; name = "WLockAutoRelease-$tag" }.start()
    }
}
