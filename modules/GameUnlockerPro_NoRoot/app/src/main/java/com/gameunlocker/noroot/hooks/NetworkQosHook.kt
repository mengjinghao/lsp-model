package com.gameunlocker.noroot.hooks

import com.gameunlocker.noroot.models.GameConfig
import com.gameunlocker.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Network QoS Booster（实验性，NoRoot 版）
 *
 * Hook TrafficStats 监控 per-app 网络使用
 * Hook Socket.connect 设置 TCP_NODELAY + KEEPALIVE
 * 为游戏连接设置高 QoS 优先级
 */
object NetworkQosHook {

    private var totalRxBytes = 0L
    private var totalTxBytes = 0L

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        LogX.i("【实验性】Network QoS Booster 启动（NoRoot）")

        hookTrafficStats(lpparam, cfg)
        hookSocketConnect(lpparam, cfg)
    }

    private fun hookTrafficStats(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        try {
            val tsCls = XposedHelpers.findClassIfExists(
                "android.net.TrafficStats", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                tsCls, "getUidRxBytes",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val result = p.result as? Long ?: 0L
                            totalRxBytes += result
                        } catch (e: Exception) {
                            LogX.e("getUidRxBytes 监控异常(NoRoot)", e)
                        }
                    }
                })

            XposedHelpers.findAndHookMethod(
                tsCls, "getUidTxBytes",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val result = p.result as? Long ?: 0L
                            totalTxBytes += result
                        } catch (e: Exception) {
                            LogX.e("getUidTxBytes 监控异常(NoRoot)", e)
                        }
                    }
                })
            LogX.hookSuccess("TrafficStats", "getUidRxBytes/getUidTxBytes->QoS(NoRoot)")
        } catch (e: Exception) {
            LogX.e("Hook TrafficStats(NoRoot) 异常", e)
        }
    }

    private fun hookSocketConnect(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        try {
            val socketCls = XposedHelpers.findClassIfExists(
                "java.net.Socket", lpparam.classLoader
            ) ?: return

            XposedHelpers.findAndHookMethod(
                socketCls, "connect",
                "java.net.SocketAddress",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val socket = p.thisObject as? java.net.Socket ?: return
                            socket.tcpNoDelay = true
                            socket.keepAlive = true
                            socket.performancePreferences(1, 0, 1)
                            LogX.d("Socket QoS(NoRoot): TCP_NODELAY + KEEPALIVE")
                        } catch (e: Exception) {
                            LogX.e("Socket.connect QoS(NoRoot) 异常", e)
                        }
                    }
                })

            XposedHelpers.findAndHookMethod(
                socketCls, "connect",
                "java.net.SocketAddress",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            val socket = p.thisObject as? java.net.Socket ?: return
                            socket.tcpNoDelay = true
                            socket.keepAlive = true
                            LogX.d("Socket QoS(NoRoot,no-timeout): TCP_NODELAY + KEEPALIVE")
                        } catch (e: Exception) {
                            LogX.e("Socket.connect QoS(NoRoot) 异常", e)
                        }
                    }
                })
            LogX.hookSuccess("Socket", "connect->QoS(NoRoot)")
        } catch (e: Exception) {
            LogX.e("Hook Socket.connect(NoRoot) 异常", e)
        }
    }
}
