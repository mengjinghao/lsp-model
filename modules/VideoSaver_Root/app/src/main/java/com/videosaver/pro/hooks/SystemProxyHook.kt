package com.videosaver.pro.hooks

import com.videosaver.pro.models.VideoConfig
import com.videosaver.pro.utils.LogX
import com.videosaver.pro.utils.ShizukuHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 系统级 HTTP 代理 Hook（Root 版独有）
 *
 * 通过 Shizuku 设置全局 HTTP 代理用于视频流拦截：
 *  - settings put global http_proxy 127.0.0.1:8080 开启代理
 *  - settings put global http_proxy :0 关闭代理
 *  - 可捕获流媒体 URL 用于下载
 *
 * 硬性限制：
 *  - 必须 ShizukuHelper.isShizukuAvailable()
 *  - 需要本地代理服务运行（如 mitmproxy、Charles）
 *  - 全部 try-catch 保护
 */
object SystemProxyHook {

    private var isApplied = false
    private var proxyActive = false

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: VideoConfig) {
        if (!cfg.systemProxyEnabled) {
            LogX.d("SystemProxyHook 未启用，跳过")
            return
        }
        if (isApplied) return

        LogX.i("SystemProxyHook 启动：系统级 HTTP 代理")

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        isApplied = true
                        if (!ShizukuHelper.isShizukuAvailable()) {
                            LogX.w("Shizuku 不可用，跳过系统代理设置")
                            return
                        }
                    }
                })
            LogX.hookSuccess("Application", "onCreate->SystemProxyHook")
        } catch (e: Throwable) {
            LogX.e("SystemProxyHook Application.onCreate Hook 异常", e)
        }
    }

    fun enableProxy(host: String = "127.0.0.1", port: Int = 8080): Boolean {
        if (proxyActive) return true
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) {
                LogX.w("Shizuku 不可用，无法开启代理")
                return false
            }
            val proxy = "$host:$port"
            val result = ShizukuHelper.execShell("settings put global http_proxy $proxy 2>&1")
            proxyActive = result != null
            LogX.i("系统代理已开启: $proxy -> $result")
            proxyActive
        } catch (e: Throwable) {
            LogX.e("开启代理异常", e)
            false
        }
    }

    fun disableProxy(): Boolean {
        if (!proxyActive) return true
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) return false
            val result = ShizukuHelper.execShell("settings put global http_proxy :0 2>&1")
            proxyActive = false
            LogX.i("系统代理已关闭 -> $result")
            true
        } catch (e: Throwable) {
            LogX.e("关闭代理异常", e)
            false
        }
    }

    fun isProxyActive(): Boolean = proxyActive

    fun getCurrentProxy(): String? {
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) return null
            ShizukuHelper.execShell("settings get global http_proxy 2>&1")
        } catch (e: Throwable) { null }
    }

    fun setProxy(host: String, port: Int): Boolean {
        return try {
            if (!ShizukuHelper.isShizukuAvailable()) return false
            val proxy = "$host:$port"
            ShizukuHelper.execShell("settings put global http_proxy $proxy 2>&1") != null
        } catch (e: Throwable) {
            LogX.e("设置代理异常: $host:$port", e)
            false
        }
    }

    fun release() {
        try {
            if (proxyActive) disableProxy()
        } catch (_: Throwable) {}
        isApplied = false
    }
}
