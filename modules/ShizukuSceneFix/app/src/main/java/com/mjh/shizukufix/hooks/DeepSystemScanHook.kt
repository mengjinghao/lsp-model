package com.mjh.shizukufix.hooks

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.mjh.shizukufix.models.ShizukuFixConfig
import com.mjh.shizukufix.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 【实验性】Deep System Scanner
 *
 * 扫描所有已安装应用，检测：
 *  - 请求 Shizuku 权限但未在授权列表中的应用
 *  - Shizuku 变体应用（Rikka、Sui、SAI 等 fork）
 *  - 存在包可见性问题的应用
 *
 * 触发时机：Shizuku 进程 Application.onCreate 后延迟 3 秒执行。
 * 结果以 diagnostics JSON 字符串输出到日志。
 */
object DeepSystemScanHook {

    private val SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23"
    private val SHIZUKU_VARIANT_SIGNATURES = listOf(
        "shizuku", "rikka", "sui", "sai", "shell",
        "adb", "privileged", "manager", "server", "suda"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: ShizukuFixConfig) {
        if (!cfg.deepSystemScanEnabled) return
        LogX.i("【实验性】Deep System Scanner 启动")

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val ctx = p.thisObject as? android.content.Context ?: return
                        Thread {
                            try {
                                Thread.sleep(3000)
                                val result = performDeepScan(ctx)
                                LogX.i("【DeepScan】Diagnostics Result:\n$result")
                            } catch (t: Throwable) {
                                LogX.e("【DeepScan】扫描异常", t)
                            }
                        }.start()
                    }
                })
            LogX.hookSuccess("DeepSystemScan", "Application.onCreate")
        } catch (e: Throwable) {
            LogX.hookFailed("DeepSystemScan", "Application.onCreate", e)
        }
    }

    private fun performDeepScan(ctx: android.content.Context): String {
        val pm = ctx.packageManager
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"scanner\": \"DeepSystemScan\",")
        sb.appendLine("  \"timestamp\": ${System.currentTimeMillis()},")

        val allPackages = try {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA)
        } catch (e: Throwable) {
            LogX.e("【DeepScan】获取包列表失败", e)
            sb.appendLine("  \"error\": \"${e.message}\"")
            sb.appendLine("}")
            return sb.toString()
        }

        val shizukuPermissionApps = mutableListOf<String>()
        val variantCandidates = mutableListOf<String>()
        val visibilityIssues = mutableListOf<String>()
        val shizukuRelated = mutableListOf<String>()

        for (pkg in allPackages) {
            val pkgName = pkg.packageName ?: continue
            val lowerName = pkgName.lowercase()

            // 检测 Shizuku 变体
            if (SHIZUKU_VARIANT_SIGNATURES.any { sig -> lowerName.contains(sig) }) {
                variantCandidates.add(pkgName)
            }

            // 检测请求 Shizuku 权限的应用
            if (pkg.requestedPermissions != null &&
                SHIZUKU_PERMISSION in pkg.requestedPermissions) {
                shizukuPermissionApps.add(pkgName)
                if (lowerName !in variantCandidates.map { it.lowercase() }) {
                    shizukuRelated.add(pkgName)
                }
            }

            // 检测包可见性问题（系统应用标志异常等）
            try {
                if ((pkg.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (pkg.applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) {
                    // 系统应用，跳过
                }
                if (pkg.applicationInfo.enabled &&
                    (pkg.applicationInfo.flags and ApplicationInfo.FLAG_INSTALLED) != 0) {
                    // 正常安装
                } else {
                    visibilityIssues.add(pkgName)
                }
            } catch (_: Throwable) {
                visibilityIssues.add(pkgName)
            }
        }

        // Shizuku 变体
        sb.append("  \"shizuku_variants\": [")
        sb.append(variantCandidates.joinToString(",") { "\"$it\"" })
        sb.appendLine("],")

        // Shizuku 权限请求应用
        sb.append("  \"shizuku_permission_apps\": [")
        sb.append(shizukuPermissionApps.joinToString(",") { "\"$it\"" })
        sb.appendLine("],")

        // 相关但不在已知列表中
        sb.append("  \"potential_missing\": [")
        sb.append(shizukuRelated.filter { it !in variantCandidates }.joinToString(",") { "\"$it\"" })
        sb.appendLine("],")

        // 可见性问题
        sb.append("  \"visibility_issues\": [")
        sb.append(visibilityIssues.take(20).joinToString(",") { "\"$it\"" })
        sb.appendLine("],")

        // 统计
        sb.appendLine("  \"summary\": {")
        sb.appendLine("    \"total_packages\": ${allPackages.size},")
        sb.appendLine("    \"shizuku_variants_count\": ${variantCandidates.size},")
        sb.appendLine("    \"shizuku_permission_count\": ${shizukuPermissionApps.size},")
        sb.appendLine("    \"visibility_issues_count\": ${visibilityIssues.size}")
        sb.appendLine("  }")
        sb.appendLine("}")

        return sb.toString()
    }
}
