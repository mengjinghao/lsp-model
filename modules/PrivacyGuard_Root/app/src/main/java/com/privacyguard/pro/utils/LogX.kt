package com.privacyguard.pro.utils

import android.util.Log

/**
 * 统一日志工具类
 * Xposed模块中Log.d/i/e可被XposedInstaller/LSPatch日志捕获
 * TAG统一使用模块名，便于过滤
 */
object LogX {

    private const val TAG = "PrivacyGuard"
    private var debugEnabled = true

    fun setDebug(enabled: Boolean) {
        debugEnabled = enabled
    }

    fun d(msg: String) {
        if (debugEnabled) Log.d(TAG, msg)
    }

    fun i(msg: String) {
        Log.i(TAG, msg)
    }

    fun w(msg: String) {
        Log.w(TAG, msg)
    }

    fun e(msg: String) {
        Log.e(TAG, msg)
    }

    fun e(msg: String, throwable: Throwable) {
        Log.e(TAG, msg, throwable)
    }

    /** 记录Hook成功信息 */
    fun hookSuccess(className: String, methodName: String) {
        d("[Hook] ✓ $className.$methodName")
    }

    /** 记录Hook失败信息 */
    fun hookFailed(className: String, methodName: String, throwable: Throwable? = null) {
        w("[Hook] ✗ $className.$methodName")
        if (throwable != null) {
            e("Hook异常: ${throwable.message}", throwable)
        }
    }
}
