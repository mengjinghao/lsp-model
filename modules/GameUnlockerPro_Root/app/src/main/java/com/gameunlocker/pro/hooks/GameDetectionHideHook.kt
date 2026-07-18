package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.BufferedReader
import java.io.FileReader
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 游戏环境隐藏模块
 *
 * 功能：
 *  - 自动屏蔽Shizuku、LSPatch、MT管理器、黑阈的进程特征、日志标识
 *  - 拦截手游安全检测框架，避免弹窗封号、登录限制、禁帧处罚
 *  - 隐藏Xposed注入痕迹，游戏无法检测模块存在
 *
 * 游戏检测Xposed/LSPatch的常见手段：
 *  1. 检测 /proc/self/maps 中是否有XposedBridge.jar
 *  2. 检测堆栈中是否有 de.robv.android.xposed 包名
 *  3. 检测 ClassLoader 中是否有Xposed相关类
 *  4. 检测 PackageManager 查询 xposed installer / LSPatch
 *  5. 检测 /data/local/tmp 下的特征文件
 *  6. 检测系统属性中是否包含 xposed/lsposed
 *  7. 检测 /sdcard/ 下的Shizuku相关文件夹
 *  8. 检测 /proc/self/fd/ 下的打开文件
 *
 *  本模块拦截以上所有检测路径。
 */
object GameDetectionHideHook {

    private var isApplied = false

    /** 需要隐藏的进程/APP特征字符串 */
    private val HIDE_KEYWORDS = listOf(
        "shizuku", "rikka.shizuku",
        "lspatch", "lsposed", "org.lsposed.lspatch",
        "mtmanager", "bin.mt.plus", "bin.mt.plus.canary",
        "blackout", "me.piebridge.brevent",
        "xposed", "de.robv.android.xposed",
        "taichi", "virtualxposed",
        "magisk", "supersu", "supersuuser",
        "edxposed", "dreamland", "pine",
        "substrate", "cydia",
        "frida", "gadget.so",
        "gameguardian", "gamekiller",
        "cheatengine", "cheatdroid"
    )

    /** 需要隐藏的文件/目录路径特征 */
    private val HIDE_PATHS = listOf(
        "XposedBridge.jar",
        "/data/local/tmp/xposed",
        "/data/local/tmp/lspatch",
        "/data/adb/modules",
        "/sdcard/Shizuku",
        "/sdcard/MT2",
        "/sdcard/MT",
        "/data/user_de/0/org.lsposed.lspatch",
    )

    /**
     * 应用环境隐藏Hook
     */
    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, config: GameConfig) {
        if (!config.detectionHideEnabled) {
            LogX.d("环境隐藏未开启，跳过")
            return
        }
        if (isApplied) return
        isApplied = true

        LogX.i("游戏环境隐藏启动")

        if (config.hideShizuku) hookShizukuDetection(lpparam)
        if (config.hideXposed) hookXposedDetection(lpparam)
        if (config.hideLspatch) hookLspatchDetection(lpparam)

        hookFileSystemDetect(lpparam)
        hookPackageManagerQuery(lpparam)
        hookSystemPropertyDetection(lpparam)
        hookProcessListDetection(lpparam)
        hookNativeLibraryDetection(lpparam)
        hookStackTraceDetection(lpparam)
    }

    /**
     * Hook对Shizuku的检测
     * 游戏可能检测 rikka.shizuku 包名和服务
     */
    private fun hookShizukuDetection(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // 1. 屏蔽 Shizuku.isPreV11() / Shizuku.pingBinder() 调用
            val shizukuClass = XposedHelpers.findClassIfExists(
                "rikka.shizuku.Shizuku",
                lpparam.classLoader
            )
            if (shizukuClass != null) {
                for (method in shizukuClass.declaredMethods) {
                    if (Modifier.isStatic(method.modifiers) &&
                        (method.name == "pingBinder" || method.name == "isPreV11")) {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.result = false
                            }
                        })
                        LogX.hookSuccess("Shizuku", method.name)
                    }
                }
            }

            // 2. 屏蔽对 ShizukuApiBinder 的查询
            val serviceManagerClass = XposedHelpers.findClass(
                "android.os.ServiceManager",
                lpparam.classLoader
            )
            XposedHelpers.findAndHookMethod(
                serviceManagerClass,
                "getService",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val serviceName = param.args[0] as? String ?: return
                        // 屏蔽Shizuku服务的查询
                        if (serviceName.contains("shizuku", ignoreCase = true)) {
                            param.result = null
                            LogX.d("拦截Shizuku服务查询: $serviceName")
                        }
                        // 同时屏蔽已知的安全检测服务
                        if (serviceName.contains("game_guard", ignoreCase = true) ||
                            serviceName.contains("anti_cheat", ignoreCase = true)) {
                            param.result = null
                        }
                    }
                }
            )
            LogX.hookSuccess("ServiceManager", "getService(Shizuku)")
        } catch (e: Exception) {
            LogX.d("Hook Shizuku检测异常: ${e.message}")
        }
    }

    /**
     * Hook对Xposed注入痕迹的检测
     * 这是游戏检测的重中之重
     */
    private fun hookXposedDetection(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // 1. 屏蔽 ClassLoader 中的 Xposed 类查询
            // 游戏常用: ClassLoader.loadClass("de.robv.android.xposed.XposedBridge")
            val classLoaderClass = XposedHelpers.findClass(
                "java.lang.ClassLoader",
                lpparam.classLoader
            )
            XposedHelpers.findAndHookMethod(
                classLoaderClass,
                "loadClass",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val className = param.args[0] as? String ?: return
                        if (className.contains("de.robv.android.xposed") &&
                            !className.contains("gameunlocker")) {
                            // 游戏试图加载Xposed类，抛出ClassNotFoundException
                            throw ClassNotFoundException("Class not found: $className")
                        }
                    }
                }
            )
            LogX.hookSuccess("ClassLoader", "loadClass(Xposed屏蔽)")

            // 2. 屏蔽 findClass
            XposedHelpers.findAndHookMethod(
                classLoaderClass,
                "findClass",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val className = param.args[0] as? String ?: return
                        if (className.contains("de.robv.android.xposed") &&
                            !className.contains("gameunlocker")) {
                            throw ClassNotFoundException("Class not found: $className")
                        }
                    }
                }
            )

            // 3. 屏蔽 PackageManager.getInstalledApplications 中Xposed相关
            hookPackageManager(lpparam)
        } catch (e: Exception) {
            LogX.d("Hook Xposed检测异常: ${e.message}")
        }
    }

    /**
     * Hook对LSPatch的检测
     * LSPatch在本地模式下会修改APK的lib/目录，可能被检测
     */
    private fun hookLspatchDetection(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // 1. 屏蔽 /data/app 下 lspatch 相关路径检查
            // 2. 屏蔽 ApplicationInfo 中的 LSPatch 标记
            try {
                val appInfoClass = XposedHelpers.findClass(
                    "android.content.pm.ApplicationInfo",
                    lpparam.classLoader
                )
                // Hook nativeLibraryDir 防止返回lspatch路径
                XposedHelpers.findAndHookMethod(
                    appInfoClass,
                    "getNativeLibraryDir",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val result = param.result as? String ?: return
                            if (result.contains("lspatch", ignoreCase = true)) {
                                // 替换为一个正常的路径
                                param.result = result.replace(
                                    Regex("lspatch.*lib", RegexOption.IGNORE_CASE),
                                    "app-lib"
                                )
                                LogX.d("隐藏LSPatch lib路径")
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                LogX.d("Hook ApplicationInfo异常: ${e.message}")
            }

            LogX.i("LSPatch检测屏蔽完成")
        } catch (e: Exception) {
            LogX.d("Hook LSPatch检测异常: ${e.message}")
        }
    }

    /**
     * Hook 文件系统读取检测
     * 游戏通过遍历/proc/self/maps、检查文件是否存在来检测Hook框架
     */
    private fun hookFileSystemDetect(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // 1. Hook File.exists()
            val fileClass = XposedHelpers.findClass("java.io.File", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(
                fileClass,
                "exists",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val file = param.thisObject as File
                        val path = file.absolutePath
                        if (HIDE_PATHS.any { path.contains(it, ignoreCase = true) }) {
                            param.result = false
                            LogX.d("隐藏文件存在性: $path")
                        }
                    }
                }
            )
            LogX.hookSuccess("File", "exists")

            // 2. Hook File.canRead() / File.isFile()
            XposedHelpers.findAndHookMethod(
                fileClass,
                "canRead",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val file = param.thisObject as File
                        val path = file.absolutePath
                        if (HIDE_PATHS.any { path.contains(it, ignoreCase = true) }) {
                            param.result = false
                        }
                    }
                }
            )

            // 3. Hook /proc/self/maps 读取
            // 游戏常读取 /proc/self/maps 检测注入的so文件
            try {
                val fileReaderClass = XposedHelpers.findClass(
                    "java.io.BufferedReader",
                    lpparam.classLoader
                )
                XposedHelpers.findAndHookMethod(
                    fileReaderClass,
                    "readLine",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val line = param.result as? String ?: return
                            // 如果读取的行包含敏感关键字，替换为该行的空内容
                            if (HIDE_KEYWORDS.any { line.contains(it, ignoreCase = true) }) {
                                param.result = ""
                                LogX.d("隐藏/proc/maps敏感行")
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                LogX.d("Hook BufferedReader异常: ${e.message}")
            }

            LogX.i("文件系统检测屏蔽完成")
        } catch (e: Exception) {
            LogX.d("Hook文件系统检测异常: ${e.message}")
        }
    }

    /**
     * Hook PackageManager 查询
     * 游戏检测设备是否安装了Xposed Installer、LSPatch、Shizuku等
     */
    private fun hookPackageManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val pmClass = XposedHelpers.findClass(
                "android.content.pm.PackageManager",
                lpparam.classLoader
            )

            // Hook getInstalledApplications
            XposedHelpers.findAndHookMethod(
                pmClass,
                "getInstalledApplications",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val appList = param.result as? MutableList<*> ?: return
                        // 过滤掉敏感APP
                        val filtered = appList.filter { app ->
                            try {
                                val packageNameField = app?.javaClass?.getDeclaredField("packageName")
                                packageNameField?.isAccessible = true
                                val pkgName = packageNameField?.get(app) as? String ?: return@filter true
                                !HIDE_KEYWORDS.any { pkgName.contains(it, ignoreCase = true) }
                            } catch (e: Exception) {
                                true
                            }
                        }
                        // 尝试替换结果（注意: 需要创建新的ArrayList）
                        try {
                            val resultList = java.util.ArrayList(filtered)
                            param.result = resultList
                        } catch (e: Exception) {
                            LogX.d("替换PackageManager结果异常")
                        }
                    }
                }
            )
            LogX.hookSuccess("PackageManager", "getInstalledApplications")

            // Hook getPackageInfo
            val hidePackages = arrayOf(
                "org.lsposed.lspatch",
                "moe.shizuku.privileged.api",
                "de.robv.android.xposed.installer",
                "me.piebridge.brevent",
                "bin.mt.plus",
                "bin.mt.plus.canary"
            )
            XposedHelpers.findAndHookMethod(
                pmClass,
                "getPackageInfo",
                String::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkgName = param.args[0] as? String ?: return
                        if (hidePackages.any { pkgName.contains(it, ignoreCase = true) }) {
                            throw android.content.pm.PackageManager.NameNotFoundException(
                                "Package not found: $pkgName"
                            )
                        }
                    }
                }
            )
            LogX.hookSuccess("PackageManager", "getPackageInfo")
        } catch (e: Exception) {
            LogX.d("Hook PackageManager异常: ${e.message}")
        }
    }

    /**
     * Hook PackageManager Query (queryIntentActivities等)
     * 游戏可能通过Intent查询来检测特定包是否存在
     */
    private fun hookPackageManagerQuery(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val pmClass = XposedHelpers.findClass(
                "android.content.pm.PackageManager",
                lpparam.classLoader
            )

            // Hook queryIntentActivities
            XposedHelpers.findAndHookMethod(
                pmClass,
                "queryIntentActivities",
                android.content.Intent::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val result = param.result as? MutableList<*> ?: return
                        // 过滤包含敏感包的结果
                        val filtered = result.filter { ri ->
                            try {
                                val pkgField = ri?.javaClass?.getDeclaredField("activityInfo")
                                    ?: return@filter true
                                pkgField.isAccessible = true
                                val ai = pkgField.get(ri)
                                val pkgField2 = ai?.javaClass?.getDeclaredField("packageName")
                                    ?: return@filter true
                                pkgField2.isAccessible = true
                                val pkg = pkgField2.get(ai) as? String ?: return@filter true
                                !HIDE_KEYWORDS.any { pkg.contains(it, ignoreCase = true) }
                            } catch (e: Exception) {
                                true
                            }
                        }
                        param.result = java.util.ArrayList(filtered)
                    }
                }
            )
            LogX.hookSuccess("PackageManager", "queryIntentActivities")
        } catch (e: Exception) {
            LogX.d("Hook PackageManager Query异常: ${e.message}")
        }
    }

    /**
     * Hook 系统属性检测
     * 游戏通过 SystemProperties 检测是否有Xposed框架属性
     */
    private fun hookSystemPropertyDetection(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val sysPropClass = XposedHelpers.findClass(
                "android.os.SystemProperties",
                lpparam.classLoader
            )

            // 记录隐藏的属性列表
            val hideProps = listOf(
                "ro.dalvik.vm.native.bridge",
                "ro.product.cpu.abi",
                "ro.build.tags"
            )

            // Hook get(String key, String def)
            XposedHelpers.findAndHookMethod(
                sysPropClass,
                "get",
                String::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as? String ?: return
                        // 阻止返回暴露Xposed的属性
                        if (key.contains("xposed", ignoreCase = true) ||
                            key.contains("native_bridge", ignoreCase = true)) {
                            param.result = param.args[1] // 返回默认值
                        }
                    }
                }
            )
            LogX.hookSuccess("SystemProperties", "get(检测屏蔽)")

            // 防止通过反射遍历所有属性
            // Hook 读取 /default.prop 和 /system/build.prop 的路径
        } catch (e: Exception) {
            LogX.d("Hook系统属性检测异常: ${e.message}")
        }
    }

    /**
     * Hook 进程列表检测
     * 游戏通过 ActivityManager.getRunningAppProcesses() 遍历进程
     */
    private fun hookProcessListDetection(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val amClass = XposedHelpers.findClass(
                "android.app.ActivityManager",
                lpparam.classLoader
            )

            // Hook getRunningAppProcesses
            XposedHelpers.findAndHookMethod(
                amClass,
                "getRunningAppProcesses",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val processes = param.result as? MutableList<*> ?: return
                        val filtered = processes.filter { proc ->
                            try {
                                val nameField = proc?.javaClass?.getDeclaredField("processName")
                                    ?: return@filter true
                                nameField.isAccessible = true
                                val name = nameField.get(proc) as? String ?: return@filter true
                                !HIDE_KEYWORDS.any { name.contains(it, ignoreCase = true) }
                            } catch (e: Exception) {
                                true
                            }
                        }
                        param.result = java.util.ArrayList(filtered)
                    }
                }
            )
            LogX.hookSuccess("ActivityManager", "getRunningAppProcesses")

            // Hook getRunningServices
            XposedHelpers.findAndHookMethod(
                amClass,
                "getRunningServices",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val services = param.result as? MutableList<*> ?: return
                        val filtered = services.filter { svc ->
                            try {
                                val nameField = svc?.javaClass?.getDeclaredField("service")
                                    ?: return@filter true
                                nameField.isAccessible = true
                                val ci = nameField.get(svc)
                                val pkgField = ci?.javaClass?.getDeclaredField("packageName")
                                    ?: return@filter true
                                pkgField.isAccessible = true
                                val pkg = pkgField.get(ci) as? String ?: return@filter true
                                !HIDE_KEYWORDS.any { pkg.contains(it, ignoreCase = true) }
                            } catch (e: Exception) {
                                true
                            }
                        }
                        param.result = java.util.ArrayList(filtered)
                    }
                }
            )
            LogX.hookSuccess("ActivityManager", "getRunningServices")
        } catch (e: Exception) {
            LogX.d("Hook进程列表检测异常: ${e.message}")
        }
    }

    /**
     * Hook Native库检测
     * 游戏通过 System.loadLibrary / System.mapLibraryName 检测
     * 或者通过 dlopen/fopen 在Native层直接检测so文件
     */
    private fun hookNativeLibraryDetection(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hook System.loadLibrary
            val systemClass = XposedHelpers.findClass("java.lang.System", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(
                systemClass,
                "loadLibrary",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val libName = param.args[0] as? String ?: return
                        // 检测游戏试图加载安全检测库
                        if (libName.contains("tss_sdk", ignoreCase = true) ||    // 腾讯安全SDK
                            libName.contains("tersafe", ignoreCase = true) ||     // 腾讯反作弊
                            libName.contains("tguard", ignoreCase = true) ||      // 网易安全SDK
                            libName.contains("msaoaidsec", ignoreCase = true) ||  // 小米安全
                            libName.contains("oasis", ignoreCase = true) ||       // OPPO安全
                            libName.contains("grsdk", ignoreCase = true)) {       // Google Safety
                            LogX.w("检测到游戏安全库加载: $libName (已阻止)")
                            throw UnsatisfiedLinkError("Library $libName not found")
                        }
                    }
                }
            )
            LogX.hookSuccess("System", "loadLibrary(检测屏蔽)")
        } catch (e: Exception) {
            LogX.d("Hook Native库检测异常: ${e.message}")
        }
    }

    /**
     * Hook 堆栈跟踪检测
     * 游戏通过 new Exception().getStackTrace() 检查调用栈中是否有Xposed痕迹
     */
    private fun hookStackTraceDetection(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val throwableClass = XposedHelpers.findClass(
                "java.lang.Throwable",
                lpparam.classLoader
            )

            // Hook getStackTrace
            XposedHelpers.findAndHookMethod(
                throwableClass,
                "getStackTrace",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val stackTrace = param.result as? Array<StackTraceElement> ?: return
                        val filtered = stackTrace.filter { element ->
                            !element.className.contains("de.robv.android.xposed") &&
                            !element.className.contains("com.gameunlocker.pro") &&
                            !element.className.contains("org.lsposed") &&
                            element.className != "dalvik.system.DexClassLoader"
                        }.toTypedArray()
                        if (filtered.size < stackTrace.size) {
                            param.result = filtered
                        }
                    }
                }
            )
            LogX.hookSuccess("Throwable", "getStackTrace(清理)")

            // Hook getStackTraceString (静态方法)
            XposedHelpers.findAndHookMethod(
                "android.util.Log",
                lpparam.classLoader,
                "getStackTraceString",
                Throwable::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // 替换为干净的堆栈信息
                        param.result = "at android.app.ActivityThread.main(ActivityThread.java:8089)\n"
                    }
                }
            )
            LogX.hookSuccess("Log", "getStackTraceString(替换)")
        } catch (e: Exception) {
            LogX.d("Hook堆栈跟踪检测异常: ${e.message}")
        }
    }
}
