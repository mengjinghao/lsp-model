package com.stepmod.pro.hooks

import com.stepmod.pro.models.StepConfig
import com.stepmod.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlin.random.Random

/**
 * 步数上报 Hook（基础功能）
 *
 * 拦截各运动APP的步数上报方法，强制返回伪造步数。
 *
 * 多候选类名设计：
 *  - 支付宝: com.eg.android.AlipayGphone.stepRunModule / com.alipay.mobile.sportapp.*
 *  - 微信: com.tencent.mm.plugin.sport.*
 *  - 小米运动健康: com.xiaomi.hm.health.b / com.xiaomi.hm.health.data.*
 *  - 华为运动健康: com.huawei.health.sport / com.huawei.health.*
 *  - 咕咚: com.codoon.gps.*
 *  - 悦跑圈: com.joyrun.gps.*
 *  - Keep: com.keepfitness.*
 */
object StepReportHook {

    private val random = Random(System.currentTimeMillis())

    private val candidates: List<Pair<String, List<String>>> = listOf(
        "com.eg.android.AlipayGphone" to listOf(
            "com.alipay.mobile.sportapp.SportStepService",
            "com.alipay.mobile.sportapp.model.StepReportModel",
            "com.eg.android.AlipayGphone.stepRunModule.service.StepService"
        ),
        "com.tencent.mm" to listOf(
            "com.tencent.mm.plugin.sport.service.MMSportService",
            "com.tencent.mm.plugin.sport.model.SportStepInfo",
            "com.tencent.mm.plugin.sport.service.StepReportHelper"
        ),
        "com.xiaomi.hm.health" to listOf(
            "com.xiaomi.hm.health.b.a.b",
            "com.xiaomi.hm.health.data.model.StepData",
            "com.xiaomi.hm.health.sport.StepReportService"
        ),
        "com.huawei.health" to listOf(
            "com.huawei.health.sport.StepReportManager",
            "com.huawei.health.data.model.StepData",
            "com.huawei.health.b.model.SportStepInfo"
        ),
        "com.codoon.gps" to listOf(
            "com.codoon.gps.service.StepReportService",
            "com.codoon.gps.model.StepData"
        ),
        "com.joyrun.gps" to listOf(
            "com.joyrun.gps.service.StepReportService",
            "com.joyrun.gps.model.StepData"
        ),
        "com.keepfitness" to listOf(
            "com.keepfitness.sport.StepReportService",
            "com.keepfitness.model.StepData"
        )
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        if (!cfg.stepModifyEnabled) return
        LogX.i("步数上报 Hook 启动 | pkg=${lpparam.packageName}")
        hookStepReportMethods(lpparam, cfg)
    }

    private fun hookStepReportMethods(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        val pkg = lpparam.packageName
        val targetClasses = candidates.firstOrNull { it.first == pkg }?.second ?: emptyList()
        if (targetClasses.isEmpty()) {
            tryHookGenericReport(lpparam, cfg)
            return
        }

        var hitCount = 0
        for (clsName in targetClasses) {
            try {
                val cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader) ?: continue
                hookAllIntLongMethods(cls, cfg)
                hitCount++
                LogX.d("命中候选类: $clsName")
            } catch (e: Exception) { LogX.w("候选类 $clsName 异常: ${e.message}") }
        }
        if (hitCount == 0) {
            tryHookGenericReport(lpparam, cfg)
        }
    }

    private fun hookAllIntLongMethods(cls: Class<*>, cfg: StepConfig) {
        val methodNames = listOf(
            "setStep", "setSteps", "uploadStep", "reportStep",
            "commitStep", "saveStep", "updateStep", "addStep",
            "setStepCount", "setStepData"
        )
        for (mName in methodNames) {
            try {
                val methods = cls.declaredMethods.filter { it.name == mName }
                if (methods.isEmpty()) continue
                for (m in methods) {
                    val paramTypes = m.parameterTypes
                    try {
                        XposedHelpers.findAndHookMethod(cls, mName, *paramTypes.map { it as Any }.toTypedArray(),
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(p: MethodHookParam) {
                                    try {
                                        val fakeStep = computeFakeStep(cfg)
                                        for (i in p.args.indices) {
                                            val arg = p.args[i]
                                            if (arg is Int) {
                                                p.args[i] = fakeStep
                                                LogX.d("$mName 替换 Int 参数为 $fakeStep")
                                                break
                                            } else if (arg is Long) {
                                                p.args[i] = fakeStep.toLong()
                                                LogX.d("$mName 替换 Long 参数为 $fakeStep")
                                                break
                                            }
                                        }
                                    } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                                }
                            })
                        LogX.hookSuccess(cls.simpleName, mName)
                    } catch (e: Exception) { LogX.w("hook ${cls.name}.$mName 失败: ${e.message}") }
                }
            } catch (e: Exception) { LogX.w("异常: ${e.message}") }
        }
    }

    private fun tryHookGenericReport(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        try {
            val fallbackNames = listOf(
                "com.step.sdk.StepReportService",
                "com.sport.common.StepData"
            )
            for (name in fallbackNames) {
                val cls = XposedHelpers.findClassIfExists(name, lpparam.classLoader) ?: continue
                hookAllIntLongMethods(cls, cfg)
                LogX.d("兜底候选类命中: $name")
            }
            // 兜底 Hook：SensorManager.getSensorList(int)
            val smCls = XposedHelpers.findClassIfExists(
                "android.hardware.SensorManager", lpparam.classLoader) ?: return
            XposedHelpers.findAndHookMethod(smCls, "getSensorList",
                Int::class.javaPrimitiveType, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val type = p.args[0] as? Int ?: return
                            if (type == 18 || type == 19) {
                                LogX.d("getSensorList 命中 step type=$type (兜底)")
                            }
                        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("SensorManager", "getSensorList(fallback)")
        } catch (e: Exception) {
            LogX.hookFailed("StepReportHook", "fallback", e)
        }
    }

    private fun computeFakeStep(cfg: StepConfig): Int {
        val fl = if (cfg.randomFluctuation > 0) random.nextInt(-cfg.randomFluctuation, cfg.randomFluctuation + 1) else 0
        return (cfg.customSteps + fl).coerceAtLeast(0)
    }
}
