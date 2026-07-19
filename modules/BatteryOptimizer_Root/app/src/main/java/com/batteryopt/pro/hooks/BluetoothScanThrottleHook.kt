package com.batteryopt.pro.hooks

import com.batteryopt.pro.models.BatteryConfig
import com.batteryopt.pro.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.ConcurrentHashMap

/**
 * 【实验性】蓝牙扫描降频 Hook（应用层）
 */
object BluetoothScanThrottleHook {

    private val lastScanTs = ConcurrentHashMap<String, Long>()

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        LogX.i("【实验性】蓝牙扫描降频启动 | 最小间隔=${cfg.bluetoothScanMinIntervalMs}ms")

        hookStartScan(lpparam, cfg)
        hookStartDiscovery(lpparam, cfg)
    }

    private fun hookStartScan(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        val cls = XposedHelpers.findClassIfExists(
            "android.bluetooth.le.BluetoothLeScanner", lpparam.classLoader
        ) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                cls, "startScan",
                "android.bluetooth.le.ScanCallback",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (shouldThrottle("ble_startScan_cb", cfg)) {
                            p.result = null
                            LogX.w("BLE startScan(callback) 节流")
                        }
                    }
                })
            LogX.hookSuccess("BluetoothLeScanner", "startScan(callback)")
        } catch (_: Throwable) {}

        try {
            XposedHelpers.findAndHookMethod(
                cls, "startScan",
                "java.util.List",
                "android.bluetooth.le.ScanSettings",
                "android.bluetooth.le.ScanCallback",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (shouldThrottle("ble_startScan_filter", cfg)) {
                            p.result = null
                            LogX.w("BLE startScan(filters,settings,callback) 节流")
                        }
                    }
                })
            LogX.hookSuccess("BluetoothLeScanner", "startScan(filters,settings,callback)")
        } catch (_: Throwable) {}

        try {
            XposedHelpers.findAndHookMethod(
                cls, "startScan",
                "java.util.List",
                "android.bluetooth.le.ScanSettings",
                "android.app.PendingIntent",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (shouldThrottle("ble_startScan_pi", cfg)) {
                            p.result = 0
                            LogX.w("BLE startScan(filters,settings,intent) 节流")
                        }
                    }
                })
            LogX.hookSuccess("BluetoothLeScanner", "startScan(filters,settings,intent)")
        } catch (_: Throwable) {}
    }

    private fun hookStartDiscovery(lpparam: XC_LoadPackage.LoadPackageParam, cfg: BatteryConfig) {
        val cls = XposedHelpers.findClassIfExists(
            "android.bluetooth.BluetoothAdapter", lpparam.classLoader
        ) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                cls, "startDiscovery",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        if (shouldThrottle("bt_startDiscovery", cfg)) {
                            p.result = false
                            LogX.w("BluetoothAdapter.startDiscovery 节流")
                        }
                    }
                })
            LogX.hookSuccess("BluetoothAdapter", "startDiscovery")
        } catch (_: Throwable) {}
    }

    private fun shouldThrottle(key: String, cfg: BatteryConfig): Boolean {
        val now = System.currentTimeMillis()
        val last = lastScanTs[key] ?: 0L
        return if (now - last < cfg.bluetoothScanMinIntervalMs) {
            true
        } else {
            lastScanTs[key] = now
            false
        }
    }
}
