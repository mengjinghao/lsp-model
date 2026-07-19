package com.batteryopt.pro.hooks

import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * ContentResolver 同步优化 Hook（应用层）
 */
object BackgroundSyncHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("Sync 同步优化启动 | 最小间隔=${cfg.syncMinIntervalMs}ms")

        hookRequestSync(lpparam, cfg)
        hookAddPeriodicSync(lpparam, cfg)
    }

    private fun hookRequestSync(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        try {
            val crCls = XposedHelpers.findClassIfExists(
                "android.content.ContentResolver", lpparam.classLoader
            ) ?: return

            val lastRequestTs = HashMap<String, Long>()

            XposedHelpers.findAndHookMethod(
                crCls, "requestSync",
                "android.accounts.Account",
                String::class.java,
                "android.os.Bundle",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val authority = p.args[1] as? String ?: return
                        val now = System.currentTimeMillis()
                        val last = lastRequestTs[authority] ?: 0L
                        if (now - last < cfg.syncMinIntervalMs) {
                            p.result = null
                            LogX.w("requestSync 节流: $authority 间隔不足，跳过")
                        } else {
                            lastRequestTs[authority] = now
                            LogX.d("requestSync 放行: $authority")
                        }
                    }
                })
            LogX.hookSuccess("ContentResolver", "requestSync")
        } catch (e: Exception) {
            LogX.e("Hook requestSync 异常", e)
        }
    }

    private fun hookAddPeriodicSync(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        try {
            val crCls = XposedHelpers.findClassIfExists(
                "android.content.ContentResolver", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                crCls, "addPeriodicSync",
                "android.accounts.Account",
                String::class.java,
                "android.os.Bundle",
                java.lang.Long.TYPE,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val authority = p.args[1] as? String ?: return
                        val pollSeconds = p.args[3] as Long
                        val minSeconds = cfg.syncMinIntervalMs / 1000L
                        if (pollSeconds < minSeconds) {
                            val old = pollSeconds
                            p.args[3] = minSeconds
                            LogX.w("addPeriodicSync 周期放大: $authority ${old}s -> ${minSeconds}s")
                        } else {
                            LogX.d("addPeriodicSync: $authority ${pollSeconds}s")
                        }
                    }
                })
            LogX.hookSuccess("ContentResolver", "addPeriodicSync")
        } catch (e: Exception) {
            LogX.e("Hook addPeriodicSync 异常", e)
        }
    }
}
