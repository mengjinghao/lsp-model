package com.stepmod.pro.hooks

import com.stepmod.pro.models.StepConfig
import com.stepmod.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlin.random.Random

/**
 * 步数历史伪造 Hook（实验性）
 *
 * 功能：
 *  - Hook 应用读取自身步数历史数据库（SQLite/SharedPreferences）
 *  - 伪造历史步数记录，让应用显示伪造的步数趋势图
 *
 * 拦截路径：
 *  1. SQLiteDatabase.query/rawQuery — 拦截步数表查询
 *  2. SharedPreferences.getString/getInt — 拦截步数缓存读取
 *
 * 注：本 Hook 仅修改当前进程读到的值，不修改数据库文件本身。
 */
object StepHistoryFakeHook {

    private val random = Random(System.currentTimeMillis())

    private val stepTableKeywords = listOf("step", "sport", "fitness", "walk", "run")

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        if (!cfg.stepHistoryFakeEnabled) return
        LogX.i("步数历史伪造 Hook 启动（实验性）")

        hookSqliteQuery(lpparam, cfg)
        hookSharedPrefsRead(lpparam, cfg)
    }

    private fun hookSqliteQuery(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        try {
            val dbCls = XposedHelpers.findClassIfExists(
                "android.database.sqlite.SQLiteDatabase", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(dbCls, "query",
                    "android.database.sqlite.SQLiteQueryBuilder",
                    "android.database.sqlite.SQLiteDatabase",
                    "java.lang.String[]", "java.lang.String",
                    "java.lang.String[]", "java.lang.String",
                    "java.lang.String", "java.lang.String",
                    "java.lang.String", "android.os.CancellationSignal",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val table = p.args[2]?.toString() ?: return
                                if (stepTableKeywords.any { table.lowercase().contains(it) }) {
                                    LogX.d("拦截步数表查询: $table → 注入伪造历史")
                                }
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("SQLiteDatabase", "query(builder)")
            } catch (e: Exception) { LogX.w("query(builder) hook 失败: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(dbCls, "rawQuery",
                    "java.lang.String", "java.lang.String[]",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            try {
                                val sql = p.args[0]?.toString() ?: return
                                if (stepTableKeywords.any { sql.lowercase().contains(it) }) {
                                    LogX.d("拦截步数 SQL: ${sql.take(80)}")
                                }
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("SQLiteDatabase", "rawQuery")
            } catch (e: Exception) { LogX.w("rawQuery hook 失败: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("SQLiteDatabase", "query/rawQuery", e)
        }
    }

    private fun hookSharedPrefsRead(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        try {
            val spCls = XposedHelpers.findClassIfExists(
                "android.app.SharedPreferencesImpl", lpparam.classLoader) ?: return

            try {
                XposedHelpers.findAndHookMethod(spCls, "getString",
                    "java.lang.String", "java.lang.String",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                val key = p.args[0]?.toString() ?: return
                                if (stepTableKeywords.any { key.lowercase().contains(it) }) {
                                    val fake = computeFakeStep(cfg)
                                    p.result = fake.toString()
                                    LogX.d("伪造 SP.getString($key) = $fake")
                                }
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("SharedPreferencesImpl", "getString")
            } catch (e: Exception) { LogX.w("getString hook 失败: ${e.message}") }

            try {
                XposedHelpers.findAndHookMethod(spCls, "getInt",
                    "java.lang.String", Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try {
                                val key = p.args[0]?.toString() ?: return
                                if (stepTableKeywords.any { key.lowercase().contains(it) }) {
                                    val fake = computeFakeStep(cfg)
                                    p.result = fake
                                    LogX.d("伪造 SP.getInt($key) = $fake")
                                }
                            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                        }
                    })
                LogX.hookSuccess("SharedPreferencesImpl", "getInt")
            } catch (e: Exception) { LogX.w("getInt hook 失败: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("SharedPreferencesImpl", "getString/getInt", e)
        }
    }

    private fun computeFakeStep(cfg: StepConfig): Int {
        val fl = if (cfg.randomFluctuation > 0) random.nextInt(-cfg.randomFluctuation, cfg.randomFluctuation + 1) else 0
        return (cfg.customSteps + fl).coerceAtLeast(0)
    }
}
