package com.privacyguard.noroot.ui

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.privacyguard.noroot.R
import com.privacyguard.noroot.models.PrivacyConfig
import com.privacyguard.noroot.utils.ConfigManager

/**
 * 主界面Activity - 隐私保护开关面板
 *
 * 显示作用域内各 APP 的隐私保护开关，修改后下次 APP 启动生效。
 * 简单实现：所有 APP 共用一套全局配置（按 "global" 键存储），
 * 每个目标 APP 启动时读取 "global" 配置应用。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var cfg: PrivacyConfig

    // 开关控件引用
    private lateinit var swDeviceId: SwitchCompat
    private lateinit var swClipboard: SwitchCompat
    private lateinit var swClipboardBlock: SwitchCompat
    private lateinit var swPermission: SwitchCompat
    private lateinit var swLocation: SwitchCompat
    private lateinit var swSensor: SwitchCompat
    private lateinit var swAdId: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ConfigManager.init(this)
        // 使用 "global" 作为统一配置键，所有作用域 APP 共享
        cfg = ConfigManager.getConfig("global")

        container = findViewById(R.id.configContainer)
        inflateSwitches()
        loadUI()
        setupButtons()
    }

    private fun inflateSwitches() {
        swDeviceId = findViewById(R.id.swDeviceId)
        swClipboard = findViewById(R.id.swClipboard)
        swClipboardBlock = findViewById(R.id.swClipboardBlock)
        swPermission = findViewById(R.id.swPermission)
        swLocation = findViewById(R.id.swLocation)
        swSensor = findViewById(R.id.swSensor)
        swAdId = findViewById(R.id.swAdId)
    }

    private fun loadUI() {
        swDeviceId.isChecked = cfg.deviceIdSpoofEnabled
        swClipboard.isChecked = cfg.clipboardGuardEnabled
        swClipboardBlock.isChecked = cfg.clipboardBlockRead
        swPermission.isChecked = cfg.permissionSpoofEnabled
        swLocation.isChecked = cfg.locationSpoofEnabled
        swSensor.isChecked = cfg.sensorFakerEnabled
        swAdId.isChecked = cfg.advertisingIdBlockEnabled
    }

    private fun saveUI() {
        cfg.deviceIdSpoofEnabled = swDeviceId.isChecked
        cfg.clipboardGuardEnabled = swClipboard.isChecked
        cfg.clipboardBlockRead = swClipboardBlock.isChecked
        cfg.permissionSpoofEnabled = swPermission.isChecked
        cfg.locationSpoofEnabled = swLocation.isChecked
        cfg.sensorFakerEnabled = swSensor.isChecked
        cfg.advertisingIdBlockEnabled = swAdId.isChecked
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
