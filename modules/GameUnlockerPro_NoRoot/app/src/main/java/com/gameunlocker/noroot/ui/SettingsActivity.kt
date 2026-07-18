package com.gameunlocker.noroot.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.gameunlocker.noroot.models.DeviceProfile
import com.gameunlocker.noroot.models.GameConfig
import com.gameunlocker.noroot.utils.ConfigManager
import com.gameunlocker.noroot.utils.DeviceProfileDatabase

class SettingsActivity : AppCompatActivity() {

    companion object {
        fun start(ctx: Context, pkg: String, name: String) {
            ctx.startActivity(Intent(ctx, SettingsActivity::class.java).apply {
                putExtra("pkg", pkg); putExtra("name", name)
            })
        }
    }

    private lateinit var cfg: GameConfig
    private lateinit var pkg: String

    private lateinit var swSpoof: Switch
    private lateinit var spProfile: Spinner
    private lateinit var swFps: Switch
    private lateinit var spFps: Spinner
    private lateinit var swHide: Switch
    private lateinit var swRes: Switch
    private lateinit var swOptimize: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        pkg = intent.getStringExtra("pkg") ?: return finish()
        val name = intent.getStringExtra("name") ?: pkg
        supportActionBar?.title = "$name 配置"

        ConfigManager.init(this)
        cfg = ConfigManager.getGameConfig(pkg)

        bindViews()
        loadUI()
        setupListeners()
    }

    private fun bindViews() {
        swSpoof = findViewById(R.id.swDeviceSpoof)
        spProfile = findViewById(R.id.spProfile)
        swFps = findViewById(R.id.swFrameRate)
        spFps = findViewById(R.id.spTargetFps)
        swHide = findViewById(R.id.swDetectionHide)
        swRes = findViewById(R.id.swResolutionSpoof)
        swOptimize = findViewById(R.id.swProcessOptimize)

        spProfile.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_item,
            DeviceProfileDatabase.getAllNames()
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        spFps.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_item,
            arrayOf("60帧", "90帧", "120帧", "144帧", "160帧", "自动")
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun loadUI() {
        swSpoof.isChecked = cfg.deviceSpoofEnabled
        val idx = DeviceProfileDatabase.getAllIds().indexOf(cfg.selectedDeviceProfileId)
        if (idx >= 0) spProfile.setSelection(idx)
        swFps.isChecked = cfg.frameRateUnlockEnabled
        spFps.setSelection(listOf(60, 90, 120, 144, 160, -1).indexOf(cfg.targetFps).coerceAtLeast(0))
        swHide.isChecked = cfg.detectionHideEnabled
        swRes.isChecked = cfg.resolutionSpoofEnabled
        swOptimize.isChecked = cfg.processOptimizeEnabled
    }

    private fun saveUI() {
        cfg.deviceSpoofEnabled = swSpoof.isChecked
        cfg.frameRateUnlockEnabled = swFps.isChecked
        cfg.detectionHideEnabled = swHide.isChecked
        cfg.resolutionSpoofEnabled = swRes.isChecked
        cfg.processOptimizeEnabled = swOptimize.isChecked

        val ids = DeviceProfileDatabase.getAllIds()
        if (spProfile.selectedItemPosition in ids.indices)
            cfg.selectedDeviceProfileId = ids[spProfile.selectedItemPosition]

        val fpsVals = listOf(60, 90, 120, 144, 160, -1)
        if (spFps.selectedItemPosition in fpsVals.indices)
            cfg.targetFps = fpsVals[spFps.selectedItemPosition]
    }

    private fun setupListeners() {
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveUI()
            ConfigManager.saveGameConfig(cfg)
            Toast.makeText(this, "已保存，重启游戏生效", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.btnReset).setOnClickListener {
            cfg = ConfigManager.createDefault(pkg)
            ConfigManager.saveGameConfig(cfg)
            loadUI()
            Toast.makeText(this, "已恢复默认", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnCustomDevice).setOnClickListener {
            showCustomDeviceDialog()
        }
    }

    private fun showCustomDeviceDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 32)
        }
        val etBrand = EditText(this).apply { hint = "品牌"; setText(cfg.customDeviceProfile?.brand ?: "") }
        val etModel = EditText(this).apply { hint = "型号"; setText(cfg.customDeviceProfile?.model ?: "") }
        val etCpu = EditText(this).apply { hint = "CPU"; setText(cfg.customDeviceProfile?.cpuModel ?: "") }
        val etGpu = EditText(this).apply { hint = "GPU"; setText(cfg.customDeviceProfile?.gpuModel ?: "") }

        layout.addView(etBrand); layout.addView(etModel); layout.addView(etCpu); layout.addView(etGpu)

        AlertDialog.Builder(this)
            .setTitle("自定义机型")
            .setView(layout)
            .setPositiveButton("确定") { _, _ ->
                cfg.customDeviceProfile = DeviceProfile(
                    id = "custom_${System.currentTimeMillis()}",
                    displayName = "${etBrand.text} ${etModel.text}",
                    brand = etBrand.text.toString(),
                    model = etModel.text.toString(),
                    manufacturer = etBrand.text.toString(),
                    device = etModel.text.toString(),
                    cpuModel = etCpu.text.toString(),
                    gpuModel = etGpu.text.toString(),
                    isCustom = true
                )
                Toast.makeText(this, "自定义机型已设置", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
