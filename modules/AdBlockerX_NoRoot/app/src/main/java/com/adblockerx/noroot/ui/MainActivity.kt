package com.adblockerx.noroot.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.adblockerx.noroot.R
import com.adblockerx.noroot.utils.ConfigManager

class MainActivity : AppCompatActivity() {

    private lateinit var swWebView: SwitchCompat
    private lateinit var swOkHttp: SwitchCompat
    private lateinit var swUrlConn: SwitchCompat
    private lateinit var swHosts: SwitchCompat
    private lateinit var swAdView: SwitchCompat
    private lateinit var swInjectJs: SwitchCompat
    private lateinit var swBuiltin: SwitchCompat
    private lateinit var swLog: SwitchCompat
    private lateinit var etCustomList: android.widget.EditText
    private lateinit var tvBlockedCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ConfigManager.init(this)

        bindViews()
        loadConfig()

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveConfig()
            Toast.makeText(this, "配置已保存，重启目标APP生效", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnReset).setOnClickListener {
            ConfigManager.resetAll()
            loadConfig()
            Toast.makeText(this, "已恢复默认配置", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnResetCount).setOnClickListener {
            ConfigManager.resetBlockedCount()
            tvBlockedCount.text = "已拦截次数：0"
            Toast.makeText(this, "计数已清零", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindViews() {
        swWebView = findViewById(R.id.swWebView)
        swOkHttp = findViewById(R.id.swOkHttp)
        swUrlConn = findViewById(R.id.swUrlConn)
        swHosts = findViewById(R.id.swHosts)
        swAdView = findViewById(R.id.swAdView)
        swInjectJs = findViewById(R.id.swInjectJs)
        swBuiltin = findViewById(R.id.swBuiltin)
        swLog = findViewById(R.id.swLog)
        etCustomList = findViewById(R.id.etCustomList)
        tvBlockedCount = findViewById(R.id.tvBlockedCount)
    }

    private fun loadConfig() {
        val cfg = ConfigManager.getConfig()
        swWebView.isChecked = cfg.webViewBlockEnabled
        swOkHttp.isChecked = cfg.okHttpBlockEnabled
        swUrlConn.isChecked = cfg.urlConnectionBlockEnabled
        swHosts.isChecked = cfg.hostsFilterEnabled
        swAdView.isChecked = cfg.adViewHideEnabled
        swInjectJs.isChecked = cfg.injectJsEnabled
        swBuiltin.isChecked = cfg.builtinBlocklistEnabled
        swLog.isChecked = cfg.logEnabled
        etCustomList.setText(cfg.customBlocklist.joinToString("\n"))
        tvBlockedCount.text = "已拦截次数：${ConfigManager.getBlockedCount()}"
    }

    private fun saveConfig() {
        val cfg = ConfigManager.getConfig()
        cfg.webViewBlockEnabled = swWebView.isChecked
        cfg.okHttpBlockEnabled = swOkHttp.isChecked
        cfg.urlConnectionBlockEnabled = swUrlConn.isChecked
        cfg.hostsFilterEnabled = swHosts.isChecked
        cfg.adViewHideEnabled = swAdView.isChecked
        cfg.injectJsEnabled = swInjectJs.isChecked
        cfg.builtinBlocklistEnabled = swBuiltin.isChecked
        cfg.logEnabled = swLog.isChecked
        ConfigManager.saveConfig(cfg)
        ConfigManager.saveCustomBlocklistRaw(etCustomList.text.toString())
    }
}
