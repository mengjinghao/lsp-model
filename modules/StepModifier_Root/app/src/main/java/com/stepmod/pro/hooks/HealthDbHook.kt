package com.stepmod.pro.hooks

import com.stepmod.pro.models.StepConfig
import com.stepmod.pro.utils.LogX
import com.stepmod.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 健康数据库直接操作 Hook（Root 版独有）
 *
 * 通过 Shizuku 直接操作健康 APP 数据库：
 *  - sqlite3 Google Fit 数据库写入步数
 *  - sqlite3 华为健康数据库写入步数
 *  - 直接写入小米健康 SharedPreferences
 *  - 内核级健康数据注入
 *
 * 硬性限制：
 *  - 必须 ShizukuHelper.isShizukuAvailable()
 *  - 需要 sqlite3 二进制
 *  - 全部 try-catch 保护
 */
object HealthDbHook {

    private var isApplied = false

    private val healthDbPaths = listOf(
        "/data/data/com.google.android.apps.fitness/databases/fitness.db",
        "/data/data/com.huawei.health/databases/health_db",
        "/data/data/com.xiaomi.hm.health/databases/health_db"
    )

    private val sharedPrefsFiles = listOf(
        "/data/data/com.xiaomi.hm.health/shared_prefs/health_prefs.xml",
        "/data/data/com.huawei.health/shared_prefs/health_prefs.xml",
        "/data/data/com.google.android.apps.fitness/shared_prefs/fitness_prefs.xml"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        if (!cfg.healthDbDirectEnabled) {
            LogX.d("HealthDbHook 未启用，跳过")
            return
        }
        if (isApplied) return

        LogX.i("HealthDbHook 启动：健康数据库直接操作")

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        isApplied = true
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku 不可用，跳过健康数据库操作")
                            return
                        }
                        injectHealthData(cfg)
                    }
                })
            LogX.hookSuccess("Application", "onCreate->HealthDbHook")
        } catch (e: Throwable) {
            LogX.e("HealthDbHook Application.onCreate Hook 异常", e)
        }
    }

    private fun injectHealthData(cfg: StepConfig) {
        for (dbPath in healthDbPaths) {
            try {
                val checkDb = ShizukuHelper.execShell("test -f $dbPath && echo exists 2>/dev/null")
                if (checkDb?.contains("exists") != true) continue

                val updateSql = buildString {
                    append("UPDATE step_records SET steps=${cfg.customSteps}, ")
                    append("distance=${cfg.customSteps * 0.7}, ")
                    append("calories=${cfg.customSteps * 0.04}, ")
                    append("timestamp=strftime('%s','now')*1000 ")
                    append("WHERE date >= date('now','-1 day');")
                }
                val result = ShizukuHelper.execShell(
                    "sqlite3 $dbPath \"$updateSql\" 2>&1"
                )
                LogX.d("sqlite3 UPDATE [$dbPath]: $result")
            } catch (e: Throwable) { LogX.w("[$dbPath] 数据库操作异常: ${e.message}") }
        }

        for (prefPath in sharedPrefsFiles) {
            try {
                val checkFile = ShizukuHelper.execShell("test -f $prefPath && echo exists 2>/dev/null")
                if (checkFile?.contains("exists") != true) continue

                val sedCmd = "sed -i 's|<int name=\"today_steps\" value=\"[0-9]*\"|<int name=\"today_steps\" value=\"${cfg.customSteps}\"|g' $prefPath"
                val result = ShizukuHelper.execShell("$sedCmd 2>&1")
                LogX.d("SharedPrefs 写入 [$prefPath]: $result")
            } catch (e: Throwable) { LogX.w("[$prefPath] SharedPrefs 操作异常: ${e.message}") }
        }

        LogX.i("HealthDbHook: 健康数据库步数注入完成")
    }

    fun injectGoogleFitSteps(steps: Int): Boolean {
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) return false
            val dbPath = "/data/data/com.google.android.apps.fitness/databases/fitness.db"
            val sql = "UPDATE step_records SET steps=$steps WHERE date >= date('now','-1 day');"
            ShizukuHelper.execShell("sqlite3 $dbPath \"$sql\" 2>&1") != null
        } catch (e: Throwable) {
            LogX.e("Google Fit 步数注入异常: $steps", e)
            false
        }
    }

    fun injectHuaweiHealthSteps(steps: Int): Boolean {
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) return false
            val dbPath = "/data/data/com.huawei.health/databases/health_db"
            val sql = "UPDATE step_records SET steps=$steps WHERE date >= date('now','-1 day');"
            ShizukuHelper.execShell("sqlite3 $dbPath \"$sql\" 2>&1") != null
        } catch (e: Throwable) {
            LogX.e("华为健康步数注入异常: $steps", e)
            false
        }
    }

    fun queryHealthDb(dbPath: String, sql: String): String? {
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) return null
            ShizukuHelper.execShell("sqlite3 $dbPath \"$sql\" 2>&1")
        } catch (e: Throwable) { null }
    }

    fun release() {
        isApplied = false
    }
}
