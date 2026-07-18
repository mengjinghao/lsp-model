package com.gameunlocker.pro.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.ConfigManager
import com.gameunlocker.pro.utils.DeviceProfileDatabase
import com.gameunlocker.pro.utils.LogX

/**
 * 主界面Activity
 * 显示游戏列表，点击进入独立配置页
 *
 * 注意：LSPatch本地模式下，模块自身Activity在模块APK进程内运行
 * 而非在目标游戏进程。用户在此界面修改配置，下次游戏启动时生效。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var gameListLayout: LinearLayout
    private lateinit var btnExportAll: Button
    private lateinit var btnImportAll: Button
    private lateinit var btnResetAll: Button
    private lateinit var tvInfo: TextView

    // 预设游戏列表（与arrays.xml保持同步）
    private val gamePackages = listOf(
        "com.tencent.tmgp.sgame" to "王者荣耀",
        "com.miHoYo.Yuanshen" to "原神",
        "com.tencent.tmgp.pubgmhd" to "和平精英",
        "com.miHoYo.hkrpg" to "崩坏:星穹铁道",
        "com.tencent.tmgp.cod" to "使命召唤手游",
        "com.tencent.tmgp.gnyx" to "高能英雄",
        "com.gameblackmyth.mobile" to "黑神话手游"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化ConfigManager
        ConfigManager.init(this)

        // 绑定视图
        gameListLayout = findViewById(R.id.gameListLayout)
        btnExportAll = findViewById(R.id.btnExportAll)
        btnImportAll = findViewById(R.id.btnImportAll)
        btnResetAll = findViewById(R.id.btnResetAll)
        tvInfo = findViewById(R.id.tvInfo)

        setupUI()
    }

    private fun setupUI() {
        // ========== 游戏列表 ==========
        gameListLayout.removeAllViews()
        for ((pkg, name) in gamePackages) {
            val config = ConfigManager.getGameConfig(pkg)
            val gameItem = createGameItemView(pkg, name, config)
            gameListLayout.addView(gameItem)
        }

        // ========== 自定义包名输入 ==========
        val customPkgLayout = findViewById<LinearLayout>(R.id.customPkgLayout)
        val etCustomPkg = findViewById<EditText>(R.id.etCustomPkg)
        val btnAddCustom = findViewById<Button>(R.id.btnAddCustom)

        btnAddCustom.setOnClickListener {
            val customPkg = etCustomPkg.text.toString().trim()
            if (customPkg.isNotEmpty()) {
                val config = ConfigManager.getGameConfig(customPkg)
                ConfigManager.saveGameConfig(config)
                val gameItem = createGameItemView(customPkg, customPkg, config)
                gameListLayout.addView(gameItem)
                etCustomPkg.text.clear()
                Toast.makeText(this, "已添加: $customPkg", Toast.LENGTH_SHORT).show()
            }
        }

        // ========== 按钮事件 ==========
        btnExportAll.setOnClickListener {
            exportAllConfigs()
        }
        btnImportAll.setOnClickListener {
            importAllConfigs()
        }
        btnResetAll.setOnClickListener {
            ConfigManager.resetAllConfigs()
            setupUI()
            Toast.makeText(this, "所有配置已重置", Toast.LENGTH_SHORT).show()
        }

        // ========== 信息显示 ==========
        tvInfo.text = """
            Game-Unlocker Pro v1.0.0
            LSPatch本地模式 | 免Root运行
            
            点击游戏名称进入独立配置
            修改后下次启动游戏生效
        """.trimIndent()
    }

    /**
     * 创建单个游戏条目View
     */
    private fun createGameItemView(pkg: String, name: String, config: GameConfig): LinearLayout {
        val itemLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                40.dpToPx(), 24.dpToPx(),
                40.dpToPx(), 24.dpToPx()
            )
        }

        // 顶部：游戏名 + 状态指示
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val tvName = TextView(this).apply {
            text = name
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        val tvStatus = TextView(this).apply {
            text = buildStatusText(config)
            textSize = 12f
            setTextColor(0xFF66BB6A.toInt())
        }

        topRow.addView(tvName)
        topRow.addView(tvStatus)

        // 底部：包名 + 配置概要
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dpToPx() }
        }

        val tvPkg = TextView(this).apply {
            text = pkg
            textSize = 11f
            setTextColor(0x99FFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        val tvConfig = TextView(this).apply {
            text = buildConfigSummary(config)
            textSize = 11f
            setTextColor(0x99FFFFFF.toInt())
        }

        bottomRow.addView(tvPkg)
        bottomRow.addView(tvConfig)

        // 分割线
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)
            ).apply { topMargin = 16.dpToPx() }
            setBackgroundColor(0x22FFFFFF.toInt())
        }

        itemLayout.addView(topRow)
        itemLayout.addView(bottomRow)
        itemLayout.addView(divider)

        // 点击事件
        itemLayout.setOnClickListener {
            SettingsActivity.start(this, pkg, name)
        }

        // 长按删除
        itemLayout.setOnLongClickListener {
            ConfigManager.deleteGameConfig(pkg)
            setupUI()
            Toast.makeText(this, "已删除: $name", Toast.LENGTH_SHORT).show()
            true
        }

        return itemLayout
    }

    private fun buildStatusText(config: GameConfig): String {
        val list = mutableListOf<String>()
        if (config.deviceSpoofEnabled) list.add("机型伪装")
        if (config.frameRateUnlockEnabled) list.add("${config.targetFps}帧")
        if (config.thermalBypassEnabled) list.add("温控关")
        if (config.detectionHideEnabled) list.add("环境隐藏")
        return list.joinToString(" | ")
    }

    private fun buildConfigSummary(config: GameConfig): String {
        val profile = DeviceProfileDatabase.findById(config.selectedDeviceProfileId)
        val modelName = profile?.displayName ?: "未设置"
        return "伪装机型: $modelName"
    }

    /**
     * 导出所有配置
     */
    private fun exportAllConfigs() {
        val result = ConfigManager.exportConfigs(filesDir)
        if (result != null) {
            Toast.makeText(this, "配置已导出: $result", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 导入配置
     */
    private fun importAllConfigs() {
        // 简化版：从assets内置默认配置恢复
        val importFile = java.io.File(filesDir, "restore.json")
        if (importFile.exists()) {
            val count = ConfigManager.importConfigs(importFile, merge = false)
            setupUI()
            Toast.makeText(this, "已导入 $count 条配置", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "未找到导入文件，已使用默认配置", Toast.LENGTH_SHORT).show()
            ConfigManager.resetAllConfigs()
            setupUI()
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density + 0.5f).toInt()
    private fun Int.pxToDp(): Int = (this / resources.displayMetrics.density + 0.5f).toInt()
}
