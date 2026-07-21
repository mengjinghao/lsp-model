package com.stepmod.pro.hooks

import com.stepmod.pro.models.StepConfig
import com.stepmod.pro.utils.LogX
import com.stepmod.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Shizuku直接写入健康APP数据库（Root 专属）
 *
 * 通过 Shizuku 执行系统级操作。
 * 硬性限制：需 Shizuku root 级授权
 */
object HealthDatabaseInjectHook {

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        if (!cfg.healthDbInjectEnabled) return
        LogX.i("HealthDatabaseInjectHook 启动（Root 专属）")

        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku不可用，跳过HealthDatabaseInjectHook")
                            return
                        }
                        execute()
                        LogX.i("HealthDatabaseInjectHook 完成")
                    } catch (e: Throwable) {
                        LogX.w("HealthDatabaseInjectHook 异常: ${e.message}")
                    }
                }
            })
        LogX.hookSuccess("Application", "onCreate->HealthDatabaseInjectHook")
    }

    private fun execute() {
        // 直接写入健康 APP 数据库
        val healthApps = mapOf(
            "com.xiaomi.hm.health" to "/data/data/com.xiaomi.hm.health/databases/health.db",
            "com.huawei.health" to "/data/data/com.huawei.health/databases/health.db"
        )
        for ((pkg, db) in healthApps) {
            val result = ShizukuHelper.execSqlite(db, "UPDATE step_table SET steps=10000 WHERE date=strftime('%Y-%m-%d','now')")
            if (result != null) {
                LogX.d("已写入 $pkg 步数数据库")
            }
        }
    }
}
