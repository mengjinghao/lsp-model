package com.adblockerx.pro.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.adblockerx.pro.R
import com.adblockerx.pro.hooks.PrivateDnsHook
import com.adblockerx.pro.hooks.SystemHostsHook
import com.adblockerx.pro.utils.ConfigManager
import com.adblockerx.pro.utils.ShizukuHelper

class MainActivity : AppCompatActivity() {

    private lateinit var swWebView: SwitchCompat
    private lateinit var swOkHttp: SwitchCompat
    private lateinit var swUrlConn: SwitchCompat
    private lateinit var swHosts: SwitchCompat
    private lateinit var swAdView: SwitchCompat
    private lateinit var swInjectJs: SwitchCompat
    private lateinit var swBuiltin: SwitchCompat
    private lateinit var swLog: SwitchCompat
    private lateinit var swSystemHosts: SwitchCompat
    private lateinit var swPrivateDns: SwitchCompat
    private lateinit var swDnsResolverHook: SwitchCompat
    private lateinit var swShizukuBridge: SwitchCompat
    private lateinit var etCustomList: android.widget.EditText
    private lateinit var etPrivateDnsHost: android.widget.EditText
    private lateinit var tvBlockedCount: TextView
    private lateinit var tvShizukuStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ConfigManager.init(this)

        bindViews()
        loadConfig()
        updateShizukuStatus()

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveConfig()
            Toast.makeText(this, "配置已保存，重启目标APP生效", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnRestoreHosts).setOnClickListener {
            val ok = SystemHostsHook.restoreOriginalHosts()
            Toast.makeText(this, if (ok) "系统 hosts 已恢复" else "恢复失败（Shizuku未授权？）", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnRestoreDns).setOnClickListener {
            val ok = PrivateDnsHook.restorePrivateDns()
            Toast.makeText(this, if (ok) "Private DNS 已恢复自动" else "恢复失败（Shizuku未授权？）", Toast.LENGTH_SHORT).show()
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
        swSystemHosts = findViewById(R.id.swSystemHosts)
        swPrivateDns = findViewById(R.id.swPrivateDns)
        swDnsResolverHook = findViewById(R.id.swDnsResolverHook)
        swShizukuBridge = findViewById(R.id.swShizukuBridge)
        etCustomList = findViewById(R.id.etCustomList)
        etPrivateDnsHost = findViewById(R.id.etPrivateDnsHost)
        tvBlockedCount = findViewById(R.id.tvBlockedCount)
        tvShizukuStatus = findViewById(R.id.tvShizukuStatus)
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
        swSystemHosts.isChecked = cfg.systemHostsEnabled
        swPrivateDns.isChecked = cfg.privateDnsEnabled
        swDnsResolverHook.isChecked = cfg.dnsResolverHookEnabled
        swShizukuBridge.isChecked = cfg.shizukuBridgeEnabled
        etCustomList.setText(cfg.customBlocklist.joinToString("\n"))
        etPrivateDnsHost.setText(cfg.privateDnsHost)
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
        cfg.systemHostsEnabled = swSystemHosts.isChecked
        cfg.privateDnsEnabled = swPrivateDns.isChecked
        cfg.privateDnsHost = etPrivateDnsHost.text.toString().trim().ifBlank { "dns.adblockplus.org" }
        cfg.dnsResolverHookEnabled = swDnsResolverHook.isChecked
        cfg.shizukuBridgeEnabled = swShizukuBridge.isChecked
        ConfigManager.saveConfig(cfg)
        ConfigManager.saveCustomBlocklistRaw(etCustomList.text.toString())
    }

    private fun updateShizukuStatus() {
        val available = try { ShizukuHelper.isShizukuAvailable() } catch (_: Throwable) { false }
        tvShizukuStatus.text = "Shizuku 状态：${if (available) "可用 ✓" else "未授权 ✗（系统级功能不可用）"}"
        tvShizukuStatus.setTextColor(if (available) 0xFF4CAF50.toInt() else 0xFFFFC107.toInt())
    }
}
