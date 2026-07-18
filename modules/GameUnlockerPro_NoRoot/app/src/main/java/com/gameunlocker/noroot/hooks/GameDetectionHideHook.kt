package com.gameunlocker.noroot.hooks

import com.gameunlocker.noroot.models.GameConfig
import com.gameunlocker.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 游戏环境检测隐藏Hook
 *
 * 硬性限制：
 *  - 全部为Java层Hook，无法拦截Native层直接dlopen/fopen的检测
 *  - LSPatch本地模式会修改APK签名和lib路径，主流游戏反作弊SDK可能检测到
 *  - 封号风险需自行承担，建议仅用于单机或轻检测游戏
 *
 * 拦截路径：
 *  1. PackageManager查询 → 隐藏Xposed/Shizuku/MT管理器等包
 *  2. ClassLoader.loadClass → 拦截 de.robv.android.xposed 类加载
 *  3. File.exists/canRead() → 隐藏特征文件存在性
 *  4. System.loadLibrary → 拦截安全检测so库加载
 *  5. ActivityManager.getRunningAppProcesses → 过滤敏感进程
 *  6. Throwable.getStackTrace → 清除堆栈中Xposed痕迹
 */
object GameDetectionHideHook {

    private val HIDE_PKGS = arrayOf(
        "org.lsposed.lspatch", "moe.shizuku.privileged.api",
        "de.robv.android.xposed.installer",
        "me.piebridge.brevent", "bin.mt.plus", "bin.mt.plus.canary"
    )

    private val HIDE_PATHS = listOf(
        "XposedBridge.jar", "/data/local/tmp/xposed",
        "/data/adb/modules", "/sdcard/Shizuku"
    )

    /** 需要阻止加载的安全检测so库 */
    private val BLOCK_LIBS = listOf(
        "tss_sdk", "tersafe", "tguard", "msaoaidsec", "oasis", "grsdk"
    )

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        if (!cfg.detectionHideEnabled) return
        LogX.i("环境隐藏启动")

        if (cfg.hideShizuku || cfg.hideXposed || cfg.hideLspatch) {
            hookPackageManager(lpparam)
            hookClassLoader(lpparam)
            hookFileSystem(lpparam)
            hookProcessList(lpparam)
            hookNativeLibs(lpparam)
            hookStackTrace(lpparam)
        }
    }

    /** PackageManager: 隐藏安装的敏感APP */
    private fun hookPackageManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val pm = XposedHelpers.findClass("android.content.pm.PackageManager", lpparam.classLoader)

            // getPackageInfo 抛出异常使游戏认为APP未安装
            XposedHelpers.findAndHookMethod(pm, "getPackageInfo",
                String::class.java, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val name = p.args[0] as String
                        if (HIDE_PKGS.any { name.contains(it, true) }) {
                            throw android.content.pm.PackageManager.NameNotFoundException()
                        }
                    }
                })

            // getInstalledApplications 过滤结果
            XposedHelpers.findAndHookMethod(pm, "getInstalledApplications",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val list = p.result as? MutableList<*> ?: return
                        val filtered = list.filter { item ->
                            try {
                                val field = item?.javaClass?.getDeclaredField("packageName")
                                field?.isAccessible = true
                                val name = field?.get(item) as? String ?: return@filter true
                                !HIDE_PKGS.any { name.contains(it, true) }
                            } catch (_: Exception) { true }
                        }
                        p.result = java.util.ArrayList(filtered)
                    }
                })
            LogX.d("PackageManager检测屏蔽完成")
        } catch (e: Exception) {
            LogX.e("PackageManager Hook异常", e)
        }
    }

    /** ClassLoader: 阻止加载Xposed相关类 */
    private fun hookClassLoader(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val cl = XposedHelpers.findClass("java.lang.ClassLoader", lpparam.classLoader)
            val hook = object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    val name = p.args[0] as? String ?: return
                    if (name.contains("de.robv.android.xposed") && !name.contains("gameunlocker")) {
                        throw ClassNotFoundException()
                    }
                }
            }
            XposedHelpers.findAndHookMethod(cl, "loadClass", String::class.java, hook)
            XposedHelpers.findAndHookMethod(cl, "findClass", String::class.java, hook)
            LogX.d("ClassLoader Xposed类加载屏蔽完成")
        } catch (e: Exception) {
            LogX.e("ClassLoader Hook异常", e)
        }
    }

    /** FileSystem: 隐藏敏感文件存在性 */
    private fun hookFileSystem(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val file = XposedHelpers.findClass("java.io.File", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(file, "exists", object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    val path = (p.thisObject as java.io.File).absolutePath
                    if (HIDE_PATHS.any { path.contains(it, true) }) p.result = false
                }
            })
            LogX.d("FileSystem检测屏蔽完成")
        } catch (e: Exception) {
            LogX.e("FileSystem Hook异常", e)
        }
    }

    /** ActivityManager: 过滤敏感进程 */
    private fun hookProcessList(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val am = XposedHelpers.findClass("android.app.ActivityManager", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(am, "getRunningAppProcesses", object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val list = p.result as? MutableList<*> ?: return
                    p.result = java.util.ArrayList(list.filter { proc ->
                        try {
                            val f = proc?.javaClass?.getDeclaredField("processName")
                            f?.isAccessible = true
                            val name = f?.get(proc) as? String ?: return@filter true
                            !HIDE_PKGS.any { name.contains(it, true) }
                        } catch (_: Exception) { true }
                    })
                }
            })
            LogX.d("进程列表检测屏蔽完成")
        } catch (e: Exception) {
            LogX.e("进程列表Hook异常", e)
        }
    }

    /** System.loadLibrary: 阻止加载安全检测so */
    private fun hookNativeLibs(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val sys = XposedHelpers.findClass("java.lang.System", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(sys, "loadLibrary", String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val lib = p.args[0] as String
                        if (BLOCK_LIBS.any { lib.contains(it, true) }) {
                            LogX.w("拦截安全库: $lib")
                            throw UnsatisfiedLinkError("Library $lib not found")
                        }
                    }
                })
            LogX.d("Native库检测屏蔽完成")
        } catch (e: Exception) {
            LogX.e("Native库Hook异常", e)
        }
    }

    /** 清除堆栈中的Xposed痕迹 */
    private fun hookStackTrace(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val t = XposedHelpers.findClass("java.lang.Throwable", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(t, "getStackTrace", object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val trace = p.result as? Array<StackTraceElement> ?: return
                    val clean = trace.filter { el ->
                        !el.className.contains("de.robv.android.xposed") &&
                        !el.className.contains("com.gameunlocker") &&
                        !el.className.contains("org.lsposed")
                    }.toTypedArray()
                    if (clean.size < trace.size) p.result = clean
                }
            })
            LogX.d("堆栈痕迹清除完成")
        } catch (e: Exception) {
            LogX.e("堆栈Hook异常", e)
        }
    }
}
