package com.adblockerx.noroot.utils

import android.util.Log

/**
 * 统一日志工具
 * TAG: AdBlockerX，可用 adb logcat -s AdBlockerX:V 查看
 */
object LogX {
    private const val TAG = "AdBlockerX"
    var debugEnabled = true

    fun d(msg: String) { if (debugEnabled) Log.d(TAG, msg) }
    fun i(msg: String) { Log.i(TAG, msg) }
    fun w(msg: String) { Log.w(TAG, msg) }
    fun e(msg: String) { Log.e(TAG, msg) }
    fun e(msg: String, t: Throwable) { Log.e(TAG, msg, t) }
}
