package com.privacyguard.noroot.utils

import android.util.Log

/**
 * 统一日志工具
 * TAG: PrivacyGuard，可用 adb logcat -s PrivacyGuard:V 查看
 */
object LogX {
    private const val TAG = "PrivacyGuard"
    var debugEnabled = true

    fun d(msg: String) { if (debugEnabled) Log.d(TAG, msg) }
    fun i(msg: String) { Log.i(TAG, msg) }
    fun w(msg: String) { Log.w(TAG, msg) }
    fun e(msg: String) { Log.e(TAG, msg) }
    fun e(msg: String, t: Throwable) { Log.e(TAG, msg, t) }

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
