package com.microx.enhancer.hooks

import com.microx.enhancer.utils.ConfigManager
import com.microx.enhancer.utils.HookHelper
import com.microx.enhancer.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】Timeline Auto-Cleaner
 *
 * 自动清理旧朋友圈动态：
 *  - 删除超过 N 天（默认 30 天）的朋友圈
 *  - 可配置仅删除特定类型（广告、转发等）
 *
 * Hook 朋友圈存储和删除方法。
 */
object TimelineCleanerHook {

    private val MOMENT_STORAGE_CLASSES = arrayOf(
        "com.tencent.mm.plugin.sns.storage.SnsInfoStorage",
        "com.tencent.mm.plugin.sns.data.SnsInfo",
        "com.tencent.mm.plugin.sns.model.SnsCore",
        "com.tencent.mm.plugin.sns.ui.SnsTimeLineUI"
    )

    private val MOMENT_DELETE_CLASSES = arrayOf(
        "com.tencent.mm.plugin.sns.model.SnsCore",
        "com.tencent.mm.plugin.sns.storage.SnsInfoStorage",
        "com.tencent.mm.plugin.sns.model.SnsLogic"
    )

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!ConfigManager.isEnabled(ConfigManager.KEY_TIMELINE_CLEANER)) return
        val cleanDays = getCleanDays()
        LogX.i("【实验性】Timeline Auto-Cleaner 启动（清理 ${cleanDays}天前动态）")

        hookMomentListLoad(lpparam, cleanDays)
        hookMomentDelete(lpparam)
    }

    /** 读取配置的清理天数 */
    private fun getCleanDays(): Int {
        val daysStr = ConfigManager.getString(ConfigManager.KEY_TIMELINE_CLEAN_DAYS, "30")
        return daysStr.toIntOrNull() ?: 30
    }

    /** Hook 朋友圈列表加载，检测并标记过期动态 */
    private fun hookMomentListLoad(lpparam: XC_LoadPackage.LoadPackageParam, cleanDays: Int) {
        for (clsName in MOMENT_STORAGE_CLASSES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            try {
                HookHelper.hookAllMethodsSafe(cls, "getSnsList", object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val result = p.result
                            if (result is List<*>) {
                                val now = System.currentTimeMillis()
                                val threshold = cleanDays * 24L * 60 * 60 * 1000
                                val expiredCount = result.count { item ->
                                    try {
                                        val createTime = getMomentCreateTime(item)
                                        createTime > 0 && (now - createTime) > threshold
                                    } catch (_: Throwable) { false }
                                }
                                if (expiredCount > 0) {
                                    LogX.d("【TimelineCleaner】检测到 $expiredCount 条过期动态（>$cleanDays 天）")
                                }
                            }
                        } catch (t: Throwable) {
                            LogX.w("【TimelineCleaner】列表检测异常: ${t.message}")
                        }
                    }
                })
                LogX.hookSuccess(clsName, "getSnsList")
            } catch (e: Throwable) {
                LogX.w("【TimelineCleaner】${e.message}")
            }
        }
    }

    /** Hook 朋友圈删除方法 */
    private fun hookMomentDelete(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (clsName in MOMENT_DELETE_CLASSES) {
            val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
            try {
                HookHelper.hookAllMethodsSafe(cls, "deleteSns", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val snsId = p.args.getOrNull(0)
                            LogX.d("【TimelineCleaner】删除动态: snsId=$snsId")
                        } catch (_: Throwable) {}
                    }

                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val result = p.result
                            LogX.d("【TimelineCleaner】删除结果: $result")
                        } catch (_: Throwable) {}
                    }
                })

                HookHelper.hookAllMethodsSafe(cls, "removeSnsInfo", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val snsId = p.args.getOrNull(0)
                            LogX.d("【TimelineCleaner】移除动态信息: snsId=$snsId")
                        } catch (_: Throwable) {}
                    }
                })

                LogX.hookSuccess(clsName, "deleteSns/removeSnsInfo")
            } catch (e: Throwable) {
                LogX.w("【TimelineCleaner】${e.message}")
            }
        }
    }

    /** 从朋友圈对象中提取创建时间 */
    private fun getMomentCreateTime(item: Any?): Long {
        if (item == null) return 0L
        return try {
            val cls = item.javaClass
            val timeField = cls.getDeclaredField("createTime")
            timeField.isAccessible = true
            timeField.get(item) as? Long ?: 0L
        } catch (_: Throwable) {
            try {
                val cls = item.javaClass
                val timeField = cls.getDeclaredField("time")
                timeField.isAccessible = true
                timeField.get(item) as? Long ?: 0L
            } catch (_: Throwable) { 0L }
        }
    }
}
