package com.microx.enhancer.utils

import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Hook辅助工具类
 * 提供安全的类查找、方法Hook、日志输出等通用功能
 * 所有操作仅作用于当前被修补的应用进程
 */
object HookHelper {

    const val TAG = "MicroXEnhancer"
    private var isDebug = false

    fun setDebug(debug: Boolean) {
        isDebug = debug
    }

    /** 调试日志：仅在debug模式输出 */
    fun logD(msg: String) {
        if (isDebug) {
            Log.d(TAG, msg)
            XposedBridge.log("[$TAG-D] $msg")
        }
    }

    /** 普通日志：始终输出到Xposed日志 */
    fun log(msg: String) {
        Log.i(TAG, msg)
        XposedBridge.log("[$TAG] $msg")
    }

    /** 错误日志 */
    fun logE(msg: String, t: Throwable? = null) {
        Log.e(TAG, msg, t)
        if (t != null) {
            XposedBridge.log("[$TAG-E] $msg: ${t.message}")
        } else {
            XposedBridge.log("[$TAG-E] $msg")
        }
    }

    /**
     * 安全查找类：尝试多个可能的类名，返回第一个找到的
     * 适用于微信/QQ版本间类名变化的情况
     *
     * @param lpparam LoadPackage参数
     * @param candidateNames 候选类名列表（按优先级排序）
     * @return 找到的Class对象，未找到返回null
     */
    fun findClassSafe(
        lpparam: XC_LoadPackage.LoadPackageParam,
        vararg candidateNames: String
    ): Class<*>? {
        for (name in candidateNames) {
            try {
                return XposedHelpers.findClass(name, lpparam.classLoader)
            } catch (e: XposedHelpers.ClassNotFoundError) {
                logD("类未找到(尝试下一个): $name")
            } catch (e: Exception) {
                logD("查找类异常: $name — ${e.message}")
            }
        }
        logE("所有候选类名均未找到: ${candidateNames.joinToString()}")
        return null
    }

    /**
     * 安全Hook方法：封装try-catch，防止单个Hook失败影响其他功能
     *
     * @param clazz 目标类
     * @param methodName 方法名
     * @param hookCallback Hook回调
     * @param paramTypes 方法参数类型（可选）
     */
    fun hookMethodSafe(
        clazz: Class<*>?,
        methodName: String,
        hookCallback: XC_MethodHook,
        vararg paramTypes: Any?
    ): Boolean {
        if (clazz == null) {
            logE("Hook失败: 目标类为空 ($methodName)")
            return false
        }
        return try {
            val params = if (paramTypes.isNotEmpty())
                paramTypes.toList().toTypedArray()
            else
                null
            val paramsStr = if (params != null)
                params.joinToString(", ") { it?.toString() ?: "null" }
            else
                "(自动匹配)"

            if (params != null) {
                XposedHelpers.findAndHookMethod(clazz, methodName, *params, hookCallback)
            } else {
                XposedHelpers.findAndHookMethod(clazz, methodName, hookCallback)
            }
            logD("Hook成功: ${clazz.name}.$methodName($paramsStr)")
            true
        } catch (e: NoSuchMethodError) {
            logE("Hook失败(方法不存在): ${clazz.name}.$methodName — ${e.message}")
            false
        } catch (e: Exception) {
            logE("Hook失败(异常): ${clazz.name}.$methodName — ${e.message}", e)
            false
        }
    }

    /**
     * Hook所有匹配名称的方法（不限参数类型）
     * 适用于方法有多个重载版本的场景
     */
    fun hookAllMethodsSafe(
        clazz: Class<*>?,
        methodName: String,
        hookCallback: XC_MethodHook
    ): Boolean {
        if (clazz == null) {
            logE("HookAll失败: 目标类为空 ($methodName)")
            return false
        }
        return try {
            XposedBridge.hookAllMethods(clazz, methodName, hookCallback)
            logD("HookAll成功: ${clazz.name}.$methodName(*)")
            true
        } catch (e: Exception) {
            logE("HookAll失败: ${clazz.name}.$methodName — ${e.message}", e)
            false
        }
    }

    /** 判断当前进程是否为微信主进程 */
    fun isWeChatMainProcess(processName: String): Boolean {
        return processName == "com.tencent.mm" ||
                processName == "com.tencent.mm:tools" // tools进程也需Hook（部分UI在tools进程）
    }

    /** 判断当前进程是否为QQ主进程 */
    fun isQQMainProcess(processName: String): Boolean {
        return processName == "com.tencent.mobileqq"
    }

    /** 获取布尔参数，带默认值处理 */
    fun getBooleanArg(args: XC_MethodHook.MethodHookParam, index: Int, default: Boolean): Boolean {
        return try {
            args.args[index] as? Boolean ?: default
        } catch (e: Exception) {
            default
        }
    }

    /** 获取字符串参数 */
    fun getStringArg(args: XC_MethodHook.MethodHookParam, index: Int): String? {
        return try {
            args.args[index] as? String
        } catch (e: Exception) {
            null
        }
    }
}
