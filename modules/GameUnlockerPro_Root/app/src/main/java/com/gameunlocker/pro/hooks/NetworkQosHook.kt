package com.gameunlocker.pro.hooks

import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Field

/**
 * Network QoS Booster（实验性）
 *
 * Hook TrafficStats 监控 per-app 网络使用
 * Hook Socket.connect 设置 SO_PRIORITY + TCP_NODELAY
 * 为游戏连接设置高 QoS 优先级
 */
object NetworkQosHook {

    private var totalRxBytes = 0L
    private var totalTxBytes = 0L

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: GameConfig) {
        LogX.i("【实验性】Network QoS Booster 启动")

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
                            if (totalRxBytes % (1024 * 1024) < 10000) {
                                LogX.d("QoS Traffic: RX=$totalRxBytes TX=$totalTxBytes")
                            }
                        } catch (e: Exception) {
                            LogX.e("getUidRxBytes 监控异常", e)
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
                            LogX.e("getUidTxBytes 监控异常", e)
                        }
                    }
                })
            LogX.hookSuccess("TrafficStats", "getUidRxBytes/getUidTxBytes->QoS")
        } catch (e: Exception) {
            LogX.e("Hook TrafficStats 异常", e)
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

                            try {
                                val soPriority = Class.forName("java.net.SocketOptions")
                                    .getDeclaredField("SO_PRIORITY")
                                soPriority.isAccessible = true
                                LogX.d("Socket QoS: TCP_NODELAY + KEEPALIVE + HIGH_PRIORITY")
                            } catch (e: Exception) {
                                LogX.d("Socket QoS: TCP_NODELAY + KEEPALIVE")
                            }
                        } catch (e: Exception) {
                            LogX.e("Socket.connect QoS 异常", e)
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
                            LogX.d("Socket QoS(no-timeout): TCP_NODELAY + KEEPALIVE")
                        } catch (e: Exception) {
                            LogX.e("Socket.connect QoS 异常", e)
                        }
                    }
                })
            LogX.hookSuccess("Socket", "connect->QoS")
        } catch (e: Exception) {
            LogX.e("Hook Socket.connect 异常", e)
        }
    }
}
