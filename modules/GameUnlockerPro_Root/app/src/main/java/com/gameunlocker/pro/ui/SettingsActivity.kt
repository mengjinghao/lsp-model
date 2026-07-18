package com.gameunlocker.pro.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.gameunlocker.pro.models.DeviceProfile
import com.gameunlocker.pro.models.GameConfig
import com.gameunlocker.pro.utils.ConfigManager
import com.gameunlocker.pro.utils.DeviceProfileDatabase

/**
 * 游戏独立配置Activity
 * 为每个游戏设置独立的：
 *  - 机型伪装方案
 *  - 帧率解锁参数
 *  - 温控策略
 *  - 环境隐藏开关
 *  - 分辨率伪装
 *  - GPU优化
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_PACKAGE = "package_name"
        private const val EXTRA_GAME_NAME = "game_name"

        fun start(context: Context, packageName: String, gameName: String) {
            val intent = Intent(context, SettingsActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE, packageName)
                putExtra(EXTRA_GAME_NAME, gameName)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var config: GameConfig
    private lateinit var packageName: String

    // ========== 视图引用 ==========
    private lateinit var swDeviceSpoof: Switch
    private lateinit var spProfile: Spinner
    private lateinit var swFrameRate: Switch
    private lateinit var spTargetFps: Spinner
    private lateinit var swThermal: Switch
    private lateinit var etThermalThreshold: EditText
    private lateinit var swDetectionHide: Switch
    private lateinit var swHideShizuku: Switch
    private lateinit var swHideXposed: Switch
    private lateinit var swHideLspatch: Switch
    private lateinit var swResolutionSpoof: Switch
    private lateinit var etResWidth: EditText
    private lateinit var etResHeight: EditText
    private lateinit var etResDpi: EditText
    private lateinit var swGpuOptimize: Switch
    private lateinit var swShizukuBridge: Switch
    private lateinit var btnSave: Button
    private lateinit var btnExport: Button
    private lateinit var btnImport: Button
    private lateinit var btnReset: Button
    private lateinit var btnCustomDevice: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: return finish()
        val gameName = intent.getStringExtra(EXTRA_GAME_NAME) ?: packageName
        supportActionBar?.title = "$gameName 独立配置"

        ConfigManager.init(this)
        config = ConfigManager.getGameConfig(packageName)

        bindViews()
        loadConfigToUI()
        setupListeners()
    }

    private fun bindViews() {
        swDeviceSpoof = findViewById(R.id.swDeviceSpoof)
        spProfile = findViewById(R.id.spProfile)
        swFrameRate = findViewById(R.id.swFrameRate)
        spTargetFps = findViewById(R.id.spTargetFps)
        swThermal = findViewById(R.id.swThermal)
        etThermalThreshold = findViewById(R.id.etThermalThreshold)
        swDetectionHide = findViewById(R.id.swDetectionHide)
        swHideShizuku = findViewById(R.id.swHideShizuku)
        swHideXposed = findViewById(R.id.swHideXposed)
        swHideLspatch = findViewById(R.id.swHideLspatch)
        swResolutionSpoof = findViewById(R.id.swResolutionSpoof)
        etResWidth = findViewById(R.id.etResWidth)
        etResHeight = findViewById(R.id.etResHeight)
        etResDpi = findViewById(R.id.etResDpi)
        swGpuOptimize = findViewById(R.id.swGpuOptimize)
        swShizukuBridge = findViewById(R.id.swShizukuBridge)
        btnSave = findViewById(R.id.btnSave)
        btnExport = findViewById(R.id.btnExport)
        btnImport = findViewById(R.id.btnImport)
        btnReset = findViewById(R.id.btnReset)
        btnCustomDevice = findViewById(R.id.btnCustomDevice)

        // 初始化机型下拉列表
        val profileNames = DeviceProfileDatabase.getAllDisplayNames()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, profileNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spProfile.adapter = adapter

        // 初始化帧率下拉列表
        val fpsLabels = arrayOf("60帧", "90帧", "120帧", "144帧", "160帧", "自动(屏幕最高)")
        val fpsAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fpsLabels)
        fpsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spTargetFps.adapter = fpsAdapter
    }

    /**
     * 将当前配置加载到UI控件
     */
    private fun loadConfigToUI() {
        swDeviceSpoof.isChecked = config.deviceSpoofEnabled
        swFrameRate.isChecked = config.frameRateUnlockEnabled
        swThermal.isChecked = config.thermalBypassEnabled
        etThermalThreshold.setText(config.customThermalThreshold.toString())
        swDetectionHide.isChecked = config.detectionHideEnabled
        swHideShizuku.isChecked = config.hideShizuku
        swHideXposed.isChecked = config.hideXposed
        swHideLspatch.isChecked = config.hideLspatch
        swResolutionSpoof.isChecked = config.resolutionSpoofEnabled
        etResWidth.setText(config.spoofWidth.toString())
        etResHeight.setText(config.spoofHeight.toString())
        etResDpi.setText(config.spoofDpi.toString())
        swGpuOptimize.isChecked = config.gpuOptimizeEnabled
        swShizukuBridge.isChecked = config.shizukuBridgeEnabled

        // 设置机型选择器位置
        val profileIds = DeviceProfileDatabase.getAllIds()
        val currentIdIndex = profileIds.indexOf(config.selectedDeviceProfileId)
        if (currentIdIndex >= 0) {
            spProfile.setSelection(currentIdIndex)
        }
    }

    /**
     * 将UI控件值回填到配置对象
     */
    private fun saveUItoConfig() {
        config.deviceSpoofEnabled = swDeviceSpoof.isChecked
        config.frameRateUnlockEnabled = swFrameRate.isChecked
        config.thermalBypassEnabled = swThermal.isChecked
        config.customThermalThreshold = etThermalThreshold.text.toString().toIntOrNull() ?: 50
        config.detectionHideEnabled = swDetectionHide.isChecked
        config.hideShizuku = swHideShizuku.isChecked
        config.hideXposed = swHideXposed.isChecked
        config.hideLspatch = swHideLspatch.isChecked
        config.resolutionSpoofEnabled = swResolutionSpoof.isChecked
        config.spoofWidth = etResWidth.text.toString().toIntOrNull() ?: 2560
        config.spoofHeight = etResHeight.text.toString().toIntOrNull() ?: 1440
        config.spoofDpi = etResDpi.text.toString().toIntOrNull() ?: 560
        config.gpuOptimizeEnabled = swGpuOptimize.isChecked
        config.shizukuBridgeEnabled = swShizukuBridge.isChecked

        // 机型
        val profileIds = DeviceProfileDatabase.getAllIds()
        val selectedProfileIndex = spProfile.selectedItemPosition
        if (selectedProfileIndex in profileIds.indices) {
            config.selectedDeviceProfileId = profileIds[selectedProfileIndex]
        }

        // 帧率
        val fpsValues = arrayOf("60", "90", "120", "144", "160", "-1")
        val selectedFpsIndex = spTargetFps.selectedItemPosition
        if (selectedFpsIndex in fpsValues.indices) {
            config.targetFps = fpsValues[selectedFpsIndex].toInt()
        }
    }

    private fun setupListeners() {
        btnSave.setOnClickListener {
            saveUItoConfig()
            ConfigManager.saveGameConfig(config)
            Toast.makeText(this, "配置已保存，下次启动游戏生效", Toast.LENGTH_LONG).show()
        }

        btnExport.setOnClickListener {
            val json = com.google.gson.Gson().toJson(config)
            val fileName = "GameConfig_${packageName}_${System.currentTimeMillis()}.json"
            val file = java.io.File(filesDir, fileName)
            file.writeText(json)
            Toast.makeText(this, "配置已导出: $fileName", Toast.LENGTH_LONG).show()
        }

        btnImport.setOnClickListener {
            // 从filesDir查找最后一次导出的配置
            val files = filesDir.listFiles()?.filter { it.name.startsWith("GameConfig_") }
            val latest = files?.maxByOrNull { it.lastModified() }
            if (latest != null) {
                try {
                    config = com.google.gson.Gson().fromJson(latest.readText(), GameConfig::class.java)
                    ConfigManager.saveGameConfig(config)
                    loadConfigToUI()
                    Toast.makeText(this, "配置已导入", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "未找到导入文件", Toast.LENGTH_SHORT).show()
            }
        }

        btnReset.setOnClickListener {
            config = ConfigManager.createDefaultConfig(packageName)
            ConfigManager.saveGameConfig(config)
            loadConfigToUI()
            Toast.makeText(this, "已恢复默认", Toast.LENGTH_SHORT).show()
        }

        btnCustomDevice.setOnClickListener {
            showCustomDeviceDialog()
        }
    }

    /**
     * 弹出自定义机型对话框
     */
    private fun showCustomDeviceDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("自定义机型参数")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }

        val etBrand = EditText(this).apply { hint = "品牌 (如: HUAWEI)"; setText(config.customDeviceProfile?.brand ?: "") }
        val etModel = EditText(this).apply { hint = "型号 (如: ALN-AL80)"; setText(config.customDeviceProfile?.model ?: "") }
        val etManufacturer = EditText(this).apply { hint = "制造商"; setText(config.customDeviceProfile?.manufacturer ?: "") }
        val etDevice = EditText(this).apply { hint = "设备代号"; setText(config.customDeviceProfile?.device ?: "") }
        val etCpu = EditText(this).apply { hint = "CPU型号 (如: Snapdragon 8 Gen 3)"; setText(config.customDeviceProfile?.cpuModel ?: "") }
        val etGpu = EditText(this).apply { hint = "GPU型号 (如: Adreno 750)"; setText(config.customDeviceProfile?.gpuModel ?: "") }

        layout.addView(etBrand)
        layout.addView(etModel)
        layout.addView(etManufacturer)
        layout.addView(etDevice)
        layout.addView(etCpu)
        layout.addView(etGpu)

        builder.setView(layout)
        builder.setPositiveButton("确定") { _, _ ->
            config.customDeviceProfile = DeviceProfile(
                id = "custom_${System.currentTimeMillis()}",
                displayName = "${etBrand.text} ${etModel.text}",
                brand = etBrand.text.toString(),
                model = etModel.text.toString(),
                manufacturer = etManufacturer.text.toString(),
                device = etDevice.text.toString(),
                board = "custom",
                hardware = "custom",
                product = etDevice.text.toString(),
                cpuModel = etCpu.text.toString(),
                gpuModel = etGpu.text.toString(),
                isCustom = true
            )
            Toast.makeText(this, "自定义机型已设置", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("取消", null)
        builder.show()
    }
}
