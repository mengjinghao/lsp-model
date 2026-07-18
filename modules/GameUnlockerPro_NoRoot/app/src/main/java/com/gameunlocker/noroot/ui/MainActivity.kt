package com.gameunlocker.noroot.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.gameunlocker.noroot.models.GameConfig
import com.gameunlocker.noroot.utils.ConfigManager
import com.gameunlocker.noroot.utils.DeviceProfileDatabase

class MainActivity : AppCompatActivity() {

    private val games = listOf(
        "com.tencent.tmgp.sgame" to "王者荣耀",
        "com.miHoYo.Yuanshen" to "原神",
        "com.tencent.tmgp.pubgmhd" to "和平精英",
        "com.miHoYo.hkrpg" to "崩坏:星穹铁道",
        "com.tencent.tmgp.cod" to "使命召唤手游",
        "com.tencent.tmgp.gnyx" to "高能英雄",
        "com.gameblackmyth.mobile" to "黑神话手游"
    )

    private lateinit var listLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        listLayout = findViewById(R.id.gameListLayout)
        ConfigManager.init(this)
        setupUI()
    }

    private fun setupUI() {
        listLayout.removeAllViews()
        for ((pkg, name) in games) {
            listLayout.addView(createItem(pkg, name))
        }

        // 自定义包名添加
        findViewById<Button>(R.id.btnAddCustom).setOnClickListener {
            val pkg = findViewById<EditText>(R.id.etCustomPkg).text.toString().trim()
            if (pkg.isNotEmpty()) {
                listLayout.addView(createItem(pkg, pkg))
                Toast.makeText(this, "已添加: $pkg", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnResetAll).setOnClickListener {
            ConfigManager.resetAll()
            setupUI()
            Toast.makeText(this, "已重置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createItem(pkg: String, name: String): LinearLayout {
        val cfg = ConfigManager.getGameConfig(pkg)
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 24)
        }

        val profile = DeviceProfileDatabase.findById(cfg.selectedDeviceProfileId)
        val summary = listOfNotNull(
            if (cfg.deviceSpoofEnabled) profile?.displayName else null,
            if (cfg.frameRateUnlockEnabled) "${cfg.targetFps}帧" else null,
            if (cfg.detectionHideEnabled) "环境隐藏" else null,
            if (cfg.processOptimizeEnabled) "性能优化" else null
        ).joinToString(" | ")

        val tvName = TextView(this).apply {
            text = name; textSize = 16f; setTextColor(0xFFFFFFFF.toInt())
        }
        val tvSummary = TextView(this).apply {
            text = "$pkg  |  $summary"; textSize = 11f; setTextColor(0x99FFFFFF.toInt())
        }

        item.addView(tvName)
        item.addView(tvSummary)

        // 分割线
        val div = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 4
            ).apply { topMargin = 16 }
            setBackgroundColor(0x22FFFFFF.toInt())
        }
        item.addView(div)

        item.setOnClickListener { SettingsActivity.start(this, pkg, name) }
        item.setOnLongClickListener {
            ConfigManager.deleteGameConfig(pkg)
            setupUI()
            Toast.makeText(this, "已删除: $name", Toast.LENGTH_SHORT).show()
            true
        }

        return item
    }
}
