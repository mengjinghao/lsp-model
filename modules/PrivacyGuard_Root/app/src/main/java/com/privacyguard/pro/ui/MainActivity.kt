package com.privacyguard.pro.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.privacyguard.pro.R
import com.privacyguard.pro.models.PrivacyConfig
import com.privacyguard.pro.utils.ConfigManager
import com.privacyguard.pro.utils.ShizukuHelper

/**
 * 主界面Activity - 隐私保护开关面板（Root 版）
 *
 * 显示作用域内各 APP 的隐私保护开关，包括：
 *  - 应用层开关（设备ID/剪贴板/权限/位置/传感器/广告ID）
 *  - 系统级开关（系统属性/全局权限/网络标识/Shizuku桥接）
 *
 * 修改后下次 APP 启动生效。
 * 简单实现：所有 APP 共用一套全局配置（按 "global" 键存储）。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var cfg: PrivacyConfig

    // 应用层开关
    private lateinit var swDeviceId: SwitchCompat
    private lateinit var swClipboard: SwitchCompat
    private lateinit var swClipboardBlock: SwitchCompat
    private lateinit var swPermission: SwitchCompat
    private lateinit var swLocation: SwitchCompat
    private lateinit var swSensor: SwitchCompat
    private lateinit var swAdId: SwitchCompat

    // 系统级开关
    private lateinit var swSystemProp: SwitchCompat
    private lateinit var swGlobalPerm: SwitchCompat
    private lateinit var swNetworkId: SwitchCompat
    private lateinit var swShizukuBridge: SwitchCompat
    private lateinit var swClearTracking: SwitchCompat

    private lateinit var tvShizukuStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ConfigManager.init(this)
        cfg = ConfigManager.getConfig("global")

        bindViews()
        loadUI()
        updateShizukuStatus()
        setupButtons()
    }

    private fun bindViews() {
        swDeviceId = findViewById(R.id.swDeviceId)
        swClipboard = findViewById(R.id.swClipboard)
        swClipboardBlock = findViewById(R.id.swClipboardBlock)
        swPermission = findViewById(R.id.swPermission)
        swLocation = findViewById(R.id.swLocation)
        swSensor = findViewById(R.id.swSensor)
        swAdId = findViewById(R.id.swAdId)
        swSystemProp = findViewById(R.id.swSystemProp)
        swGlobalPerm = findViewById(R.id.swGlobalPerm)
        swNetworkId = findViewById(R.id.swNetworkId)
        swShizukuBridge = findViewById(R.id.swShizukuBridge)
        swClearTracking = findViewById(R.id.swClearTracking)
        tvShizukuStatus = findViewById(R.id.tvShizukuStatus)
    }

    private fun loadUI() {
        swDeviceId.isChecked = cfg.deviceIdSpoofEnabled
        swClipboard.isChecked = cfg.clipboardGuardEnabled
        swClipboardBlock.isChecked = cfg.clipboardBlockRead
        swPermission.isChecked = cfg.permissionSpoofEnabled
        swLocation.isChecked = cfg.locationSpoofEnabled
        swSensor.isChecked = cfg.sensorFakerEnabled
        swAdId.isChecked = cfg.advertisingIdBlockEnabled
        swSystemProp.isChecked = cfg.systemPropSpoofEnabled
        swGlobalPerm.isChecked = cfg.globalPermissionControlEnabled
        swNetworkId.isChecked = cfg.networkIdentifierSpoofEnabled
        swShizukuBridge.isChecked = cfg.shizukuBridgeEnabled
        swClearTracking.isChecked = cfg.clearAppTrackingData
    }

    private fun saveUI() {
        cfg.deviceIdSpoofEnabled = swDeviceId.isChecked
        cfg.clipboardGuardEnabled = swClipboard.isChecked
        cfg.clipboardBlockRead = swClipboardBlock.isChecked
        cfg.permissionSpoofEnabled = swPermission.isChecked
        cfg.locationSpoofEnabled = swLocation.isChecked
        cfg.sensorFakerEnabled = swSensor.isChecked
        cfg.advertisingIdBlockEnabled = swAdId.isChecked
        cfg.systemPropSpoofEnabled = swSystemProp.isChecked
        cfg.globalPermissionControlEnabled = swGlobalPerm.isChecked
        cfg.networkIdentifierSpoofEnabled = swNetworkId.isChecked
        cfg.shizukuBridgeEnabled = swShizukuBridge.isChecked
        cfg.clearAppTrackingData = swClearTracking.isChecked
    }

    private fun updateShizukuStatus() {
        val available = try { ShizukuHelper.isShizukuAvailable() } catch (_: Exception) { false }
        tvShizukuStatus.text = if (available) {
            "Shizuku 状态: 已激活（系统级 Hook 可用）"
        } else {
            "Shizuku 状态: 未激活（系统级 Hook 将跳过，仅应用层生效）"
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveUI()
            ConfigManager.saveConfig(cfg)
            Toast.makeText(this, "配置已保存，重启目标APP生效", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.btnReset).setOnClickListener {
            cfg = ConfigManager.createDefault("global")
            ConfigManager.saveConfig(cfg)
            loadUI()
            Toast.makeText(this, "已恢复默认", Toast.LENGTH_SHORT).show()
        }
    }
}
