package com.mjh.shizukufix.hooks

import android.content.Context
import com.mjh.shizukufix.XposedLoader
import com.mjh.shizukufix.models.ShizukuFixConfig
import com.mjh.shizukufix.utils.LogX
import com.mjh.shizukufix.utils.PackageHelper
import com.mjh.shizukufix.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object ShizukuRootBridge {

    private val bridgeApplied = java.util.concurrent.atomic.AtomicBoolean(false)

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: ShizukuFixConfig) {
        if (!cfg.rootBridgeEnabled) return
        LogX.i("ShizukuRootBridge 启动 (directGrant=${cfg.rootDirectGrantEnabled}, svcRestart=${cfg.rootServiceRestartEnabled})")

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val app = p.thisObject as? Context ?: return
                        val pkg = app.packageName ?: return
                        if (!XposedLoader.DEFAULT_SHIZUKU_PACKAGES.any { pkg == it } &&
                            !ShizukuVariantDetectorHook.isShizukuProcess(pkg)) return
                        if (!bridgeApplied.compareAndSet(false, true)) return
                        Thread {
                            executeBridgeCommands(app, cfg)
                        }.start()
                    }
                })
            LogX.hookSuccess("ShizukuRootBridge", "Application.onCreate")
        } catch (e: Throwable) {
            LogX.hookFailed("ShizukuRootBridge", "Application.onCreate", e)
        }
    }

    private fun executeBridgeCommands(ctx: Context, cfg: ShizukuFixConfig) {
        val scenePkg = XposedLoader.SCENE_PACKAGE
        val sceneUid = PackageHelper.getUid(ctx, scenePkg)
        val managerPkg = findManagerPackage()

        LogX.i("[RootBridge] Scene UID=$sceneUid, Manager=$managerPkg")

        if (cfg.rootDirectGrantEnabled) {
            executeDirectPermissionGrant(scenePkg, managerPkg)
        }

        executeAuthStateRead(managerPkg)

        executePermissionNotification(scenePkg, managerPkg)

        if (cfg.rootServiceRestartEnabled) {
            executeServiceRestart(managerPkg)
        }

        executePermissionVerification(scenePkg)
    }

    private fun findManagerPackage(): String? {
        val candidate = ShizukuHelper.findShizukuManagerPackage()
        if (candidate != null) return candidate
        for (pkg in arrayOf("moe.shizuku.manager", "rikka.shizuku.manager", "moe.shizuku.privileged.api")) {
            val r = ShizukuHelper.execShell("pm list packages $pkg 2>/dev/null")
            if (r.stdout.contains("package:$pkg")) return pkg
        }
        return "moe.shizuku.manager"
    }

    private fun executeDirectPermissionGrant(scenePkg: String, managerPkg: String?) {
        LogX.i("[RootBridge] 执行直接权限授予...")
        val perms = listOf(
            "moe.shizuku.manager.permission.API_V23",
            "moe.shizuku.manager.permission.API"
        )
        for (perm in perms) {
            val result = ShizukuHelper.execShell("pm grant $scenePkg $perm 2>&1")
            LogX.i("[RootBridge] pm grant $perm => exit=${result.exitCode} out=${result.stdout} err=${result.stderr}")
        }

        if (managerPkg != null) {
            val selfGrant = ShizukuHelper.execShell("pm grant $managerPkg moe.shizuku.manager.permission.API_V23 2>&1")
            LogX.i("[RootBridge] Self-grant manager => exit=${selfGrant.exitCode}")
        }
    }

    private fun executeAuthStateRead(managerPkg: String?) {
        LogX.i("[RootBridge] 读取 Shizuku 授权状态...")
        val prefsDir = ShizukuHelper.getShizukuSharedPrefsPath()
        if (prefsDir != null) {
            val result = ShizukuHelper.execShell("cat $prefsDir/*.xml 2>/dev/null")
            if (result.exitCode == 0 && result.stdout.isNotEmpty()) {
                LogX.i("[RootBridge] Auth prefs:\n${result.stdout.take(2000)}")
            } else {
                LogX.w("[RootBridge] 无法读取授权文件: ${result.stderr}")
            }
        } else {
            LogX.w("[RootBridge] 未找到 Shizuku 数据目录")
        }

        if (managerPkg != null) {
            val authCheck = ShizukuHelper.execShell("dumpsys package $managerPkg 2>/dev/null | grep -i permission")
            if (authCheck.stdout.isNotEmpty()) {
                LogX.i("[RootBridge] Manager permissions:\n${authCheck.stdout.take(1000)}")
            }
        }
    }

    private fun executePermissionNotification(scenePkg: String, managerPkg: String?) {
        LogX.i("[RootBridge] 发送权限变更通知...")
        val actions = listOf(
            "moe.shizuku.manager.action.APPLICATION_PERMISSION_CHANGED",
            "moe.shizuku.manager.intent.action.PERMISSION_CHANGED"
        )
        for (action in actions) {
            val result = ShizukuHelper.execShell(
                "am broadcast -a $action --es package_name $scenePkg 2>&1"
            )
            LogX.i("[RootBridge] broadcast $action => exit=${result.exitCode}")
        }
    }

    private fun executeServiceRestart(managerPkg: String?) {
        LogX.i("[RootBridge] 执行服务重启...")
        val services = listOf(
            "$managerPkg/.ShizukuService",
            "$managerPkg/.service.ShizukuService",
            "moe.shizuku.privileged.api/.ShizukuService"
        )
        for (svc in services) {
            val result = ShizukuHelper.execShell("am startservice $svc 2>&1")
            LogX.i("[RootBridge] startservice $svc => exit=${result.exitCode} out=${result.stdout}")
        }

        val killResult = ShizukuHelper.execShell("kill \$(pgrep -f shizuku) 2>/dev/null; sleep 1; am startservice $managerPkg/.ShizukuService 2>&1")
        LogX.i("[RootBridge] kill+restart => exit=${killResult.exitCode}")
    }

    private fun executePermissionVerification(scenePkg: String) {
        LogX.i("[RootBridge] 验证权限传播...")
        val result = ShizukuHelper.execShell("pm list packages --uid 2>/dev/null | grep ${scenePkg.takeLast(20)}")
        LogX.i("[RootBridge] pm list packages --uid => ${result.stdout.take(500)}")

        val dumpsysResult = ShizukuHelper.execShell("dumpsys package $scenePkg 2>/dev/null | grep -A5 'requested permissions'")
        if (dumpsysResult.stdout.isNotEmpty()) {
            LogX.i("[RootBridge] Scene 权限状态:\n${dumpsysResult.stdout.take(1000)}")
        }
    }
}
